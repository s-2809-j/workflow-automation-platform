package com.company.workflowautomation.workflow_execution.jpa;

import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_execution",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"workflow_execution_id", "step_id"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionEntity {
    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workflow_execution_id", nullable = false)
    private UUID workflowExecutionId;
    @Column(name = "step_id", nullable = false)
    private UUID stepId;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "status" , nullable = false)
    @Enumerated(EnumType.STRING)
    private StepStatus status;
    @JdbcTypeCode((SqlTypes.JSON))
    @Column(name = "input_data", columnDefinition = "jsonb")
    private JsonNode inputData;
    @JdbcTypeCode((SqlTypes.JSON))
    @Column(name = "output_data", columnDefinition = "jsonb")
    private JsonNode outputData;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "attempt_count",nullable = false)
    private int attemptCount = 0;
    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
