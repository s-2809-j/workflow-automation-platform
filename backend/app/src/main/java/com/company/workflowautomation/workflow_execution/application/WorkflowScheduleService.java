package com.company.workflowautomation.workflow_execution.application;

import com.company.workflowautomation.util.SecurityUtils;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowScheduleEntity;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowScheduleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowScheduleService {

    private final WorkflowScheduleRepository scheduleRepository;
    private final WorkflowExecutionService workflowExecutionService;
    private final EntityManager entityManager;

    @Transactional
    public WorkflowScheduleEntity createSchedule(UUID workflowId,
                                                 String cronExpression,
                                                 String timezone) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        validateCron(cronExpression);
        validateTimezone(timezone);

        if (scheduleRepository.existsByWorkflowIdAndCronExpressionAndTimezone(
                workflowId, cronExpression, timezone)) {
            throw new IllegalStateException(
                    "Schedule already exists for workflowId=" + workflowId
                            + " cron=" + cronExpression + " tz=" + timezone);
        }

        WorkflowScheduleEntity schedule =
                new WorkflowScheduleEntity(workflowId, organizationId,
                        cronExpression, timezone);
        WorkflowScheduleEntity saved = scheduleRepository.save(schedule);
        log.info("Schedule created. scheduleId={} workflowId={} cron={} tz={}",
                saved.getId(), workflowId, cronExpression, timezone);
        return saved;
    }

    @Transactional
    public WorkflowScheduleEntity updateSchedule(UUID workflowId,
                                                 String cronExpression,
                                                 String timezone) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        validateCron(cronExpression);
        validateTimezone(timezone);

        WorkflowScheduleEntity schedule = scheduleRepository
                .findByWorkflowIdAndOrganizationId(workflowId, organizationId)
                .orElseThrow(() -> new RuntimeException(
                        "No schedule found for workflowId=" + workflowId));

        schedule.setCronExpression(cronExpression);
        schedule.setTimezone(timezone);
        schedule.computeAndSetNextRun();

        WorkflowScheduleEntity saved = scheduleRepository.save(schedule);
        log.info("Schedule updated. scheduleId={} workflowId={}", saved.getId(), workflowId);
        return saved;
    }

    @Transactional
    public void deleteSchedule(UUID workflowId) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        WorkflowScheduleEntity schedule = scheduleRepository
                .findByWorkflowIdAndOrganizationId(workflowId, organizationId)
                .orElseThrow(() -> new RuntimeException(
                        "No schedule found for workflowId=" + workflowId));
        scheduleRepository.delete(schedule);
        log.info("Schedule deleted. workflowId={}", workflowId);
    }

    @Transactional
    public WorkflowScheduleEntity setEnabled(UUID workflowId, boolean enabled) {
        UUID organizationId = SecurityUtils.getOrganizationId();
        WorkflowScheduleEntity schedule = scheduleRepository
                .findByWorkflowIdAndOrganizationId(workflowId, organizationId)
                .orElseThrow(() -> new RuntimeException(
                        "No schedule found for workflowId=" + workflowId));
        schedule.setEnabled(enabled);
        schedule.setUpdatedAt(java.time.Instant.now());
        WorkflowScheduleEntity saved = scheduleRepository.save(schedule);
        log.info("Schedule {} workflowId={}", enabled ? "enabled" : "disabled", workflowId);
        return saved;
    }

    public List<WorkflowScheduleEntity> getSchedules() {
        UUID organizationId = SecurityUtils.getOrganizationId();
        return scheduleRepository.findByOrganizationId(organizationId);
    }

    // Called by the cron trigger — no HTTP session context exists here.
    // Must set app.current_organization explicitly per schedule so RLS
    // allows the workflow lookup in WorkflowExecutionService.
    @Transactional
    public void triggerDueSchedules() {
        List<WorkflowScheduleEntity> due =
                scheduleRepository.findDueSchedules(java.time.Instant.now());

        if (due.isEmpty()) return;

        log.info("Found {} due schedules to trigger.", due.size());

        for (WorkflowScheduleEntity schedule : due) {
            try {
                // Set org context so RLS allows workflow table access
                // in this scheduler thread which has no HTTP session
                setOrganizationContext(schedule.getOrganizationId());

                log.info("Triggering scheduled execution. workflowId={} scheduleId={}",
                        schedule.getWorkflowId(), schedule.getId());

                workflowExecutionService.startExecutionInternal(
                        schedule.getWorkflowId(), schedule.getOrganizationId());

                schedule.recordRun();
                scheduleRepository.save(schedule);

            } catch (Exception e) {
                log.error("Failed to trigger scheduled workflow. workflowId={} error={}",
                        schedule.getWorkflowId(), e.getMessage(), e);
                // Don't rethrow — one bad schedule must not block others
            } finally {
                // Clear org context after each schedule to prevent bleed
                // into the next iteration
                clearOrganizationContext();
            }
        }
    }

    // Sets the PostgreSQL session variable that RLS policies read.
    // Uses SET LOCAL so it is scoped to the current transaction.
    private void setOrganizationContext(UUID organizationId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, false)"
                )
                .setParameter("orgId", organizationId.toString())
                .getSingleResult();
    }

    // Clears the org context after each scheduled trigger.
    private void clearOrganizationContext() {
        try {
            entityManager.createNativeQuery(
                    "SELECT set_config('app.current_organization', '', false)"
            ).getSingleResult();
        } catch (Exception e) {
            log.warn("Failed to clear organization context", e);
        }
    }

    private void validateCron(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid cron expression: '" + cron + "' — " + e.getMessage());
        }
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid timezone: '" + timezone + "'");
        }
    }
}