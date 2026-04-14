//package com.company.workflowautomation.workflow_execution.application;
//
//import com.company.workflowautomation.ai.adapter.AiAdapter;
//import com.company.workflowautomation.ai.application.AiService;
//import com.company.workflowautomation.ai.dto.AiRequest;
//import com.company.workflowautomation.ai.dto.AiResponse;
//import com.company.workflowautomation.ai.retry.RetryOrchestrator;
//import com.company.workflowautomation.workflow.domain.WorkflowRun;
//import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
//import com.company.workflowautomation.workflow_execution.application.schedular.StepAttemptTransactionService;
//import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
//import com.company.workflowautomation.workflow_execution.model.StepStatus;
//import com.company.workflowautomation.workflow_steps.application.WorkflowStepService;
//import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.UUID;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class WorkflowStepExecutionService {
//
//    private final StepExecutionRepository stepExecutionRepository;
//    private final StepAttemptTransactionService stepAttemptTransactionService;
//    private final RetryProperties retryProperties;
//    private final AiAdapter aiAdapter;
//    private final RetryOrchestrator retryOrchestrator;
//    private static final String[] SUPPORTED_STEP_TYPES = {
//            "HTTP",
//            "LOG",
//            "DELAY",
//            "DATABASE",
//            "SCRIPT",
//            "EMAIL",
//            "WEBHOOK"
//    };
//    public void executeStep(UUID executionId, UUID orgId,
//                            WorkflowStepEntity step, StepNode node, WorkflowRun run)
//            throws JsonProcessingException, InterruptedException {
//        log.info("Starting step execution. workflowId={} stepId={} orgId={} stepName={}",
//                run.getWorkflowId(), step.getId(), orgId, step.getName());
//        if (orgId == null) {
//            log.error("OrgId is null for stepId={}", step.getId());
//            throw new RuntimeException("orgId is null in async thread");
//        }
////        System.out.println("ORG ID IN STEP = " + orgId);
//        validateStepType(step);
//        // ✅ All DB calls (existsBy, findBy, saveAndFlush) now inside a transaction
//        // with set_config on the same connection — no RLS "" risk
//        stepAttemptTransactionService.initializeStepExecution(
//                executionId, orgId, step.getId());
//
//        // ✅ This read is safe now — connection is warmed by initializeStepExecution
//        boolean alreadyExecuted = stepExecutionRepository
//                .existsByWorkflowExecutionIdAndStepIdAndStatus(
//                        executionId, step.getId(), StepStatus.SUCCESS);
//
//        if (alreadyExecuted) {
//            node.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.SUCCESS);
//            return;
//        }
//
//        boolean started = node.getStatus()
//                .compareAndSet(StepStatus.PENDING, StepStatus.RUNNING);
//        if (!started) return;
//
////        int maxRetries = retryProperties.getMaxAttempts();      // ← from yml
////        long baseDelayMs = retryProperties.getStep().getBaseDelayMs();
//        while (true){
//            try {
//                stepAttemptTransactionService.incrementAttemptCount(
//                        executionId, orgId, step.getId());
//                stepAttemptTransactionService.executeSingleAttempt(
//                        executionId, orgId, step, node);
//                run.markSuccess();
//                log.info("Step execution completed successfully. stepId={} executionId={}",
//                        step.getId(), executionId);
//                return;
//
//
//        } catch (WorkflowStepService.UnsupportedStepTypeException e) {
//            log.error("Step type is not supported. stepId={} stepType={} error={}",
//                    step.getId(), step.getStepType(), e.getMessage());
//            stepAttemptTransactionService.markFinalFailure(
//                    executionId, orgId, step, node, e);
//            AiRequest aiRequest = buildAiRequest(
//                    run.getWorkflowId(), step, e);
//            AiResponse aiResponse = aiAdapter.analyzeExecution(aiRequest);
//            retryOrchestrator.handle(run, aiResponse);
//            throw new RuntimeException(
//                    "Step type '" + step.getStepType() + "' is not supported: "
//                            + e.getMessage(), e);
//
//        } catch (Exception e) {
//            log.warn("Step failed for stepId={} error={}",
//                    step.getId(), e.getMessage());
//            AiRequest aiRequest = buildAiRequest(
//                    run.getWorkflowId(), step, e);
//            AiResponse aiResponse = aiAdapter.analyzeExecution(aiRequest);
//            boolean shouldRetry = retryOrchestrator.handle(run, aiResponse);
//            if (!shouldRetry) {
//                stepAttemptTransactionService.markFinalFailure(
//                        executionId, orgId, step, node, e);
//                throw new RuntimeException(
//                        "Step permanently failed after AI decision: "
//                                + e.getMessage(), e);
//            }
//            log.info("Retrying step stepId={} retryCount={}",
//                    step.getId(), run.getRetryCount());
//            }
//        }
//
//
//    }
//
//    private AiRequest buildAiRequest(UUID workflowId, WorkflowStepEntity step, Exception e) {
//        return AiRequest.builder()
//                .workflowId(workflowId)
//                .runId(step.getId())
//                .errorType(e.getClass().getSimpleName())   // "timeout" triggers retry in mock
//                .durationMs(0L)
//                .build();
//    }
//    private void validateStepType(WorkflowStepEntity step) {
//        String stepType = step.getStepType();
//
//        if (stepType == null || stepType.trim().isEmpty()) {
//            throw new WorkflowStepService.UnsupportedStepTypeException(
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
//        throw new WorkflowStepService.UnsupportedStepTypeException(
//                "Step type '" + stepType + "' is not supported. " +
//                        "Supported types are: " + String.join(", ", SUPPORTED_STEP_TYPES));
//    }
//    public static class UnsupportedStepTypeException extends RuntimeException {
//        public UnsupportedStepTypeException(String message) {
//            super(message);
//        }
//    }
//}
package com.company.workflowautomation.workflow_execution.application;

