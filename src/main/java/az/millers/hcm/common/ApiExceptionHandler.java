package az.millers.hcm.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import az.millers.hcm.config.plan.PlanLimitGate;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Login lockout moved to Keycloak's built-in brute-force protection
    // when the OIDC swap landed (milestone 19). The exception handler
    // that previously translated AccountLockedException → 429 is gone.

    /**
     * A plan ceiling was hit. 403 (not 400): the request is well-formed, the
     * tenant's edition simply doesn't permit it. {@code code} lets the SPA show
     * an upgrade prompt instead of a validation error.
     */
    @ExceptionHandler(PlanLimitGate.PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(
            PlanLimitGate.PlanLimitExceededException ex) {
        Map<String, Object> payload = baseBody(HttpStatus.FORBIDDEN, ex.getMessage());
        payload.put("code", "PLAN_LIMIT_EXCEEDED");
        payload.put("plan", ex.plan().name());
        payload.put("limit", ex.limit());
        payload.put("current", ex.current());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(payload);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> payload = baseBody(HttpStatus.BAD_REQUEST, "Validation failed");
        payload.put("fieldErrors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(baseBody(status, message));
    }

    private Map<String, Object> baseBody(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        return payload;
    }
}
