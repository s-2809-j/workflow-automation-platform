package com.company.workflowautomation.workflow_execution;

import com.company.workflowautomation.workflow.infrastructure.WorkflowRunRepository;
import com.company.workflowautomation.workflow_execution.application.WorkflowExecutionService;
import com.company.workflowautomation.workflow_execution.application.WorkflowStepExecutionService;
import com.company.workflowautomation.workflow_execution.application.schedular.WorkflowScheduler;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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

    @Test
    void testSchedulerHandlesEmptyStepGraphWithoutThrowing() throws InterruptedException {
        WorkflowScheduler scheduler = new WorkflowScheduler(
                mock(WorkflowStepExecutionService.class),
                mock(PlatformTransactionManager.class),
                mock(StepExecutionRepository.class),
                mock(WorkflowRunRepository.class)
        );
        ReflectionTestUtils.setField(scheduler, "poolSize", 1);
        scheduler.init();

        AtomicBoolean succeeded = new AtomicBoolean(false);
        AtomicBoolean failed = new AtomicBoolean(false);
        Map<UUID, com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity> emptyStepMap = new HashMap<>();

        assertDoesNotThrow(() -> scheduler.execute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new HashMap<>(),
                emptyStepMap,
                () -> succeeded.set(true),
                () -> failed.set(true)
        ));

        assertTrue(succeeded.get());
        assertFalse(failed.get());
    }
}