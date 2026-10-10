package com.company.workflowautomation.workflow_execution.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class WorkflowExecutorConfig {

    @Bean(name = "workflowExecutor", destroyMethod = "shutdown")
    public ExecutorService workflowExecutor(
            @Value("${workflow.executor.pool-size:5}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize);
    }
}
