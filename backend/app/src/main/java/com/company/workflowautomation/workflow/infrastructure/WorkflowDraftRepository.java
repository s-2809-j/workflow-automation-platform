package com.company.workflowautomation.workflow.infrastructure;

import com.company.workflowautomation.workflow.domain.WorkflowDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDraftRepository extends JpaRepository<WorkflowDraft, UUID>{
    Optional<WorkflowDraft> findByIdAndOrganizationId(UUID id, UUID organizationId);
List<WorkflowDraft> findByOrganizationId(UUID organizationId);

}

