package com.company.workflowautomation.workflow_execution.application;

import com.company.workflowautomation.ai.adapter.AiAdapter;
import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.ai.retry.RetryOrchestrator;
import com.company.workflowautomation.workflow.domain.WorkflowRun;
import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
import com.company.workflowautomation.workflow_execution.application.schedular.StepAttemptTransactionService;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.company.workflowautomation.workflow_steps.application.WorkflowStepService;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowStepExecutionService {

    private final StepExecutionRepository stepExecutionRepository;
    private final StepAttemptTransactionService stepAttemptTransactionService;
    private final RetryProperties retryProperties;
    private final AiAdapter aiAdapter;
    private final RetryOrchestrator retryOrchestrator;

    private static final String[] SUPPORTED_STEP_TYPES = {
            "HTTP", "LOG", "DELAY", "DATABASE", "SCRIPT", "EMAIL", "WEBHOOK", "ACTION"
    };

    public void executeStep(UUID executionId, UUID orgId,
                            WorkflowStepEntity step, StepNode node, WorkflowRun run)
            {

        log.info("Starting step execution. workflowId={} stepId={} orgId={} stepName={}",
                run.getWorkflowId(), step.getId(), orgId, step.getName());

        if (orgId == null) {
            log.error("OrgId is null for stepId={}", step.getId());
            throw new RuntimeException("orgId is null in async thread");
        }

        validateStepType(step);

        stepAttemptTransactionService.initializeStepExecution(executionId, orgId, step.getId());

        boolean alreadyExecuted = stepExecutionRepository
                .existsByWorkflowExecutionIdAndStepIdAndOrganizationIdAndStatus(
                        executionId, step.getId(), orgId, StepStatus.SUCCESS);

        if (alreadyExecuted) {
            node.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.SUCCESS);
            return;
        }

        boolean started = node.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.RUNNING);
        if (!started) return;

        int maxAttempts = retryProperties.getMaxAttempts();

        while (true) {
            try {
                stepAttemptTransactionService.incrementAttemptCount(executionId, orgId, step.getId());
                stepAttemptTransactionService.executeSingleAttempt(executionId, orgId, step, node);
                log.info("Step execution completed successfully. stepId={} executionId={}",
                        step.getId(), executionId);
                return;

            } catch (WorkflowStepService.UnsupportedStepTypeException e) {
                log.error("Step type not supported. stepId={} stepType={} error={}",
                        step.getId(), step.getStepType(), e.getMessage());
                stepAttemptTransactionService.markFinalFailure(
                        executionId, orgId, step, node, e, null);
                AiRequest aiRequest = buildAiRequest(run.getWorkflowId(), step, e);

                AiResponse aiResponse = null;
                try {
                    aiResponse = aiAdapter.analyzeExecution(aiRequest);
                } catch (Exception aiEx) {
                    log.warn("AI analysis failed for stepId={}. Proceeding without AI response. error={}",
                            step.getId(), aiEx.getMessage());
                    stepAttemptTransactionService.markFinalFailure(
                            executionId, orgId, step, node, e, "AI_ANALYSIS_FAILED");
                }
                retryOrchestrator.handle(run, aiResponse, executionId, orgId, step.getId());
                throw new RuntimeException(
                        "Step type '" + step.getStepType() + "' is not supported: " + e.getMessage(), e);

            } catch (Exception e) {
                int attemptCount = stepExecutionRepository
                        .findByWorkflowExecutionIdAndStepIdAndOrganizationId(executionId, step.getId(), orgId)
                        .map(StepExecutionEntity::getAttemptCount)
                        .orElse(0);

                log.error("Step attempt {}/{} failed. stepId={} stepName={} error={}",
                        attemptCount, maxAttempts, step.getId(), step.getName(), e.getMessage(), e);

                AiRequest aiRequest = buildAiRequest(run.getWorkflowId(), step, e);
                AiResponse aiResponse = null;
                try {
                    aiResponse = aiAdapter.analyzeExecution(aiRequest);
                } catch (Exception aiEx) {
                    log.warn("AI analysis failed for stepId={}. Proceeding without AI response. error={}",
                            step.getId(), aiEx.getMessage(), aiEx);
                }

                boolean shouldRetry = retryOrchestrator.handle(
                        run, aiResponse, executionId, orgId, step.getId());

                attemptCount = stepExecutionRepository
                        .findByWorkflowExecutionIdAndStepIdAndOrganizationId(
                                executionId, step.getId(), orgId)
                        .map(StepExecutionEntity::getAttemptCount)
                        .orElse(attemptCount);

                if (!shouldRetry || attemptCount >= maxAttempts) {
                    String reason = attemptCount >= maxAttempts
                            ? "RETRY_LIMIT_EXHAUSTED" : null;
                    stepAttemptTransactionService.markFinalFailure(
                            executionId, orgId, step, node, e, reason);
                    log.error("Step permanently failed. stepId={} stepName={} executionId={} error={}",
                            step.getId(), step.getName(), executionId, e.getMessage(), e);
                    String rawMsg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
                    throw new RuntimeException(
                            "Step '" + step.getName() + "' permanently failed after " + attemptCount + " attempt(s): "
                                    + rawMsg, e);
                }

                log.info("Retrying step. stepId={} attempt={}/{}",
                        step.getId(), attemptCount, maxAttempts);
            }
        }
    }

    private AiRequest buildAiRequest(UUID workflowId, WorkflowStepEntity step, Exception e) {
        return AiRequest.builder()
                .workflowId(workflowId)
                .runId(step.getId())
                .errorType(e.getClass().getSimpleName())
                .durationMs(System.currentTimeMillis())
                .build();
    }

    private void validateStepType(WorkflowStepEntity step) {
        String stepType = step.getStepType();
        if (stepType == null || stepType.trim().isEmpty()) {
            throw new WorkflowStepService.UnsupportedStepTypeException(
                    "Step type is null or empty for stepId=" + step.getId());
        }
        for (String supported : SUPPORTED_STEP_TYPES) {
            if (supported.equalsIgnoreCase(stepType)) return;
        }
        throw new WorkflowStepService.UnsupportedStepTypeException(
                "Step type '" + stepType + "' is not supported. Supported: "
                        + String.join(", ", SUPPORTED_STEP_TYPES));
    }
}
