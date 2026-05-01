package com.company.workflowautomation.ai.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateWorkflowRequest {

    private String prompt;
    private String systemPrompt;

    public GenerateWorkflowRequest(String prompt) {
        this.prompt = prompt;
    }

    public GenerateWorkflowRequest(String prompt, String systemPrompt) {
        this.prompt       = prompt;
        this.systemPrompt = systemPrompt;
    }
}