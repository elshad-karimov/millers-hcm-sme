package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.VacancyState;

public record PositionResponse(
        UUID id,
        String code,
        String title,
        /** M146 / §8 — parent position in the hierarchy; null for root. */
        UUID parentPositionId,
        UUID orgUnitId,
        String orgUnitLabel,
        String grade,
        String jobFamily,
        String jobLevel,
        int approvedHeadcount,
        int occupiedHeadcount,
        int vacantHeadcount,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        String employmentType,
        String costCentre,
        String budgetCode,
        String location,
        // ── M254 / PRD §44 — compliance fields ──
        String establishmentNumber,
        String civilServiceGrade,
        String unionCategory,
        String exemptStatus,
        String occupationalCategory,
        String laborClassification,
        String legalBasisReference,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        VacancyState vacancyState,
        PositionStatus status,
        /** M243 — lifecycle breadcrumbs (latest event metadata). */
        String freezeReason,
        OffsetDateTime frozenAt,
        String frozenBy,
        LocalDate scheduledUnfreezeDate,
        String closureReason,
        OffsetDateTime closedAt,
        String closedBy,
        OffsetDateTime approvedAt,
        String approvedBy,
        String reviewReason,
        OffsetDateTime underReviewAt,
        String underReviewBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static PositionResponse from(Position p) {
        return new PositionResponse(
                p.getId(),
                p.getCode(),
                p.getTitle(),
                p.getParentPositionId(),
                p.getOrgUnitId(),
                p.getOrgUnitLabel(),
                p.getGrade(),
                p.getJobFamily(),
                p.getJobLevel(),
                p.getApprovedHeadcount(),
                p.getOccupiedHeadcount(),
                Math.max(0, p.getApprovedHeadcount() - p.getOccupiedHeadcount()),
                p.getSalaryMin(),
                p.getSalaryMax(),
                p.getCurrency(),
                p.getEmploymentType(),
                p.getCostCentre(),
                p.getBudgetCode(),
                p.getLocation(),
                // M254 — compliance fields
                p.getEstablishmentNumber(),
                p.getCivilServiceGrade(),
                p.getUnionCategory(),
                p.getExemptStatus(),
                p.getOccupationalCategory(),
                p.getLaborClassification(),
                p.getLegalBasisReference(),
                p.getEffectiveFrom(),
                p.getEffectiveTo(),
                p.getVacancyState(),
                p.getStatus(),
                p.getFreezeReason(),
                p.getFrozenAt(),
                p.getFrozenBy(),
                p.getScheduledUnfreezeDate(),
                p.getClosureReason(),
                p.getClosedAt(),
                p.getClosedBy(),
                p.getApprovedAt(),
                p.getApprovedBy(),
                p.getReviewReason(),
                p.getUnderReviewAt(),
                p.getUnderReviewBy(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getCreatedBy(),
                p.getUpdatedBy());
    }
}
