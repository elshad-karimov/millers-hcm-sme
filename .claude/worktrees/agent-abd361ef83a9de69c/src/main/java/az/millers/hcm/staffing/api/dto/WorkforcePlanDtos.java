package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.staffing.domain.ScenarioType;
import az.millers.hcm.staffing.domain.WorkforcePlan;
import az.millers.hcm.staffing.domain.WorkforcePlanLine;
import az.millers.hcm.staffing.domain.WorkforcePlanStatus;
import az.millers.hcm.staffing.service.WorkforcePlanService.DiffResult;
import az.millers.hcm.staffing.service.WorkforcePlanService.DiffRow;
import az.millers.hcm.staffing.service.WorkforcePlanService.VarianceResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class WorkforcePlanDtos {

    private WorkforcePlanDtos() {}

    // ── Header ──────────────────────────────────────────────────────

    public record PlanHeaderRequest(
            @NotNull UUID legalEntityId,
            @NotBlank @Size(max = 64) String versionCode,
            @Size(max = 200) String title,
            ScenarioType scenarioType,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 2000) String notes) {

        public WorkforcePlan toEntity() {
            WorkforcePlan p = new WorkforcePlan();
            p.setLegalEntityId(legalEntityId);
            p.setVersionCode(versionCode);
            p.setTitle(title);
            p.setScenarioType(scenarioType == null ? ScenarioType.BASELINE : scenarioType);
            p.setEffectiveFrom(effectiveFrom);
            p.setEffectiveTo(effectiveTo);
            p.setNotes(notes);
            return p;
        }
    }

    public record PlanResponse(
            UUID id, UUID legalEntityId,
            String versionCode, String title,
            ScenarioType scenarioType,
            LocalDate effectiveFrom, LocalDate effectiveTo,
            WorkforcePlanStatus status,
            UUID parentPlanId,
            String notes,
            String submittedBy, OffsetDateTime submittedAt,
            String approvedBy, OffsetDateTime approvedAt,
            String rejectedBy, OffsetDateTime rejectedAt, String rejectReason,
            OffsetDateTime archivedAt,
            OffsetDateTime createdAt, String createdBy,
            OffsetDateTime updatedAt, String updatedBy,
            int totalLines, int totalHeadcount, BigDecimal totalMonthlyCost) {

        public static PlanResponse from(WorkforcePlan p, List<WorkforcePlanLine> lines) {
            int hc = lines.stream().mapToInt(WorkforcePlanLine::getPlannedHeadcount).sum();
            BigDecimal cost = lines.stream()
                    .map(WorkforcePlanLine::getPlannedMonthlyCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PlanResponse(p.getId(), p.getLegalEntityId(),
                    p.getVersionCode(), p.getTitle(),
                    p.getScenarioType(),
                    p.getEffectiveFrom(), p.getEffectiveTo(),
                    p.getStatus(),
                    p.getParentPlanId(), p.getNotes(),
                    p.getSubmittedBy(), p.getSubmittedAt(),
                    p.getApprovedBy(), p.getApprovedAt(),
                    p.getRejectedBy(), p.getRejectedAt(), p.getRejectReason(),
                    p.getArchivedAt(),
                    p.getCreatedAt(), p.getCreatedBy(),
                    p.getUpdatedAt(), p.getUpdatedBy(),
                    lines.size(), hc, cost);
        }
    }

    public record CloneRequest(
            @NotBlank @Size(max = 64) String newVersionCode,
            @Size(max = 200) String newTitle,
            ScenarioType scenarioType) {}

    public record RejectRequest(@NotBlank @Size(max = 2000) String reason) {}

    // ── Line ────────────────────────────────────────────────────────

    public record LineRequest(
            int lineNo,
            UUID orgUnitId,
            @Size(max = 200) String orgUnitLabel,
            @Size(max = 64) String jobFamily,
            @Size(max = 32) String grade,
            @Size(max = 200) String positionTitle,
            @PositiveOrZero int plannedHeadcount,
            @PositiveOrZero BigDecimal plannedFte,
            @PositiveOrZero BigDecimal plannedMonthlyCost,
            @Size(min = 3, max = 3) String currency,
            @Size(max = 32) String changeType,
            LocalDate targetStartDate,
            @Size(max = 2000) String justification,
            @Size(max = 2000) String notes) {

        public WorkforcePlanLine toEntity() {
            WorkforcePlanLine l = new WorkforcePlanLine();
            l.setLineNo(lineNo);
            l.setOrgUnitId(orgUnitId);
            l.setOrgUnitLabel(orgUnitLabel);
            l.setJobFamily(jobFamily);
            l.setGrade(grade);
            l.setPositionTitle(positionTitle);
            l.setPlannedHeadcount(plannedHeadcount);
            if (plannedFte != null) l.setPlannedFte(plannedFte);
            if (plannedMonthlyCost != null) l.setPlannedMonthlyCost(plannedMonthlyCost);
            if (currency != null) l.setCurrency(currency);
            l.setChangeType(changeType);
            l.setTargetStartDate(targetStartDate);
            l.setJustification(justification);
            l.setNotes(notes);
            return l;
        }
    }

    public record LineResponse(
            UUID id, UUID workforcePlanId, int lineNo,
            UUID orgUnitId, String orgUnitLabel,
            String jobFamily, String grade, String positionTitle,
            int plannedHeadcount, BigDecimal plannedFte,
            BigDecimal plannedMonthlyCost, BigDecimal plannedAnnualCost,
            String currency, String changeType,
            LocalDate targetStartDate,
            String justification, String notes) {

        public static LineResponse from(WorkforcePlanLine l) {
            return new LineResponse(l.getId(), l.getWorkforcePlanId(), l.getLineNo(),
                    l.getOrgUnitId(), l.getOrgUnitLabel(),
                    l.getJobFamily(), l.getGrade(), l.getPositionTitle(),
                    l.getPlannedHeadcount(), l.getPlannedFte(),
                    l.getPlannedMonthlyCost(), l.plannedAnnualCost(),
                    l.getCurrency(), l.getChangeType(),
                    l.getTargetStartDate(),
                    l.getJustification(), l.getNotes());
        }
    }

    // ── Diff / Variance ─────────────────────────────────────────────

    public record DiffRowResponse(
            String key, String positionTitle, String grade, String orgUnitLabel,
            Integer hcA, Integer hcB, Integer hcDelta,
            BigDecimal costA, BigDecimal costB, BigDecimal costDelta) {

        public static DiffRowResponse from(DiffRow r) {
            return new DiffRowResponse(r.key(),
                    r.positionTitle(), r.grade(), r.orgUnitLabel(),
                    r.hcA(), r.hcB(), r.hcDelta(),
                    r.costA(), r.costB(), r.costDelta());
        }
    }

    public record DiffResponse(
            UUID planAId, UUID planBId,
            int totalHcA, int totalHcB,
            BigDecimal totalCostA, BigDecimal totalCostB,
            List<DiffRowResponse> rows) {

        public static DiffResponse from(DiffResult d) {
            return new DiffResponse(d.planAId(), d.planBId(),
                    d.totalHcA(), d.totalHcB(),
                    d.totalCostA(), d.totalCostB(),
                    d.rows().stream().map(DiffRowResponse::from).toList());
        }
    }

    public record VarianceResponse(
            UUID planId,
            int plannedHeadcount, int actualHeadcount, int headcountGap,
            BigDecimal plannedMonthlyCost, BigDecimal actualMonthlyCost, BigDecimal monthlyCostGap) {

        public static VarianceResponse from(VarianceResult v) {
            return new VarianceResponse(v.planId(),
                    v.plannedHeadcount(), v.actualHeadcount(), v.headcountGap(),
                    v.plannedMonthlyCost(), v.actualMonthlyCost(), v.monthlyCostGap());
        }
    }
}
