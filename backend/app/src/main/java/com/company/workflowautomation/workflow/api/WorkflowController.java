package com.company.workflowautomation.workflow.api;

import com.company.workflowautomation.util.SecurityUtils;
import com.company.workflowautomation.workflow.jpa.WorkflowEntity;
import com.company.workflowautomation.workflow.dto.CreateWorkflowRequest;
import com.company.workflowautomation.workflow.application.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    @GetMapping
    public ResponseEntity<List<WorkflowEntity>> getWorkflows() {
        UUID userId = SecurityUtils.getUserId();
        UUID organizationId = SecurityUtils.getOrganizationId();

        List<WorkflowEntity> workflows = workflowService.getWorkflows(userId, organizationId); // ✅ add this
        return ResponseEntity.ok(workflows);
    }
    @PostMapping

    public ResponseEntity<WorkflowEntity> createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest request) {

        UUID userId = SecurityUtils.getUserId();               // ← use existing util
        UUID organizationId = SecurityUtils.getOrganizationId();

        WorkflowEntity workflow =
                workflowService.createWorkflow(request, userId, organizationId);
        return ResponseEntity.ok(workflow);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable UUID id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

}

