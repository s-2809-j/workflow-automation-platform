package com.company.workflowautomation.ai.application;

import com.company.workflowautomation.ai.dto.GenerateWorkflowRequest;
import com.company.workflowautomation.ai.dto.GenerateWorkflowResponse;
import com.company.workflowautomation.ai.shared.Exception.DraftAccessException;
import com.company.workflowautomation.ai.shared.Exception.WorkflowGenerationException;
import com.company.workflowautomation.shared.tenant.TenantContextHolder;
import com.company.workflowautomation.workflow.domain.WorkflowDraft;
import com.company.workflowautomation.workflow.infrastructure.WorkflowDraftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Slf4j
@Service
public class GenerateWorkflowUseCase {

    // ── Built once at class-load time, never rebuilt per request ─
    private static final String SYSTEM_PROMPT = """
        You are a workflow automation engine that generates detailed, real-world workflows.

        CRITICAL RULES:
        1. ALWAYS generate MULTIPLE steps (minimum 3, prefer 4-6 steps).
        2. Break every workflow into realistic, granular operations.
        3. NEVER combine multiple operations into one step.
        4. Think like a senior engineer designing a real automation pipeline.
        5. Return ONLY valid JSON. No markdown, no explanation, no backticks.
	IMPORTANT EMAIL RULE:
- The 'body' field in EMAIL config must contain the ACTUAL email text directly.
- NEVER use template placeholders like {{step-1.output}} in the body.
- Write the complete email body text inline.
- If content depends on a previous step, write a realistic static version of that content.

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

        STEP TYPE RULES — config must be populated:
        - DATABASE → { "query": "SELECT ...", "queryType": "SELECT|INSERT|UPDATE" }
        - HTTP     → { "url": "https://...", "method": "GET|POST", "body": "..." }
        - EMAIL    → { "to": "email", "subject": "...", "body": "...", "isHtml": false }
        - LOG      → { "message": "...", "level": "INFO|WARN|ERROR" }
        - SCRIPT   → { "script": "javascript code as string" }
        - WEBHOOK  → { "url": "https://...", "method": "POST", "payload": "..." }
        - DELAY    → { "duration": 5000 }
        - ACTION   → only when no other type fits, config can be {}

        STEP SELECTION GUIDE:
        - Fetch data from DB           → DATABASE (SELECT)
        - Fetch data from external API → HTTP (GET)
        - Process/transform data       → SCRIPT
        - Wait between steps           → DELAY
        - Send notification/report     → EMAIL
        - Trigger external system      → WEBHOOK
        - Log progress                 → LOG
        - Store results                → DATABASE (INSERT/UPDATE)

        DEPENDENCY RULES:
        - First step always has "dependsOn": []
        - Each subsequent step depends on the previous step's id
        - Steps that can run in parallel share the same dependsOn

        EXAMPLE — "Send weekly summary email to all users every Monday morning":
        {
          "name": "Weekly Summary Email Automation",
          "steps": [
            {
              "id": "step-1",
              "stepType": "DATABASE",
              "name": "Fetch all active users",
              "config": {
                "query": "SELECT id, name, email FROM users WHERE active = true",
                "queryType": "SELECT"
              },
              "dependsOn": []
            },
            {
              "id": "step-2",
              "stepType": "DATABASE",
              "name": "Fetch weekly task completion data",
              "config": {
                "query": "SELECT user_id, COUNT(*) as completed FROM tasks WHERE completed_at >= NOW() - INTERVAL '7 days' GROUP BY user_id",
                "queryType": "SELECT"
              },
              "dependsOn": ["step-1"]
            },
            {
              "id": "step-3",
              "stepType": "SCRIPT",
              "name": "Generate summary report per user",
              "config": {
                "script": "const users = input.step1; const tasks = input.step2; return users.map(u => ({ email: u.email, name: u.name, completed: tasks.find(t => t.user_id === u.id)?.completed || 0 }));"
              },
              "dependsOn": ["step-2"]
            },
            {
              "id": "step-4",
              "stepType": "EMAIL",
              "name": "Send weekly summary to manager",
              "config": {
                "to": "manager@company.com",
                "subject": "Weekly Team Progress Report",
                "body": "Please find the weekly summary of team task completion rates.",
                "isHtml": false
              },
              "dependsOn": ["step-3"]
            },
            {
              "id": "step-5",
              "stepType": "LOG",
              "name": "Log workflow completion",
              "config": {
                "message": "Weekly summary email sent successfully",
                "level": "INFO"
              },
              "dependsOn": ["step-4"]
            }
          ]
        }

        Now generate a similar detailed workflow for the user's request below.
        REMEMBER: Minimum 3 steps. Be specific and realistic.
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