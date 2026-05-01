package com.company.workflowautomation.shared.external;

public class ExternalServiceException extends RuntimeException {

    private final String errorCode;

    public ExternalServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}