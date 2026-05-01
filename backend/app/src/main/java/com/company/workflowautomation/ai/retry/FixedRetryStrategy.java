package com.company.workflowautomation.ai.retry;

import com.company.workflowautomation.workflow_execution.application.RetryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("FIXED")
@RequiredArgsConstructor
public class FixedRetryStrategy implements RetryStrategy {

    private final RetryProperties retryProperties;

    @Override
    public long nextDelayMs(int attemptNumber) {
        return retryProperties.getFixed().getDelayMs();
    }
}