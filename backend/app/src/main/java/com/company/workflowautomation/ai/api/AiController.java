package com.company.workflowautomation.ai.api;

import com.company.workflowautomation.ai.adapter.AiAdapter;
import com.company.workflowautomation.ai.application.ApproveDraftUseCase;
import com.company.workflowautomation.ai.application.GenerateWorkflowUseCase;
import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.workflow.domain.WorkflowDraft;
import com.company.workflowautomation.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiAdapter aiAdapter;
    private final GenerateWorkflowUseCase generateWorkflowUseCase;
    private final ApproveDraftUseCase approveDraftUseCase;

    @PostMapping("/analyze")
    public ResponseEntity<AiResponse> analyze(@RequestBody @Valid AiRequest request) {
        log.info("AI analyze request for the workflowId = {}", request.getWorkflowId());
        AiResponse response = aiAdapter.analyzeExecution(request);
        return ResponseEntity.ok(response);
    }

    // Kept for backward compat — frontend now uses POST /drafts instead
    @PostMapping("/generate-workflow")
    public ResponseEntity<Map<String, UUID>> generateWorkflow(
            @RequestBody Map<String, Object> input,
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("Generate workflow request from the userId={}", userId);
        UUID draftId = generateWorkflowUseCase.execute(input, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("draftId", draftId));
    }

    // POST /api/v1/ai/drafts — createDraft (matches frontend)
    @PostMapping("/drafts")
    public ResponseEntity<Map<String, UUID>> createDraft(@RequestBody Map<String, Object> body) {
        UUID userId = SecurityUtils.getUserId();
        log.info("Create draft request from userId={}", userId);
        UUID draftId = generateWorkflowUseCase.execute(body, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("draftId", draftId));
    }

    // GET /api/v1/ai/drafts — getDrafts (userId from JWT, not header)
    @GetMapping("/drafts")
    public ResponseEntity<List<WorkflowDraft>> getDraftsByUser() {
        UUID userId = SecurityUtils.getUserId();
        log.info("Fetching drafts for userId={}", userId);
        List<WorkflowDraft> drafts = generateWorkflowUseCase.getDraftsByUser(userId);
        return ResponseEntity.ok(drafts);
    }

    // GET /api/v1/ai/drafts/{draftId}
    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<WorkflowDraft> getDraft(@PathVariable UUID draftId) {
        log.info("Fetching draft draftId={}", draftId);
        WorkflowDraft draft = generateWorkflowUseCase.getDraft(draftId);
        return ResponseEntity.ok(draft);
    }

    // POST /api/v1/ai/drafts/{draftId}/approve
    @PostMapping("/drafts/{draftId}/approve")
    public ResponseEntity<Map<String, UUID>> approveDraft(@PathVariable UUID draftId) {
        UUID userId = SecurityUtils.getUserId();
        log.info("Approving draft draftId={} by userId={}", draftId, userId);
        UUID workflowId = approveDraftUseCase.execute(draftId, userId);
        return ResponseEntity.ok(Map.of("workflowId", workflowId));
    }

    // POST /api/v1/ai/drafts/{draftId}/reject
    @PostMapping("/drafts/{draftId}/reject")
    public ResponseEntity<Void> rejectDraft(@PathVariable UUID draftId) {
        UUID userId = SecurityUtils.getUserId();
        log.info("Rejecting draft draftId={} by userId={}", draftId, userId);
        generateWorkflowUseCase.rejectDraft(draftId, userId);
        return ResponseEntity.noContent().build();
    }
}