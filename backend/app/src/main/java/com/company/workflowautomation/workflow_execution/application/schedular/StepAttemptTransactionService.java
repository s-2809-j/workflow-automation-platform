package com.company.workflowautomation.workflow_execution.application.schedular;

import com.company.workflowautomation.workflow_execution.application.dag.StepNode;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.StepExecutionRepository;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionEntity;
import com.company.workflowautomation.workflow_execution.jpa.WorkflowExecutionRepository;
import com.company.workflowautomation.workflow_execution.model.StepStatus;
import com.company.workflowautomation.workflow_steps.jpa.WorkflowStepEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.internet.MimeMessage;

import java.net.UnknownHostException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;


@Service
@Slf4j
public class StepAttemptTransactionService {
    @Autowired
    private org.springframework.core.env.Environment env;

    private final StepExecutionRepository stepExecutionRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${workflow.script.timeout-seconds:10}")
    private int scriptTimeoutSeconds;

    // ── Schema registry ──────────────────────────────────────────────────────────
    private static final Map<String, Set<String>> VALID_COLUMNS = Map.of(
            "users",               Set.of("id","organization_id","email","password_hash","status","created_at","updated_at"),
            "organizations",       Set.of("id","name","slug","status","created_at","updated_at"),
            "workflow",           Set.of("id","organization_id","name","description","status","created_by","created_at"),
            "workflow_execution", Set.of("id","workflow_id","organization_id","status","started_at","completed_at")
    );

    private static final Set<String> VALID_STATUS_VALUES =
            Set.of("ACTIVE","INACTIVE","PENDING","RUNNING","SUCCESS","FAILED");

