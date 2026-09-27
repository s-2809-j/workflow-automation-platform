package com.company.workflowautomation.workflow_execution.application;

import com.company.workflowautomation.shared.tenant.TenantContextHolder;
import com.company.workflowautomation.util.SecurityUtils;
import com.company.workflowautomation.workflow_execution.application.dag.DagBuilder;
import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
import com.company.workflowautomation.workflow_execution.application.schedular.WorkflowScheduler;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionRepository;
import com.company.workflowautomation.workflow.jpa.WorkflowEntity;
import com.company.workflowautomation.workflow.jpa.WorkflowJpaRepository;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionService {
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowStepRepository stepRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final DagBuilder dagBuilder;
    private final WorkflowScheduler scheduler;
    private final PlatformTransactionManager transactionManager;
    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowJpaRepository workflowRepository;

    /**
     * Public entry-point for the HTTP controller — accepts optional runtime parameters.
     * When runtimeParams is null or empty the workflow starts with empty triggerData,
     * preserving existing behaviour for all callers that don't supply a body.
     */
    @Transactional
    public WorkflowExecutionEntity startExecution(UUID workflowId, JsonNode runtimeParams) {

        UUID organizationId = SecurityUtils.getOrganizationId();
        setOrganizationContext(organizationId);
        WorkflowEntity workflow = workflowRepository
                .findByIdAndOrganizationId(workflowId, organizationId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        // Normalise: treat null/missing body the same as empty object
        JsonNode triggerData = (runtimeParams != null && !runtimeParams.isNull() && runtimeParams.size() > 0)
                ? runtimeParams
                : objectMapper.createObjectNode();

        WorkflowExecutionEntity execution = createExecution(workflowId, organizationId, triggerData);

        try {
            List<WorkflowStepEntity> steps =
                    stepRepository.findByWorkflowIdAndOrganizationIdOrderByStepOrder(
                            workflow.getId(), organizationId);

            Map<UUID, WorkflowStepEntity> stepMap =
                    steps.stream().collect(Collectors.toMap(WorkflowStepEntity::getId, s -> s));

            Map<UUID, StepNode> graph = dagBuilder.buildGraph(steps);


            CompletableFuture.runAsync(() -> {
                TenantContextHolder.setTenantId(organizationId);
                UUID orgIdSafe = execution.getOrganizationId();
                try {
                    scheduler.execute(
                            execution.getId(),
                            orgIdSafe,
                            graph,
                            stepMap,
                            () -> markExecutionStatus(execution, "SUCCESS", null),
                            (errMsg) -> markExecutionStatus(execution, "FAILED", errMsg),
                            null  // run is managed inside scheduler
                    );
                } catch (Exception e) {
                    log.error("Async workflow execution failed. executionId={} error={}",
                            execution.getId(), e.getMessage(), e);
                    String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
                    markExecutionStatus(execution, "FAILED", msg);
                } finally {
                    TenantContextHolder.clear();
                }
            });

            return execution;

        } catch (Exception e) {
            log.error("Failed to start workflow execution. workflowId={} error={}",
                    workflowId, e.getMessage(), e);
            String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
            return markExecutionStatus(execution, "FAILED", msg);
        }
    }

    /**
     * Backward-compatible no-arg overload used by any internal callers that do not
     * supply runtime parameters (e.g. tests, scheduled triggers).
     */
    @Transactional
    public WorkflowExecutionEntity startExecution(UUID workflowId) {
        return startExecution(workflowId, null);
    }

    private WorkflowExecutionEntity createExecution(UUID workflowId, UUID organizationId, JsonNode triggerData) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("SELECT set_config('app.current_organization', :organizationId, true)")
                    .setParameter("organizationId", organizationId.toString())
                    .getSingleResult();

            WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
            execution.setId(UUID.randomUUID());
            execution.setWorkflowId(workflowId);
            execution.setOrganizationId(organizationId);
            execution.setStatus("RUNNING");
            execution.setTriggerData(triggerData != null ? triggerData : objectMapper.createObjectNode());
            execution.setStartedAt(Instant.now());
            execution.setCompletedAt(null);
            return executionRepository.saveAndFlush(execution);
        });
    }

    private WorkflowExecutionEntity markExecutionStatus(
            WorkflowExecutionEntity execution,
            String status,
            String errorMessage
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(txStatus -> {
            entityManager.createNativeQuery("SELECT set_config('app.current_organization', :organizationId, true)")
                    .setParameter("organizationId", execution.getOrganizationId().toString())
                    .getSingleResult();

            WorkflowExecutionEntity managedExecution = executionRepository
                    .findByIdAndOrganizationId(execution.getId(), execution.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Workflow execution not found"));
            managedExecution.setStatus(status);

            if (errorMessage != null && !errorMessage.isBlank()) {
                managedExecution.setErrorMessage(errorMessage);
            } else if ("FAILED".equalsIgnoreCase(status)) {
                if (managedExecution.getErrorMessage() == null || managedExecution.getErrorMessage().isBlank()) {
                    List<StepExecutionEntity> stepExecutions = stepExecutionRepository
                            .findByWorkflowExecutionIdAndOrganizationId(execution.getId(), execution.getOrganizationId());
                    String stepError = stepExecutions.stream()
                            .filter(s -> s.getStatus() == StepStatus.FAILED && s.getOutputData() != null)
                            .map(s -> {
                                JsonNode out = s.getOutputData();
                                if (out.has("error") && !out.get("error").asText("").isBlank()) {
                                    return out.get("error").asText();
                                }
                                if (out.has("reason") && !out.get("reason").asText("").isBlank()) {
                                    return out.get("reason").asText();
                                }
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse("Workflow execution failed");
                    managedExecution.setErrorMessage(stepError);
                }
            } else {
                managedExecution.setErrorMessage(null);
            }

            managedExecution.setCompletedAt(Instant.now());
            WorkflowExecutionEntity saved = executionRepository.saveAndFlush(managedExecution);
            log.info("Workflow execution status updated. executionId={} status={} errorMessage={}",
                    execution.getId(), status, managedExecution.getErrorMessage());
            return saved;
        });
    }
    public List<WorkflowExecutionEntity> getExecutions(UUID workflowId) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        return executionRepository.findByWorkflowIdAndOrganizationId(workflowId, organizationId);
    }

    public List<StepExecutionEntity> getStepExecutions(UUID executionId) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        executionRepository.findByIdAndOrganizationId(executionId, organizationId)
                .orElseThrow(() -> new RuntimeException("Workflow execution not found"));
        return stepExecutionRepository.findByWorkflowExecutionIdAndOrganizationId(
                executionId, organizationId);
    }

    private static class WorkflowNotFoundException extends RuntimeException {
        private WorkflowNotFoundException(UUID workflowId) {
            super("Workflow not found: " + workflowId);
        }
    }

    // Internal entry point for scheduled/system-triggered executions
    // Does not rely on SecurityUtils — org is passed explicitly
    @Transactional
    public WorkflowExecutionEntity startExecutionInternal(UUID workflowId, UUID organizationId) {
        setOrganizationContext(organizationId);
        WorkflowEntity workflow = workflowRepository
                .findByIdAndOrganizationId(workflowId, organizationId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        WorkflowExecutionEntity execution = createExecution(workflowId, organizationId, objectMapper.createObjectNode());

        try {
            List<WorkflowStepEntity> steps =
                    stepRepository.findByWorkflowIdAndOrganizationIdOrderByStepOrder(
                            workflow.getId(), organizationId);

            Map<UUID, WorkflowStepEntity> stepMap =
                    steps.stream().collect(Collectors.toMap(WorkflowStepEntity::getId, s -> s));

            Map<UUID, StepNode> graph = dagBuilder.buildGraph(steps);

            CompletableFuture.runAsync(() -> {
                TenantContextHolder.setTenantId(organizationId);
                try {
                    scheduler.execute(
                            execution.getId(),
                            organizationId,
                            graph,
                            stepMap,
                            () -> markExecutionStatus(execution, "SUCCESS", null),
                            (errMsg) -> markExecutionStatus(execution, "FAILED", errMsg),
                            null
                    );
                } catch (Exception e) {
                    log.error("Scheduled workflow execution failed. executionId={} error={}",
                            execution.getId(), e.getMessage(), e);
                    String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
                    markExecutionStatus(execution, "FAILED", msg);
                } finally {
                    TenantContextHolder.clear();
                }
            });

            return execution;

        } catch (Exception e) {
            log.error("Failed to start scheduled execution. workflowId={} error={}",
                    workflowId, e.getMessage(), e);
            String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
            return markExecutionStatus(execution, "FAILED", msg);
        }
    }

    private void setOrganizationContext(UUID organizationId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :organizationId, true)")
                .setParameter("organizationId", organizationId.toString())
                .getSingleResult();
    }

}
