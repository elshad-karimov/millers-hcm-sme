package az.millers.hcm.skills.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.skills.domain.GapSeverity;

/**
 * M127 — wire DTOs for the position-requirements admin surface and the
 * gap viewer.
 */
public final class SkillsDtos {

    private SkillsDtos() {}

    public record PositionCompetencyRequest(
            UUID competencyId,
            int requiredLevel,
            Boolean mandatory,
            String notes) {}

    public record PositionCompetencyResponse(
            UUID id,
            UUID positionId,
            UUID competencyId,
            String competencyCode,
            String competencyName,
            String competencyCategory,
            int requiredLevel,
            boolean mandatory,
            String notes,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {}

    public record SkillGapRow(
            UUID competencyId,
            String competencyCode,
            String competencyName,
            int requiredLevel,
            int currentLevel,
            boolean mandatory,
            GapSeverity severity) {}

    public record EmployeeGapResponse(
            UUID employeeId,
            UUID positionId,
            int fitScore,
            int totalRequirements,
            int blockers,
            int majorGaps,
            int minorGaps,
            int met,
            List<SkillGapRow> gaps) {}

    public record EmployeeFitRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            int fitScore,
            int blockers,
            int majorGaps) {}

    public record PositionFitResponse(
            UUID positionId,
            int totalCandidates,
            List<EmployeeFitRow> ranked) {}
}
