package com.company.workflowautomation.workflow_execution.api;

import com.company.workflowautomation.util.SecurityUtils;
import com.company.workflowautomation.workflow_execution.application.WorkflowExecutionService;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionEntity;
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

    @PostMapping("/workflows/{workflowId}/execute")
    public ResponseEntity<?> execute(@PathVariable UUID workflowId) {
        workflowExecutionService.startExecution(workflowId);

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
