package com.company.workflowautomation.workflow.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CreateWorkflowRequest {
    @NotBlank(message = "Workflow name is required")
    private String name;
    @NotBlank(message = "workflow description is required")
    private String description;
    private String status;
}
