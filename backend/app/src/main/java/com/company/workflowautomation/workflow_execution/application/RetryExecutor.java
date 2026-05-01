package com.company.workflowautomation.workflow_execution.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static java.lang.Thread.sleep;

@Component
@RequiredArgsConstructor
public class RetryExecutor {
    private final RetryProperties properties;

    public void execute(Runnable task){
        int attempt = 0;
        long delay = properties.getInitialDelayMs();

        while (attempt < properties.getMaxAttempts()) {
            try {
                attempt++;
                task.run();
                return;
            } catch (Exception e) {

                if (!isRetryable(e)) {
                    throw e;

                }
                if (attempt >= properties.getMaxAttempts()) {
                    throw e;
                }
                sleep(delay);
                delay = Math.min((long) (delay * properties.getMultiplier()), properties.getMaxDelayMs());

            }
        }
    }

    private boolean isRetryable(Exception e) {
        return !(e instanceof IllegalArgumentException);
    }

    private void sleep(long delay) {
        try {
            Thread.sleep(delay);

        }
        catch (InterruptedException ignored){};
    }
}
