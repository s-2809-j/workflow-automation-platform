package com.company.workflowautomation.workflow_execution.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow_execution")
@Getter
@Setter

public class WorkflowExecutionEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "organization_id")
    private UUID organizationId;

    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_data", columnDefinition = "jsonb")
    private JsonNode triggerData;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
    @PrePersist                          // ← Fix B: auto-set startedAt on first save
    public void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

}
