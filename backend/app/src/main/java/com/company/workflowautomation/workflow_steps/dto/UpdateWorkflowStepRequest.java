package com.company.workflowautomation.workflow_steps.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class UpdateWorkflowStepRequest {

    private Integer stepOrder;

    private String name;

    private String type;

    private JsonNode config;

    private JsonNode dependsOn;
}
