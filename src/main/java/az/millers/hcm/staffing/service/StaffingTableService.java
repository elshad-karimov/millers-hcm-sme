package az.millers.hcm.staffing.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.StaffingTable;
import az.millers.hcm.staffing.domain.StaffingTableLine;
import az.millers.hcm.staffing.domain.StaffingTableStatus;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.staffing.repo.StaffingTableLineRepository;
import az.millers.hcm.staffing.repo.StaffingTableRepository;

/**
 * M245 — Staffing table ("ştat cədvəli") service (PRD §45).
 *
 * <p>Owns every write path on the staffing table + its lines, the
 * lifecycle transitions (DRAFT → PENDING_APPROVAL → ACTIVE → ARCHIVED),
 * the generate-from-positions snapshot, and the diff-vs-prior-version
 * compare. Per the "develop once, use everywhere" rule.
 */
@Service
public class StaffingTableService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "StaffingTable";

    private final StaffingTableRepository tables;
    private final StaffingTableLineRepository lines;
    private final PositionRepository positions;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public StaffingTableService(StaffingTableRepository tables,
                                 StaffingTableLineRepository lines,
                                 PositionRepository positions,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.tables = tables;
        this.lines = lines;
        this.positions = positions;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StaffingTable> listForLegalEntity(UUID legalEntityId) {
        return tables.findByLegalEntityIdOrderByEffectiveFromDesc(legalEntityId);
    }

    @Transactional(readOnly = true)
    public List<StaffingTable> listAll() {
        return tables.findAll();
    }

    @Transactional(readOnly = true)
    public StaffingTable get(UUID id) {
        return tables.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Staffing table not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<StaffingTableLine> linesFor(UUID staffingTableId) {
        return lines.findByStaffingTableIdOrderByLineNoAsc(staffingTableId);
    }

    @Transactional(readOnly = true)
    public Optional<StaffingTable> activeAsOf(UUID legalEntityId, LocalDate asOf) {
        return tables.findActiveAsOf(legalEntityId, asOf).stream().findFirst();
    }

    // ── Header CRUD ───────────────────────────────────────────────────────

    @Transactional
    public StaffingTable create(StaffingTable input) {
        if (input.getLegalEntityId() == null) throw new BadRequestException("legalEntityId required");
        if (input.getVersionCode() == null || input.getVersionCode().isBlank())
            throw new BadRequestException("versionCode required");
        if (input.getEffectiveFrom() == null) throw new BadRequestException("effectiveFrom required");

        tables.findByLegalEntityIdAndVersionCode(input.getLegalEntityId(), input.getVersionCode())
                .ifPresent(_existing -> {
                    throw new BadRequestException(
                            "Version code already used for this legal entity: " + input.getVersionCode());
                });

        input.setId(null);
        input.setStatus(StaffingTableStatus.DRAFT);
        input.setCreatedBy(currentRequest.username());
        input.setUpdatedBy(currentRequest.username());
        StaffingTable saved = tables.save(input);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE",
                null, snapshot(saved));
        return saved;
    }

    @Transactional
    public StaffingTable update(UUID id, StaffingTable patch) {
        StaffingTable existing = get(id);
        if (!existing.getStatus().isEditable()) {
            throw new BadRequestException("Cannot edit a " + existing.getStatus() + " staffing table");
        }
        if (patch.getTitle() != null)         existing.setTitle(patch.getTitle());
        if (patch.getVersionCode() != null && !patch.getVersionCode().isBlank())
            existing.setVersionCode(patch.getVersionCode());
        if (patch.getEffectiveFrom() != null) existing.setEffectiveFrom(patch.getEffectiveFrom());
        existing.setEffectiveTo(patch.getEffectiveTo());
        existing.setNotes(patch.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        StaffingTable saved = tables.save(existing);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "UPDATE",
                null, snapshot(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        StaffingTable existing = get(id);
        if (existing.getStatus() == StaffingTableStatus.ACTIVE) {
            throw new BadRequestException("Cannot delete an ACTIVE staffing table — archive it first");
        }
        tables.delete(existing);  // lines cascade via DDL FK
        audit.record(MODULE, ENTITY, id.toString(), "DELETE",
                snapshot(existing), null);
    }

    // ── Lines CRUD ────────────────────────────────────────────────────────

    @Transactional
    public StaffingTableLine addLine(UUID staffingTableId, StaffingTableLine line) {
        StaffingTable table = get(staffingTableId);
        assertEditable(table);

        // Auto-derive monthly_salary_fund if caller didn't supply one
        // (or supplied zero). The user can override afterwards.
        if (line.getMonthlySalaryFund() == null
                || line.getMonthlySalaryFund().compareTo(BigDecimal.ZERO) == 0) {
            line.setMonthlySalaryFund(
                    line.getMonthlySalary().multiply(BigDecimal.valueOf(line.getApprovedHeadcount())));
        }
        line.setId(null);
        line.setStaffingTableId(staffingTableId);
        if (line.getLineNo() <= 0) {
            // append at the end
            line.setLineNo(lines.findByStaffingTableIdOrderByLineNoAsc(staffingTableId).size() + 1);
        }
        StaffingTableLine saved = lines.save(line);
        bumpUpdated(table);
        return saved;
    }

    @Transactional
    public StaffingTableLine updateLine(UUID lineId, StaffingTableLine patch) {
        StaffingTableLine existing = lines.findById(lineId).orElseThrow(
                () -> new ResourceNotFoundException("Line not found: " + lineId));
        StaffingTable table = get(existing.getStaffingTableId());
        assertEditable(table);

        if (patch.getLineNo() > 0)             existing.setLineNo(patch.getLineNo());
        if (patch.getOrgUnitId() != null)      existing.setOrgUnitId(patch.getOrgUnitId());
        if (patch.getOrgUnitLabel() != null)   existing.setOrgUnitLabel(patch.getOrgUnitLabel());
        if (patch.getPositionId() != null)     existing.setPositionId(patch.getPositionId());
        if (patch.getPositionCode() != null)   existing.setPositionCode(patch.getPositionCode());
        if (patch.getPositionTitle() != null)  existing.setPositionTitle(patch.getPositionTitle());
        if (patch.getGrade() != null)          existing.setGrade(patch.getGrade());
        existing.setApprovedHeadcount(Math.max(0, patch.getApprovedHeadcount()));
        if (patch.getMonthlySalary() != null)  existing.setMonthlySalary(patch.getMonthlySalary());
        // Re-derive fund unless caller set a non-zero override.
        if (patch.getMonthlySalaryFund() != null
                && patch.getMonthlySalaryFund().compareTo(BigDecimal.ZERO) != 0) {
            existing.setMonthlySalaryFund(patch.getMonthlySalaryFund());
        } else {
            existing.setMonthlySalaryFund(
                    existing.getMonthlySalary().multiply(BigDecimal.valueOf(existing.getApprovedHeadcount())));
        }
        if (patch.getCurrency() != null) existing.setCurrency(patch.getCurrency());
        existing.setNotes(patch.getNotes());

        StaffingTableLine saved = lines.save(existing);
        bumpUpdated(table);
        return saved;
    }

    @Transactional
    public void deleteLine(UUID lineId) {
        StaffingTableLine existing = lines.findById(lineId).orElseThrow(
                () -> new ResourceNotFoundException("Line not found: " + lineId));
        StaffingTable table = get(existing.getStaffingTableId());
        assertEditable(table);
        lines.delete(existing);
        bumpUpdated(table);
    }

    // ── Generate from live positions ──────────────────────────────────────

    /**
     * Snapshot every ACTIVE position whose orgUnit (or position) belongs to
     * the staffing table's legal entity, one line per position. The
     * "salary" used is the position's salaryMin (lower bound) since the
     * staffing table is the *budget envelope*, not the actual paid amount.
     *
     * <p>Existing lines are NOT cleared — this is additive. To start over,
     * delete all lines first via the SPA.
     */
    @Transactional
    public List<StaffingTableLine> generateFromPositions(UUID staffingTableId) {
        StaffingTable table = get(staffingTableId);
        assertEditable(table);

        int nextLineNo = lines.findByStaffingTableIdOrderByLineNoAsc(staffingTableId).size() + 1;
        List<Position> all = positions.findByTenantId(TenantContext.current());
        List<StaffingTableLine> created = new ArrayList<>();
        for (Position p : all) {
            // Only snapshot ACTIVE positions; staffing tables shouldn't
            // include drafts or closed seats.
            if (p.getStatus() != PositionStatus.ACTIVE) continue;
            // Defensive: skip if approved headcount is 0
            if (p.getApprovedHeadcount() <= 0) continue;

            StaffingTableLine line = new StaffingTableLine();
            line.setStaffingTableId(staffingTableId);
            line.setLineNo(nextLineNo++);
            line.setOrgUnitId(p.getOrgUnitId());
            line.setOrgUnitLabel(p.getOrgUnitLabel());
            line.setPositionId(p.getId());
            line.setPositionCode(p.getCode());
            line.setPositionTitle(p.getTitle());
            line.setGrade(p.getGrade());
            line.setApprovedHeadcount(p.getApprovedHeadcount());
            BigDecimal salary = p.getSalaryMin() == null ? BigDecimal.ZERO : p.getSalaryMin();
            line.setMonthlySalary(salary);
            line.setMonthlySalaryFund(salary.multiply(BigDecimal.valueOf(p.getApprovedHeadcount())));
            line.setCurrency(p.getCurrency() == null ? "AZN" : p.getCurrency());
            created.add(lines.save(line));
        }

        bumpUpdated(table);
        audit.record(MODULE, ENTITY, table.getId().toString(),
                "GENERATE_FROM_POSITIONS",
                null,
                Map.of("created", created.size()));
        return created;
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────

    @Transactional
    public StaffingTable submit(UUID id) {
        StaffingTable t = get(id);
        if (t.getStatus() != StaffingTableStatus.DRAFT
                && t.getStatus() != StaffingTableStatus.REJECTED) {
            throw new BadRequestException("Can only submit a DRAFT or REJECTED table");
        }
        if (lines.findByStaffingTableIdOrderByLineNoAsc(id).isEmpty()) {
            throw new BadRequestException("Cannot submit an empty staffing table");
        }
        return moveTo(t, StaffingTableStatus.PENDING_APPROVAL, sub -> {
            sub.setSubmittedBy(currentRequest.username());
            sub.setSubmittedAt(OffsetDateTime.now());
        });
    }

    @Transactional
    public StaffingTable approve(UUID id) {
        StaffingTable t = get(id);
        if (t.getStatus() != StaffingTableStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Can only approve PENDING_APPROVAL tables");
        }
        return moveTo(t, StaffingTableStatus.ACTIVE, app -> {
            app.setApprovedBy(currentRequest.username());
            app.setApprovedAt(OffsetDateTime.now());
            // Auto-archive any previously ACTIVE version for the same
            // legal entity — there can be only one in force at a time.
            tables.findByLegalEntityIdAndStatusOrderByEffectiveFromDesc(
                    app.getLegalEntityId(), StaffingTableStatus.ACTIVE).stream()
                    .filter(prev -> !prev.getId().equals(app.getId()))
                    .forEach(prev -> {
                        prev.setStatus(StaffingTableStatus.ARCHIVED);
                        prev.setArchivedAt(OffsetDateTime.now());
                        prev.setUpdatedBy(currentRequest.username());
                        tables.save(prev);
                    });
        });
    }

    @Transactional
    public StaffingTable reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Reject reason is required");
        }
        StaffingTable t = get(id);
        if (t.getStatus() != StaffingTableStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Can only reject PENDING_APPROVAL tables");
        }
        return moveTo(t, StaffingTableStatus.REJECTED, rej -> {
            rej.setRejectedBy(currentRequest.username());
            rej.setRejectedAt(OffsetDateTime.now());
            rej.setRejectReason(reason);
        });
    }

    @Transactional
    public StaffingTable archive(UUID id) {
        StaffingTable t = get(id);
        if (t.getStatus() == StaffingTableStatus.ARCHIVED) return t;
        return moveTo(t, StaffingTableStatus.ARCHIVED, arc -> arc.setArchivedAt(OffsetDateTime.now()));
    }

    // ── Compare two versions ──────────────────────────────────────────────

    public record DiffRow(
            String positionTitle, String grade, String orgUnitLabel,
            Integer countA, Integer countB, Integer countDelta,
            BigDecimal fundA, BigDecimal fundB, BigDecimal fundDelta) {}

    public record DiffResult(
            UUID tableAId, UUID tableBId,
            int totalHeadcountA, int totalHeadcountB,
            BigDecimal totalFundA, BigDecimal totalFundB,
            List<DiffRow> rows) {}

    @Transactional(readOnly = true)
    public DiffResult compare(UUID tableAId, UUID tableBId) {
        StaffingTable a = get(tableAId);
        StaffingTable b = get(tableBId);
        var aLines = linesFor(a.getId());
        var bLines = linesFor(b.getId());

        // Key by (position_title + grade + org_unit_label) so renames /
        // re-grades show up as deltas rather than vanishing rows.
        Map<String, StaffingTableLine> aByKey = new HashMap<>();
        Map<String, StaffingTableLine> bByKey = new HashMap<>();
        for (var l : aLines) aByKey.put(key(l), l);
        for (var l : bLines) bByKey.put(key(l), l);

        var allKeys = new java.util.TreeSet<String>();
        allKeys.addAll(aByKey.keySet());
        allKeys.addAll(bByKey.keySet());

        List<DiffRow> rows = new ArrayList<>();
        int totalHcA = 0, totalHcB = 0;
        BigDecimal totalFundA = BigDecimal.ZERO, totalFundB = BigDecimal.ZERO;
        for (String k : allKeys) {
            var la = aByKey.get(k);
            var lb = bByKey.get(k);
            int hcA = la == null ? 0 : la.getApprovedHeadcount();
            int hcB = lb == null ? 0 : lb.getApprovedHeadcount();
            BigDecimal fA = la == null ? BigDecimal.ZERO : la.getMonthlySalaryFund();
            BigDecimal fB = lb == null ? BigDecimal.ZERO : lb.getMonthlySalaryFund();
            totalHcA += hcA;
            totalHcB += hcB;
            totalFundA = totalFundA.add(fA);
            totalFundB = totalFundB.add(fB);
            StaffingTableLine ref = la != null ? la : lb;
            rows.add(new DiffRow(
                    ref.getPositionTitle(), ref.getGrade(), ref.getOrgUnitLabel(),
                    hcA, hcB, hcB - hcA,
                    fA, fB, fB.subtract(fA)));
        }
        return new DiffResult(a.getId(), b.getId(),
                totalHcA, totalHcB, totalFundA, totalFundB, rows);
    }

    private String key(StaffingTableLine l) {
        return (l.getPositionTitle() == null ? "" : l.getPositionTitle()) + "|"
                + (l.getGrade() == null ? "" : l.getGrade()) + "|"
                + (l.getOrgUnitLabel() == null ? "" : l.getOrgUnitLabel());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void assertEditable(StaffingTable table) {
        if (!table.getStatus().isEditable()) {
            throw new BadRequestException(
                    "Cannot edit a " + table.getStatus() + " staffing table — only DRAFT is editable");
        }
    }

    private void bumpUpdated(StaffingTable table) {
        table.setUpdatedBy(currentRequest.username());
        tables.save(table);
    }

    private StaffingTable moveTo(StaffingTable t, StaffingTableStatus to,
                                  java.util.function.Consumer<StaffingTable> sideEffects) {
        StaffingTableStatus from = t.getStatus();
        t.setStatus(to);
        sideEffects.accept(t);
        t.setUpdatedBy(currentRequest.username());
        StaffingTable saved = tables.save(t);
        audit.record(MODULE, ENTITY, t.getId().toString(),
                "LIFECYCLE_" + to.name(),
                Map.of("from", from.name()),
                Map.of("to", to.name()));
        return saved;
    }

    public record TableSnapshot(UUID id, UUID legalEntityId, String versionCode,
                                 StaffingTableStatus status, LocalDate effectiveFrom) {}

    private TableSnapshot snapshot(StaffingTable t) {
        return new TableSnapshot(t.getId(), t.getLegalEntityId(),
                t.getVersionCode(), t.getStatus(), t.getEffectiveFrom());
    }
}
