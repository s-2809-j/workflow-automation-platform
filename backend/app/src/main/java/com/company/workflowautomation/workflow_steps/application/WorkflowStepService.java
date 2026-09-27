
package com.company.workflowautomation.workflow_steps.application;

import com.company.workflowautomation.util.SecurityUtils;
import com.company.workflowautomation.workflow_steps.dto.CreateWorkflowStepRequest;
import com.company.workflowautomation.workflow_steps.dto.UpdateWorkflowStepRequest;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepRepository;
import com.company.workflowautomation.workflow.jpa.WorkflowJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowStepService {

    private final WorkflowStepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final WorkflowJpaRepository workflowRepository;

    private static final String[] SUPPORTED_STEP_TYPES = {
            "HTTP", "LOG", "DELAY", "DATABASE", "SCRIPT", "EMAIL", "WEBHOOK"
    };

    // ─────────────────────────────────────────────────────────────
    // Public exception — shared with WorkflowStepExecutionService
    // ─────────────────────────────────────────────────────────────
    public static class UnsupportedStepTypeException extends RuntimeException {
        public UnsupportedStepTypeException(String message) { super(message); }
        public UnsupportedStepTypeException(String message, Throwable cause) { super(message, cause); }
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * MUST be called inside an active transaction so the connection-level
     * set_config stays bound to the same connection used by the subsequent
     * repository call. Never call this outside a TransactionTemplate.
     */
    private void setOrgContext(UUID organizationId) {
        entityManager
                .createNativeQuery("SELECT set_config('app.current_organization', :orgId, true)")
                .setParameter("orgId", organizationId.toString())
                .getSingleResult();
    }

    private void validateStepType(WorkflowStepEntity step) {
        String stepType = step.getStepType();
        if (stepType == null || stepType.trim().isEmpty()) {
            throw new UnsupportedStepTypeException(
                    "Step type is null or empty for stepId=" + step.getId());
        }
        for (String supported : SUPPORTED_STEP_TYPES) {
            if (supported.equalsIgnoreCase(stepType)) {
                log.debug("Step type validated. stepType={}", stepType);
                return;
            }
        }
        throw new UnsupportedStepTypeException(
                "Step type '" + stepType + "' is not supported. Supported: "
                        + String.join(", ", SUPPORTED_STEP_TYPES));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    // ─────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────

    public WorkflowStepEntity createStep(UUID stepId, UUID organizationId,
                                         UUID workflowId,
                                         CreateWorkflowStepRequest request) {
        return tx().execute(status -> {
            // set_config FIRST — required for RLS + FK check, same connection
            setOrgContext(organizationId);

            workflowRepository.findByIdAndOrganizationId(workflowId, organizationId)
                    .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

            WorkflowStepEntity step = new WorkflowStepEntity();
            step.setId(stepId);
            step.setOrganizationId(organizationId);
            step.setWorkflowId(workflowId);
            step.setName(request.getName());
            step.setStepOrder(request.getStepOrder());
            step.setStepType(request.getType());
            step.setConfig(request.getConfig());
            step.setDependsOn(request.getDependsOn() != null
                    ? request.getDependsOn()
                    : objectMapper.createArrayNode());
            step.setCreatedAt(Instant.now());
            step.setUpdatedAt(Instant.now());

            validateStepType(step);
            return stepRepository.save(step);
        });
    }

    /**
     * ✅ FIX: getWorkflowSteps now wraps the repository call in a transaction
     * so set_config is on the same DB connection. Previously this was called
     * outside a transaction which meant RLS could see an empty config value.
     */
    public List<WorkflowStepEntity> getWorkflowSteps(UUID workflowId) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        return tx().execute(status -> {
            setOrgContext(organizationId);
            return stepRepository.findByWorkflowIdAndOrganizationIdOrderByStepOrder(
                    workflowId, organizationId);
        });
    }

    public WorkflowStepEntity updateStep(UUID stepId, UpdateWorkflowStepRequest request) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        return tx().execute(status -> {
            setOrgContext(organizationId);

            WorkflowStepEntity step = stepRepository.findByIdAndOrganizationId(stepId, organizationId)
                    .orElseThrow(() -> new RuntimeException("Step not found: " + stepId));

            workflowRepository.findByIdAndOrganizationId(step.getWorkflowId(), organizationId)
                    .orElseThrow(() -> new RuntimeException("Workflow not found: " + step.getWorkflowId()));

            if (request.getStepOrder() != null) step.setStepOrder(request.getStepOrder());
            if (request.getName() != null) step.setName(request.getName());
            if (request.getType() != null) step.setStepType(request.getType());

            if (request.getConfig() != null) {
                step.setConfig(request.getConfig());
            }

            if (request.getDependsOn() != null) {
                step.setDependsOn(request.getDependsOn());
            }
            step.setUpdatedAt(Instant.now());

            validateStepType(step);
            return stepRepository.saveAndFlush(step);
        });
    }

    public void deleteStep(UUID stepId) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        tx().execute(status -> {
            setOrgContext(organizationId);
            WorkflowStepEntity step = stepRepository.findByIdAndOrganizationId(stepId, organizationId)
                    .orElseThrow(() -> new RuntimeException("Step not found: " + stepId));
            workflowRepository.findByIdAndOrganizationId(step.getWorkflowId(), organizationId)
                    .orElseThrow(() -> new RuntimeException("Workflow not found: " + step.getWorkflowId()));
            
            // Clean up dependsOn references in other steps
            List<WorkflowStepEntity> allSteps = stepRepository.findByWorkflowIdAndOrganizationIdOrderByStepOrder(
                    step.getWorkflowId(), organizationId);
            
            for (WorkflowStepEntity otherStep : allSteps) {
                if (otherStep.getId().equals(stepId)) continue;
                
                boolean changed = false;
                List<String> newDeps = new java.util.ArrayList<>();
                for (String dep : otherStep.getDependsOnList()) {
                    if (dep.equals(stepId.toString())) {
                        changed = true;
                    } else {
                        newDeps.add(dep);
                    }
                }
                
                if (changed) {
                    otherStep.setDependsOn(objectMapper.valueToTree(newDeps));
                    stepRepository.save(otherStep);
                }
            }
            
            stepRepository.delete(step);
            return null;
        });
    }

    public List<Map<String, String>> getRequiredInputs(UUID workflowId, UUID organizationId) {
        return tx().execute(status -> {
            setOrgContext(organizationId);
            List<WorkflowStepEntity> steps = stepRepository
                    .findByWorkflowIdAndOrganizationIdOrderByStepOrder(workflowId, organizationId);

            List<Map<String, String>> required = new java.util.ArrayList<>();

            for (WorkflowStepEntity step : steps) {
                JsonNode config = step.getConfig();
                if (config == null) continue;

                // EMAIL step with WORKFLOW_INPUT recipient
                if ("EMAIL".equalsIgnoreCase(step.getStepType())
                        && config.has("recipientSource")
                        && "WORKFLOW_INPUT".equalsIgnoreCase(
                        config.get("recipientSource").asText())) {

                    String inputKey = config.has("inputKey")
                            ? config.get("inputKey").asText() : "recipientEmail";

                    Map<String, String> field = new java.util.LinkedHashMap<>();
                    field.put("stepId",   step.getId().toString());
                    field.put("stepName", step.getName());
                    field.put("stepType", step.getStepType());
                    field.put("key",      inputKey);
                    field.put("label",    "Recipient Email Address");
                    field.put("type",     "email");
                    field.put("required", "true");
                    required.add(field);
                }

                // HTTP step with dynamic URL from workflow input
                if (("HTTP".equalsIgnoreCase(step.getStepType())
                        || "WEBHOOK".equalsIgnoreCase(step.getStepType()))
                        && config.has("urlSource")
                        && "WORKFLOW_INPUT".equalsIgnoreCase(
                        config.get("urlSource").asText())) {

                    String inputKey = config.has("inputKey")
                            ? config.get("inputKey").asText() : "url";

                    Map<String, String> field = new java.util.LinkedHashMap<>();
                    field.put("stepId",   step.getId().toString());
                    field.put("stepName", step.getName());
                    field.put("stepType", step.getStepType());
                    field.put("key",      inputKey);
                    field.put("label",    "Request URL");
                    field.put("type",     "url");
                    field.put("required", "true");
                    required.add(field);
                }
            }

            return required;
        });
    }
}
