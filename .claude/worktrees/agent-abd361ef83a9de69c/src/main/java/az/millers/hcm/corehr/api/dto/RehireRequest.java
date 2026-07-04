package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Rehire payload (M78 / P2-15). Targets a previously-terminated employee
 * row; the service creates a fresh employee record with
 * {@code previous_employee_id} set, copies stable PII fields, and starts a
 * new probation cycle.
 */
public record RehireRequest(
        @NotNull UUID previousEmployeeId,
        @NotNull LocalDate newHireDate,
        @Size(max = 4000) String reason,
        /** Override only — null keeps the prior employee's manager. */
        UUID managerId,
        /** Override only — null keeps the prior employee's department/position. */
        String departmentName,
        String positionTitle,
        UUID orgUnitId,
        UUID positionId) {
}
