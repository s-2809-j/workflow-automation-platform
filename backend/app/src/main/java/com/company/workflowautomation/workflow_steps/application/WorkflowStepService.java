//package com.company.workflowautomation.workflow_steps.application;
//
//import com.company.workflowautomation.util.SecurityUtils;
//import com.company.workflowautomation.workflow_steps.dto.CreateWorkflowStepRequest;
//import com.company.workflowautomation.workflow_steps.dto.UpdateWorkflowStepRequest;
//import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
//import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepRepository;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.persistence.EntityManager;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.PlatformTransactionManager;
//import org.springframework.transaction.support.TransactionTemplate;
//
//import java.time.Instant;
//import java.util.List;
//import java.util.UUID;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class WorkflowStepService {
//    private final WorkflowStepRepository stepRepository;
//    private final ObjectMapper objectMapper;
//    private final EntityManager entityManager;                    // ← ADD
//    private final PlatformTransactionManager transactionManager;
//    // ✅ ADD THIS - at the top of your class (after the existing fields)
//    private static final String[] SUPPORTED_STEP_TYPES = {
//            "HTTP",
//            "DATABASE",
//            "SCRIPT",
//            "EMAIL",
//            "WEBHOOK"
//    };
//    private void validateStepType(WorkflowStepEntity step) {
//        String stepType = step.getStepType();
//
//        if (stepType == null || stepType.trim().isEmpty()) {
//            throw new UnsupportedStepTypeException(
//                    "Step type is null or empty for stepId=" + step.getId());
//        }
//
//        for (String supportedType : SUPPORTED_STEP_TYPES) {
//            if (supportedType.equalsIgnoreCase(stepType)) {
//                log.debug("Step type validated successfully. stepType={}", stepType);
//                return;
//            }
//        }
//
//        throw new UnsupportedStepTypeException(
//                "Step type '" + stepType + "' is not supported. " +
//                        "Supported types are: " + String.join(", ", SUPPORTED_STEP_TYPES));
//    }
//    public static class UnsupportedStepTypeException extends RuntimeException {
//        public UnsupportedStepTypeException(String message) {
//            super(message);
//        }
//
//        public UnsupportedStepTypeException(String message, Throwable cause) {
//            super(message, cause);
//        }
//    }
//    private void setOrgContext(UUID organizationId) {
//        entityManager.createNativeQuery("SELECT set_config('app.current_organization', :orgId, true)")
//                .setParameter("orgId",organizationId.toString())
//                .getSingleResult();
//    }
//    public WorkflowStepEntity createStep(UUID stepId, UUID organizationId,
//                                         UUID workflowId,
//                                         CreateWorkflowStepRequest request) throws JsonProcessingException {
//
//        TransactionTemplate transactionTemplate =
//                new TransactionTemplate(transactionManager);
//        return transactionTemplate.execute(status -> {
//            // set org context FIRST — required for RLS + FK check
//            setOrgContext(organizationId);
//
//            WorkflowStepEntity step = new WorkflowStepEntity();
//            step.setId(stepId);
//            step.setOrganizationId(organizationId);
//            step.setWorkflowId(workflowId);
//            step.setName(request.getName());
//            step.setStepOrder(request.getStepOrder());
//            step.setStepType(request.getType());
//            step.setConfig(request.getConfig());
//
//            if (request.getDependsOn() != null) {
//                step.setDependsOn((request.getDependsOn()));
//            } else {
//                step.setDependsOn(objectMapper.createArrayNode());
//            }
//            step.setCreatedAt(Instant.now());
//            step.setUpdatedAt(Instant.now());
//            return stepRepository.save(step);
//        });
//    }
//
//
//    public List<WorkflowStepEntity> getWorkflowSteps(UUID WorkflowId)
//    {
//        UUID organizationId = SecurityUtils.getOrganizationId();
//        setOrgContext(organizationId);
//        return stepRepository.findByWorkflowIdOrderByStepOrder(WorkflowId);
//    }
//
//    public WorkflowStepEntity updateStep(UUID stepId,
//                                         UpdateWorkflowStepRequest request) throws JsonProcessingException {
//
//        UUID organizationId = SecurityUtils.getOrganizationId();
//        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
//        return transactionTemplate.execute(status -> {
//            setOrgContext(organizationId);
//
//            WorkflowStepEntity step = stepRepository.findById(stepId).orElseThrow(() ->
//                    new RuntimeException("Step not found: " + stepId));
//
//            step.setStepOrder(request.getStepOrder());
//            step.setName(request.getName());
//            step.setStepType(request.getType());
//            try {
//                step.setConfig(objectMapper.readTree(request.getConfig()));
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException("Invalid config json", e);
//            }
//
//            if (request.getDependsOn() != null) {
//                step.setDependsOn(request.getDependsOn());
//            }
//            step.setUpdatedAt(Instant.now());
//
//            return stepRepository.saveAndFlush(step);
//        });
//    }
//
//    public void deleteStep(UUID stepId)
//    {
//        UUID organizationId = SecurityUtils.getOrganizationId();
//        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
//        transactionTemplate.execute(status -> {
//            setOrgContext(organizationId);
//            stepRepository.deleteById(stepId);
//            return null;
//        });
//    }
//
//}
package com.company.workflowautomation.workflow_steps.application;

import com.company.workflowautomation.util.SecurityUtils;
import com.company.workflowautomation.workflow_steps.dto.CreateWorkflowStepRequest;
import com.company.workflowautomation.workflow_steps.dto.UpdateWorkflowStepRequest;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowStepService {

    private final WorkflowStepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

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
            return stepRepository.findByWorkflowIdOrderByStepOrder(workflowId);
        });
    }

    public WorkflowStepEntity updateStep(UUID stepId, UpdateWorkflowStepRequest request) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        return tx().execute(status -> {
            setOrgContext(organizationId);

            WorkflowStepEntity step = stepRepository.findById(stepId)
                    .orElseThrow(() -> new RuntimeException("Step not found: " + stepId));

            step.setStepOrder(request.getStepOrder());
            step.setName(request.getName());
            step.setStepType(request.getType());

            try {
                step.setConfig(objectMapper.readTree(request.getConfig()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Invalid config JSON", e);
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
            stepRepository.deleteById(stepId);
            return null;
        });
    }
}