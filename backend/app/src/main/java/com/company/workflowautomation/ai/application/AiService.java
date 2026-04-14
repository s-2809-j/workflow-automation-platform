package com.company.workflowautomation.ai.application;

import com.company.workflowautomation.ai.config.AiProperties;
import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.ai.dto.GenerateWorkflowRequest;
import com.company.workflowautomation.ai.dto.GenerateWorkflowResponse;
import com.company.workflowautomation.ai.shared.Exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;

    // ─── ANALYZE EXECUTION ───────────────────────────────────────
    public AiResponse analyzeExecution(AiRequest request) {
        if (aiProperties.isMockMode()) {
            log.info("[MOCK] analyzeExecution called for workflowId={}", request.getWorkflowId());
            return mockExecutionResponse(request);
        }
        try {
            ResponseEntity<AiResponse> response =
                    restTemplate.postForEntity(
                            aiProperties.getExecutionUrl(),
                            request,
                            AiResponse.class
                    );
            if (response.getBody() == null) {
                throw new AiServiceException("Empty AI response", new RuntimeException("null body"));
            }
            return response.getBody();
        } catch (Exception e) {
            log.error("AI service call failed: {}", e.getMessage(), e);
            throw new AiServiceException("AI service call failed", e);
        }
    }

    // ─── GENERATE WORKFLOW ───────────────────────────────────────
    public GenerateWorkflowResponse generateWorkflowResponse(GenerateWorkflowRequest request) {
        if (aiProperties.isMockMode()) {
            log.info("[MOCK] generateWorkflow called for prompt={}", request.getPrompt());
            return mockWorkflowResponse(request);
        }
        try {
            ResponseEntity<GenerateWorkflowResponse> response =
                    restTemplate.postForEntity(
                            aiProperties.getWorkflowUrl(),  // ← FIXED (was getExecutionUrl)
                            request,
                            GenerateWorkflowResponse.class
                    );
            if (response.getBody() == null) {
                throw new AiServiceException("Empty workflow response", new RuntimeException("null body"));
            }
            return response.getBody();
        } catch (Exception e) {
            log.error("AI workflow generation failed: {}", e.getMessage(), e);
            throw new AiServiceException("AI service call failed", e);
        }
    }

    // ─── MOCK: ANALYZE EXECUTION ─────────────────────────────────
    private AiResponse mockExecutionResponse(AiRequest request) {
        String errorType = request.getErrorType();

        // Transient error → retry
        if (errorType != null && errorType.contains("timeout")) {
            return AiResponse.builder()
                    .retryDecision(AiResponse.RetryDecision.builder()
                            .shouldRetry(true)
                            .maxRetries(2)
                            .strategy("FIXED")
                            .build())
                    .anomaly(AiResponse.Anomaly.builder()
                            .type("TRANSIENT_ERROR")
                            .severity("MEDIUM")
                            .build())
                    .nextAction("EXECUTE_RETRY")
                    .build();
        }

        // Permanent error → no retry
        return AiResponse.builder()
                .retryDecision(AiResponse.RetryDecision.builder()
                        .shouldRetry(false)
                        .maxRetries(0)
                        .strategy("NONE")
                        .build())
                .anomaly(AiResponse.Anomaly.builder()
                        .type("PERMANENT_ERROR")
                        .severity("HIGH")
                        .build())
                .nextAction("FAIL_WORKFLOW")
                .build();
    }

    // ─── MOCK: GENERATE WORKFLOW ─────────────────────────────────
    private GenerateWorkflowResponse mockWorkflowResponse(GenerateWorkflowRequest request) {
        Map<String, Object> mockOutput = new HashMap<>();
        mockOutput.put("intent", "mock_intent");
        mockOutput.put("trigger", "manual");
        mockOutput.put("actions", List.of("mock_action"));
        mockOutput.put("prompt", request.getPrompt());

        GenerateWorkflowResponse response = new GenerateWorkflowResponse();
        response.setOutput(mockOutput);
        return response;
    }
}