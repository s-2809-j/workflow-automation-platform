package com.company.workflowautomation.workflow_execution.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowScheduleTrigger {

    private final WorkflowScheduleService workflowScheduleService;

    // Checks every 60 seconds for due schedules
    @Scheduled(fixedDelay = 60_000)
    public void pollDueSchedules() {
        log.debug("Polling for due workflow schedules.");
        workflowScheduleService.triggerDueSchedules();
    }
}