package com.company.workflowautomation.workflow_execution.application.schedular;

import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.internet.MimeMessage;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class StepAttemptTransactionService {

    private final StepExecutionRepository stepExecutionRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Value(
            "${workflow.script.timeout-seconds:10}")
    private int scriptTimeoutSeconds;

    public StepAttemptTransactionService(
            StepExecutionRepository stepExecutionRepository,
            ObjectMapper objectMapper,
            @Qualifier("stepRestTemplate") RestTemplate restTemplate,
            JavaMailSender mailSender) {
        this.stepExecutionRepository = stepExecutionRepository;
        this.objectMapper            = objectMapper;
        this.restTemplate            = restTemplate;
        this.mailSender              = mailSender;
    }

    // ─────────────────────────────────────────────────────────────
    // Transaction helpers
    // ─────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initializeStepExecution(UUID executionId, UUID orgId, UUID stepId) {
        setOrgContext(orgId);
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
    public void incrementAttemptCount(UUID executionId, UUID orgId, UUID stepId) {
        setOrgContext(orgId);
        stepExecutionRepository.incrementAttempt(executionId, stepId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFinalFailure(UUID executionId, UUID orgId,
                                  WorkflowStepEntity step,
                                  StepNode node,
                                  Exception e) {
        log.error("Marking step as failed. stepId={} executionId={} error={}",
                step.getId(), executionId, e.getMessage(), e);
        setOrgContext(orgId);

        StepExecutionEntity stepExecution = stepExecutionRepository
                .findByWorkflowExecutionIdAndStepId(executionId, step.getId())
                .orElseThrow(() -> new RuntimeException(
                        "StepExecution not found for stepId=" + step.getId()));

        ObjectNode errorOutput = objectMapper.createObjectNode();
        errorOutput.put("error", e.getMessage());
        errorOutput.put("errorType", e.getClass().getSimpleName());

        stepExecution.setOutputData(errorOutput);
        stepExecution.setStatus(StepStatus.FAILED);
        stepExecution.setUpdatedAt(Instant.now());
        node.getStatus().set(StepStatus.FAILED);

        stepExecutionRepository.saveAndFlush(stepExecution);
    }

    // ─────────────────────────────────────────────────────────────
    // Core execution dispatcher
    // ─────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeSingleAttempt(UUID executionId, UUID orgId,
                                      WorkflowStepEntity step,
                                      StepNode node) throws Exception {

        log.info("Executing step. stepId={} stepType={} executionId={} orgId={}",
                step.getId(), step.getStepType(), executionId, orgId);

        setOrgContext(orgId);

        if (executionId == null || step.getId() == null) {
            throw new RuntimeException("INVALID UUID → executionId="
                    + executionId + ", stepId=" + step.getId());
        }

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
            JsonNode output = switch (step.getStepType().toUpperCase()) {
                case "LOG"      -> executeLog(step);
                case "DELAY"    -> executeDelay(step);
                case "HTTP"     -> executeHttp(step);
                case "DATABASE" -> executeDatabase(step);
                case "SCRIPT"   -> executeScript(step);
                case "EMAIL"    -> executeEmail(step);
                case "WEBHOOK"  -> executeWebhook(step);
                case "ACTION"   -> executeAction(step);
                default -> throw new RuntimeException(
                        "Unknown step type: " + step.getStepType());
            };

            stepExecution.setOutputData(output);
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

    // ─────────────────────────────────────────────────────────────
    // LOG
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeLog(WorkflowStepEntity step) {
        JsonNode config = step.getConfig();
        if (!config.has("message")) {
            throw new RuntimeException("LOG step missing 'message' in config");
        }
        String message = config.get("message").asText();
        String level   = config.has("level")
                ? config.get("level").asText("INFO") : "INFO";

        switch (level.toUpperCase()) {
            case "DEBUG" -> log.debug("[WORKFLOW LOG] {}", message);
            case "WARN"  -> log.warn("[WORKFLOW LOG] {}", message);
            case "ERROR" -> log.error("[WORKFLOW LOG] {}", message);
            default      -> log.info("[WORKFLOW LOG] {}", message);
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("status",    "logged");
        output.put("level",     level);
        output.put("message",   message);
        output.put("timestamp", Instant.now().toString());
        return output;
    }

    // ─────────────────────────────────────────────────────────────
    // DELAY
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeDelay(WorkflowStepEntity step)
            throws InterruptedException {
        JsonNode config = step.getConfig();
        if (!config.has("duration")) {
            throw new RuntimeException("DELAY step missing 'duration' in config");
        }
        int durationMs = config.get("duration").asInt();
        if (durationMs < 0 || durationMs > 300_000) {
            throw new RuntimeException(
                    "DELAY duration must be 0–300000 ms, got: " + durationMs);
        }
        log.info("Delaying for {}ms", durationMs);
        Thread.sleep(durationMs);

        ObjectNode output = objectMapper.createObjectNode();
        output.put("status",     "delayed");
        output.put("durationMs", durationMs);
        return output;
    }

    // ─────────────────────────────────────────────────────────────
    // HTTP
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeHttp(WorkflowStepEntity step) throws Exception {
        JsonNode config = step.getConfig();
        if (!config.has("url")) {
            throw new RuntimeException("HTTP step missing 'url' in config");
        }

        String url    = config.get("url").asText();
        String method = config.has("method")
                ? config.get("method").asText("GET") : "GET";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.has("headers")) {
            config.get("headers").fields().forEachRemaining(entry ->
                    headers.add(entry.getKey(), entry.getValue().asText()));
        }

        String body = null;
        if (config.has("body")) {
            body = config.get("body").isTextual()
                    ? config.get("body").asText()
                    : config.get("body").toString();
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        log.info("HTTP step: {} {}", method, url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method.toUpperCase()),
                    entity, String.class);

            ObjectNode output = objectMapper.createObjectNode();
            output.put("status",     "http_executed");
            output.put("url",        url);
            output.put("method",     method);
            output.put("statusCode", response.getStatusCode().value());
            output.put("success",    response.getStatusCode().is2xxSuccessful());

            if (response.getBody() != null) {
                try {
                    output.set("responseBody",
                            objectMapper.readTree(response.getBody()));
                } catch (Exception ex) {
                    output.put("responseBody", response.getBody());
                }
            }

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "HTTP request failed with status: "
                                + response.getStatusCode());
            }
            return output;

        } catch (Exception e) {
            throw new RuntimeException(
                    "HTTP request failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DATABASE — internal (EntityManager) or external (JDBC URL)
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeDatabase(WorkflowStepEntity step) throws Exception {
        JsonNode config = step.getConfig();
        if (!config.has("query")) {
            throw new RuntimeException("DATABASE step missing 'query' in config");
        }

        String query     = config.get("query").asText();
        String queryType = config.has("queryType")
                ? config.get("queryType").asText("SELECT").toUpperCase()
                : "SELECT";

        boolean isExternal = config.has("jdbcUrl")
                && !config.get("jdbcUrl").asText().isBlank();

        log.info("DATABASE step: queryType={} external={}", queryType, isExternal);

        return isExternal
                ? executeExternalDatabase(config, query, queryType)
                : executeInternalDatabase(query, queryType);
    }

    private JsonNode executeInternalDatabase(String query,
                                              String queryType) throws Exception {
        try {
            if ("SELECT".equals(queryType)) {
                List<?> results =
                        entityManager.createNativeQuery(query).getResultList();
                ObjectNode output = objectMapper.createObjectNode();
                output.put("status",    "database_executed");
                output.put("queryType", queryType);
                output.put("rowCount",  results.size());
                output.set("rows",      objectMapper.valueToTree(results));
                return output;
            } else {
                int affected =
                        entityManager.createNativeQuery(query).executeUpdate();
                ObjectNode output = objectMapper.createObjectNode();
                output.put("status",       "database_executed");
                output.put("queryType",    queryType);
                output.put("rowsAffected", affected);
                return output;
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Internal DB query failed: " + e.getMessage(), e);
        }
    }

    private JsonNode executeExternalDatabase(JsonNode config,
                                              String query,
                                              String queryType) throws Exception {
        String jdbcUrl  = config.get("jdbcUrl").asText();
        String username = config.has("username")
                ? config.get("username").asText() : "";
        String password = config.has("password")
                ? config.get("password").asText() : "";

        if (jdbcUrl.isBlank()) {
            throw new RuntimeException(
                    "DATABASE step: jdbcUrl must not be empty");
        }
        if (!password.isBlank()) {
            log.warn("DATABASE step contains plaintext password in config — "
                    + "use a secrets manager reference in production.");
        }

        try (Connection conn = DriverManager.getConnection(
                    jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            if ("SELECT".equals(queryType)) {
                ResultSet rs   = stmt.executeQuery();
                ResultSetMetaData meta = rs.getMetaData();
                int colCount   = meta.getColumnCount();

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }

                ObjectNode output = objectMapper.createObjectNode();
                output.put("status",    "database_executed");
                output.put("queryType", queryType);
                output.put("rowCount",  rows.size());
                output.set("rows",      objectMapper.valueToTree(rows));
                return output;

            } else {
                int affected = stmt.executeUpdate();
                ObjectNode output = objectMapper.createObjectNode();
                output.put("status",       "database_executed");
                output.put("queryType",    queryType);
                output.put("rowsAffected", affected);
                return output;
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "External DB query failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SCRIPT — GraalVM JS with sandbox + hard timeout
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeScript(WorkflowStepEntity step) throws Exception {
        JsonNode config = step.getConfig();
        if (!config.has("script")) {
            throw new RuntimeException("SCRIPT step missing 'script' in config");
        }

        String rawScript = config.get("script").asText();
// Wrap in IIFE so Gemini-generated top-level `return` statements work
String script = "(function() {\n" + rawScript + "\n})()";
        Map<String, Object> inputBindings = new HashMap<>();
        if (config.has("inputs")) {
            config.get("inputs").fields().forEachRemaining(entry ->
                    inputBindings.put(entry.getKey(),
                            entry.getValue().asText()));
        }

        log.info("SCRIPT step: executing JS. scriptLength={}", script.length());

        ExecutorService scriptExecutor = Executors.newSingleThreadExecutor();
        Future<String> future = scriptExecutor.submit(() -> {
            try (org.graalvm.polyglot.Context context =
                    org.graalvm.polyglot.Context.newBuilder("js")
                            .allowAllAccess(false)
                            .allowIO(org.graalvm.polyglot.io.IOAccess.NONE)
                            .allowCreateThread(false)
                            .option("js.ecmascript-version", "2022")
                            .build()) {

                org.graalvm.polyglot.Value bindings =
                        context.getBindings("js");
                inputBindings.forEach(bindings::putMember);

                org.graalvm.polyglot.Value result =
                        context.eval("js", script);
                return result.isNull() ? "null" : result.toString();
            }
        });

        try {
            String result = future.get(scriptTimeoutSeconds, TimeUnit.SECONDS);

            ObjectNode output = objectMapper.createObjectNode();
            output.put("status", "script_executed");
            output.put("result", result);
            try {
                output.set("resultJson", objectMapper.readTree(result));
            } catch (Exception ignored) { /* plain string result — fine */ }
            return output;

        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Script timed out after "
                    + scriptTimeoutSeconds + "s");
        } catch (ExecutionException e) {
            throw new RuntimeException("Script failed: "
                    + e.getCause().getMessage(), e.getCause());
        } finally {
            scriptExecutor.shutdownNow();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // EMAIL — JavaMailSender (SMTP)
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeEmail(WorkflowStepEntity step) throws Exception {
        JsonNode config = step.getConfig();

        if (!config.has("to"))      throw new RuntimeException("EMAIL step missing 'to'");
        if (!config.has("subject")) throw new RuntimeException("EMAIL step missing 'subject'");
        if (!config.has("body"))    throw new RuntimeException("EMAIL step missing 'body'");

        String  to      = config.get("to").asText();
        String  subject = config.get("subject").asText();
        String  body    = config.get("body").asText();
        boolean isHtml  = config.has("isHtml")
                && config.get("isHtml").asBoolean();
        String  cc      = config.has("cc")  ? config.get("cc").asText()  : null;
        String  bcc     = config.has("bcc") ? config.get("bcc").asText() : null;

        log.info("EMAIL step: to={} subject={} isHtml={}", to, subject, isHtml);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to.split(","));
            helper.setSubject(subject);
            helper.setText(body, isHtml);
            if (cc  != null && !cc.isBlank())  helper.setCc(cc.split(","));
            if (bcc != null && !bcc.isBlank()) helper.setBcc(bcc.split(","));

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);

            ObjectNode output = objectMapper.createObjectNode();
            output.put("status",    "email_sent");
            output.put("to",        to);
            output.put("subject",   subject);
            output.put("timestamp", Instant.now().toString());
            return output;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Email sending failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // WEBHOOK
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeWebhook(WorkflowStepEntity step) throws Exception {
        JsonNode config = step.getConfig();
        if (!config.has("url")) {
            throw new RuntimeException("WEBHOOK step missing 'url' in config");
        }

        String url    = config.get("url").asText();
        String method = config.has("method")
                ? config.get("method").asText("POST") : "POST";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.has("authHeader")) {
            headers.set("Authorization",
                    config.get("authHeader").asText());
        }
        if (config.has("headers")) {
            config.get("headers").fields().forEachRemaining(entry ->
                    headers.add(entry.getKey(), entry.getValue().asText()));
        }

        String payload = null;
        if (config.has("payload")) {
            payload = config.get("payload").isTextual()
                    ? config.get("payload").asText()
                    : config.get("payload").toString();
        }

        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        log.info("WEBHOOK step: {} {}", method, url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method.toUpperCase()),
                    entity, String.class);

            ObjectNode output = objectMapper.createObjectNode();
            output.put("status",     "webhook_fired");
            output.put("url",        url);
            output.put("method",     method);
            output.put("statusCode", response.getStatusCode().value());
            output.put("success",    response.getStatusCode().is2xxSuccessful());

            if (response.getBody() != null) {
                try {
                    output.set("responseBody",
                            objectMapper.readTree(response.getBody()));
                } catch (Exception ex) {
                    output.put("responseBody", response.getBody());
                }
            }

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "Webhook failed with status: "
                                + response.getStatusCode());
            }
            return output;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Webhook execution failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ACTION — name-based routing with config guard
    // ─────────────────────────────────────────────────────────────
private JsonNode executeAction(WorkflowStepEntity step) throws Exception {
    JsonNode config  = step.getConfig();
    String  stepName = step.getName() == null ? "" : step.getName().toLowerCase();

    log.info("ACTION step: resolving by name. stepName={}", step.getName());

    // EMAIL
    if (stepName.contains("email") || stepName.contains("send")
            || stepName.contains("distribute") || stepName.contains("notify")) {
        if (config.has("to") && config.has("subject") && config.has("body")) {
            return executeEmail(step);
        }
    }

    // HTTP
    if (stepName.contains("fetch") || stepName.contains("retrieve")
            || stepName.contains("pull") || stepName.contains("api")
            || stepName.contains("request") || stepName.contains("get")) {
        if (config.has("url")) {
            return executeHttp(step);
        }
    }

    // DATABASE
    if (stepName.contains("database") || stepName.contains("query")
            || stepName.contains("db") || stepName.contains("sql")) {
        if (config.has("query")) {
            return executeDatabase(step);
        }
    }

    // SCRIPT
    if (stepName.contains("generate") || stepName.contains("compute")
            || stepName.contains("process") || stepName.contains("calculate")
            || stepName.contains("script") || stepName.contains("analyze")) {
        if (config.has("script")) {
            return executeScript(step);
        }
    }

    // WEBHOOK
    if (stepName.contains("webhook") || stepName.contains("trigger")
            || stepName.contains("alert")) {
        if (config.has("url")) {
            return executeWebhook(step);
        }
    }

    // Final fallback — always succeeds, never throws
    log.info("ACTION '{}' completed as generic step.", step.getName());
    ObjectNode output = objectMapper.createObjectNode();
    output.put("status",      "action_completed");
    output.put("stepName",    step.getName());
    output.put("resolvedType","GENERIC");
    output.put("timestamp",   Instant.now().toString());
    return output;
}
   
    private ObjectNode actionIncompleteOutput(String resolvedType,
                                               String stepName) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("status",      "action_completed");
        output.put("resolvedType", resolvedType);
        output.put("stepName",    stepName);
        output.put("warning",     resolvedType + " config not fully provided — "
                + "add required fields to step config for real execution.");
        output.put("timestamp",   Instant.now().toString());
        return output;
    }



    private void setOrgContext(UUID orgId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization',"
                                + " :orgId, false)")
                .setParameter("orgId", orgId.toString())
                .getSingleResult();
    }
}