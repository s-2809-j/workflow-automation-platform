package com.company.workflowautomation.ai.shared.Exception;

public class DraftAccessException extends WorkflowBaseException {
    public DraftAccessException(String message,int statusCode) {
        super(message, 403);
    }

}
