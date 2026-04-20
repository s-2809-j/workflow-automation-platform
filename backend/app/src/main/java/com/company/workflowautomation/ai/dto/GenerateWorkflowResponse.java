package com.company.workflowautomation.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateWorkflowResponse {

    private WorkflowData workflow;
    private Double confidence;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowData {
        private String name;
        private List<Map<String, Object>> steps;
    }

    // Keep this so GenerateWorkflowUseCase still works
    public Map<String, Object> getOutput() {
        if (workflow == null) return null;
        return Map.of(
            "name", workflow.getName() != null ? workflow.getName() : "",
            "steps", workflow.getSteps() != null ? workflow.getSteps() : List.of(),
            "confidence", confidence != null ? confidence : 0.0
        );
    }
}