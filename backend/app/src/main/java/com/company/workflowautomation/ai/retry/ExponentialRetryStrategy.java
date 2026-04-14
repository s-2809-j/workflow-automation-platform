package com.company.workflowautomation.ai.retry;

import com.company.workflowautomation.workflow_execution.application.RetryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("EXPONENTIAL")
@RequiredArgsConstructor

public class ExponentialRetryStrategy implements RetryStrategy {

    private final RetryProperties retryProperties;

    @Override
    public long nextDelayMs(int attemptNumber) {
        long base = retryProperties.getExponential().getBaseDelayMs();
        return (long) (base * Math.pow(2, attemptNumber));
    }
}