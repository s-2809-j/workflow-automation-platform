package com.company.workflowautomation.workflow_execution.jpa;

import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
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
    List<StepExecutionEntity> findByWorkflowExecutionId(UUID executionId);
    @Query("SELECT s FROM StepExecutionEntity s WHERE s.workflowExecutionId = :executionId AND s.stepId = :stepId")
    Optional<StepExecutionEntity> findByWorkflowExecutionIdAndStepId(
            @Param("executionId") UUID executionId,
            @Param("stepId") UUID stepId
    );
    boolean existsByWorkflowExecutionIdAndStepIdAndStatus(
            UUID workflowId,
            UUID stepId,
            StepStatus status
    );


    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
UPDATE StepExecutionEntity s
SET s.attemptCount = s.attemptCount + 1
WHERE s.workflowExecutionId = :executionId
AND s.stepId = :stepId
""")
    void incrementAttempt(  @Param("executionId") UUID executionId,
                            @Param("stepId") UUID stepId);
}
