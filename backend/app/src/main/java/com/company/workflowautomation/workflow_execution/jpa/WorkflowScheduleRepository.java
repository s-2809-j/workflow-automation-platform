package com.company.workflowautomation.workflow_execution.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowScheduleRepository extends JpaRepository<WorkflowScheduleEntity, UUID> {

    Optional<WorkflowScheduleEntity> findByWorkflowIdAndOrganizationId(
            UUID workflowId, UUID organizationId);

    boolean existsByWorkflowIdAndCronExpressionAndTimezone(
            UUID workflowId, String cronExpression, String timezone);

    List<WorkflowScheduleEntity> findByOrganizationId(UUID organizationId);

    @Query("SELECT s FROM WorkflowScheduleEntity s " +
            "WHERE s.enabled = true AND s.nextRunAt <= :now")
    List<WorkflowScheduleEntity> findDueSchedules(@Param("now") Instant now);
}