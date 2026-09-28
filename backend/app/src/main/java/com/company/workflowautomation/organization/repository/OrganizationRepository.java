package com.company.workflowautomation.organization.repository;

import com.company.workflowautomation.organization.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
}
