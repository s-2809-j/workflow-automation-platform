package com.company.workflowautomation.ai.api;

import com.company.workflowautomation.ai.adapter.AiAdapter;
import com.company.workflowautomation.ai.application.GenerateWorkflowUseCase;
import com.company.workflowautomation.ai.dto.AiRequest;
import com.company.workflowautomation.ai.dto.AiResponse;
import com.company.workflowautomation.workflow.domain.WorkflowDraft;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {
    private final AiAdapter aiAdapter;
    private final GenerateWorkflowUseCase generateWorkflowUseCase;

    @PostMapping("/analyze")
    public ResponseEntity<AiResponse> analyze(@RequestBody @Valid AiRequest request) {
        log.info("AI analyze request for the workflowId = {}", request.getWorkflowId());
        AiResponse response = aiAdapter.analyzeExecution(request);
        return ResponseEntity.ok(response);

    }

    @PostMapping("/generate-workflow")
    public ResponseEntity<Map<String, UUID>> generateWorkflow(
            @RequestBody Map<String,Object> input,
            @RequestHeader("X-User-Id") UUID userId)
    {
        log.info("Generate workflow request from the userId={}",userId);
        UUID draftId = generateWorkflowUseCase.execute(input,userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("draftId",draftId));

    }
    @GetMapping("/drafts/{draftId}")

    public ResponseEntity<WorkflowDraft> getDraft(@PathVariable UUID draftId) {
        log.info("Fetching draft draftId = {}",draftId);

        WorkflowDraft draft = generateWorkflowUseCase.getDraft(draftId);
        return ResponseEntity.ok(draft);
    }
}
