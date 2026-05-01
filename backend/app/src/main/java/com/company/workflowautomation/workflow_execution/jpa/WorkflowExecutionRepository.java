package com.company.workflowautomation.workflow_execution.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, UUID> {
    List<WorkflowExecutionEntity> findByWorkflowId(UUID workflowId);

}
