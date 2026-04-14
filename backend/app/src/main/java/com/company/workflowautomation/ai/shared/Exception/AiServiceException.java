package com.company.workflowautomation.ai.shared.Exception;

public class AiServiceException extends WorkflowBaseException {
    public AiServiceException(String message, Throwable cause) {
        super(message,502,cause);
    }
}
