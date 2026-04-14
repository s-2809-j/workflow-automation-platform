package com.company.workflowautomation.ai.adapter;

import com.company.workflowautomation.ai.application.AiService;
import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;


@Component

@RequiredArgsConstructor
public class DefaultAdapter implements AiAdapter {
    private final AiService aiService;


    @Override
    public AiResponse analyzeExecution(AiRequest request) {
        try {
            return aiService.analyzeExecution(request);
        } catch (Exception e) {
            return AiResponse.builder()
                    .retryDecision(
                            AiResponse.RetryDecision.builder()
                                    .shouldRetry(true)
                                    .maxRetries(2)
                                    .strategy("FIXED")
                                    .build()
                    )
                    .anomaly(
                            AiResponse.Anomaly.builder()
                                    .type("AI_FAILURE")
                                    .severity("LOW")
                                    .build()
                    )
                    .nextAction("EXECUTE_RETRY")
                    .build();

        }
    }


}
