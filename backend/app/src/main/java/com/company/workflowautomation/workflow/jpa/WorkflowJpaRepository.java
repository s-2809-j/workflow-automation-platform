package com.company.workflowautomation.workflow.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowJpaRepository extends JpaRepository<WorkflowEntity, UUID> {
    List<WorkflowEntity> findByOrganizationId(UUID organizationId);
}
