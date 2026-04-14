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
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class GenerateWorkflowUseCase {

    private final AiService aiService;
    private final WorkflowDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    public GenerateWorkflowUseCase(AiService aiService,
                                   WorkflowDraftRepository draftRepository,
                                   ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.draftRepository = draftRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID execute(Map<String, Object> input, UUID userId) {

        // renamed from tenantId → organizationId to match DB column
        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }

        GenerateWorkflowRequest request =
                new GenerateWorkflowRequest(input.get("prompt").toString());
        GenerateWorkflowResponse response = aiService.generateWorkflowResponse(request);

        Map<String, Object> workflowJson = response.getOutput();
        try {
            String jsonString = objectMapper.writeValueAsString(workflowJson);

            // WorkflowDraft constructor now takes organizationId
            WorkflowDraft draft = new WorkflowDraft(jsonString, organizationId);
            draftRepository.save(draft);

            return draft.getId();

        } catch (Exception e) {
            throw new WorkflowGenerationException("Failed to serialize workflow JSON", e);
        }
    }

    public WorkflowDraft getDraft(UUID draftId) {

        UUID organizationId = TenantContextHolder.getTenantId();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID missing from context");
        }

        // single DB call — tenant isolation enforced at query level
        // no more findById() + manual check
        return draftRepository.findByIdAndOrganizationId(draftId, organizationId)
                .orElseThrow(() -> new DraftAccessException(
                        "Draft not found or access denied", 404));
    }
}