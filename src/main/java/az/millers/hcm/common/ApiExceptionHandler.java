package az.millers.hcm.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import az.millers.hcm.config.plan.PlanLimitGate;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    /**
     * A database constraint rejected the write. 409, not 500: the request is a
     * legitimate conflict with data that already exists, and the user can act on
     * it once they are told which value clashed.
     *
     * The constraint name is translated to a sentence rather than passed through
     * — a raw PSQLException names schemas, tables and columns, which is both
     * meaningless to an HR user and more than an outsider should learn about the
     * schema. Anything unrecognised falls back to a generic conflict message and
     * is logged in full, so an unmapped constraint degrades to a vague message
     * rather than a leak.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        String constraint = constraintNameOf(ex);
        String message = CONSTRAINT_MESSAGES.getOrDefault(constraint,
                "This record conflicts with one that already exists.");
        log.warn("Constraint violation ({}) — returning 409", constraint, ex);
        Map<String, Object> payload = baseBody(HttpStatus.CONFLICT, message);
        payload.put("code", "CONSTRAINT_VIOLATION");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
    }

    /**
     * Last resort. Without it an unmapped exception falls through to Spring's
     * default /error, whose body carries no {@code message} at all — which is
     * why a failed save could only ever be reported to the user as "Save
     * failed", with the real cause visible nowhere but the server log.
     *
     * The message stays generic on purpose (an arbitrary exception's text may
     * carry internals), but the response now carries the {@code traceId} that
     * every log line for this request is already stamped with. That is what
     * turns "it broke" into a single grep on the server.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) throws Exception {
        // A catch-all in @RestControllerAdvice runs BEFORE Spring's own
        // resolvers and before ExceptionTranslationFilter, so left unguarded it
        // would quietly turn every 403, 401 and 400 into a 500 — a far worse bug
        // than the one this handler exists to fix. Rethrowing hands these back:
        // ExceptionHandlerExceptionResolver then declines the exception and the
        // machinery that already knows their correct status takes over.
        //   • ErrorResponse           — ResponseStatusException plus the standard
        //                               MVC exceptions (unreadable body, wrong
        //                               method, no such resource, …)
        //   • @ResponseStatus         — exceptions that pin their own status
        //   • Access/Authentication   — must reach ExceptionTranslationFilter,
        //                               which is what makes @PreAuthorize a 403
        if (ex instanceof ErrorResponse
                || ex instanceof AccessDeniedException
                || ex instanceof AuthenticationException
                || AnnotatedElementUtils.hasAnnotation(ex.getClass(), ResponseStatus.class)) {
            throw ex;
        }

        String traceId = MDC.get("traceId");
        log.error("Unhandled exception — returning 500 (traceId={})", traceId, ex);
        Map<String, Object> payload = baseBody(HttpStatus.INTERNAL_SERVER_ERROR,
                traceId == null
                        ? "Something went wrong. Please contact your administrator."
                        : "Something went wrong. Quote reference " + traceId
                                + " to your administrator.");
        payload.put("code", "INTERNAL_ERROR");
        if (traceId != null) {
            payload.put("traceId", traceId);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(payload);
    }

    /**
     * Known unique constraints → what an HR user should read. Keyed by the
     * Postgres constraint name so the mapping survives column renames.
     */
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
            "employee_employee_no_key",
            "That employee number is already in use.",
            "employee_email_key",
            "An employee with this email address already exists.");

    /**
     * Digs the constraint name out of the exception chain. Hibernate exposes it
     * directly; a raw Postgres error only carries it in the message text.
     */
    private static String constraintNameOf(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
            String text = t.getMessage();
            if (text != null) {
                Matcher m = CONSTRAINT_IN_TEXT.matcher(text);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return "unknown";
    }

    private static final Pattern CONSTRAINT_IN_TEXT =
            Pattern.compile("violates unique constraint \"([^\"]+)\"");

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
