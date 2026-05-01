package com.company.workflowautomation.workflow_steps.api;


import com.company.workflowautomation.util.SecurityUtils;
import com.company.workflowautomation.workflow_steps.dto.CreateWorkflowStepRequest;
import com.company.workflowautomation.workflow_steps.application.WorkflowStepService;
import com.company.workflowautomation.workflow_steps.dto.UpdateWorkflowStepRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowStepController {
    private final WorkflowStepService stepService;

    @PostMapping("/workflows/{workflowId}/steps")
    public ResponseEntity<?> createStep(
            @PathVariable UUID workflowId, @RequestBody CreateWorkflowStepRequest request
            ) throws JsonProcessingException {
        UUID organizationId = SecurityUtils.getOrganizationId();
        UUID stepId = UUID.randomUUID();
        return ResponseEntity.ok(stepService.createStep(stepId,organizationId,workflowId,request));
    }

    @GetMapping("/workflows/{workflowId}/steps")
    public ResponseEntity<?> getSteps(@PathVariable UUID workflowId)
    {
        return ResponseEntity.ok(stepService.getWorkflowSteps(workflowId));
    }

    @PutMapping("/steps/{id}")
    public ResponseEntity<?> updateStep(@PathVariable UUID id, @RequestBody UpdateWorkflowStepRequest request) throws JsonProcessingException {
        return ResponseEntity.ok(stepService.updateStep(id,request));
    }

    @DeleteMapping("/steps/{id}")
    public ResponseEntity<?> deleteStep(@PathVariable UUID id)
    {
        stepService.deleteStep(id);
        return ResponseEntity.noContent().build();
    }
}


