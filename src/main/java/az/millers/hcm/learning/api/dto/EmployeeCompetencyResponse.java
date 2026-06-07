package az.millers.hcm.learning.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.learning.domain.EmployeeCompetency;

public record EmployeeCompetencyResponse(
        UUID id,
        UUID employeeId,
        UUID competencyId,
        int proficiency,
        String source,
        UUID sourceRef,
        OffsetDateTime awardedAt,
        LocalDate validUntil,
        // M136 — Section 16 closure
        BigDecimal yearsOfExperience,
        UUID endorsedByEmployeeId,
        OffsetDateTime endorsedAt,
        Integer endorsedLevel,
        String endorsementNote,
        /**
         * Derived helper — {@code true} iff the record carries a
         * non-null endorsement. Lets the SPA paint chips without
         * recomputing.
         */
        boolean endorsed) {

    public static EmployeeCompetencyResponse from(EmployeeCompetency e) {
        return new EmployeeCompetencyResponse(
                e.getId(), e.getEmployeeId(), e.getCompetencyId(),
                e.getProficiency(), e.getSource(), e.getSourceRef(),
                e.getAwardedAt(), e.getValidUntil(),
                e.getYearsOfExperience(),
                e.getEndorsedByEmployeeId(),
                e.getEndorsedAt(),
                e.getEndorsedLevel(),
                e.getEndorsementNote(),
                e.getEndorsedByEmployeeId() != null);
    }
}
