package com.company.workflowautomation.workflow_execution.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;
import java.util.UUID;

@Entity
@Table(
        name = "workflow_schedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_schedule_workflow_cron_tz",
                columnNames = {"workflow_id", "cron_expression", "timezone"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class WorkflowScheduleEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkflowScheduleEntity(UUID workflowId, UUID organizationId,
                                  String cronExpression, String timezone) {
        this.workflowId     = workflowId;
        this.organizationId = organizationId;
        this.cronExpression = cronExpression;
        this.timezone       = timezone;
        this.enabled        = true;
        this.createdAt      = Instant.now();
        this.updatedAt      = Instant.now();
        this.nextRunAt      = computeNextRun(cronExpression, timezone);
    }

    public void computeAndSetNextRun() {
        this.nextRunAt  = computeNextRun(this.cronExpression, this.timezone);
        this.updatedAt  = Instant.now();
    }

    public void recordRun() {
        this.lastRunAt = Instant.now();
        this.nextRunAt = computeNextRun(this.cronExpression, this.timezone);
        this.updatedAt = Instant.now();
    }

    private static Instant computeNextRun(String cron, String timezone) {
        CronExpression expr = CronExpression.parse(cron);
        ZonedDateTime next  = expr.next(ZonedDateTime.now(ZoneId.of(timezone)));
        return next == null ? null : next.toInstant();
    }
}