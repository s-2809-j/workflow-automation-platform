package com.company.workflowautomation.workflow.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_run")
@Getter
@NoArgsConstructor

public class WorkflowRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowRunStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkflowRun(UUID workflowId, UUID organizationId) {
        this.workflowId = workflowId;
        this.organizationId = organizationId;
        this.status = WorkflowRunStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markRunning() {
        this.status = WorkflowRunStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public void markRetrying(String errorMessage) {
        this.status = WorkflowRunStatus.RETRYING;
        this.errorMessage = errorMessage;
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public void markSuccess() {
        this.status = WorkflowRunStatus.SUCCESS;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = WorkflowRunStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }
}
