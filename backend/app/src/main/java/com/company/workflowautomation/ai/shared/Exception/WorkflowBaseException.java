package com.company.workflowautomation.ai.shared.Exception;

public class WorkflowBaseException extends RuntimeException {

    private final int statusCode;

    public WorkflowBaseException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public WorkflowBaseException(String message, int statusCode, Throwable cause) {
        super(message,cause);
        this.statusCode = statusCode;
    }
    public int getStatusCode() {
        return statusCode;
    }

}
