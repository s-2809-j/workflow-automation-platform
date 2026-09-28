package com.company.workflowautomation.ai.retry;

import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.workflow.domain.WorkflowRun;
import com.company.workflowautomation.workflow.infrastructure.WorkflowRunRepository;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryOrchestrator {
    private final WorkflowRunRepository workflowRunRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, RetryStrategy> retryStrategies;
    @PersistenceContext
    private EntityManager entityManager;

    private void setOrgContext(UUID orgId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, true)")
                .setParameter("orgId", orgId.toString())
                .getSingleResult();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean handle(WorkflowRun run, AiResponse aiResponse,
                          UUID executionId, UUID orgId, UUID stepId) {

        setOrgContext(orgId);
        if (aiResponse == null) {
            log.warn("AiResponse is null for executionId={} stepId={}. Defaulting to no-retry.", executionId, stepId);
            markFailed(run, executionId, orgId, stepId, "AI_UNAVAILABLE");
            return false;
        }
        AiResponse.RetryDecision decision = aiResponse.getRetryDecision();
        String nextAction = aiResponse.getNextAction();

        WorkflowRun freshRun = workflowRunRepository
                .findByIdAndOrganizationId(run.getId(), orgId)
                .orElseThrow(() -> new IllegalStateException(
                        "WorkflowRun not found for retry: runId=" + run.getId()));

        StepExecutionEntity stepExecution = stepExecutionRepository
                .findByWorkflowExecutionIdAndStepIdAndOrganizationId(executionId, stepId, orgId)
                .orElseThrow(() -> new IllegalStateException(
                        "StepExecution not found for retry: executionId=" + executionId
                                + " stepId=" + stepId));

        int attemptCount = stepExecutionRepository
                .findByWorkflowExecutionIdAndStepIdAndOrganizationId(executionId, stepId, orgId)
                .map(StepExecutionEntity::getAttemptCount)
                .orElse(stepExecution.getAttemptCount());
        int maxRetries = decision.getMaxRetries() > 0
                ? decision.getMaxRetries()
                : 3;

        log.info("Handling AI decision for runId={} action={} shouldRetry={} stepAttemptCount={}",
                freshRun.getId(), nextAction, decision.isShouldRetry(), attemptCount);

        if ("FAIL_WORKFLOW".equals(nextAction) || !decision.isShouldRetry()) {
            String reason = aiResponse.getAnomaly() != null
                    ? aiResponse.getAnomaly().getType()
                    : "AI_DECIDED_NO_RETRY";
            markFailed(freshRun, executionId, orgId, stepId, reason);
            return false;
        }

        if (attemptCount >= maxRetries) {
            log.warn("Retry limit reached for executionId={} stepId={} after {} attempts (max={})",
                    executionId, stepId, attemptCount, maxRetries);
            markFailed(freshRun, executionId, orgId, stepId, "RETRY_LIMIT_EXHAUSTED");
            return false;
        }

        if (freshRun.getStatus().isTerminal()) {
            log.warn("Skipping retry — WorkflowRun already terminal. runId={} status={}",
                    freshRun.getId(), freshRun.getStatus());
            return false;
        }

        RetryStrategy strategy = retryStrategies.getOrDefault(
                decision.getStrategy(),
                retryStrategies.get("FIXED")
        );

        long delayMs = strategy.nextDelayMs(freshRun.getRetryCount());

        log.info("Retrying runId={} stepAttempt={}/{} delayMs={} strategy={}",
                freshRun.getId(), attemptCount + 1, maxRetries, delayMs, decision.getStrategy());

        freshRun.markRetrying("Retrying after failure — strategy: " + decision.getStrategy());
        workflowRunRepository.saveAndFlush(freshRun);

        try {
            Thread.sleep(Math.min(delayMs, 5000)); // hard cap at 5s max per retry sleep
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted for runId={}", freshRun.getId());
            return false; // don't retry if interrupted
        }


        return true;
    }

    private void markFailed(WorkflowRun run, UUID executionId, UUID orgId,
                            UUID stepId, String reason) {
        setOrgContext(orgId);
        log.error("Workflow permanently failed. runId={} reason={}", run.getId(), reason);

        workflowRunRepository.findByIdAndOrganizationId(run.getId(), orgId)
                .ifPresent(freshRun -> {
                    if (!freshRun.getStatus().isTerminal()) {
                        freshRun.markFailed(reason);
                        workflowRunRepository.saveAndFlush(freshRun);
                    }
                });

        stepExecutionRepository.findByWorkflowExecutionIdAndStepIdAndOrganizationId(
                        executionId, stepId, orgId)
                .ifPresent(stepExecution -> {
                    ObjectNode output = stepExecution.getOutputData() != null
                            && stepExecution.getOutputData().isObject()
                            ? (ObjectNode) stepExecution.getOutputData().deepCopy()
                            : objectMapper.createObjectNode();
                    output.put("reason", reason);
                    stepExecution.setOutputData(output);
                    stepExecution.setStatus(StepStatus.FAILED);
                    stepExecution.setUpdatedAt(Instant.now());
                    stepExecutionRepository.saveAndFlush(stepExecution);
                });

        workflowExecutionRepository.findByIdAndOrganizationId(executionId, orgId)
                .ifPresent(execution -> {
                    execution.setStatus("FAILED");
                    execution.setErrorMessage(reason);
                    execution.setCompletedAt(Instant.now());
                    workflowExecutionRepository.saveAndFlush(execution);
                });
    }
}
