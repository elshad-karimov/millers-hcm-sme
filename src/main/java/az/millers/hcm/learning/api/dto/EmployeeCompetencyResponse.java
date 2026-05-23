package az.millers.hcm.learning.api.dto;

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
        LocalDate validUntil) {

    public static EmployeeCompetencyResponse from(EmployeeCompetency e) {
        return new EmployeeCompetencyResponse(
                e.getId(), e.getEmployeeId(), e.getCompetencyId(),
                e.getProficiency(), e.getSource(), e.getSourceRef(),
                e.getAwardedAt(), e.getValidUntil());
    }
}