import com.company.workflowautomation.ai.adapter.AiAdapter;
import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.ai.retry.RetryOrchestrator;
import com.company.workflowautomation.workflow.domain.WorkflowRun;
import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
import com.company.workflowautomation.workflow_execution.application.schedular.StepAttemptTransactionService;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
// ✅ Use the single canonical exception from WorkflowStepService (not a duplicate here)
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

    // ✅ Keep supported types in sync with WorkflowStepService
    private static final String[] SUPPORTED_STEP_TYPES = {
            "HTTP", "LOG", "DELAY", "DATABASE", "SCRIPT", "EMAIL", "WEBHOOK"
    };

    public void executeStep(UUID executionId, UUID orgId,
                            WorkflowStepEntity step, StepNode node, WorkflowRun run)
            throws InterruptedException {

        log.info("Starting step execution. workflowId={} stepId={} orgId={} stepName={}",
                run.getWorkflowId(), step.getId(), orgId, step.getName());

        if (orgId == null) {
            log.error("OrgId is null for stepId={}", step.getId());
            throw new RuntimeException("orgId is null in async thread");
        }

        // ✅ Validate step type using canonical exception from WorkflowStepService
        validateStepType(step);

        stepAttemptTransactionService.initializeStepExecution(executionId, orgId, step.getId());

        boolean alreadyExecuted = stepExecutionRepository
                .existsByWorkflowExecutionIdAndStepIdAndStatus(
                        executionId, step.getId(), StepStatus.SUCCESS);

        if (alreadyExecuted) {
            node.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.SUCCESS);
            return;
        }

        boolean started = node.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.RUNNING);
        if (!started) return;

        while (true) {
            try {
                stepAttemptTransactionService.incrementAttemptCount(executionId, orgId, step.getId());
                stepAttemptTransactionService.executeSingleAttempt(executionId, orgId, step, node);
                run.markSuccess();
                log.info("Step execution completed successfully. stepId={} executionId={}",
                        step.getId(), executionId);
                return;

            } catch (WorkflowStepService.UnsupportedStepTypeException e) {
                // ✅ Use canonical exception - no need for local duplicate
                log.error("Step type not supported. stepId={} stepType={} error={}",
                        step.getId(), step.getStepType(), e.getMessage());
                stepAttemptTransactionService.markFinalFailure(executionId, orgId, step, node, e);
                AiRequest aiRequest = buildAiRequest(run.getWorkflowId(), step, e);
                AiResponse aiResponse = aiAdapter.analyzeExecution(aiRequest);
                retryOrchestrator.handle(run, aiResponse);
                throw new RuntimeException(
                        "Step type '" + step.getStepType() + "' is not supported: " + e.getMessage(), e);

            } catch (Exception e) {
                log.warn("Step failed for stepId={} error={}", step.getId(), e.getMessage());
                AiRequest aiRequest = buildAiRequest(run.getWorkflowId(), step, e);
                AiResponse aiResponse = aiAdapter.analyzeExecution(aiRequest);
                boolean shouldRetry = retryOrchestrator.handle(run, aiResponse);
                if (!shouldRetry) {
                    stepAttemptTransactionService.markFinalFailure(executionId, orgId, step, node, e);
                    throw new RuntimeException(
                            "Step permanently failed after AI decision: " + e.getMessage(), e);
                }
                log.info("Retrying step stepId={} retryCount={}", step.getId(), run.getRetryCount());
            }
        }
    }

    private AiRequest buildAiRequest(UUID workflowId, WorkflowStepEntity step, Exception e) {
        return AiRequest.builder()
                .workflowId(workflowId)
                .runId(step.getId())
                .errorType(e.getClass().getSimpleName())
                .durationMs(0L)
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
    // ✅ REMOVED: duplicate UnsupportedStepTypeException inner class
    //    Use WorkflowStepService.UnsupportedStepTypeException everywhere instead
}