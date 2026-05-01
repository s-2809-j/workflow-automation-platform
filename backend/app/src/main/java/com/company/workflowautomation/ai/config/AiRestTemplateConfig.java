package com.company.workflowautomation.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiRestTemplateConfig {

    @Value("${workflow.ai.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${workflow.ai.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Value("${workflow.step.connect-timeout-ms:5000}")
    private int stepConnectTimeoutMs;

    @Value("${workflow.step.read-timeout-ms:15000}")
    private int stepReadTimeoutMs;

    // Used by AiService for calls to your AI API
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    // Used by StepAttemptTransactionService for HTTP/WEBHOOK steps
    @Bean("stepRestTemplate")
    public RestTemplate stepRestTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(stepConnectTimeoutMs);
        factory.setReadTimeout(stepReadTimeoutMs);
        return new RestTemplate(factory);
    }
}