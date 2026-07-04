package az.millers.hcm.career.api.dto;

import az.millers.hcm.career.domain.Idp;
import az.millers.hcm.career.domain.IdpActivity;
import az.millers.hcm.career.domain.IdpSkillGap;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record IdpResponse(
        UUID id,
        UUID employeeId,
        String targetRole,
        LocalDate targetDate,
        String status,
        String managerComment,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        List<SkillGapDto> skillGaps,
        List<ActivityDto> activities
) {
    public record SkillGapDto(
            UUID id,
            UUID competencyId,
            String skillName,
            Short currentLevel,
            Short targetLevel,
            String notes
    ) {
        static SkillGapDto from(IdpSkillGap g) {
            return new SkillGapDto(g.getId(), g.getCompetencyId(), g.getSkillName(),
                    g.getCurrentLevel(), g.getTargetLevel(), g.getNotes());
        }
    }

    public record ActivityDto(
            UUID id,
            String title,
            String activityType,
            UUID courseId,
            LocalDate dueDate,
            String status,
            LocalDate completedAt,
            String notes
    ) {
        static ActivityDto from(IdpActivity a) {
            return new ActivityDto(a.getId(), a.getTitle(), a.getActivityType(),
                    a.getCourseId(), a.getDueDate(), a.getStatus(), a.getCompletedAt(), a.getNotes());
        }
    }

    public static IdpResponse from(Idp idp) {
        return new IdpResponse(
                idp.getId(),
                idp.getEmployeeId(),
                idp.getTargetRole(),
                idp.getTargetDate(),
                idp.getStatus(),
                idp.getManagerComment(),
                idp.getCreatedAt(),
                idp.getCreatedBy(),
                idp.getUpdatedAt(),
                idp.getSkillGaps().stream().map(SkillGapDto::from).collect(Collectors.toList()),
                idp.getActivities().stream().map(ActivityDto::from).collect(Collectors.toList())
        );
    }
}
