package com.company.workflowautomation.workflow.dto;

import java.util.UUID;

public class WorkflowDraftResponse {
    private UUID draftId;
    private String status;
    private String rawJson;

    public WorkflowDraftResponse(UUID draftId,String status,String rawJson){
        this.draftId=draftId;
        this.status=status;
        this.rawJson=rawJson;
    }

    public UUID getDraftId(){
        return draftId;
    }
    public String getStatus(){
        return status;
    }

    public String getRawJson(){
        return rawJson;
    }

}
