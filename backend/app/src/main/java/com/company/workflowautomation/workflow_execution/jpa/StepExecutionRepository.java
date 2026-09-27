package com.company.workflowautomation.workflow_execution.jpa;

import com.company.workflowautomation.workflow_execution.model.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StepExecutionRepository extends JpaRepository<StepExecutionEntity, UUID> {
    List<StepExecutionEntity> findByWorkflowExecutionIdAndOrganizationId(
            UUID executionId, UUID organizationId);
    @Query("SELECT s FROM StepExecutionEntity s WHERE s.workflowExecutionId = :executionId AND s.stepId = :stepId AND s.organizationId = :organizationId")
    Optional<StepExecutionEntity> findByWorkflowExecutionIdAndStepIdAndOrganizationId(
            @Param("executionId") UUID executionId,
            @Param("stepId") UUID stepId,
            @Param("organizationId") UUID organizationId
    );
    boolean existsByWorkflowExecutionIdAndStepIdAndOrganizationIdAndStatus(
            UUID executionId,
            UUID stepId,
            UUID organizationId,
            StepStatus status
    );
    List<StepExecutionEntity> findByWorkflowExecutionIdAndOrganizationIdAndStatus(
            UUID workflowExecutionId,
            UUID organizationId,
            StepStatus status
    );


    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
UPDATE StepExecutionEntity s
SET s.attemptCount = s.attemptCount + 1
WHERE s.workflowExecutionId = :executionId
AND s.stepId = :stepId
AND s.organizationId = :organizationId
""")
    void incrementAttempt(  @Param("executionId") UUID executionId,
                             @Param("stepId") UUID stepId,
                             @Param("organizationId") UUID organizationId);

}
