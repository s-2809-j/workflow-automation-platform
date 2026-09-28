package com.company.workflowautomation.ai.application;

import com.company.workflowautomation.ai.shared.Exception.DraftAccessException;
import com.company.workflowautomation.shared.tenant.TenantContextHolder;
import com.company.workflowautomation.workflow.domain.WorkflowDraft;
import com.company.workflowautomation.workflow.infrastructure.WorkflowDraftRepository;
import com.company.workflowautomation.workflow.jpa.WorkflowEntity;
import com.company.workflowautomation.workflow.jpa.WorkflowJpaRepository;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApproveDraftUseCase {

    private final WorkflowDraftRepository draftRepository;
    private final WorkflowJpaRepository workflowRepository;
    private final WorkflowStepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Transactional
    public UUID execute(UUID draftId, UUID userId,
                        Map<String, Map<String, Object>> inputOverrides) {

        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }

        // RLS is evaluated by PostgreSQL on this transaction's connection, not
        // from the request ThreadLocal. Set the authenticated tenant before the
        // workflow insert so WITH CHECK matches workflow.organization_id.
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :organizationId, true)")
                .setParameter("organizationId", organizationId.toString())
                .getSingleResult();

        // 1. Fetch and validate draft
        WorkflowDraft draft = draftRepository
                .findByIdAndOrganizationId(draftId, organizationId)  // tenant_id is the actual column
                .orElseThrow(() -> new DraftAccessException("Draft not found or access denied", 404));

        if ("APPROVED".equals(draft.getStatus()) || "ACTIVE".equals(draft.getStatus())) {
            throw new IllegalStateException("Draft already approved: draftId=" + draftId);
        }

        // 2. Parse jsonContent
        JsonNode root;
        try {
            root = objectMapper.readTree(draft.getJsonContent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse draft JSON", e);
        }

        String workflowName = root.path("name").asText("Untitled Workflow");

        JsonNode stepsValidation = root.path("steps");
        validateHttpSteps(stepsValidation);
        validateEmailBodies(stepsValidation);
        validateEmailRecipients(stepsValidation, draftId, inputOverrides);
        // 3. Create WorkflowEntity
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setOrganizationId(organizationId);
        workflow.setName(workflowName);
        workflow.setDescription("Generated from AI draft " + draftId);
        workflow.setStatus("ACTIVE");
        workflow.setCreatedBy(userId);
        workflow.setCreatedAt(Instant.now());
        workflow.setUpdatedAt(Instant.now());
        WorkflowEntity saved = workflowRepository.save(workflow);

        log.info("Created workflow id={} name={}", saved.getId(), workflowName);

        // 4 & 5. Create WorkflowStepEntities using two-pass saving
        // Pass 1: Save steps to generate true database UUIDs (Hibernate @UuidGenerator assigns on INSERT)
        // Pass 2: Wire up dependsOn with the real database UUIDs from Pass 1
        JsonNode steps = root.path("steps");
        Map<String, UUID> aiIdToRealId = new HashMap<>();
        List<WorkflowStepEntity> savedEntities = new ArrayList<>();

        if (steps.isArray()) {
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                String aiId = step.path("id").asText();

                String stepType = step.has("stepType")
                        ? step.path("stepType").asText("ACTION")
                        : step.path("step_type").asText("ACTION");

                // Get config — make a mutable copy so we can apply overrides
                com.fasterxml.jackson.databind.node.ObjectNode config = step.has("config")
                        ? (com.fasterxml.jackson.databind.node.ObjectNode)
                        step.path("config").deepCopy()
                        : objectMapper.createObjectNode();

                // Apply user overrides from the smart config panel
                Map<String, Object> overridesForStep =
                        inputOverrides != null ? inputOverrides.get(aiId) : null;

                if (overridesForStep != null && !overridesForStep.isEmpty()) {
                    if ("SCRIPT".equalsIgnoreCase(stepType)) {
                        com.fasterxml.jackson.databind.node.ObjectNode inputsNode =
                                config.has("inputs") && config.get("inputs").isObject()
                                        ? (com.fasterxml.jackson.databind.node.ObjectNode)
                                        config.get("inputs").deepCopy()
                                        : objectMapper.createObjectNode();

                        overridesForStep.forEach((k, v) ->
                                inputsNode.set(k, objectMapper.valueToTree(v)));
                        config.set("inputs", inputsNode);
                        log.info("Applied {} input override(s) to SCRIPT step aiId={}",
                                overridesForStep.size(), aiId);
                    }

                    if ("EMAIL".equalsIgnoreCase(stepType)) {
                        if (overridesForStep.containsKey("recipient")) {
                            String recipient = overridesForStep.get("recipient").toString().trim();
                            if (!recipient.isBlank()) {
                                config.put("recipient", recipient);
                                config.put("recipientSource", "FIXED");
                                log.info("Applied recipient override to EMAIL step aiId={} recipient={}",
                                        aiId, recipient);
                            }
                        }
                    }
                }

                WorkflowStepEntity stepEntity = new WorkflowStepEntity();
                stepEntity.setOrganizationId(organizationId);
                stepEntity.setWorkflowId(saved.getId());
                stepEntity.setStepOrder(i + 1);
                stepEntity.setName(step.path("name").asText("Step " + (i + 1)));
                stepEntity.setStepType(stepType);
                stepEntity.setConfig(config);
                stepEntity.setDependsOn(objectMapper.createArrayNode());
                stepEntity.setCreatedAt(Instant.now());
                stepEntity.setUpdatedAt(Instant.now());

                WorkflowStepEntity savedStep = stepRepository.saveAndFlush(stepEntity);
                aiIdToRealId.put(aiId, savedStep.getId());
                savedEntities.add(savedStep);
                log.info("Pass 1: Created step order={} name={} with dbId={}",
                        i + 1, savedStep.getName(), savedStep.getId());
            }

            // Pass 2: Wire up dependsOn with guaranteed real database UUIDs
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                WorkflowStepEntity savedStep = savedEntities.get(i);

                JsonNode depsNode = step.has("dependsOn")
                        ? step.path("dependsOn")
                        : step.path("depends_on");

                List<String> resolvedDeps = new ArrayList<>();
                if (depsNode != null && depsNode.isArray()) {
                    for (JsonNode dep : depsNode) {
                        String depAiId = dep.asText();
                        UUID resolvedId = aiIdToRealId.get(depAiId);
                        if (resolvedId != null) {
                            resolvedDeps.add(resolvedId.toString());
                            log.info("Resolved dep: aiId={} → realDbId={}", depAiId, resolvedId);
                        } else {
                            log.warn("Dependency aiId={} not found in aiIdToRealId keys: {}",
                                    depAiId, aiIdToRealId.keySet());
                        }
                    }
                }

                // Fallback: If not step 0 and resolvedDeps is empty, chain to the previous step's real UUID
                if (resolvedDeps.isEmpty() && i > 0) {
                    UUID prevRealId = savedEntities.get(i - 1).getId();
                    resolvedDeps.add(prevRealId.toString());
                    log.info("Auto-chained dep for step i={}: fell back to prevStep realDbId={}", i, prevRealId);
                }

                savedStep.setDependsOn(objectMapper.valueToTree(resolvedDeps));
                savedStep.setUpdatedAt(Instant.now());
                stepRepository.saveAndFlush(savedStep);
                log.info("Pass 2: Saved step order={} name={} dependsOn={}",
                        i + 1, savedStep.getName(), resolvedDeps);
            }
        }

        // 6. Mark draft as approved
        draft.approve(saved.getId());
        draftRepository.saveAndFlush(draft);

        return saved.getId();
    }
    // Backward-compatible overload for any internal callers without overrides
    @Transactional
    public UUID execute(UUID draftId, UUID userId) {
        return execute(draftId, userId, Map.of());
    }
    private void validateHttpSteps(JsonNode steps) {
        if (!steps.isArray()) return;
        for (JsonNode step : steps) {
            String stepType = step.has("stepType")
                    ? step.path("stepType").asText("")
                    : step.path("step_type").asText("");
            if (!"HTTP".equalsIgnoreCase(stepType)) continue;

            JsonNode config = step.path("config");
            String url = config.has("url") ? config.get("url").asText("").trim() : "";
            String stepName = step.path("name").asText("unknown");

            if (url.isBlank()) {
                throw new IllegalStateException(
                        "Cannot approve draft: HTTP step '" + stepName
                                + "' has no URL configured. Please edit the step config before approving.");
            }

            // Reject known stale API patterns
            if (url.contains("exchangerate-api.com/v4")
                    || url.contains("api.fixer.io/latest")
                    || url.contains("openexchangerates.org/api/latest")) {
                throw new IllegalStateException(
                        "Cannot approve draft: HTTP step '" + stepName
                                + "' uses a known stale or unreliable API endpoint: " + url
                                + ". Please update to a reliable live API such as https://open.er-api.com/v6/latest/USD");
            }
        }
    }
    private void validateEmailBodies(JsonNode steps) {
        if (!steps.isArray()) return;
        for (JsonNode step : steps) {
            String stepType = step.has("stepType")
                    ? step.path("stepType").asText("") : step.path("step_type").asText("");
            if (!"EMAIL".equalsIgnoreCase(stepType)) continue;

            JsonNode config = step.path("config");
            String body = config.has("body") ? config.get("body").asText("") : "";
            String stepName = step.path("name").asText("unknown");

            // Detect JavaScript code mistakenly placed in email body
            if (body.contains("var ") || body.contains("function(")
                    || body.contains("JSON.parse") || body.contains("return ")) {
                throw new IllegalStateException(
                        "Cannot approve draft: EMAIL step '" + stepName
                                + "' has JavaScript code in the body field. "
                                + "The email body must be a plain static string. "
                                + "Use a SCRIPT step before this EMAIL step to construct dynamic content.");
            }
        }
    }
    private void validateEmailRecipients(JsonNode steps, UUID draftId,
                                         Map<String, Map<String, Object>> inputOverrides) {
        if (!steps.isArray()) return;
        for (JsonNode step : steps) {
            String stepType = step.has("stepType")
                    ? step.path("stepType").asText("")
                    : step.path("step_type").asText("");
            if (!"EMAIL".equalsIgnoreCase(stepType)) continue;

            String stepName  = step.path("name").asText(step.path("id").asText("unknown"));
            String stepAiId  = step.path("id").asText("");
            JsonNode config  = step.path("config");
            String source    = config.has("recipientSource")
                    ? config.get("recipientSource").asText("").trim().toUpperCase()
                    : "";

            switch (source) {
                case "FIXED" -> {
                    String recipient = config.has("recipient")
                            ? config.get("recipient").asText("").trim() : "";
                    boolean inOverrides = inputOverrides != null
                            && inputOverrides.containsKey(stepAiId)
                            && inputOverrides.get(stepAiId).containsKey("recipient")
                            && !inputOverrides.get(stepAiId).get("recipient").toString().trim().isBlank();
                    if (recipient.isBlank() && !inOverrides) {
                        throw new IllegalStateException(
                                "Cannot approve draft: EMAIL step '" + stepName
                                        + "' requires a recipient email address. "
                                        + "Please enter your email in the configuration panel.");
                    }
                }
                case "WORKFLOW_INPUT" -> {
                    String inputKey = config.has("inputKey")
                            ? config.get("inputKey").asText("").trim() : "";
                    if (inputKey.isBlank()) {
                        throw new IllegalStateException(
                                "Cannot approve draft: EMAIL step '" + stepName
                                        + "' has recipientSource=WORKFLOW_INPUT but inputKey is missing.");
                    }
                }
                case "PREVIOUS_STEP_OUTPUT" -> {
                    // No validation needed — executeEmail resolves
                    // body/subject from the most recent SCRIPT step automatically.
                }
                default -> throw new IllegalStateException(
                        "Cannot approve draft: EMAIL step '" + stepName
                                + "' has no valid recipientSource. "
                                + "Configure the recipient using PUT /ai/drafts/" + draftId + "/email-recipient.");
            }
        }
    }

}
