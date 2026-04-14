package com.company.workflowautomation.ai.shared.Exception;

public class WorkflowGenerationException extends WorkflowBaseException {

    public WorkflowGenerationException(String message,Throwable cause) {
        super(message,500, cause);
    }
}
