package com.company.workflowautomation.workflow_steps.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStepEntity, UUID> {

    List<WorkflowStepEntity> findByWorkflowIdAndOrganizationIdOrderByStepOrder(
            UUID workflowId, UUID organizationId);
    Optional<WorkflowStepEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);


}
