package com.company.workflowautomation.ai.application;

import com.company.workflowautomation.ai.dto.GenerateWorkflowRequest;
import com.company.workflowautomation.ai.dto.GenerateWorkflowResponse;
import com.company.workflowautomation.ai.dto.EmailRecipientConfigurationRequest;
import com.company.workflowautomation.ai.shared.Exception.DraftAccessException;
import com.company.workflowautomation.ai.shared.Exception.WorkflowGenerationException;
import com.company.workflowautomation.shared.tenant.TenantContextHolder;
import com.company.workflowautomation.workflow.domain.WorkflowDraft;
import com.company.workflowautomation.workflow.infrastructure.WorkflowDraftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Slf4j
@Service
public class GenerateWorkflowUseCase {

    // ── Built once at class-load time, never rebuilt per request ─
    private static final String SYSTEM_PROMPT = """
    You are a workflow automation engine that generates detailed, executable, real-world workflows.

    CRITICAL RULES:
    1. Return ONLY valid JSON. No markdown, no backticks, no explanation.
    2. Always generate MULTIPLE steps (minimum 3, prefer 4-6).
    3. Never combine multiple operations into one step.
    4. Think like a senior engineer designing a real automation pipeline.
    5. Never hardcode time-sensitive values such as exchange rates, prices, thresholds,
       temperatures, or any value that changes over time. Always use configurable thresholds.

            EMAIL RULES:
            - Never invent or emit a real email address.
            - EMAIL config must always include recipientSource: FIXED, WORKFLOW_INPUT, or PREVIOUS_STEP_OUTPUT.
            - Unless the user explicitly provided an email address, always use:
              { "recipientSource": "FIXED", "recipient": "" }
            - The recipient is configured by the user before the draft is approved.
            - The EMAIL body and subject must ALWAYS be plain static strings — NO template syntax, NO JS.
            - HOWEVER: When a previous SCRIPT step produces a meaningful result (e.g. rate crossed/not crossed,
              weather alert, anomaly detected), you MUST insert a dedicated SCRIPT step immediately before
              the EMAIL step that constructs a human-readable email body string and returns it as:
                JSON.stringify({ emailBody: "...", emailSubject: "..." })
              Then the EMAIL step must use:
                { "recipientSource": "PREVIOUS_STEP_OUTPUT", "outputField": "emailBody", "recipient": "",
                  "subject": "...", "body": "", "isHtml": false }
            - This pattern is REQUIRED whenever the workflow monitors a value, checks a threshold,
              or produces a decision. Never send a vague static body like "check the logs" in those cases.
            - Example of the body-builder SCRIPT step:
              {
                "id": "step-3",
                "stepType": "SCRIPT",
                "name": "Build email body from rate check result",
                "config": {
                  "inputs": {},
                  "script": "var result = {}; try { result = JSON.parse(previousStepOutput || '{}'); } catch(e) {} var body = result.message || 'Exchange rate check completed.'; JSON.stringify({ emailBody: body, emailSubject: result.crossed ? 'ALERT: Threshold Crossed' : 'OK: Rate Within Threshold' });"
                },
                "dependsOn": ["step-2"]
              }
    HTTP RULES — CRITICAL:
    - Always use reliable, live, production-grade APIs that do not require authentication
      unless the user explicitly names a specific API.
    - Use ONLY these approved APIs for common use cases:
      * Exchange rates  → https://open.er-api.com/v6/latest/{BASE_CURRENCY}
        e.g. USD base: https://open.er-api.com/v6/latest/USD
        Response path to rate: rates.{TARGET_CURRENCY} e.g. rates.INR
      * Weather         → https://wttr.in/{city}?format=j1
        Response path to temp: current_condition[0].temp_C
      * Public holidays → https://date.nager.at/api/v3/PublicHolidays/{year}/{countryCode}
      * World time      → https://worldtimeapi.org/api/timezone/{timezone}
      * * News/RSS     → https://feeds.bbci.co.uk/news/world/rss.xml (BBC RSS, no key needed, XML format)
                                 OR https://gnews.io/api/v4/search?q={topic}&lang=en&token={API_KEY} (requires free key)
                               → Instruct user to register at gnews.io for a free API key and add it to the URL.
                               → Always add a LOG step before the HTTP step explaining this requirement.
    - Never use versioned legacy endpoints (e.g. /v4/, /v1/ when newer versions exist).
    - Never use APIs that return cached or stale data.
    - If no approved API fits the use case, use a realistic placeholder URL and add a
      LOG step before it explaining that the user must configure the correct API URL.

    SCRIPT RULES — CRITICAL:
    - Never hardcode numeric thresholds, rates, prices, or any time-sensitive value
      in script code. Instead, read them from the config object using the inputs binding.
    - Scripts run in a sandboxed JavaScript engine. They cannot make HTTP calls,
      import modules, or access global state.
    - The step config must include an "inputs" object with all configurable values.
    - The script reads those values via the bound variable names.
    - Example of CORRECT threshold script for exchange rate monitoring:
      config: {
        "inputs": { "threshold": 85, "targetCurrency": "INR" },
        "script": "var rate = parseFloat(previousStepResult) || 0; var threshold = parseFloat(inputs.threshold) || 85; var crossed = rate > threshold; JSON.stringify({ rate: rate, threshold: threshold, crossed: crossed, message: crossed ? 'Rate ' + rate + ' crossed threshold ' + threshold : 'Rate ' + rate + ' is within threshold' });"
      }
    - The threshold value in inputs is the DEFAULT. Users can update it in the workflow
      step configuration at any time without touching the script code.
    - Always wrap the entire script in a single expression or use JSON.stringify() as
      the last statement to return structured output.
      
      LOG RULES — CRITICAL:
      - The LOG message must be a plain static string. Never use {{previousStepOutput}}\s
                or any template syntax — these are not evaluated and will appear literally in logs.
      - To log a result, use a SCRIPT step before the LOG step that extracts the value\s
                you want to log and returns it as a string, then LOG that extracted value statically.

    REQUIRED OUTPUT FORMAT (strict):
    {
      "name": "descriptive workflow name",
      "steps": [
        {
          "id": "step-1",
          "stepType": "ONE OF: HTTP|LOG|DELAY|DATABASE|SCRIPT|EMAIL|WEBHOOK|ACTION",
          "name": "human readable step name",
          "config": { ... populated fields based on stepType ... },
          "dependsOn": []
        }
      ]
    }

    STEP TYPE CONFIG RULES:
    - DATABASE → { "query": "SELECT ...", "queryType": "SELECT|INSERT|UPDATE" }
    - HTTP     → { "url": "https://...", "method": "GET|POST" }
    - EMAIL    → { "recipientSource": "FIXED", "recipient": "", "subject": "...", "body": "...", "isHtml": false }
    - LOG      → { "message": "...", "level": "INFO|WARN|ERROR" }
    - SCRIPT   → { "inputs": { "key": defaultValue }, "script": "javascript using inputs.key" }
    - WEBHOOK  → { "url": "https://...", "method": "POST", "payload": "..." }
    - DELAY    → { "duration": 5000 }
    - ACTION   → only when no other type fits

    STEP SELECTION GUIDE:
    - Fetch data from external API  → HTTP (GET) using approved API list above
    - Process/evaluate/compare data → SCRIPT with inputs for all configurable values
    - Send notification             → EMAIL
    - Log progress or result        → LOG
    - Wait between steps            → DELAY
    - Trigger external system       → WEBHOOK
    - Fetch/store from your database → DATABASE

    DEPENDENCY RULES:
    - First step always has "dependsOn": []
    - Each subsequent step depends on the previous step's id unless parallel execution is intended

    ACTUAL DATABASE SCHEMA (use ONLY these tables and columns):
    - users              → id, organization_id, email, password_hash, status, created_at, updated_at
    - organizations      → id, name, slug, status, created_at, updated_at
    - workflow           → id, organization_id, name, description, status, created_by, created_at, updated_at
    - workflow_execution → id, workflow_id, organization_id, status, started_at, completed_at, error_message

    STATUS VALUES: Always uppercase — 'ACTIVE', 'INACTIVE', 'PENDING', 'RUNNING', 'SUCCESS', 'FAILED'
    IMPORTANT: No tasks table, no user_activities table, no name column on users.

            EXAMPLE — Exchange rate monitoring workflow:
             {
               "name": "Daily USD/INR Exchange Rate Monitor",
               "steps": [
                 {
                   "id": "step-1",
                   "stepType": "HTTP",
                   "name": "Fetch latest USD exchange rates",
                   "config": { "url": "https://open.er-api.com/v6/latest/USD", "method": "GET" },
                   "dependsOn": []
                 },
                 {
                   "id": "step-2",
                   "stepType": "SCRIPT",
                   "name": "Check USD/INR rate against configurable threshold",
                   "config": {
                     "inputs": { "threshold": 85, "targetCurrency": "INR" },
                     "script": "var rate = 0; try { var parsed = JSON.parse(previousStepOutput || '{}'); rate = parsed.rates ? parseFloat(parsed.rates[inputs.targetCurrency] || 0) : 0; } catch(e) { rate = 0; } var threshold = parseFloat(inputs.threshold) || 85; var crossed = rate > threshold; JSON.stringify({ rate: rate, targetCurrency: inputs.targetCurrency, threshold: threshold, crossed: crossed, message: crossed ? 'ALERT: USD/' + inputs.targetCurrency + ' rate ' + rate + ' has crossed threshold ' + threshold + '. Immediate attention required.' : 'OK: USD/' + inputs.targetCurrency + ' rate ' + rate + ' is within the threshold of ' + threshold + '. No action needed.' });"
                   },
                   "dependsOn": ["step-1"]
                 },
                 {
                   "id": "step-3",
                   "stepType": "SCRIPT",
                   "name": "Build email body from rate check result",
                   "config": {
                     "inputs": {},
                     "script": "var result = {}; try { result = JSON.parse(previousStepOutput || '{}'); } catch(e) {} var body = result.message || 'Exchange rate check completed.'; var subject = result.crossed ? 'ALERT: USD/' + (result.targetCurrency || 'INR') + ' Threshold Crossed' : 'OK: USD/' + (result.targetCurrency || 'INR') + ' Rate Normal'; JSON.stringify({ emailBody: body, emailSubject: subject });"
                   },
                   "dependsOn": ["step-2"]
                 },
                 {
                   "id": "step-4",
                   "stepType": "EMAIL",
                   "name": "Notify team with rate decision",
                   "config": {
                     "recipientSource": "PREVIOUS_STEP_OUTPUT",
                     "outputField": "emailBody",
                     "subjectField": "emailSubject",
                     "recipient": "",
                     "subject": "USD/INR Exchange Rate Alert",
                     "body": "",
                     "isHtml": false
                   },
                   "dependsOn": ["step-3"]
                 },
                 {
                   "id": "step-5",
                   "stepType": "LOG",
                   "name": "Log daily exchange rate check result",
                   "config": { "message": "Daily USD/INR exchange rate check completed.", "level": "INFO" },
                   "dependsOn": ["step-4"]
                 }
               ]
             }

    Generate a similar detailed workflow for the user request below.
    Remember: minimum 3 steps, use approved APIs only, never hardcode thresholds.
    """;

