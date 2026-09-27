package com.company.workflowautomation.workflow_execution.api;

import com.company.workflowautomation.workflow_execution.application.WorkflowScheduleService;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowScheduleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows/{workflowId}/schedule")
@RequiredArgsConstructor
public class WorkflowScheduleController {

    private final WorkflowScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<WorkflowScheduleEntity> createSchedule(
            @PathVariable UUID workflowId,
            @RequestBody Map<String, String> body) {
        String cron = body.get("cronExpression");
        String tz   = body.getOrDefault("timezone", "UTC");
        return ResponseEntity.ok(scheduleService.createSchedule(workflowId, cron, tz));
    }

    @PutMapping
    public ResponseEntity<WorkflowScheduleEntity> updateSchedule(
            @PathVariable UUID workflowId,
            @RequestBody Map<String, String> body) {
        String cron = body.get("cronExpression");
        String tz   = body.getOrDefault("timezone", "UTC");
        return ResponseEntity.ok(scheduleService.updateSchedule(workflowId, cron, tz));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID workflowId) {
        scheduleService.deleteSchedule(workflowId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<WorkflowScheduleEntity>> getSchedules() {
        return ResponseEntity.ok(scheduleService.getSchedules());
    }

    @PatchMapping("/enable")
    public ResponseEntity<WorkflowScheduleEntity> enableSchedule(
            @PathVariable UUID workflowId) {
        return ResponseEntity.ok(scheduleService.setEnabled(workflowId, true));
    }

    @PatchMapping("/disable")
    public ResponseEntity<WorkflowScheduleEntity> disableSchedule(
            @PathVariable UUID workflowId) {
        return ResponseEntity.ok(scheduleService.setEnabled(workflowId, false));
    }
}
