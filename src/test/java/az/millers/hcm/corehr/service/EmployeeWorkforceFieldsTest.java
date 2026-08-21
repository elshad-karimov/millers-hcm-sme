package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.repo.EmployeeRepository;

/**
 * M150 — the two workforce-register fields that carry real consequences.
 *
 * <p>The three approver references route timesheets and expense claims, so a
 * self-reference would let somebody approve their own work and quietly hollow
 * out the approval audit trail (GLOBAL RULE 10), and a dangling reference
 * would route work into nothing. The external HR ID is the reconciliation key
 * against the customer's legacy register — two employees sharing one number
 * means a migration silently matches the wrong person.
 *
 * <p>Exercises the validators directly against a stubbed repository rather
 * than driving {@code update()}: the checks depend on nothing but the
 * repository, and {@code EmployeeService}'s other collaborators are concrete
 * classes this toolchain cannot mock (same reason as
 * {@code BulkAssignServiceTest}).
 */
class EmployeeWorkforceFieldsTest {

    private EmployeeRepository repository;
    private EmployeeService service;

    private Employee subject;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRepository.class);
        // Only the repository is consulted by the validators under test; the
        // remaining constructor arguments are pure field assignments.
        service = new EmployeeService(
                repository, null, null, null, null, null, null, null, null, null,
                // PRD §4: contract + compensation services, unused by these validators
                null, null);

        subjectId = UUID.randomUUID();
        subject = employee(subjectId, "EMP-0001");

        lenient().when(repository.findByExternalHrIdIgnoreCase(any())).thenReturn(Optional.empty());
    }

    // ── Approver references ──────────────────────────────────────────────

    @Test
    @DisplayName("an employee cannot be set as their own approver")
    void approverCannotBeSelf() {
        assertThatThrownBy(() ->
                service.validateApprover(subjectId, subject, "timesheet approver"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("their own timesheet approver");

        assertThatThrownBy(() ->
                service.validateApprover(subjectId, subject, "expense approver"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("their own expense approver");

        assertThatThrownBy(() ->
                service.validateApprover(subjectId, subject, "HR timesheet verifier"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("their own HR timesheet verifier");
    }

    @Test
    @DisplayName("an approver that doesn't exist in this tenant is rejected, not silently stored")
    void unknownApproverRejected() {
        UUID stranger = UUID.randomUUID();
        when(repository.existsById(stranger)).thenReturn(false);

        assertThatThrownBy(() ->
                service.validateApprover(stranger, subject, "timesheet approver"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Employee not found for timesheet approver");
    }

    @Test
    @DisplayName("a real approver passes through unchanged")
    void validApproverAccepted() {
        UUID approver = UUID.randomUUID();
        when(repository.existsById(approver)).thenReturn(true);

        assertThat(service.validateApprover(approver, subject, "timesheet approver"))
                .isEqualTo(approver);
    }

    @Test
    @DisplayName("a null approver is the normal case — it means 'route to the line manager'")
    void nullApproverAccepted() {
        assertThat(service.validateApprover(null, subject, "timesheet approver")).isNull();
    }

    @Test
    @DisplayName("self-reference is caught before the existence lookup, so a new hire is safe too")
    void selfCheckRunsBeforeLookup() {
        // On create the employee has no id yet — a self-reference is
        // impossible, and the reference must still be validated for existence.
        Employee unsaved = employee(null, null);
        UUID approver = UUID.randomUUID();
        when(repository.existsById(approver)).thenReturn(true);

        assertThat(service.validateApprover(approver, unsaved, "expense approver"))
                .isEqualTo(approver);
    }

    // ── External HR ID ───────────────────────────────────────────────────

    @Test
    @DisplayName("a duplicate external HR ID is rejected with a readable message")
    void duplicateExternalHrIdRejected() {
        Employee other = employee(UUID.randomUUID(), "EMP-0002");
        when(repository.findByExternalHrIdIgnoreCase("2004209")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.validateExternalHrIdUnique("2004209", subjectId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("external HR ID 2004209 already exists");
    }

    @Test
    @DisplayName("an employee keeping its own external HR ID is not a duplicate")
    void ownExternalHrIdNotADuplicate() {
        when(repository.findByExternalHrIdIgnoreCase("2004209")).thenReturn(Optional.of(subject));

        assertThatCode(() -> service.validateExternalHrIdUnique("2004209", subjectId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the duplicate check matches on the trimmed value, so a pasted cell can't slip past")
    void externalHrIdCheckedAfterTrimming() {
        Employee other = employee(UUID.randomUUID(), "EMP-0002");
        when(repository.findByExternalHrIdIgnoreCase("2004209")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.validateExternalHrIdUnique(" 2004209 ", subjectId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("blank and null external HR IDs skip the check entirely")
    void blankExternalHrIdSkipsCheck() {
        assertThatCode(() -> service.validateExternalHrIdUnique(null, subjectId))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateExternalHrIdUnique("   ", subjectId))
                .doesNotThrowAnyException();
    }

    // ── trimToNull ───────────────────────────────────────────────────────

    @Test
    @DisplayName("blank external IDs land as null so the partial unique index ignores them")
    void trimToNullNormalisesBlanks() {
        assertThat(EmployeeService.trimToNull(null)).isNull();
        assertThat(EmployeeService.trimToNull("")).isNull();
        assertThat(EmployeeService.trimToNull("   ")).isNull();
        assertThat(EmployeeService.trimToNull(" 2004209 ")).isEqualTo("2004209");
        assertThat(EmployeeService.trimToNull("2004209")).isEqualTo("2004209");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private static Employee employee(UUID id, String employeeNo) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeNo(employeeNo);
        e.setFirstName("Abbas");
        e.setLastName("Abbasli");
        e.setHireDate(LocalDate.of(2024, 2, 8));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setEmploymentType(EmploymentType.PERMANENT);
        e.setFtePercent(new BigDecimal("100.00"));
        return e;
    }
}