    public StepAttemptTransactionService(
            StepExecutionRepository stepExecutionRepository,
            WorkflowExecutionRepository executionRepository,
            ObjectMapper objectMapper,
            @Qualifier("stepRestTemplate") RestTemplate restTemplate,
            JavaMailSender mailSender) {
        this.stepExecutionRepository = stepExecutionRepository;
        this.executionRepository     = executionRepository;
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
                .findByWorkflowExecutionIdAndStepIdAndOrganizationId(executionId, stepId, orgId)
                .orElseGet(() -> {
                    StepExecutionEntity entity = new StepExecutionEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setWorkflowExecutionId(executionId);
                    entity.setStepId(stepId);
                    entity.setOrganizationId(orgId);
                    entity.setStatus(StepStatus.PENDING);
                    entity.setAttemptCount(0);
                    return stepExecutionRepository.saveAndFlush(entity);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementAttemptCount(UUID executionId, UUID orgId, UUID stepId) {
        setOrgContext(orgId);
        stepExecutionRepository.incrementAttempt(executionId, stepId, orgId);
        stepExecutionRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFinalFailure(UUID executionId, UUID orgId,
                                  WorkflowStepEntity step,
                                  StepNode node,
                                  Exception e,
                                  String reason) {
        String rawMsg = e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage() : e.toString();
        log.error("Marking step as failed. stepId={} stepName={} executionId={} error={}",
                step.getId(), step.getName(), executionId, rawMsg, e);
        setOrgContext(orgId);

        StepExecutionEntity stepExecution = stepExecutionRepository
                .findByWorkflowExecutionIdAndStepIdAndOrganizationId(executionId, step.getId(), orgId)
                .orElseThrow(() -> new RuntimeException(
                        "StepExecution not initialized for executionId=" + executionId
                                + " stepId=" + step.getId()));

        ObjectNode errorOutput = objectMapper.createObjectNode();
        errorOutput.put("error", rawMsg);
        errorOutput.put("errorType", e.getClass().getSimpleName());
        if (reason != null) {
            errorOutput.put("reason", reason);
        }

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
                .findByWorkflowExecutionIdAndStepIdAndOrganizationId(executionId, step.getId(), orgId)
                .orElseGet(() -> {
                    StepExecutionEntity entity = new StepExecutionEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setWorkflowExecutionId(executionId);
                    entity.setStepId(step.getId());
                    entity.setOrganizationId(orgId);
                    entity.setStatus(StepStatus.PENDING);
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
                case "SCRIPT"   -> executeScript(step, executionId, orgId);
                case "EMAIL"    -> executeEmail(step, executionId, orgId);
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

        // Placeholder domains that should never be executed — fail immediately, no retry
    private static final Set<String> PLACEHOLDER_HOSTS = Set.of(
            "api.example.com", "example.com", "your-api.com",
            "placeholder.com", "localhost.invalid"
    );

    private static void validateHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new RuntimeException(
                    "HTTP_CONFIG_ERROR: URL is empty. Please configure a valid endpoint URL before running.");
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new RuntimeException(
                        "HTTP_CONFIG_ERROR: URL '" + url + "' has no valid host.");
            }
            if (PLACEHOLDER_HOSTS.contains(host.toLowerCase())) {
                throw new RuntimeException(
                        "HTTP_CONFIG_ERROR: URL '" + url + "' uses a placeholder host '"
                        + host + "'. Edit this step and set a real API endpoint before running.");
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "HTTP_CONFIG_ERROR: URL '" + url + "' is malformed: " + e.getMessage());
        }
    }

    private JsonNode executeHttp(WorkflowStepEntity step) throws Exception {
        JsonNode config = step.getConfig();
        if (!config.has("url")) {
            throw new RuntimeException("HTTP step missing 'url' in config");
        }

        String url    = config.get("url").asText();
        String method = config.has("method")
                ? config.get("method").asText("GET") : "GET";

        // Fail fast before any network call — no retry needed for config errors
        validateHttpUrl(url);

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

        } catch (ResourceAccessException e) {
            // Unwrap to get the real cause
            Throwable cause = e.getCause();
            if (cause instanceof UnknownHostException) {
                // DNS failure — retrying will never fix this. Throw with HTTP_CONFIG_ERROR
                // prefix so retryDecision.services.js routes it to permanent failure.
                throw new RuntimeException(
                        "HTTP_CONFIG_ERROR: DNS resolution failed for host in URL '"
                        + url + "'. The host does not exist or is unreachable. "
                        + "Edit this step and set a valid API endpoint.", e);
            }
            throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Re-throw HTTP_CONFIG_ERROR as-is so it isn't double-wrapped
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP_CONFIG_ERROR")) {
                throw e;
            }
            throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
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

    private String validateAndNormalizeQuery(String rawQuery) {
        // 1. Normalize status literals: 'active' → 'ACTIVE', etc.
        //    Matches status = 'anycase' and replaces value with uppercase
        java.util.regex.Pattern statusPattern =
                java.util.regex.Pattern.compile("(?i)(status\\s*=\\s*')(\\w+)(')");
        java.util.regex.Matcher statusMatcher = statusPattern.matcher(rawQuery);
        StringBuffer statusBuffer = new StringBuffer();
        while (statusMatcher.find()) {
            String val = statusMatcher.group(2).toUpperCase();
            if (!VALID_STATUS_VALUES.contains(val)) {
                throw new IllegalArgumentException(
                        "Invalid status value in query: '" + statusMatcher.group(2)
                                + "'. Valid values: " + VALID_STATUS_VALUES);
            }
            statusMatcher.appendReplacement(statusBuffer,
                    java.util.regex.Matcher.quoteReplacement(
                            statusMatcher.group(1) + val + statusMatcher.group(3)));
        }
        statusMatcher.appendTail(statusBuffer);
        String query = statusBuffer.toString();

        // 2. Validate table names
        java.util.regex.Matcher tableMatcher = java.util.regex.Pattern
                .compile("(?:FROM|JOIN)\\s+(\\w+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(query);

        while (tableMatcher.find()) {
            String table = tableMatcher.group(1).toLowerCase();
            if (!VALID_COLUMNS.containsKey(table)) {
                throw new IllegalArgumentException(
                        "Unknown table referenced in query: '" + table
                                + "'. Valid tables: " + VALID_COLUMNS.keySet());
            }
        }

        // 3. Validate SELECT columns against the first referenced table
        java.util.regex.Matcher fromMatcher = java.util.regex.Pattern
                .compile("FROM\\s+(\\w+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(query);

        if (fromMatcher.find()) {
            String primaryTable = fromMatcher.group(1).toLowerCase();
            Set<String> validCols = VALID_COLUMNS.get(primaryTable);

            java.util.regex.Matcher selectMatcher = java.util.regex.Pattern
                    .compile("SELECT\\s+(.+?)\\s+FROM", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                    .matcher(query);

            if (selectMatcher.find()) {
                String colClause = selectMatcher.group(1).trim();
                if (!"*".equals(colClause)) {
                    for (String rawCol : colClause.split(",")) {
                        // strip table alias prefix: u.email → email
                        String col = rawCol.trim()
                                .replaceAll("^\\w+\\.", "")   // alias.col → col
                                .replaceAll("\\s+.*$", "")    // strip AS alias
                                .toLowerCase();
                        if (!col.isEmpty() && !col.equals("*") && !validCols.contains(col)) {
                            throw new IllegalArgumentException(
                                    "Column '" + col + "' does not exist on table '"
                                            + primaryTable + "'. Valid columns: " + validCols);
                        }
                    }
                }
            }
        }

        return query;
    }


    private JsonNode executeInternalDatabase(String rawQuery,
                                             String queryType) throws Exception {
        // ── Validate BEFORE any execution ────────────────────────────────────────
        String query;
        try {
            query = validateAndNormalizeQuery(rawQuery);
        } catch (IllegalArgumentException e) {
            log.error("SQL schema validation failed: {}", e.getMessage());
            throw new RuntimeException("SQL_VALIDATION_FAILED: " + e.getMessage(), e);
        }

        log.info("SQL passed schema validation. queryType={}", queryType);

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

    private JsonNode executeScript(WorkflowStepEntity step, UUID executionId, UUID orgId) throws Exception {
        JsonNode config = step.getConfig();
        if (!config.has("script")) {
            throw new RuntimeException("SCRIPT step missing 'script' in config");
        }

        String rawScript = config.get("script").asText();
// Wrap in IIFE so Gemini-generated top-level `return` statements work
String script = "(function() {\n" + rawScript + "\n})()";
        Map<String, Object> inputBindings = new HashMap<>();

// Bind inputs.*
        if (config.has("inputs")) {
            Map<String, Object> inputsMap = new HashMap<>();
            config.get("inputs").fields().forEachRemaining(entry -> {
                com.fasterxml.jackson.databind.JsonNode val = entry.getValue();
                if (val.isNumber())       inputsMap.put(entry.getKey(), val.numberValue());
                else if (val.isBoolean()) inputsMap.put(entry.getKey(), val.booleanValue());
                else                      inputsMap.put(entry.getKey(), val.asText());
            });
            inputBindings.put("inputs", inputsMap);
        }

// Bind previousStepOutput — the raw JSON string from the most recent successful step.
// Scripts access this as: JSON.parse(previousStepOutput || '{}')
        // Bind previousStepOutput — the raw JSON string from the most recent successful step.
// Scripts access this as: JSON.parse(previousStepOutput || '{}')
        // Bind previousStepOutput — the raw JSON string from the most recent successful step.
// Scripts access this as: JSON.parse(previousStepOutput || '{}')
        try {
            // executionId and orgId are passed in from executeSingleAttempt (always non-null there).
            // When called from executeAction (null, null), we skip binding — safe fallback below.
            UUID resolvedExecutionId = executionId;
            UUID resolvedOrgId       = orgId != null ? orgId : step.getOrganizationId();
            List<StepExecutionEntity> successfulSteps = (resolvedExecutionId != null && resolvedOrgId != null)
                    ? stepExecutionRepository.findByWorkflowExecutionIdAndOrganizationIdAndStatus(
                    resolvedExecutionId, resolvedOrgId, StepStatus.SUCCESS)
                    : List.of();

            successfulSteps.stream()
                    .filter(s -> s.getOutputData() != null)
                    .max(Comparator.comparing(StepExecutionEntity::getUpdatedAt))
                    .ifPresent(prev -> {
                        JsonNode prevOutput = prev.getOutputData();
                        // SCRIPT steps store their JS result under "result" (raw JSON string)
                        // HTTP steps store their response under "responseBody"
                        String rawOutput;
                        if (prevOutput.has("result")) {
                            rawOutput = prevOutput.get("result").asText();
                        } else if (prevOutput.has("responseBody")) {
                            rawOutput = prevOutput.get("responseBody").toString();
                        } else {
                            rawOutput = prevOutput.toString();
                        }
                        inputBindings.put("previousStepOutput", rawOutput);
                        log.info("SCRIPT step: bound previousStepOutput from stepId={}", prev.getStepId());
                    });
        } catch (Exception e) {
            log.warn("SCRIPT step: could not resolve previousStepOutput, binding empty string. reason={}", e.getMessage());
            inputBindings.put("previousStepOutput", "{}");
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
    // EMAIL — JavaMailSender (SMTP) with explicit recipient resolution
    // ─────────────────────────────────────────────────────────────

    private JsonNode executeEmail(WorkflowStepEntity step,
                                   UUID executionId,
                                   UUID orgId) throws Exception {
        JsonNode config = step.getConfig();

        if (!config.has("subject")) throw new RuntimeException("EMAIL step missing 'subject'");
        if (!config.has("body"))    throw new RuntimeException("EMAIL step missing 'body'");

        // Support legacy config format where "to" was set directly
// without a recipientSource field
        if (!config.has("recipientSource") && config.has("to")
                && !config.get("to").asText("").isBlank()) {
            String to = config.get("to").asText().trim();
            String subject = config.has("subject") ? config.get("subject").asText() : "(no subject)";
            String body    = config.has("body")    ? config.get("body").asText()    : "";
            boolean isHtml = config.has("isHtml") && config.get("isHtml").asBoolean();

            log.info("EMAIL step using legacy 'to' field. stepId={} to={}", step.getId(), to);

            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(to.split(","));
                helper.setSubject(subject);
                helper.setText(body, isHtml);
                mailSender.send(message);
                log.info("Email sent successfully (legacy format). to={}", to);

                ObjectNode output = objectMapper.createObjectNode();
                output.put("status",    "email_sent");
                output.put("to",        to);
                output.put("subject",   subject);
                output.put("timestamp", Instant.now().toString());
                return output;
            } catch (Exception e) {
                throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
            }
        }

        String recipientSource = config.has("recipientSource")
                ? config.get("recipientSource").asText("").trim().toUpperCase()
                : "";
        String to;
        switch (recipientSource) {
            case "FIXED" -> {
                if (!config.has("recipient")
                        || config.get("recipient").asText("").isBlank()) {
                    throw new RuntimeException(
                            "EMAIL step '" + step.getName()
                            + "' has recipientSource=FIXED but no recipient address is configured. "
                            + "Configure the recipient before running this workflow.");
                }
                to = config.get("recipient").asText().trim();
            }
            case "WORKFLOW_INPUT" -> {
                if (!config.has("inputKey")
                        || config.get("inputKey").asText("").isBlank()) {
                    throw new RuntimeException(
                            "EMAIL step '" + step.getName()
                            + "' has recipientSource=WORKFLOW_INPUT but no inputKey is configured.");
                }
                String inputKey = config.get("inputKey").asText().trim();
                // Resolve from the execution's triggerData JSON
                WorkflowExecutionEntity execution = executionRepository
                        .findByIdAndOrganizationId(executionId, orgId)
                        .orElseThrow(() -> new RuntimeException(
                                "Execution not found: " + executionId));
                JsonNode triggerData = execution.getTriggerData();
                if (triggerData == null || !triggerData.has(inputKey)
                        || triggerData.get(inputKey).asText("").isBlank()) {
                    throw new RuntimeException(
                            "EMAIL step '" + step.getName()
                            + "': WORKFLOW_INPUT key '" + inputKey
                            + "' not found or blank in execution triggerData.");
                }
                to = triggerData.get(inputKey).asText().trim();
            }
            case "PREVIOUS_STEP_OUTPUT" -> {
                // In this pattern, the previous SCRIPT step provides emailBody/emailSubject
                // (resolved above from resultJson). The recipient is still taken from config,
                // set by the user via the UI before the workflow runs.
                if (!config.has("recipient") || config.get("recipient").asText("").isBlank()) {
                    throw new RuntimeException(
                            "EMAIL step '" + step.getName()
                                    + "': recipient address is not configured. "
                                    + "Set the recipient email before running this workflow.");
                }
                to = config.get("recipient").asText().trim();
            }
            default -> throw new RuntimeException(
                    "EMAIL step '" + step.getName()
                    + "' has no valid recipientSource. "
                    + "Set recipientSource to FIXED, WORKFLOW_INPUT, or PREVIOUS_STEP_OUTPUT "
                    + "and configure the corresponding field before running.");
        }

        String  subject = config.get("subject").asText();
        String  body    = config.get("body").asText();
        boolean isHtml  = config.has("isHtml") && config.get("isHtml").asBoolean();
        String  cc      = config.has("cc")  ? config.get("cc").asText()  : null;
        String  bcc     = config.has("bcc") ? config.get("bcc").asText() : null;

        // ── Priority 1: SCRIPT step output (emailBody/emailSubject keys) ──────
        // Only runs when body is blank — user-entered body is always respected
        if (body.isBlank()) {
            try {
                List<StepExecutionEntity> successfulSteps = stepExecutionRepository
                        .findByWorkflowExecutionIdAndOrganizationIdAndStatus(
                                executionId, orgId, StepStatus.SUCCESS);

                Optional<StepExecutionEntity> prevOpt = successfulSteps.stream()
                        .filter(s -> s.getOutputData() != null)
                        .filter(s -> s.getOutputData().has("resultJson")
                                || s.getOutputData().has("result"))
                        .max(Comparator.comparing(StepExecutionEntity::getUpdatedAt));

                if (prevOpt.isPresent()) {
                    JsonNode prevOutput = prevOpt.get().getOutputData();
                    JsonNode resultJson = null;

                    if (prevOutput.has("resultJson") && !prevOutput.get("resultJson").isNull()) {
                        resultJson = prevOutput.get("resultJson");
                    } else if (prevOutput.has("result")) {
                        try {
                            resultJson = objectMapper.readTree(prevOutput.get("result").asText());
                        } catch (Exception parseEx) {
                            log.warn("EMAIL step: could not parse result string as JSON.");
                        }
                    }

                    if (resultJson != null) {
                        if (resultJson.has("emailSubject")
                                && !resultJson.get("emailSubject").asText("").isBlank()) {
                            subject = resultJson.get("emailSubject").asText();
                            log.info("EMAIL step: resolved subject from SCRIPT. subject={}", subject);
                        }
                        if (resultJson.has("emailBody")
                                && !resultJson.get("emailBody").asText("").isBlank()) {
                            body = resultJson.get("emailBody").asText();
                            log.info("EMAIL step: resolved body from SCRIPT emailBody key.");
                        } else {
                            StringBuilder fallback = new StringBuilder();
                            if (resultJson.has("message") && !resultJson.get("message").asText("").isBlank())
                                fallback.append(resultJson.get("message").asText()).append("\n\n");
                            if (resultJson.has("rate") && !resultJson.get("rate").asText("").isBlank())
                                fallback.append("Current Rate: ").append(resultJson.get("rate").asText()).append("\n");
                            if (resultJson.has("price") && !resultJson.get("price").asText("").isBlank())
                                fallback.append("Current Price: ").append(resultJson.get("price").asText()).append("\n");
                            if (resultJson.has("threshold") && !resultJson.get("threshold").asText("").isBlank())
                                fallback.append("Threshold: ").append(resultJson.get("threshold").asText()).append("\n");
                            if (resultJson.has("crossed"))
                                fallback.append("Alert Triggered: ")
                                        .append(resultJson.get("crossed").asBoolean() ? "Yes" : "No").append("\n");
                            if (resultJson.has("alertStatus") && !resultJson.get("alertStatus").asText("").isBlank())
                                fallback.append("Status: ").append(resultJson.get("alertStatus").asText()).append("\n");
                            if (fallback.length() == 0)
                                fallback.append("Workflow result:\n").append(resultJson.toPrettyString());
                            body = fallback.toString();
                            log.info("EMAIL step: built body from SCRIPT result fields.");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("EMAIL step: failed to resolve body from SCRIPT output. reason={}", e.getMessage());
            }
        }

        // ── Priority 2: AI generation — only if body is still blank after SCRIPT check ──
        if (body.isBlank()) {
            log.info("EMAIL step: body still blank, requesting AI generation.");
            String aiBody = generateEmailBodyFromAI(null, step.getName(), subject);
            if (aiBody != null) {
                body = aiBody;
            } else {
                body = "This is an automated notification from your workflow: " + step.getName();
                log.warn("EMAIL step: AI generation failed, using hard fallback body.");
            }
        }
        log.info("EMAIL step: to={} subject={} isHtml={} source={}",
                to, subject, isHtml, recipientSource);

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
            log.info("Email sent successfully to={} source={}", to, recipientSource);

            ObjectNode output = objectMapper.createObjectNode();
            output.put("status",          "email_sent");
            output.put("to",              to);
            output.put("recipientSource", recipientSource);
            output.put("subject",         subject);
            output.put("timestamp",       Instant.now().toString());
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

    // EMAIL — ACTION routing intentionally omits email to prevent unconfigured sends
    // (EMAIL steps should be declared as stepType=EMAIL, not ACTION)

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
    // SCRIPT
    if (stepName.contains("generate") || stepName.contains("compute")
            || stepName.contains("process") || stepName.contains("calculate")
            || stepName.contains("script") || stepName.contains("analyze")) {
        if (config.has("script")) {
            return executeScript(step, null, null);
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

    private void setOrgContext(UUID orgId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.current_organization',"
                                + " :orgId, true)")
                .setParameter("orgId", orgId.toString())
                .getSingleResult();
    }
    private String generateEmailBodyFromAI(String workflowName, String stepName, String subject) {
        try {
            String aiServiceUrl = env.getProperty("ai.service.url", "http://localhost:3001");

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("workflowName", workflowName != null ? workflowName : "Automated Workflow");
            requestBody.put("stepName",     stepName     != null ? stepName     : "Email Notification");
            requestBody.put("subject",      subject      != null ? subject      : "Notification");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    aiServiceUrl + "/api/ai/generate-email-body",
                    HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object body = response.getBody().get("body");
                if (body != null && !body.toString().isBlank()) {
                    log.info("EMAIL step: AI generated body successfully.");
                    return body.toString();
                }
            }
        } catch (Exception e) {
            log.warn("EMAIL step: AI body generation failed, will use fallback. reason={}", e.getMessage());
        }
        return null;
    }
}
