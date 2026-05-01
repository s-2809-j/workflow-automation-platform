package com.company.workflowautomation.ai.adapter;

import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;

public interface AiAdapter {
    AiResponse analyzeExecution(AiRequest request);
}