    private final AiService aiService;
    private final WorkflowDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    public GenerateWorkflowUseCase(AiService aiService,
                                   WorkflowDraftRepository draftRepository,
                                   ObjectMapper objectMapper) {
        this.aiService      = aiService;
        this.draftRepository = draftRepository;
        this.objectMapper   = objectMapper;
    }

    @Transactional
    public UUID execute(Map<String, Object> input, UUID userId) {

        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }

        // ── Input validation ─────────────────────────────────────
        if (input == null || !input.containsKey("prompt")) {
            throw new IllegalArgumentException("Request must contain a 'prompt' field");
        }

        String userPrompt = input.get("prompt") == null
                ? null : input.get("prompt").toString().trim();

        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty");
        }
        if (userPrompt.length() > 1000) {
            throw new IllegalArgumentException(
                    "Prompt too long — maximum 1000 characters, got: "
                            + userPrompt.length());
        }

        // ── Call AI with separated user prompt + system prompt ───
        GenerateWorkflowRequest request =
                new GenerateWorkflowRequest(userPrompt, SYSTEM_PROMPT);

        GenerateWorkflowResponse response =
                aiService.generateWorkflowResponse(request);

        log.info("AI response output: {}", response.getOutput());

        Map<String, Object> workflowJson = response.getOutput();
        if (workflowJson == null || workflowJson.isEmpty()) {
            throw new WorkflowGenerationException(
                    "AI returned empty workflow output", null);
        }

        // ── Persist draft ────────────────────────────────────────
        try {
            String jsonString = objectMapper.writeValueAsString(workflowJson);
            WorkflowDraft draft = new WorkflowDraft(jsonString, organizationId);
            draftRepository.save(draft);
            return draft.getId();

        } catch (Exception e) {
            throw new WorkflowGenerationException(
                    "Failed to serialize workflow JSON", e);
        }
    }

    public WorkflowDraft getDraft(UUID draftId) {
        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }
        return draftRepository.findByIdAndOrganizationId(draftId, organizationId)
                .orElseThrow(() -> new DraftAccessException(
                        "Draft not found or access denied", 404));
    }

    public List<WorkflowDraft> getDraftsByUser(UUID userId) {
        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }
        log.info("Fetching drafts for organizationId={}", organizationId);
        return draftRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public void configureEmailRecipient(UUID draftId, EmailRecipientConfigurationRequest request, UUID userId) {
        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) throw new IllegalStateException("Organization ID missing from context");
        WorkflowDraft draft = draftRepository.findByIdAndOrganizationId(draftId, organizationId)
                .orElseThrow(() -> new DraftAccessException("Draft not found or access denied", 404));
        try {
            var root = objectMapper.readTree(draft.getJsonContent());
            var steps = root.path("steps");
            ObjectNode config = null;
            for (var step : steps) {
                if (request.stepId().equals(step.path("id").asText()) && "EMAIL".equals(step.path("stepType").asText())) {
                    config = (ObjectNode) step.with("config");
                    break;
                }
            }
            if (config == null) throw new IllegalArgumentException("EMAIL step not found in draft");
            String source = request.recipientSource().trim().toUpperCase();
            if (!List.of("FIXED", "WORKFLOW_INPUT", "PREVIOUS_STEP_OUTPUT").contains(source)) {
                throw new IllegalArgumentException("Unsupported recipient source: " + source);
            }
            config.remove(List.of("to", "recipient", "inputKey", "previousStepId", "outputField"));
            config.put("recipientSource", source);
            switch (source) {
                case "FIXED" -> {
                    if (request.recipient() == null || request.recipient().isBlank()) throw new IllegalArgumentException("A fixed recipient is required");
                    config.put("recipient", request.recipient().trim());
                }
                case "WORKFLOW_INPUT" -> {
                    if (request.inputKey() == null || request.inputKey().isBlank()) throw new IllegalArgumentException("An input key is required");
                    config.put("inputKey", request.inputKey().trim());
                }
                case "PREVIOUS_STEP_OUTPUT" -> {
                    if (request.previousStepId() == null || request.previousStepId().isBlank() || request.outputField() == null || request.outputField().isBlank()) throw new IllegalArgumentException("Previous step ID and output field are required");
                    config.put("previousStepId", request.previousStepId().trim());
                    config.put("outputField", request.outputField().trim());
                }
            }
            draft.updateJsonContent(objectMapper.writeValueAsString(root));
            draftRepository.save(draft);
        } catch (DraftAccessException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update EMAIL recipient configuration", e);
        }
    }

    @Transactional
    public void rejectDraft(UUID draftId, UUID userId) {
        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }
        log.info("Rejecting draftId={} for organizationId={}", draftId, organizationId);
        WorkflowDraft draft =
                draftRepository.findByIdAndOrganizationId(draftId, organizationId)
                        .orElseThrow(() -> new DraftAccessException(
                                "Draft not found or access denied", 404));
        draft.reject();
        draftRepository.save(draft);
    }
}
