package com.company.workflowautomation.ai.retry;

import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.workflow.domain.WorkflowRun;
import com.company.workflowautomation.workflow.infrastructure.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryOrchestrator {
    private final WorkflowRunRepository workflowRunRepository;
    private final Map<String, RetryStrategy> retryStrategies;

    public boolean handle(WorkflowRun run, AiResponse aiResponse) {
        AiResponse.RetryDecision decision = aiResponse.getRetryDecision();
        String nextAction = aiResponse.getNextAction();

        log.info("Handling AI decision for runId={} action={} shouldRetry={}",
                run.getId(), nextAction, decision.isShouldRetry());

        if ("FAIL_WORKFLOW".equals(nextAction) || !decision.isShouldRetry()) {
            String reason = aiResponse.getAnomaly() != null
                    ? aiResponse.getAnomaly().getType()
                    : "AI_DECIDED_NO_RETRY";
            markFailed(run, reason);
            return false;
        }

        if (run.getRetryCount() >= decision.getMaxRetries()) {
            log.warn("Retry limit reached for runId={} after {} attempts",
                    run.getId(), run.getRetryCount());
            markFailed(run, "RETRY_LIMIT_EXHAUSTED");
            return false;
        }

        RetryStrategy strategy = retryStrategies.getOrDefault(
                decision.getStrategy(),
                retryStrategies.get("FIXED")   // safe fallback
        );

        long delayMs = strategy.nextDelayMs(run.getRetryCount());

        log.info("Retrying runId={} attempt={} delayMs={} strategy={}",
                run.getId(), run.getRetryCount() + 1, delayMs, decision.getStrategy());

        // update DB: status=RETRYING, retryCount++
        run.markRetrying("Retrying after failure — strategy: " + decision.getStrategy());
        workflowRunRepository.save(run);

        // apply the delay (this blocks the current thread intentionally)
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted for runId={}", run.getId());
        }

        return true;
    }

    private void handleRetry(WorkflowRun run, AiResponse.RetryDecision decision) {
        RetryStrategy strategy = retryStrategies.getOrDefault(
                decision.getStrategy(),
                retryStrategies.get("FIXED")
        );

        long delayMs = strategy.nextDelayMs(run.getRetryCount());

        log.info("Scheduling retry for runId={} attempt={} delayMs={} strategy={}",
                run.getId(), run.getRetryCount() + 1, delayMs, decision.getStrategy());

        run.markRetrying("Retrying after failure");
        workflowRunRepository.save(run);

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted for runId={}", run.getId());
        }
    }

    private void markFailed(WorkflowRun run, String reason) {
        log.error("Workflow permanently failed. runId={} reason={}", run.getId(), reason);
        run.markFailed(reason);
        workflowRunRepository.save(run);
    }

}
