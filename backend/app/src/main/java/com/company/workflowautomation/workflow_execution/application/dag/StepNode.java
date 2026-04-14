package com.company.workflowautomation.workflow_execution.application.dag;

import com.company.workflowautomation.workflow_execution.model.StepStatus;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class StepNode {

    private UUID stepId;
    private List<UUID> dependencies;
    private List<StepNode> children = new ArrayList<>();
   private AtomicInteger inDegree = new AtomicInteger(0);
    private AtomicReference<StepStatus> status = new AtomicReference<>(StepStatus.PENDING);

    public StepNode(UUID stepId,List<UUID> dependencies)
    {
        this.stepId = stepId;
        this.dependencies = dependencies !=null ? dependencies:new ArrayList<>();

    }
}
