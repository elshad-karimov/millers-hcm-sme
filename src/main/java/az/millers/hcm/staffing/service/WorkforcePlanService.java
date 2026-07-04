package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.BudgetScopeType;
import az.millers.hcm.compensation.domain.BudgetType;
import az.millers.hcm.compensation.domain.CompensationBudget;
import az.millers.hcm.compensation.repo.CompensationBudgetRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.ScenarioType;
import az.millers.hcm.staffing.domain.WorkforcePlan;
import az.millers.hcm.staffing.domain.WorkforcePlanLine;
import az.millers.hcm.staffing.domain.WorkforcePlanStatus;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.staffing.repo.WorkforcePlanLineRepository;
import az.millers.hcm.staffing.repo.WorkforcePlanRepository;

/**
 * M247 — Workforce planning service (PRD §20, §42, §43).
 *
 * <p>One central service owns every write path on workforce_plan + its
 * lines, the lifecycle transitions, the clone-as-scenario shortcut,
 * and the two compare operations: plan-vs-plan + plan-vs-current-actual.
 *
 * <p>Per the "develop once, use everywhere" rule — any future
 * consumer (variance dashboard, workforce-cost forecast, scenario
 * tournament) reads from this service, not directly from the repos.
 */
@Service
public class WorkforcePlanService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "WorkforcePlan";
    private static final String TENANT = "default";

    private final WorkforcePlanRepository plans;
    private final WorkforcePlanLineRepository lines;
    private final PositionRepository positions;
    private final CompensationBudgetRepository compensationBudgets;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public WorkforcePlanService(WorkforcePlanRepository plans,
                                 WorkforcePlanLineRepository lines,
                                 PositionRepository positions,
                                 CompensationBudgetRepository compensationBudgets,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.plans = plans;
        this.lines = lines;
        this.positions = positions;
        this.compensationBudgets = compensationBudgets;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkforcePlan> listForLegalEntity(UUID legalEntityId) {
        return plans.findByLegalEntityIdOrderByEffectiveFromDesc(legalEntityId);
    }

    @Transactional(readOnly = true)
    public List<WorkforcePlan> listAll() {
        return plans.findAll();
    }

    @Transactional(readOnly = true)
    public WorkforcePlan get(UUID id) {
        return plans.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Workforce plan not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<WorkforcePlanLine> linesFor(UUID planId) {
        return lines.findByWorkforcePlanIdOrderByLineNoAsc(planId);
    }

    // ── Header CRUD ───────────────────────────────────────────────────────

    @Transactional
    public WorkforcePlan create(WorkforcePlan input) {
        if (input.getLegalEntityId() == null) throw new BadRequestException("legalEntityId required");
        if (input.getVersionCode() == null || input.getVersionCode().isBlank())
            throw new BadRequestException("versionCode required");
        if (input.getEffectiveFrom() == null) throw new BadRequestException("effectiveFrom required");

        plans.findByLegalEntityIdAndVersionCode(input.getLegalEntityId(), input.getVersionCode())
                .ifPresent(_existing -> {
                    throw new BadRequestException(
                            "Version code already used for this legal entity: " + input.getVersionCode());
                });

        input.setId(null);
        input.setStatus(WorkforcePlanStatus.DRAFT);
        if (input.getScenarioType() == null) input.setScenarioType(ScenarioType.BASELINE);
        input.setCreatedBy(currentRequest.username());
        input.setUpdatedBy(currentRequest.username());
        WorkforcePlan saved = plans.save(input);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE",
                null, snapshot(saved));
        return saved;
    }

    @Transactional
    public WorkforcePlan update(UUID id, WorkforcePlan patch) {
        WorkforcePlan existing = get(id);
        if (!existing.getStatus().isEditable()) {
            throw new BadRequestException("Cannot edit a " + existing.getStatus() + " plan");
        }
        if (patch.getTitle() != null)         existing.setTitle(patch.getTitle());
        if (patch.getVersionCode() != null && !patch.getVersionCode().isBlank())
            existing.setVersionCode(patch.getVersionCode());
        if (patch.getEffectiveFrom() != null) existing.setEffectiveFrom(patch.getEffectiveFrom());
        existing.setEffectiveTo(patch.getEffectiveTo());
        if (patch.getScenarioType() != null) existing.setScenarioType(patch.getScenarioType());
        existing.setNotes(patch.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        WorkforcePlan saved = plans.save(existing);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "UPDATE",
                null, snapshot(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        WorkforcePlan existing = get(id);
        if (existing.getStatus() == WorkforcePlanStatus.ACTIVE) {
            throw new BadRequestException("Cannot delete an ACTIVE plan — archive first");
        }
        plans.delete(existing);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", snapshot(existing), null);
    }

    // ── Clone as scenario ─────────────────────────────────────────────────

    /**
     * Fork an existing plan into a new scenario. Copies the header (new
     * version code) and every line; parent_plan_id links back so the SPA
     * can show "scenario of baseline X".
     */
    @Transactional
    public WorkforcePlan cloneAsScenario(UUID sourceId, String newVersionCode,
                                           String newTitle, ScenarioType scenarioType) {
        WorkforcePlan source = get(sourceId);

        WorkforcePlan clone = new WorkforcePlan();
        clone.setLegalEntityId(source.getLegalEntityId());
        clone.setVersionCode(newVersionCode);
        clone.setTitle(newTitle != null ? newTitle
                : (source.getTitle() == null ? source.getVersionCode() : source.getTitle())
                        + " (scenario)");
        clone.setScenarioType(scenarioType == null ? ScenarioType.WHAT_IF : scenarioType);
        clone.setEffectiveFrom(source.getEffectiveFrom());
        clone.setEffectiveTo(source.getEffectiveTo());
        clone.setParentPlanId(source.getId());
        clone.setNotes(source.getNotes());
        WorkforcePlan saved = create(clone);

        List<WorkforcePlanLine> srcLines = linesFor(source.getId());
        int n = 0;
        for (WorkforcePlanLine src : srcLines) {
            WorkforcePlanLine copy = new WorkforcePlanLine();
            copy.setWorkforcePlanId(saved.getId());
            copy.setLineNo(++n);
            copy.setOrgUnitId(src.getOrgUnitId());
            copy.setOrgUnitLabel(src.getOrgUnitLabel());
            copy.setJobFamily(src.getJobFamily());
            copy.setGrade(src.getGrade());
            copy.setPositionTitle(src.getPositionTitle());
            copy.setPlannedHeadcount(src.getPlannedHeadcount());
            copy.setPlannedFte(src.getPlannedFte());
            copy.setPlannedMonthlyCost(src.getPlannedMonthlyCost());
            copy.setCurrency(src.getCurrency());
            copy.setChangeType(src.getChangeType());
            copy.setTargetStartDate(src.getTargetStartDate());
            copy.setJustification(src.getJustification());
            copy.setNotes(src.getNotes());
            lines.save(copy);
        }
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CLONE_FROM",
                Map.of("sourceId", source.getId().toString()),
                Map.of("clonedLines", n));
        return saved;
    }

    // ── Line CRUD ─────────────────────────────────────────────────────────

    @Transactional
    public WorkforcePlanLine addLine(UUID planId, WorkforcePlanLine line) {
        WorkforcePlan plan = get(planId);
        assertEditable(plan);

        line.setId(null);
        line.setWorkforcePlanId(planId);
        if (line.getLineNo() <= 0) {
            line.setLineNo(lines.findByWorkforcePlanIdOrderByLineNoAsc(planId).size() + 1);
        }
        WorkforcePlanLine saved = lines.save(line);
        bumpUpdated(plan);
        return saved;
    }

    @Transactional
    public WorkforcePlanLine updateLine(UUID lineId, WorkforcePlanLine patch) {
        WorkforcePlanLine existing = lines.findById(lineId).orElseThrow(
                () -> new ResourceNotFoundException("Line not found: " + lineId));
        WorkforcePlan plan = get(existing.getWorkforcePlanId());
        assertEditable(plan);

        if (patch.getLineNo() > 0)            existing.setLineNo(patch.getLineNo());
        if (patch.getOrgUnitId() != null)     existing.setOrgUnitId(patch.getOrgUnitId());
        if (patch.getOrgUnitLabel() != null)  existing.setOrgUnitLabel(patch.getOrgUnitLabel());
        if (patch.getJobFamily() != null)     existing.setJobFamily(patch.getJobFamily());
        if (patch.getGrade() != null)         existing.setGrade(patch.getGrade());
        if (patch.getPositionTitle() != null) existing.setPositionTitle(patch.getPositionTitle());
        existing.setPlannedHeadcount(Math.max(0, patch.getPlannedHeadcount()));
        if (patch.getPlannedFte() != null) existing.setPlannedFte(patch.getPlannedFte());
        if (patch.getPlannedMonthlyCost() != null) existing.setPlannedMonthlyCost(patch.getPlannedMonthlyCost());
        if (patch.getCurrency() != null) existing.setCurrency(patch.getCurrency());
        existing.setChangeType(patch.getChangeType());
        existing.setTargetStartDate(patch.getTargetStartDate());
        existing.setJustification(patch.getJustification());
        existing.setNotes(patch.getNotes());

        WorkforcePlanLine saved = lines.save(existing);
        bumpUpdated(plan);
        return saved;
    }

    @Transactional
    public void deleteLine(UUID lineId) {
        WorkforcePlanLine existing = lines.findById(lineId).orElseThrow(
                () -> new ResourceNotFoundException("Line not found: " + lineId));
        WorkforcePlan plan = get(existing.getWorkforcePlanId());
        assertEditable(plan);
        lines.delete(existing);
        bumpUpdated(plan);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Transactional
    public WorkforcePlan submit(UUID id) {
        WorkforcePlan p = get(id);
        if (p.getStatus() != WorkforcePlanStatus.DRAFT
                && p.getStatus() != WorkforcePlanStatus.REJECTED) {
            throw new BadRequestException("Can only submit a DRAFT or REJECTED plan");
        }
        if (lines.findByWorkforcePlanIdOrderByLineNoAsc(id).isEmpty()) {
            throw new BadRequestException("Cannot submit an empty workforce plan");
        }
        return moveTo(p, WorkforcePlanStatus.PENDING_APPROVAL, x -> {
            x.setSubmittedBy(currentRequest.username());
            x.setSubmittedAt(OffsetDateTime.now());
        });
    }

    @Transactional
    public WorkforcePlan approve(UUID id) {
        WorkforcePlan p = get(id);
        if (p.getStatus() != WorkforcePlanStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Can only approve PENDING_APPROVAL");
        }
        return moveTo(p, WorkforcePlanStatus.ACTIVE, x -> {
            x.setApprovedBy(currentRequest.username());
            x.setApprovedAt(OffsetDateTime.now());
            // Only one BASELINE can be ACTIVE per legal entity at a time;
            // auto-archive any prior ACTIVE baseline.
            if (x.getScenarioType() == ScenarioType.BASELINE) {
                plans.findByLegalEntityIdOrderByEffectiveFromDesc(x.getLegalEntityId()).stream()
                        .filter(prev -> !prev.getId().equals(x.getId()))
                        .filter(prev -> prev.getStatus() == WorkforcePlanStatus.ACTIVE)
                        .filter(prev -> prev.getScenarioType() == ScenarioType.BASELINE)
                        .forEach(prev -> {
                            prev.setStatus(WorkforcePlanStatus.ARCHIVED);
                            prev.setArchivedAt(OffsetDateTime.now());
                            prev.setUpdatedBy(currentRequest.username());
                            plans.save(prev);
                        });
            }
        });
    }

    @Transactional
    public WorkforcePlan reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw new BadRequestException("Reject reason required");
        WorkforcePlan p = get(id);
        if (p.getStatus() != WorkforcePlanStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Can only reject PENDING_APPROVAL");
        }
        return moveTo(p, WorkforcePlanStatus.REJECTED, x -> {
            x.setRejectedBy(currentRequest.username());
            x.setRejectedAt(OffsetDateTime.now());
            x.setRejectReason(reason);
        });
    }

    @Transactional
    public WorkforcePlan archive(UUID id) {
        WorkforcePlan p = get(id);
        if (p.getStatus() == WorkforcePlanStatus.ARCHIVED) return p;
        return moveTo(p, WorkforcePlanStatus.ARCHIVED, x -> x.setArchivedAt(OffsetDateTime.now()));
    }

    // ── Compare two plans ─────────────────────────────────────────────────

    public record DiffRow(
            String key, String positionTitle, String grade, String orgUnitLabel,
            Integer hcA, Integer hcB, Integer hcDelta,
            BigDecimal costA, BigDecimal costB, BigDecimal costDelta) {}

    public record DiffResult(
            UUID planAId, UUID planBId,
            int totalHcA, int totalHcB,
            BigDecimal totalCostA, BigDecimal totalCostB,
            List<DiffRow> rows) {}

    @Transactional(readOnly = true)
    public DiffResult comparePlans(UUID planAId, UUID planBId) {
        var a = get(planAId);
        var b = get(planBId);
        var aLines = linesFor(a.getId());
        var bLines = linesFor(b.getId());

        Map<String, WorkforcePlanLine> aByKey = new HashMap<>();
        Map<String, WorkforcePlanLine> bByKey = new HashMap<>();
        for (var l : aLines) aByKey.put(key(l), l);
        for (var l : bLines) bByKey.put(key(l), l);

        var allKeys = new java.util.TreeSet<String>();
        allKeys.addAll(aByKey.keySet());
        allKeys.addAll(bByKey.keySet());

        List<DiffRow> rows = new ArrayList<>();
        int totalHcA = 0, totalHcB = 0;
        BigDecimal totalCostA = BigDecimal.ZERO, totalCostB = BigDecimal.ZERO;
        for (String k : allKeys) {
            var la = aByKey.get(k);
            var lb = bByKey.get(k);
            int hcA = la == null ? 0 : la.getPlannedHeadcount();
            int hcB = lb == null ? 0 : lb.getPlannedHeadcount();
            BigDecimal cA = la == null ? BigDecimal.ZERO : la.getPlannedMonthlyCost();
            BigDecimal cB = lb == null ? BigDecimal.ZERO : lb.getPlannedMonthlyCost();
            totalHcA += hcA;
            totalHcB += hcB;
            totalCostA = totalCostA.add(cA);
            totalCostB = totalCostB.add(cB);
            var ref = la != null ? la : lb;
            rows.add(new DiffRow(k,
                    ref.getPositionTitle(), ref.getGrade(), ref.getOrgUnitLabel(),
                    hcA, hcB, hcB - hcA,
                    cA, cB, cB.subtract(cA)));
        }
        return new DiffResult(a.getId(), b.getId(),
                totalHcA, totalHcB, totalCostA, totalCostB, rows);
    }

    // ── Variance vs current actual ────────────────────────────────────────

    public record VarianceResult(
            UUID planId,
            int plannedHeadcount,
            int actualHeadcount,
            int headcountGap,           // planned - actual ; +ve = hire, -ve = reduce
            BigDecimal plannedMonthlyCost,
            BigDecimal actualMonthlyCost,
            BigDecimal monthlyCostGap) {}

    /**
     * Plan totals vs the current state of positions in the same legal
     * entity. "Actual" headcount = sum of {@code occupiedHeadcount} on
     * ACTIVE positions; "actual cost" uses {@code salaryMin × occupied}
     * as a conservative proxy (M244 budget tables can swap in for
     * precise numbers in a follow-up).
     */
    @Transactional(readOnly = true)
    public VarianceResult varianceVsActual(UUID planId) {
        var plan = get(planId);
        var pLines = linesFor(planId);
        int plannedHc = pLines.stream().mapToInt(WorkforcePlanLine::getPlannedHeadcount).sum();
        BigDecimal plannedCost = pLines.stream()
                .map(WorkforcePlanLine::getPlannedMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Filter positions by the plan's legal entity once we have a
        // legal-entity FK on the position. For now we sum across all
        // ACTIVE positions which is acceptable until M141's LE linkage
        // lands on each position row.
        var posList = positions.findByTenantId(TENANT);
        int actualHc = 0;
        BigDecimal actualCost = BigDecimal.ZERO;
        for (Position p : posList) {
            if (p.getStatus() != PositionStatus.ACTIVE) continue;
            actualHc += p.getOccupiedHeadcount();
            BigDecimal rate = p.getSalaryMin() == null ? BigDecimal.ZERO : p.getSalaryMin();
            actualCost = actualCost.add(rate.multiply(BigDecimal.valueOf(p.getOccupiedHeadcount())));
        }

        return new VarianceResult(
                plan.getId(),
                plannedHc, actualHc, plannedHc - actualHc,
                plannedCost, actualCost, plannedCost.subtract(actualCost));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void assertEditable(WorkforcePlan plan) {
        if (!plan.getStatus().isEditable()) {
            throw new BadRequestException(
                    "Cannot edit a " + plan.getStatus() + " plan — only DRAFT is editable");
        }
    }

    private void bumpUpdated(WorkforcePlan plan) {
        plan.setUpdatedBy(currentRequest.username());
        plans.save(plan);
    }

    private WorkforcePlan moveTo(WorkforcePlan p, WorkforcePlanStatus to,
                                  java.util.function.Consumer<WorkforcePlan> sideEffects) {
        WorkforcePlanStatus from = p.getStatus();
        p.setStatus(to);
        sideEffects.accept(p);
        p.setUpdatedBy(currentRequest.username());
        WorkforcePlan saved = plans.save(p);
        audit.record(MODULE, ENTITY, p.getId().toString(),
                "LIFECYCLE_" + to.name(),
                Map.of("from", from.name()),
                Map.of("to", to.name()));
        return saved;
    }

    private String key(WorkforcePlanLine l) {
        return (l.getPositionTitle() == null ? "" : l.getPositionTitle()) + "|"
                + (l.getGrade() == null ? "" : l.getGrade()) + "|"
                + (l.getOrgUnitLabel() == null ? "" : l.getOrgUnitLabel());
    }

    public record PlanSnapshot(UUID id, UUID legalEntityId, String versionCode,
                                ScenarioType scenarioType, WorkforcePlanStatus status,
                                LocalDate effectiveFrom) {}

    private PlanSnapshot snapshot(WorkforcePlan p) {
        return new PlanSnapshot(p.getId(), p.getLegalEntityId(), p.getVersionCode(),
                p.getScenarioType(), p.getStatus(), p.getEffectiveFrom());
    }

    // ── M424: Plan→Budget Transfer ────────────────────────────────────────

    /**
     * M424: Transfer approved workforce plan totals to compensation budget.
     * Creates budget rows from plan totals. Idempotent (checks for existing transfer).
     */
    @Transactional
    public CompensationBudget transferToBudget(UUID planId) {
        WorkforcePlan plan = get(planId);

        if (plan.getStatus() != WorkforcePlanStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED plans can be transferred to budget");
        }

        // Check if already transferred (idempotency)
        String scopeRef = "PLAN:" + planId.toString();
        if (compensationBudgets.existsByScopeTypeAndScopeRef(BudgetScopeType.GLOBAL, scopeRef)) {
            throw new BadRequestException("Plan already transferred to budget");
        }

        List<WorkforcePlanLine> planLines = linesFor(planId);
        BigDecimal totalAnnualCost = planLines.stream()
                .map(WorkforcePlanLine::plannedAnnualCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CompensationBudget budget = new CompensationBudget();
        budget.setTenantId("default");
        budget.setScopeType(BudgetScopeType.GLOBAL);
        budget.setScopeRef(scopeRef);
        budget.setBudgetType(BudgetType.MERIT);
        budget.setAmount(totalAnnualCost);
        budget.setCurrency(planLines.isEmpty() ? "AZN" : planLines.get(0).getCurrency());
        budget.setEffectiveFrom(plan.getEffectiveFrom());
        budget.setEffectiveTo(plan.getEffectiveTo());
        budget.setIsActive(true);

        CompensationBudget saved = compensationBudgets.save(budget);
        audit.record(MODULE, ENTITY, planId.toString(), "TRANSFER_TO_BUDGET", null, saved.getId());
        return saved;
    }
}
