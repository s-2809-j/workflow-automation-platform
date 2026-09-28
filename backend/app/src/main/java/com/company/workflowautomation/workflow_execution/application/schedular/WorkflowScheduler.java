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
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.*;
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
    private final ConcurrentMap<UUID, Set<UUID>> submittedNodesByExecution = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Set<UUID>> decrementedNodesByExecution = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AtomicBoolean> completionFiredByExecution = new ConcurrentHashMap<>();
    @PersistenceContext
   private EntityManager entityManager;

    public boolean canExecute(StepNode node, Map<UUID, StepNode> graph) {

        for (UUID parentId : node.getDependencies()) {
            StepNode parent = graph.get(parentId);
            if (parent == null || parent.getStatus().get() != StepStatus.SUCCESS) {
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
        execute(executionId, orgId, graph, stepMap, onSuccess, (err) -> {
            if (onFailure != null) onFailure.run();
        }, null);
    }

    public void execute(
            UUID executionId,
            UUID orgId,
            Map<UUID, StepNode> graph,
            Map<UUID, WorkflowStepEntity> stepMap,
            Runnable onSuccess,
            Runnable onFailure,
            WorkflowRun run
    ) throws InterruptedException {
        execute(executionId, orgId, graph, stepMap, onSuccess, (err) -> {
            if (onFailure != null) onFailure.run();
        }, run);
    }

    public void execute(
            UUID executionId,
            UUID orgId,
            Map<UUID, StepNode> graph,
            Map<UUID, WorkflowStepEntity> stepMap,
            Runnable onSuccess,
            java.util.function.Consumer<String> onFailure,
            WorkflowRun run
    ) throws InterruptedException {
        if (graph == null || graph.isEmpty() || stepMap == null || stepMap.isEmpty()) {
            log.info("No executable steps for workflow executionId={}. Marking workflow as completed without work.", executionId);
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        log.info("Scheduler started. executionId={} steps={}", executionId, graph.size());

        UUID workflowId = stepMap.values().iterator().next().getWorkflowId();
        if (run == null) {
            run = new WorkflowRun(workflowId, orgId);
            run.markRunning();
            workflowRunRepository.saveAndFlush(run);
            log.info("WorkflowRun created. runId={}", run.getId());
        }

        AtomicBoolean failed = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<String> failureReason = new java.util.concurrent.atomic.AtomicReference<>();
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
                        failureReason,
                        latch,
                        stepMap,
                        run,
                        onSuccess,
                        onFailure
                );
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Workflow execution interrupted. executionId={}", executionId, e);
            AtomicBoolean fired = completionFiredByExecution
                    .computeIfAbsent(executionId, id -> new AtomicBoolean(false));
            if (fired.compareAndSet(false, true)) {
                run.markFailed("Workflow execution interrupted");
                workflowRunRepository.saveAndFlush(run);
                if (onFailure != null) {
                    onFailure.accept("Workflow execution interrupted");
                }
                submittedNodesByExecution.remove(executionId);
                decrementedNodesByExecution.remove(executionId);
                completionFiredByExecution.remove(executionId);
            }
        }
    }

    private void submit(
            UUID stepId,
            UUID executionId,
            UUID orgId,
            Map<UUID, StepNode> graph,
            AtomicBoolean failed,
            java.util.concurrent.atomic.AtomicReference<String> failureReason,
            CountDownLatch latch,
            Map<UUID, WorkflowStepEntity> stepMap,
            WorkflowRun run,
            Runnable onSuccess,
            java.util.function.Consumer<String> onFailure
    ) {
        submittedNodesByExecution
                .computeIfAbsent(executionId, ignored -> ConcurrentHashMap.newKeySet())
                .add(stepId);

        executor.submit(() -> {
            StepNode node = graph.get(stepId);

            // Guard: skip if already marked SKIPPED by a failed parent's propagation
            if (node.getStatus().get() == StepStatus.SKIPPED) {
                log.info("Step already SKIPPED by propagation, skipping execution. stepId={}", stepId);
                countDownOnce(executionId, stepId, latch);
                return;
            }

            // Guard: re-verify all parents succeeded before executing (race condition safety)
            if (!canExecute(node, graph)) {
                log.warn("Step cannot execute — parent not SUCCESS. Marking SKIPPED. stepId={}", stepId);
                node.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.SKIPPED);
                propagateSkip(node, executionId, orgId, latch);
                failed.set(true);
                failureReason.compareAndSet(null, "Step cannot execute because parent step did not succeed");
                countDownOnce(executionId, stepId, latch);
                return;
            }

            try {
                WorkflowStepEntity step = stepMap.get(stepId);
                String stepName = (step != null && step.getName() != null) ? step.getName() : stepId.toString();

                log.info("Executing step. name={} stepId={}", stepName, stepId);

                workflowStepExecutionService.executeStep(executionId, orgId, step, node, run);

                if (node.getStatus().get() == StepStatus.FAILED) {
                    failed.set(true);
                    failureReason.compareAndSet(null, "Step '" + stepName + "' failed");
                    log.error("Step returned FAILED status. Propagating skip. stepId={}", stepId);
                    propagateSkip(node, executionId, orgId, latch);
                }

                if (node.getStatus().get() == StepStatus.SUCCESS) {

                    for (StepNode child : node.getChildren()) {

                        int updated = child.getInDegree().decrementAndGet();

                        if (updated == 0) {

                            // Child may have been SKIPPED by a concurrent sibling's failure
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
                                        failureReason,
                                        latch,
                                        stepMap,
                                        run,
                                        onSuccess,
                                        onFailure
                                );
                            } else {
                                boolean marked = child.getStatus()
                                        .compareAndSet(StepStatus.PENDING, StepStatus.SKIPPED);
                                if (marked) {
                                    log.warn("Child cannot execute — marking SKIPPED. childStepId={}", child.getStepId());
                                    propagateSkip(child, executionId, orgId, latch);
                                    countDownOnce(executionId, child.getStepId(), latch);
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {

                failed.set(true);

                WorkflowStepEntity step = stepMap.get(stepId);
                String stepName = (step != null && step.getName() != null) ? step.getName() : stepId.toString();
                String rawMsg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
                failureReason.compareAndSet(null, "Step '" + stepName + "' failed: " + rawMsg);

                log.error("Uncaught exception in step execution. stepId={} stepName={} error={}", stepId, stepName, e.getMessage(), e);

                node.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.FAILED);
                propagateSkip(node, executionId, orgId, latch);
            }
            finally {

                countDownOnce(executionId, stepId, latch);

                if (latch.getCount() == 0) {
                    AtomicBoolean fired = completionFiredByExecution
                            .computeIfAbsent(executionId, id -> new AtomicBoolean(false));
                    if (fired.compareAndSet(false, true)) {
                        log.info("ALL STEPS COMPLETED. executionId={}", executionId);
                        if (failed.get()) {
                            String err = failureReason.get();
                            if (err == null || err.isBlank()) {
                                err = "Workflow execution failed";
                            }
                            run.markFailed(err);
                            workflowRunRepository.saveAndFlush(run);
                            if (onFailure != null) {
                                onFailure.accept(err);
                            }
                        } else {
                            run.markSuccess();
                            workflowRunRepository.saveAndFlush(run);
                            if (onSuccess != null) {
                                onSuccess.run();
                            }
                        }
                        submittedNodesByExecution.remove(executionId);
                        decrementedNodesByExecution.remove(executionId);
                        completionFiredByExecution.remove(executionId);
                    }
                }
            }
        });
    }
    public void propagateSkip(StepNode failedNode, UUID executionId,
                              UUID orgId, CountDownLatch latch) {
        Set<UUID> submittedNodes = submittedNodesByExecution.getOrDefault(executionId, Set.of());
        Queue<StepNode> queue = new LinkedList<>();
        queue.add(failedNode);
        while (!queue.isEmpty()) {
            StepNode current = queue.poll();
            for (StepNode child : current.getChildren()) {
                StepStatus status = child.getStatus().get();

                if (status == StepStatus.PENDING) {
                    boolean updated = child.getStatus().compareAndSet(StepStatus.PENDING, StepStatus.SKIPPED);
                    if (updated) {
                        StepExecutionEntity skipped = new StepExecutionEntity();
                        skipped.setId(UUID.randomUUID());
                        skipped.setWorkflowExecutionId(executionId);
                        skipped.setStepId(child.getStepId());
                        skipped.setOrganizationId(orgId);
                        skipped.setStatus(StepStatus.SKIPPED);
                        skipped.setAttemptCount(0);
                        skipped.setUpdatedAt(Instant.now());

                        Optional<StepExecutionEntity> existingEntity =
                                stepExecutionRepository.findByWorkflowExecutionIdAndStepIdAndOrganizationId(
                                        executionId, child.getStepId(), orgId);

                        if (existingEntity.isEmpty()
                                || existingEntity.get().getStatus() == StepStatus.PENDING) {
                            stepExecutionRepository.saveAndFlush(skipped);
                        }
                        countDownOnce(executionId, child.getStepId(), latch);
                        queue.add(child);
                    }
                }
            }
        }
    }

    private void countDownOnce(UUID executionId, UUID stepId, CountDownLatch latch) {
        Set<UUID> decrementedNodes = decrementedNodesByExecution
                .computeIfAbsent(executionId, ignored -> ConcurrentHashMap.newKeySet());
        if (decrementedNodes.add(stepId)) {
            latch.countDown();
        }
    }
    @PostConstruct
    public void init() {
        this.executor = Executors.newFixedThreadPool(poolSize);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down workflow executor thread pool.");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
