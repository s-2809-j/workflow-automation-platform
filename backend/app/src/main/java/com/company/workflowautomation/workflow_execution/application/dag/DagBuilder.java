package com.company.workflowautomation.workflow_execution.application.dag;

import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class DagBuilder {

    public Map<UUID, StepNode> buildGraph(List<WorkflowStepEntity> steps) {
        Map<UUID, StepNode> graph = new HashMap<>();

        // Map step name → UUID to resolve "step-1" style AI-generated refs
        Map<String, UUID> nameToId = new HashMap<>();
        for (WorkflowStepEntity step : steps) {
            graph.put(step.getId(), new StepNode(step.getId(), new ArrayList<>()));
            if (step.getName() != null) {
                nameToId.put(step.getName().toLowerCase(), step.getId());
            }
        }

        // Second pass — resolve dependencies and wire edges
        for (WorkflowStepEntity step : steps) {
            StepNode node = graph.get(step.getId());

            for (String dep : step.getDependsOnList()) {
                UUID depId = resolveStepId(dep, nameToId);
                if (depId == null) {
                    log.warn("Could not resolve dependency '{}' for step '{}'", dep, step.getName());
                    continue;
                }
                node.getDependencies().add(depId);
                StepNode parent = graph.get(depId);
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

    /**
     * Resolves a dependency string to a UUID.
     * Handles:
     *   - Real UUID strings:  "550e8400-e29b-41d4-a716-446655440000"
     *   - Step name refs:     "fetch_data", "Send Email", "step-1"
     */
    private UUID resolveStepId(String dep, Map<String, UUID> nameToId) {
        try {
            return UUID.fromString(dep);
        } catch (IllegalArgumentException ignored) {}

        return nameToId.get(dep.toLowerCase());
    }
}