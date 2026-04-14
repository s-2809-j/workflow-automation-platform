package com.company.workflowautomation.workflow_execution.application.schedular;

import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StepAttemptTransactionService {

    private final StepExecutionRepository stepExecutionRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // ✅ NEW — wraps all pre-execution DB calls in their own transaction with set_config
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initializeStepExecution(UUID executionId, UUID orgId, UUID stepId) {

        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, false)") // false = session-level ✅
                .setParameter("orgId", orgId.toString())
                .getSingleResult();
        stepExecutionRepository
                .findByWorkflowExecutionIdAndStepId(executionId, stepId)
                .orElseGet(() -> {
                    StepExecutionEntity entity = new StepExecutionEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setWorkflowExecutionId(executionId);
                    entity.setStepId(stepId);
                    entity.setOrganizationId(orgId);
                    entity.setAttemptCount(0);
                    return stepExecutionRepository.saveAndFlush(entity);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeSingleAttempt(UUID executionId, UUID orgId,
                                     WorkflowStepEntity step, StepNode node)
            throws Exception {
        log.info("Executing step. stepId={} stepType={} executionId={} orgId={}",
                step.getId(), step.getStepType(), executionId, orgId);

        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, false)") // false = session-level ✅
                .setParameter("orgId", orgId.toString())
                .getSingleResult();

        if (executionId == null || step.getId() == null
                || step.getId().toString().isBlank()) {
            throw new RuntimeException(
                    "INVALID UUID → executionId=" + executionId +
                            ", stepId=" + step.getId());
        }

        log.debug("executionId={} stepId={}", executionId, step.getId());

        StepExecutionEntity stepExecution = stepExecutionRepository
                .findByWorkflowExecutionIdAndStepId(executionId, step.getId())
                .orElseGet(() -> {
                    StepExecutionEntity entity = new StepExecutionEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setWorkflowExecutionId(executionId);
                    entity.setStepId(step.getId());
                    entity.setOrganizationId(orgId);
                    entity.setAttemptCount(0);
                    return stepExecutionRepository.saveAndFlush(entity);
                });

        stepExecution.setStatus(StepStatus.RUNNING);
        stepExecution.setUpdatedAt(Instant.now());
        stepExecutionRepository.saveAndFlush(stepExecution);

        if (step.isShouldFail()) {
            throw new RuntimeException("timeout");
        }
        try {
            switch (step.getStepType()) {
                case "LOG":
                    log.debug("Executing LOG step. stepId={}", step.getId());
                    stepExecution.setOutputData(executeLog(step));
                    break;
                case "DELAY":
                    log.debug("Executing DELAY step. stepId={}", step.getId());
                    executeDelay(step);
                    ObjectNode delayOutput = objectMapper.createObjectNode();
                    delayOutput.put("status", "delayed");
                    stepExecution.setOutputData(delayOutput);
                    break;
                case "HTTP":
                    log.debug("Executing HTTP step. stepId={} url={}",
                            step.getId(), step.getConfig().get("url"));
                    stepExecution.setOutputData(executeHttp(step));
                    break;
                case "DATABASE":
                    log.debug("Executing DATABASE step. stepId={}", step.getId());
                    stepExecution.setOutputData(executeDatabase(step));
                    break;
                case "SCRIPT":
                    log.debug("Executing SCRIPT step. stepId={}", step.getId());
                    stepExecution.setOutputData(executeScript(step));
                    break;
                default:
                    throw new RuntimeException("Unknown step type: " + step.getStepType());
            }

            stepExecution.setStatus(StepStatus.SUCCESS);
            stepExecution.setUpdatedAt(Instant.now());
            node.getStatus().set(StepStatus.SUCCESS);
            stepExecutionRepository.saveAndFlush(stepExecution);

            log.info("Step execution succeeded. stepId={} executionId={}",
                    step.getId(), executionId);
        } catch (Exception e) {
            log.error("Step execution failed. stepId={} stepType={} error={}",
                    step.getId(), step.getStepType(), e.getMessage(), e);
            throw e;
        }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementAttemptCount(UUID executionId, UUID orgId, UUID stepId) {

        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, false)")
                .setParameter("orgId", orgId.toString())
                .getSingleResult();

        stepExecutionRepository.incrementAttempt(executionId, stepId);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFinalFailure(UUID executionId,
                                 UUID orgId,
                                 WorkflowStepEntity step,
                                 StepNode node,
                                 Exception e) {
        log.error("Marking step as failed. stepId={} executionId={} error={}",
                step.getId(), executionId, e.getMessage(), e);
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization', :orgId, false)") // false = session-level ✅
                .setParameter("orgId", orgId.toString())
                .getSingleResult();

        StepExecutionEntity stepExecution = stepExecutionRepository
                .findByWorkflowExecutionIdAndStepId(executionId, step.getId())
                .orElseThrow();

        ObjectNode errorOutput = objectMapper.createObjectNode();
        errorOutput.put("error", e.getMessage());

        stepExecution.setOutputData(errorOutput);
        stepExecution.setStatus(StepStatus.FAILED);
        stepExecution.setUpdatedAt(Instant.now());
        node.getStatus().set(StepStatus.FAILED);

        stepExecutionRepository.saveAndFlush(stepExecution);
    }

    private JsonNode executeLog(WorkflowStepEntity step) throws JsonProcessingException {
        JsonNode config = step.getConfig();
        String message = config.get("message").asText();
        ObjectNode output = objectMapper.createObjectNode();
        output.put("message", message);
        return output;
    }

    private void executeDelay(WorkflowStepEntity step) {
        JsonNode config = step.getConfig();
        int duration = config.get("duration").asInt();
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Delay interrupted");
        }
    }
    private JsonNode executeHttp(WorkflowStepEntity step) throws Exception {
        JsonNode config = step.getConfig();
        String url = config.get("url").asText();
        String method = config.get("method").asText("GET");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.valueOf(method),
                    null,
                    String.class
            );

            ObjectNode output = objectMapper.createObjectNode();
            output.put("status", "http_executed");
            output.put("url", url);
            output.put("method", method);
            output.put("statusCode", response.getStatusCode().value());
            output.put("success", response.getStatusCode().is2xxSuccessful());

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("HTTP request failed with status: " + response.getStatusCode());
            }

            return output;
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    private JsonNode executeDatabase(WorkflowStepEntity step) throws JsonProcessingException {
        JsonNode config = step.getConfig();
        // Add your database execution logic here
        ObjectNode output = objectMapper.createObjectNode();
        output.put("status", "database_executed");
        output.put("query", config.get("query") != null ? config.get("query").asText() : "");
        return output;
    }

    private JsonNode executeScript(WorkflowStepEntity step) throws JsonProcessingException {
        JsonNode config = step.getConfig();
        // Add your script execution logic here
        ObjectNode output = objectMapper.createObjectNode();
        output.put("status", "script_executed");
        output.put("script", config.get("script") != null ? config.get("script").asText() : "");
        return output;
    }
}