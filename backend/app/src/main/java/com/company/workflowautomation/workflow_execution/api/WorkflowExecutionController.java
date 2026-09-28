package com.company.workflowautomation.workflow_execution.api;

import com.company.workflowautomation.workflow_execution.application.WorkflowExecutionService;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowExecutionController {
    private final WorkflowExecutionService workflowExecutionService;

    /**
     * Execute a workflow, optionally supplying runtime parameters as a JSON object.
     * <p>
     * If no body is provided (or the body is null/empty), the workflow starts with
     * empty triggerData — preserving existing behaviour for all legacy clients.
     * <p>
     * Example with parameters:
     * POST /api/workflows/{id}/execute
     * { "city": "Pune", "threshold": 35, "email": "user@example.com" }
     */
    @PostMapping("/workflows/{workflowId}/execute")
    public ResponseEntity<?> execute(
            @PathVariable UUID workflowId,
            @RequestBody(required = false) JsonNode runtimeParams) {
        workflowExecutionService.startExecution(workflowId, runtimeParams);
        return ResponseEntity.ok("Workflow execution started");
    }

    @GetMapping("/workflows/{workflowId}/executions")
    public ResponseEntity<?> getExecutions(@PathVariable UUID workflowId) {
        List<WorkflowExecutionEntity> executions = workflowExecutionService.getExecutions(workflowId);
        return ResponseEntity.ok(executions);
    }

    @GetMapping("/executions/{executionId}/steps")
    public ResponseEntity<?> getStepExecutions(@PathVariable UUID executionId) {
        List<StepExecutionEntity> steps = workflowExecutionService.getStepExecutions(executionId);
        return ResponseEntity.ok(steps);
    }
}
