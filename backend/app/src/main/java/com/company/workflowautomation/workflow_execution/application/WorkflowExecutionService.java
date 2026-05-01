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
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionService {
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowStepRepository stepRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final DagBuilder dagBuilder;
    private final WorkflowScheduler scheduler;
    private final PlatformTransactionManager transactionManager;
    private final StepExecutionRepository stepExecutionRepository;

    public WorkflowExecutionEntity startExecution(UUID workflowId) {

        UUID organizationId = SecurityUtils.getOrganizationId();
        WorkflowExecutionEntity execution = createExecution(workflowId, organizationId);

        try {
            List<WorkflowStepEntity> steps =
                    stepRepository.findByWorkflowIdOrderByStepOrder(workflowId);

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
                            () -> markExecutionStatus(execution, "FAILED", null)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    markExecutionStatus(execution, "FAILED", e.getMessage());
                }
            });

            return execution;

        } catch (Exception e) {
            e.printStackTrace();
            return markExecutionStatus(execution, "FAILED", e.getMessage());
        }
    }

    private WorkflowExecutionEntity createExecution(UUID workflowId, UUID organizationId) {
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
            execution.setTriggerData(objectMapper.createObjectNode());
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

            WorkflowExecutionEntity managedExecution = executionRepository.findById(execution.getId())
                    .orElseThrow(() -> new RuntimeException("Workflow execution not found"));
            managedExecution.setStatus(status);
            managedExecution.setErrorMessage(errorMessage);
            managedExecution.setCompletedAt(Instant.now());
            WorkflowExecutionEntity saved = executionRepository.saveAndFlush(managedExecution);
            System.out.println("Workflow " + status);
            System.out.println("AFTER SAVE EXECUTION");
            return saved;
        });
    }
    public List<WorkflowExecutionEntity> getExecutions(UUID workflowId) {
        return executionRepository.findByWorkflowId(workflowId);
    }
    public List<StepExecutionEntity> getStepExecutions(UUID executionId) {
        return stepExecutionRepository.findByWorkflowExecutionId(executionId);
    }

}
