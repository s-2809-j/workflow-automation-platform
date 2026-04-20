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

    @Transactional
    public UUID execute(UUID draftId, UUID userId) {

        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }

        // 1. Fetch and validate draft
        WorkflowDraft draft = draftRepository
                .findByIdAndOrganizationId(draftId, organizationId)
                .orElseThrow(() -> new DraftAccessException("Draft not found or access denied", 404));

        if ("APPROVED".equals(draft.getStatus())) {
            throw new IllegalStateException("Draft already approved");
        }

        // 2. Parse jsonContent
        JsonNode root;
        try {
            root = objectMapper.readTree(draft.getJsonContent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse draft JSON", e);
        }

        String workflowName = root.path("name").asText("Untitled Workflow");

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

        // 4. Build mapping from AI step id (e.g. "step-1") → real UUID
        JsonNode steps = root.path("steps");
        Map<String, UUID> aiIdToRealId = new HashMap<>();
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                String aiId = step.path("id").asText();
                aiIdToRealId.put(aiId, UUID.randomUUID());
            }
        }

        // 5. Create WorkflowStepEntities with resolved dependsOn UUIDs
        if (steps.isArray()) {
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                String aiId = step.path("id").asText();

                // Translate ["step-1", "step-2"] → ["real-uuid-1", "real-uuid-2"]
                List<String> resolvedDeps = new ArrayList<>();
                for (JsonNode dep : step.path("dependsOn")) {
                    UUID resolvedId = aiIdToRealId.get(dep.asText());
                    if (resolvedId != null) {
                        resolvedDeps.add(resolvedId.toString());
                    }
                }

                WorkflowStepEntity stepEntity = new WorkflowStepEntity();
                stepEntity.setId(aiIdToRealId.get(aiId));
                stepEntity.setOrganizationId(organizationId);
                stepEntity.setWorkflowId(saved.getId());
                stepEntity.setStepOrder(i + 1);
                stepEntity.setName(step.path("name").asText("Step " + (i + 1)));
                stepEntity.setStepType(step.path("stepType").asText("ACTION"));
                stepEntity.setConfig(step.path("config"));
                // Store resolved UUIDs back as a JsonNode (matches the JsonNode dependsOn field)
                stepEntity.setDependsOn(objectMapper.valueToTree(resolvedDeps));
                stepEntity.setCreatedAt(Instant.now());
                stepEntity.setUpdatedAt(Instant.now());

                stepRepository.save(stepEntity);
                log.info("Created step order={} name={} dependsOn={}", i + 1, stepEntity.getName(), resolvedDeps);
            }
        }

        // 6. Mark draft as approved
        draft.approve(saved.getId());
        draftRepository.save(draft);

        return saved.getId();
    }
}