package com.company.workflowautomation.ai.application;

import com.company.workflowautomation.ai.config.AiProperties;
import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.ai.dto.GenerateWorkflowRequest;
import com.company.workflowautomation.ai.dto.GenerateWorkflowResponse;
import com.company.workflowautomation.ai.shared.Exception.AiServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiService {

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final String mockSalesApiUrl;
    private final String mockMetricsApiUrl;
    private final String mockWebhookUrl;

    public AiService(
            @Qualifier("aiRestTemplate") RestTemplate restTemplate,
            AiProperties aiProperties,
            @Value("${workflow.mock.sales-api-url:http://localhost:8080/mock/sales/daily}")
            String mockSalesApiUrl,
            @Value("${workflow.mock.metrics-api-url:http://localhost:8080/mock/metrics/cpu}")
            String mockMetricsApiUrl,
            @Value("${workflow.mock.webhook-url:http://localhost:8080/mock/webhook}")
            String mockWebhookUrl) {
        this.restTemplate       = restTemplate;
        this.aiProperties       = aiProperties;
        this.mockSalesApiUrl    = mockSalesApiUrl;
        this.mockMetricsApiUrl  = mockMetricsApiUrl;
        this.mockWebhookUrl     = mockWebhookUrl;
    }

    // ─── STARTUP VALIDATION ──────────────────────────────────────
    @PostConstruct
    public void validateConfig() {
        if (!aiProperties.isMockMode()) {
            if (aiProperties.getWorkflowUrl() == null
                    || aiProperties.getWorkflowUrl().isBlank()) {
                throw new IllegalStateException(
                        "workflow.ai.workflow-url must be set when mock-mode=false");
            }
            if (aiProperties.getExecutionUrl() == null
                    || aiProperties.getExecutionUrl().isBlank()) {
                throw new IllegalStateException(
                        "workflow.ai.execution-url must be set when mock-mode=false");
            }
            if (aiProperties.getApiKey() == null
                    || aiProperties.getApiKey().isBlank()) {
                throw new IllegalStateException(
                        "workflow.ai.api-key must be set when mock-mode=false");
            }
        }
    }

    // ─── ANALYZE EXECUTION ───────────────────────────────────────
    public AiResponse analyzeExecution(AiRequest request) {
        if (aiProperties.isMockMode()) {
            log.info("[MOCK] analyzeExecution called for workflowId={}",
                    request.getWorkflowId());
            return mockExecutionResponse(request);
        }
        try {
            HttpEntity<AiRequest> entity =
                    new HttpEntity<>(request, buildAuthHeaders());

            ResponseEntity<AiResponse> response = restTemplate.exchange(
                    aiProperties.getExecutionUrl(),
                    HttpMethod.POST,
                    entity,
                    AiResponse.class
            );
            if (response.getBody() == null) {
                throw new AiServiceException("Empty AI response",
                        new RuntimeException("null body"));
            }
            return response.getBody();

        } catch (Exception e) {
            log.error("AI service call failed: {}", e.getMessage(), e);
            throw new AiServiceException("AI service call failed", e);
        }
    }

    // ─── GENERATE WORKFLOW ───────────────────────────────────────
    public GenerateWorkflowResponse generateWorkflowResponse(
            GenerateWorkflowRequest request) {
        if (aiProperties.isMockMode()) {
            log.info("[MOCK] generateWorkflow called for prompt={}",
                    request.getPrompt());
            return mockWorkflowResponse(request);
        }
        try {
            // Send prompt and systemPrompt as separate fields
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", request.getPrompt());
            requestBody.put("systemPrompt", request.getSystemPrompt());

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(requestBody, buildAuthHeaders());

            ResponseEntity<GenerateWorkflowResponse> response = restTemplate.exchange(
                    aiProperties.getWorkflowUrl(),
                    HttpMethod.POST,
                    entity,
                    GenerateWorkflowResponse.class
            );
            if (response.getBody() == null) {
                throw new AiServiceException("Empty workflow response",
                        new RuntimeException("null body"));
            }
            return response.getBody();

        } catch (Exception e) {
            log.error("AI workflow generation failed: {}", e.getMessage(), e);
            throw new AiServiceException("AI service call failed", e);
        }
    }

    // ─── AUTH HEADERS ────────────────────────────────────────────
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (aiProperties.getApiKey() != null
                && !aiProperties.getApiKey().isBlank()) {
            headers.setBearerAuth(aiProperties.getApiKey());
        }
        return headers;
    }

    // ─── MOCK: ANALYZE EXECUTION ─────────────────────────────────
    private AiResponse mockExecutionResponse(AiRequest request) {
        String errorType = request.getErrorType();

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
    private GenerateWorkflowResponse mockWorkflowResponse(
            GenerateWorkflowRequest request) {

        String prompt = request.getPrompt() == null
                ? "" : request.getPrompt().toLowerCase();

        List<Map<String, Object>> steps;

        if (prompt.contains("sales") || prompt.contains("report")) {
            steps = List.of(
                Map.of(
                    "id",        "step-1",
                    "stepType",  "HTTP",
                    "name",      "Retrieve daily sales data",
                    "config",    Map.of(
                        "url",    mockSalesApiUrl,
                        "method", "GET"
                    ),
                    "dependsOn", List.of()
                ),
                Map.of(
                    "id",        "step-2",
                    "stepType",  "LOG",
                    "name",      "Generate daily sales report",
                    "config",    Map.of(
                        "message", "Daily sales report generated from retrieved data",
                        "level",   "INFO"
                    ),
                    "dependsOn", List.of("step-1")
                ),
                Map.of(
                    "id",        "step-3",
                    "stepType",  "EMAIL",
                    "name",      "Distribute sales report to management",
                    "config",    Map.of(
                        "to",      "management@yourcompany.com",
                        "subject", "Daily Sales Report",
                        "body",    "Please find the daily sales report for today.",
                        "isHtml",  false
                    ),
                    "dependsOn", List.of("step-2")
                )
            );

        } else if (prompt.contains("monitor") || prompt.contains("cpu")
                || prompt.contains("alert")) {
            steps = List.of(
                Map.of(
                    "id",        "step-1",
                    "stepType",  "HTTP",
                    "name",      "Fetch system metrics",
                    "config",    Map.of(
                        "url",    mockMetricsApiUrl,
                        "method", "GET"
                    ),
                    "dependsOn", List.of()
                ),
                Map.of(
                    "id",        "step-2",
                    "stepType",  "WEBHOOK",
                    "name",      "Alert on-call engineer",
                    "config",    Map.of(
                        "url",     mockWebhookUrl,
                        "method",  "POST",
                        "payload", "{\"alert\": \"CPU threshold exceeded\"}"
                    ),
                    "dependsOn", List.of("step-1")
                )
            );

        } else if (prompt.contains("email") || prompt.contains("send")
                || prompt.contains("notify")) {
            steps = List.of(
                Map.of(
                    "id",        "step-1",
                    "stepType",  "EMAIL",
                    "name",      "Send notification email",
                    "config",    Map.of(
                        "to",      "team@yourcompany.com",
                        "subject", "Automated Notification",
                        "body",    "This is an automated notification from FlowEngine.",
                        "isHtml",  false
                    ),
                    "dependsOn", List.of()
                )
            );

        } else {
            steps = List.of(
                Map.of(
                    "id",        "step-1",
                    "stepType",  "LOG",
                    "name",      "Execute workflow action",
                    "config",    Map.of(
                        "message", "Workflow started: " + request.getPrompt(),
                        "level",   "INFO"
                    ),
                    "dependsOn", List.of()
                )
            );
        }

        GenerateWorkflowResponse.WorkflowData workflowData =
                new GenerateWorkflowResponse.WorkflowData();
        workflowData.setName(deriveWorkflowName(prompt));
        workflowData.setSteps(steps);

        GenerateWorkflowResponse response = new GenerateWorkflowResponse();
        response.setWorkflow(workflowData);
        response.setConfidence(1.0);
        return response;
    }

    private String deriveWorkflowName(String prompt) {
        if (prompt.contains("sales"))   return "Daily Sales Report Automation";
        if (prompt.contains("monitor")) return "System Monitoring & Alert";
        if (prompt.contains("email"))   return "Email Notification Workflow";
        if (prompt.contains("fetch"))   return "Data Fetch Automation";
        return "Automated Workflow";
    }
}