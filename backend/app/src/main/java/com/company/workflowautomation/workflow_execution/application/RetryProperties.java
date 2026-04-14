package com.company.workflowautomation.workflow_execution.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "workflow.retry")
@Getter
@Setter
public class RetryProperties {
    private int maxAttempts = 3;
    private long initialDelayMs = 500L;       // ← was "initialDelays" (typo fixed)
    private double multiplier = 2.0;
    private long maxDelayMs = 5000L;

    // ← was missing entirely
    private Step step = new Step();
    private Fixed fixed = new Fixed();             // ← add this
    private Exponential exponential = new Exponential();

    @Getter
    @Setter
    public static class Step {
        private long baseDelayMs = 200L;
    }
    @Getter @Setter
    public static class Fixed {                    // ← add this
        private long delayMs = 2000L;
    }

    @Getter @Setter
    public static class Exponential {              // ← add this
        private long baseDelayMs = 1000L;
    }
}
