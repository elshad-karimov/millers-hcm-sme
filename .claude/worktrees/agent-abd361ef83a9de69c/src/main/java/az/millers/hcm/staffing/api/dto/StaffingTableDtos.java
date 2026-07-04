package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.staffing.domain.StaffingTable;
import az.millers.hcm.staffing.domain.StaffingTableLine;
import az.millers.hcm.staffing.domain.StaffingTableStatus;
import az.millers.hcm.staffing.service.StaffingTableService.DiffResult;
import az.millers.hcm.staffing.service.StaffingTableService.DiffRow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class StaffingTableDtos {

    private StaffingTableDtos() {}

    // ── Header ──────────────────────────────────────────────────────

    public record TableHeaderRequest(
            @NotNull UUID legalEntityId,
            @NotBlank @Size(max = 64) String versionCode,
            @Size(max = 200) String title,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 2000) String notes) {

        public StaffingTable toEntity() {
            StaffingTable t = new StaffingTable();
            t.setLegalEntityId(legalEntityId);
            t.setVersionCode(versionCode);
            t.setTitle(title);
            t.setEffectiveFrom(effectiveFrom);
            t.setEffectiveTo(effectiveTo);
            t.setNotes(notes);
            return t;
        }
    }

    public record TableResponse(
            UUID id,
            UUID legalEntityId,
            String versionCode,
            String title,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            StaffingTableStatus status,
            String notes,
            String submittedBy,
            OffsetDateTime submittedAt,
            String approvedBy,
            OffsetDateTime approvedAt,
            String rejectedBy,
            OffsetDateTime rejectedAt,
            String rejectReason,
            OffsetDateTime archivedAt,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            // Aggregate roll-up so the list page can show totals without a
            // second fetch. -1 = lines not loaded.
            int totalLines,
            int totalHeadcount,
            BigDecimal totalMonthlyFund) {

        public static TableResponse from(StaffingTable t) {
            return new TableResponse(t.getId(), t.getLegalEntityId(),
                    t.getVersionCode(), t.getTitle(),
                    t.getEffectiveFrom(), t.getEffectiveTo(),
                    t.getStatus(), t.getNotes(),
                    t.getSubmittedBy(), t.getSubmittedAt(),
                    t.getApprovedBy(), t.getApprovedAt(),
                    t.getRejectedBy(), t.getRejectedAt(), t.getRejectReason(),
                    t.getArchivedAt(),
                    t.getCreatedAt(), t.getCreatedBy(),
                    t.getUpdatedAt(), t.getUpdatedBy(),
                    -1, -1, null);
        }

        public static TableResponse from(StaffingTable t, List<StaffingTableLine> lines) {
            int hc = lines.stream().mapToInt(StaffingTableLine::getApprovedHeadcount).sum();
            BigDecimal fund = lines.stream()
                    .map(StaffingTableLine::getMonthlySalaryFund)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            TableResponse base = from(t);
            return new TableResponse(base.id, base.legalEntityId, base.versionCode, base.title,
                    base.effectiveFrom, base.effectiveTo, base.status, base.notes,
                    base.submittedBy, base.submittedAt,
                    base.approvedBy, base.approvedAt,
                    base.rejectedBy, base.rejectedAt, base.rejectReason,
                    base.archivedAt,
                    base.createdAt, base.createdBy, base.updatedAt, base.updatedBy,
                    lines.size(), hc, fund);
        }
    }

    public record RejectRequest(@NotBlank @Size(max = 2000) String reason) {}

    // ── Line ────────────────────────────────────────────────────────

    public record LineRequest(
            int lineNo,
            UUID orgUnitId,
            @Size(max = 200) String orgUnitLabel,
            UUID positionId,
            @Size(max = 64) String positionCode,
            @NotBlank @Size(max = 200) String positionTitle,
            @Size(max = 32) String grade,
            @PositiveOrZero int approvedHeadcount,
            @PositiveOrZero BigDecimal monthlySalary,
            BigDecimal monthlySalaryFund,
            @Size(min = 3, max = 3) String currency,
            @Size(max = 2000) String notes) {

        public StaffingTableLine toEntity() {
            StaffingTableLine l = new StaffingTableLine();
            l.setLineNo(lineNo);
            l.setOrgUnitId(orgUnitId);
            l.setOrgUnitLabel(orgUnitLabel);
            l.setPositionId(positionId);
            l.setPositionCode(positionCode);
            l.setPositionTitle(positionTitle);
            l.setGrade(grade);
            l.setApprovedHeadcount(approvedHeadcount);
            if (monthlySalary != null) l.setMonthlySalary(monthlySalary);
            if (monthlySalaryFund != null) l.setMonthlySalaryFund(monthlySalaryFund);
            if (currency != null) l.setCurrency(currency);
            l.setNotes(notes);
            return l;
        }
    }

    public record LineResponse(
            UUID id, UUID staffingTableId, int lineNo,
            UUID orgUnitId, String orgUnitLabel,
            UUID positionId, String positionCode, String positionTitle,
            String grade,
            int approvedHeadcount,
            BigDecimal monthlySalary,
            BigDecimal monthlySalaryFund,
            String currency,
            String notes) {

        public static LineResponse from(StaffingTableLine l) {
            return new LineResponse(l.getId(), l.getStaffingTableId(), l.getLineNo(),
                    l.getOrgUnitId(), l.getOrgUnitLabel(),
                    l.getPositionId(), l.getPositionCode(), l.getPositionTitle(),
                    l.getGrade(), l.getApprovedHeadcount(),
                    l.getMonthlySalary(), l.getMonthlySalaryFund(),
                    l.getCurrency(), l.getNotes());
        }
    }

    // ── Diff ────────────────────────────────────────────────────────

    public record DiffRowResponse(
            String positionTitle, String grade, String orgUnitLabel,
            Integer countA, Integer countB, Integer countDelta,
            BigDecimal fundA, BigDecimal fundB, BigDecimal fundDelta) {

        public static DiffRowResponse from(DiffRow r) {
            return new DiffRowResponse(r.positionTitle(), r.grade(), r.orgUnitLabel(),
                    r.countA(), r.countB(), r.countDelta(),
                    r.fundA(), r.fundB(), r.fundDelta());
        }
    }

    public record DiffResponse(
            UUID tableAId, UUID tableBId,
            int totalHeadcountA, int totalHeadcountB,
            BigDecimal totalFundA, BigDecimal totalFundB,
            List<DiffRowResponse> rows) {

        public static DiffResponse from(DiffResult d) {
            return new DiffResponse(d.tableAId(), d.tableBId(),
                    d.totalHeadcountA(), d.totalHeadcountB(),
                    d.totalFundA(), d.totalFundB(),
                    d.rows().stream().map(DiffRowResponse::from).toList());
        }
    }
}
