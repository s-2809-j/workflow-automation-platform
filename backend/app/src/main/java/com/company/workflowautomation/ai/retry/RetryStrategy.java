package com.company.workflowautomation.ai.retry;

public interface RetryStrategy {
    long nextDelayMs(int attemptNumber);
}
