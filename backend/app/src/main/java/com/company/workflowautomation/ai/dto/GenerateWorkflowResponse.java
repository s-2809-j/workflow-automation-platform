package com.company.workflowautomation.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;



public class GenerateWorkflowResponse {
    private Map<String ,Object> output;

    public Map<String,Object> getOutput(){
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

}
