package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.recruitment.domain.HiringReason;
import az.millers.hcm.recruitment.domain.RequisitionType;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;

public record VacancyResponse(
        UUID id,
        String vacancyNo,
        String title,
        UUID positionId,
        String department,
        String location,
        int openings,
        String description,
        String requirements,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        UUID hiringManagerId,
        UUID recruiterId,
        VacancyStatus status,
        // ── M274 — requisition fields ──
        RequisitionType requisitionType,
        HiringReason hiringReason,
        LocalDate targetStartDate,
        String costCentre,
        String employmentType,
        UUID replacedEmployeeId,
        /** M275 — approval workflow instance (null until first submit). */
        UUID workflowInstanceId,
        /** M277 — confidential requisition flag. */
        boolean confidential,
        LocalDate openingDate,
        LocalDate closingDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static VacancyResponse from(Vacancy v) {
        return new VacancyResponse(
                v.getId(), v.getVacancyNo(), v.getTitle(), v.getPositionId(),
                v.getDepartment(), v.getLocation(), v.getOpenings(),
                v.getDescription(), v.getRequirements(),
                v.getSalaryMin(), v.getSalaryMax(), v.getCurrency(),
                v.getHiringManagerId(), v.getRecruiterId(), v.getStatus(),
                v.getRequisitionType(), v.getHiringReason(),
                v.getTargetStartDate(), v.getCostCentre(),
                v.getEmploymentType(), v.getReplacedEmployeeId(),
                v.getWorkflowInstanceId(),
                v.isConfidential(),
                v.getOpeningDate(), v.getClosingDate(),
                v.getCreatedAt(), v.getUpdatedAt(),
                v.getCreatedBy(), v.getUpdatedBy());
    }
}
