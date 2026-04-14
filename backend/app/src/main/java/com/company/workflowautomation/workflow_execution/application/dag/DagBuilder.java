package com.company.workflowautomation.workflow_execution.application.dag;

import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
@Slf4j
@Component
public class DagBuilder {
    public Map<UUID, StepNode> buildGraph(List<WorkflowStepEntity> steps) {
        Map<UUID, StepNode> graph = new HashMap<>();
        for (WorkflowStepEntity step : steps) {
            List<UUID> deps = step.getDependsOnList();
            graph.put(step.getId(), new StepNode(step.getId(), deps));

        }

        for (StepNode node : graph.values()) {
            for (UUID dep : node.getDependencies()) {
                StepNode parent = graph.get(dep);
                if (parent != null) {
                    parent.getChildren().add(node);
                    node.getInDegree().incrementAndGet();
                }

            }
            log.debug("Node {} dependencies={} inDegree={}",
                    node.getStepId(), node.getDependencies(), node.getInDegree().get());

        }
        return graph;

    }
}
