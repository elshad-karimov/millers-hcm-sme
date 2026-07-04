package az.millers.hcm.staffing.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.staffing.domain.HiringPlanLine;

public record HiringPlanLineResponse(
        UUID id,
        UUID workforcePlanId,
        UUID positionId,
        LocalDate targetStartDate,
        UUID recruiterEmployeeId,
        String recruitmentStatus,
        UUID vacancyId,
        int headcount,
        String notes
) {
    public static HiringPlanLineResponse from(HiringPlanLine h) {
        return new HiringPlanLineResponse(
                h.getId(),
                h.getWorkforcePlanId(),
                h.getPositionId(),
                h.getTargetStartDate(),
                h.getRecruiterEmployeeId(),
                h.getRecruitmentStatus().name(),
                h.getVacancyId(),
                h.getHeadcount(),
                h.getNotes()
        );
    }
}
