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
    private String baseUrl;           // eternal.ai.base-url
    private boolean mockMode;         // eternal.ai.mock-mode
    private String executionPath;     // eternal.ai.execution-path
    private String workflowPath;      // eternal.ai.workflow-path
    private int connectTimeout;       // eternal.ai.connect-timeout
    private int readTimeout;
    public String getExecutionUrl() {
        return baseUrl + executionPath;
    }

    // builds full URL: base-url + workflow-path
    public String getWorkflowUrl() {
        return baseUrl + workflowPath;
    }
}
