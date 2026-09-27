package com.company.workflowautomation.workflow.infrastructure;

import com.company.workflowautomation.workflow.domain.WorkflowRun;
import com.company.workflowautomation.workflow.domain.WorkflowRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    List<WorkflowRun> findByWorkflowIdAndOrganizationIdOrderByCreatedAtDesc(
            UUID workflowId, UUID organizationId);

    // used by RetryOrchestrator to pick up stuck RETRYING runs
    List<WorkflowRun> findByStatusAndOrganizationId(WorkflowRunStatus status, UUID organizationId);

    // tenant-safe lookup — organization_id must match
    Optional<WorkflowRun> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
