package az.millers.hcm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

/**
 * The handler's job is to make a failure legible to the user without leaking the
 * schema, and — the part that is easy to get wrong — without swallowing the
 * exceptions Spring already knows how to map. A catch-all in @ControllerAdvice
 * runs ahead of ExceptionTranslationFilter, so an unguarded one turns every
 * denied request into a 500 and quietly disables authorization.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("a duplicate employee number is a 409 naming the clash, not a 500")
    void duplicateEmployeeNoIsConflict() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrity(duplicateOf("employee_employee_no_key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "CONSTRAINT_VIOLATION");
        assertThat(response.getBody()).containsEntry(
                "message", "That employee number is already in use.");
    }

    @Test
    @DisplayName("the constraint name is read from the raw Postgres text too")
    void findsConstraintNameInMessageChain() {
        // No Hibernate ConstraintViolationException in the chain — only the
        // driver's message, which is how it arrives when the failure surfaces
        // from a native query or a batch flush.
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique"
                        + " constraint \"employee_email_key\""));

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);

        assertThat(response.getBody()).containsEntry(
                "message", "An employee with this email address already exists.");
    }

    @Test
    @DisplayName("an unmapped constraint stays vague rather than leaking the schema")
    void unknownConstraintDoesNotLeak() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrity(duplicateOf("payroll_run_secret_idx"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String message = String.valueOf(response.getBody().get("message"));
        assertThat(message).isEqualTo("This record conflicts with one that already exists.");
        assertThat(message).doesNotContain("payroll_run_secret_idx");
    }

    @Test
    @DisplayName("an unexpected failure returns the traceId so the log is findable")
    void unexpectedCarriesTraceId() throws Exception {
        MDC.put("traceId", "5d4d28da35f7d2c1fc803463ed3006d0");

        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new IllegalStateException("No legal entity found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry(
                "traceId", "5d4d28da35f7d2c1fc803463ed3006d0");
        assertThat(String.valueOf(response.getBody().get("message")))
                .contains("5d4d28da35f7d2c1fc803463ed3006d0");
    }

    @Test
    @DisplayName("the internal message is not passed through to the client")
    void unexpectedDoesNotEchoInternals() throws Exception {
        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://sme-postgres/hcm password=hunter2"));

        assertThat(String.valueOf(response.getBody().get("message")))
                .doesNotContain("postgresql")
                .doesNotContain("hunter2");
    }

    @Test
    @DisplayName("a denied request is rethrown, so it stays a 403 and not a 500")
    void accessDeniedIsRethrown() {
        AccessDeniedException denied = new AccessDeniedException("Access is denied");

        assertThatThrownBy(() -> handler.handleUnexpected(denied)).isSameAs(denied);
    }

    @Test
    @DisplayName("an exception carrying its own status is rethrown, keeping that status")
    void responseStatusExceptionIsRethrown() {
        ResponseStatusException notFound =
                new ResponseStatusException(HttpStatus.NOT_FOUND, "no such payslip");

        assertThatThrownBy(() -> handler.handleUnexpected(notFound)).isSameAs(notFound);
    }

    private static DataIntegrityViolationException duplicateOf(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "duplicate key", null, constraint));
    }
}
