package com.company.workflowautomation.workflow_steps.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
public class UpdateWorkflowStepRequest {

    private Integer stepOrder;

    private String name;

    private String type;

    private String config;

    @JdbcTypeCode((SqlTypes.JSON))
    private JsonNode dependsOn;
}
