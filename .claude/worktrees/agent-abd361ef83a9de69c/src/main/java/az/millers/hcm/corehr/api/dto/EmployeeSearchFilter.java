package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.domain.MaritalStatus;

/**
 * Bag of optional filter dimensions for the M69 advanced search
 * ({@code GET /api/employees}, P1-15).
 *
 * <p>Each field is independent — supplied filters are AND-ed by
 * {@link az.millers.hcm.corehr.service.EmployeeSpecifications}. A null /
 * empty value means "don't filter on this dimension".
 *
 * <p>The record's argument list looks long but the data is plain — no nested
 * objects, no computed fields. The DTO sits on the read path only and isn't
 * persisted.
 */
public record EmployeeSearchFilter(
        /** Free-text search across firstName + lastName + employeeNo + email (case-insensitive). */
        String search,
        /** Multi-valued status filter — null or empty = all statuses. */
        Set<EmploymentStatus> statuses,
        EmploymentType employmentType,
        /** ISO 3166-1 alpha-2 code. */
        String nationality,
        MaritalStatus maritalStatus,
        UUID departmentOrgUnitId,
        /** Free-text department name match (partial, case-insensitive). */
        String departmentName,
        UUID positionId,
        UUID managerId,
        UUID leaveGroupId,
        String costCentre,
        LocalDate hireDateFrom,
        LocalDate hireDateTo) {

    /** Null-safe accessor so the Spec builder doesn't NPE on an empty filter. */
    public static EmployeeSearchFilter empty() {
        return new EmployeeSearchFilter(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
