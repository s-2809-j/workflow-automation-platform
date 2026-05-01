package com.company.workflowautomation.workflow_execution;

import com.company.workflowautomation.workflow_execution.application.WorkflowExecutionService;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class WorkflowExecutionServiceTest {

    @Autowired
    private WorkflowExecutionService workflowExecutionService;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Test
    void testServiceIsConfigured() {
        assertNotNull(workflowExecutionService);
        System.out.println("✅ WorkflowExecutionService is properly configured");
    }

    @Test
    void testRepositoryIsConfigured() {
        assertNotNull(workflowExecutionRepository);
        long count = workflowExecutionRepository.count();
        System.out.println("✅ Repository working, total executions in DB: " + count);
    }
}