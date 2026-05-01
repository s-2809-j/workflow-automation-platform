package com.company.workflowautomation.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "eternal.ai")
public class AiProperties {
    private String apiKey; 
    private String baseUrl;           
    private boolean mockMode;         
    private String executionPath;     
    private String workflowPath;      
    private int connectTimeout;       
    private int readTimeout;
    public String getExecutionUrl() {
        return baseUrl + executionPath;
    }

    public String getWorkflowUrl() {
        return baseUrl + workflowPath;
    }
}
