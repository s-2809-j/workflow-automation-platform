package com.company.workflowautomation.workflow_execution.application.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class StepExecutionContext {
    private final UUID workflowExecutionID;
    private final UUID stepId;

    private int  attempt;
    private final int maxRetries;

    public String getIdempotency(){
        return workflowExecutionID +":"+ stepId;
    }
    public void incrementAttempt(){
        attempt++;
    }




}
