package com.company.workflowautomation.ai.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


public class GenerateWorkflowRequest {

    private String prompt;

    public GenerateWorkflowRequest(){}

    public GenerateWorkflowRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt(){
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
