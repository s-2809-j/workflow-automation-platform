package com.company.workflowautomation.ai.infrastructure;

import com.company.workflowautomation.ai.config.AiProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    private final AiProperties aiProperties;

    public RestTemplateConfig(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(
                        aiProperties.getConnectTimeout()))  // from yml: 2000
                .setReadTimeout(Duration.ofMillis(
                        aiProperties.getReadTimeout()))     // from yml: 5000
                .build();
    }
}
