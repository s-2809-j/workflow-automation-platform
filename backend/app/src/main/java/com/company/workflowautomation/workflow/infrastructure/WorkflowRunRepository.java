package com.company.workflowautomation.workflow.infrastructure;

import com.company.workflowautomation.workflow.domain.WorkflowRun;
import com.company.workflowautomation.workflow.domain.WorkflowRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    List<WorkflowRun> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId);

    // used by RetryOrchestrator to pick up stuck RETRYING runs
    List<WorkflowRun> findByStatus(WorkflowRunStatus status);

    // tenant-safe lookup — organization_id must match
    Optional<WorkflowRun> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
