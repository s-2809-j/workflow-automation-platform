package com.company.workflowautomation.ai.dto;

import java.util.Map;

public class AiError {

    private String message;
    private String code;
    private Map<String , Object> details;

    public AiError(){}

    public AiError(String code ,String message,Map<String,Object> details)
    {
        this.code=code;
        this.message=message;
        this.details=details;
    }

    public String getMessage(){return message;}
    public String getCode(){return code;}
    public Map<String,Object> getDetails(){ return details;}
}
