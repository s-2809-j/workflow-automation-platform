package com.company.workflowautomation.workflow_steps.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class CreateWorkflowStepRequest {
    private Integer stepOrder;

    private String name;
    @JsonProperty("stepType")
    private String type;


    private JsonNode config;

    private JsonNode dependsOn;

}
