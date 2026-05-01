package com.company.workflowautomation.workflow_execution.application.schedular;

import com.company.workflowautomation.workflow.domain.WorkflowRun;
import com.company.workflowautomation.workflow.infrastructure.WorkflowRunRepository;
import com.company.workflowautomation.workflow_execution.application.WorkflowStepExecutionService;
import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowScheduler {
    @Value("${workflow.executor.pool-size:5}")
    private int poolSize;
    private final WorkflowStepExecutionService workflowStepExecutionService;
    private ExecutorService executor;
    private final PlatformTransactionManager transactionManager;
    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowRunRepository workflowRunRepository;
    @PersistenceContext
   private EntityManager entityManager;

    public boolean canExecute(StepNode node, Map<UUID, StepNode> graph) {

        for (UUID parentId : node.getDependencies()) {
            StepNode parent = graph.get(parentId);
            if (parent.getStatus().get() != StepStatus.SUCCESS) {
                return false;
            }
        }

        return true;
    }

    public void execute(
            UUID executionId,
            UUID orgId,
            Map<UUID, StepNode> graph,
            Map<UUID, WorkflowStepEntity> stepMap,
            Runnable onSuccess,
            Runnable onFailure
    ) throws InterruptedException {
        log.info("Scheduler started. executionId={} steps={}", executionId, graph.size());;

        UUID workflowId = stepMap.values().iterator().next().getWorkflowId();
        WorkflowRun run = new WorkflowRun(workflowId, orgId);
        run.markRunning();
        workflowRunRepository.save(run);  // persisted once here
        log.info("WorkflowRun created. runId={}", run.getId());

        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(graph.size());

        for (StepNode node : graph.values()) {
            if (node.getInDegree().get() == 0 && node.getStatus().get() == StepStatus.PENDING) {
                log.debug("Submitting root step. stepId={}", node.getStepId());

                submit(
                        node.getStepId(),
                        executionId,
                        orgId,
                        graph,
                        failed,
                        latch,
                        stepMap,
                        run,
                        onSuccess,
                        onFailure
                );
            }
        }

    }

    private void submit(
            UUID stepId,
            UUID executionId,
            UUID orgId,
            Map<UUID, StepNode> graph,
            AtomicBoolean failed,
            CountDownLatch latch,
            Map<UUID, WorkflowStepEntity> stepMap,
            WorkflowRun run,
            Runnable onSuccess,
            Runnable onFailure
    ) {

        executor.submit(() -> {

            StepNode node = graph.get(stepId);

            if (node.getStatus().get() == StepStatus.SKIPPED) {
                latch.countDown();
                return;
            }

            try {
                WorkflowStepEntity step = stepMap.get(stepId);

                log.info("Executing step. name={} stepId={}", step.getName(), stepId);



                workflowStepExecutionService.executeStep(executionId, orgId, step,node,run);


                if (node.getStatus().get() == StepStatus.FAILED) {
                    failed.set(true);
                    propagateSkip(node,executionId,orgId);
                }

                if (node.getStatus().get() == StepStatus.SUCCESS) {

                    for (StepNode child : node.getChildren()) {

                        int updated = child.getInDegree().decrementAndGet();

                        if (updated == 0) {

                            if (child.getStatus().get() == StepStatus.SKIPPED) {
                                continue;
                            }

                            if (canExecute(child, graph)) {
                                submit(
                                        child.getStepId(),
                                        executionId,
                                        orgId,
                                        graph,
                                        failed,
                                        latch,
                                        stepMap,
                                        run,
                                        onSuccess,
                                        onFailure
                                );
                            } else {
                                child.getStatus()
                                        .compareAndSet(StepStatus.PENDING, StepStatus.SKIPPED);
                            }
                        }
                    }
                }

            } catch (Exception e) {

                failed.set(true);

                log.error("FAILED STEP: {}", stepMap.get(stepId).getName());

                propagateSkip(node,executionId,orgId);
            }
            finally {

                latch.countDown();

                if (latch.getCount() == 0) {
                    log.info("ALL STEPS COMPLETED. executionId={}", executionId);

                    if (failed.get()) {
                        onFailure.run();
                    } else {
                        onSuccess.run();
                    }
                }
            }
        });
    }
    public void propagateSkip(StepNode failedNode,UUID executionId,
                              UUID orgId) {
        Queue<StepNode> queue = new LinkedList<>();
        queue.add(failedNode);
        while (!queue.isEmpty()) {
            StepNode current = queue.poll();
            for (StepNode child : current.getChildren()) {
                StepStatus status = child.getStatus().get();

                if (status == StepStatus.PENDING) {
                    boolean updated = child.getStatus().compareAndSet(StepStatus.PENDING,StepStatus.SKIPPED);
                    if (updated) {
                        StepExecutionEntity skipped = new StepExecutionEntity();
                        skipped.setId(UUID.randomUUID());
                        skipped.setWorkflowExecutionId(executionId);
                        skipped.setStepId(child.getStepId());
                        skipped.setOrganizationId(orgId);
                        skipped.setStatus(StepStatus.SKIPPED);
                        skipped.setAttemptCount(0);
                        skipped.setUpdatedAt(Instant.now());

                        boolean exists = stepExecutionRepository
                                .findByWorkflowExecutionIdAndStepId(executionId, child.getStepId())
                                .isPresent();

                        if (!exists) {
                            stepExecutionRepository.save(skipped);
                        }
                        queue.add(child);
                    }
                }
            }
        }
    }
    @PostConstruct
    public void init() {
        this.executor = Executors.newFixedThreadPool(poolSize);
    }
}