package com.company.workflowautomation.ai.shared.Exception;

import com.company.workflowautomation.workflow_execution.application.WorkflowStepExecutionService;
import com.company.workflowautomation.workflow_steps.application.WorkflowStepService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Error response shape ───────────────────────────────────────────────
    public record ErrorResponse(String message, int status, Instant timestamp) {
        // convenience constructor — callers don't need to pass timestamp manually
        public ErrorResponse(String message, int status) {
            this(message, status, Instant.now());
        }
    }

    // ── 1. All your custom workflow exceptions (AiServiceException,
    //       WorkflowGenerationException, DraftAccessException etc.)
    //       all extend WorkflowBaseException → single handler covers all of them
    @ExceptionHandler(WorkflowBaseException.class)
    public ResponseEntity<ErrorResponse> handleWorkflowException(WorkflowBaseException ex) {
        log.error("Workflow error [status={}]: {}", ex.getStatusCode(), ex.getMessage(), ex);
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getMessage(), ex.getStatusCode()));
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid email or password", 401));
    }

    // ── 2. @Valid failures on request bodies (e.g. AiController @Valid AiRequest)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // collect all field errors into one readable message
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity
                .status(400)
                .body(new ErrorResponse(message, 400));
    }

    // ── 3. Missing tenant / bad state in use cases
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(400)
                .body(new ErrorResponse(ex.getMessage(), 400));
    }


    // ── 4. Catch-all — never expose internal details to the caller
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(500)
                .body(new ErrorResponse("Internal server error", 500));
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred: {}", ex.getMessage(), ex);

        ErrorResponse error = new ErrorResponse(
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    @ExceptionHandler(WorkflowStepService.UnsupportedStepTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedStepType(
            WorkflowStepService.UnsupportedStepTypeException ex) {
        log.error("Unsupported step type: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

}

