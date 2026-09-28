package com.company.workflowautomation.workflow.domain;

public enum WorkflowRunStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRYING;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean isRetryable() {
        return this == RUNNING || this == RETRYING;
    }
}