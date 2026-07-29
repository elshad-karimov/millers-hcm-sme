package az.millers.hcm.staffing.service;
import az.millers.hcm.common.tenant.TenantContext;

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
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.recruitment.domain.VacancyStatus;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.staffing.api.dto.PositionHeadcountDtos.HeadcountSummary;
import az.millers.hcm.staffing.api.dto.PositionHeadcountDtos.OrgUnitRoll;
import az.millers.hcm.staffing.api.dto.PositionHeadcountDtos.PositionHeadcountRow;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * Position control gate (M109).
 *
 * <p>Central choke-point for every code path that <em>fills</em> a position —
 * direct {@code EmployeeService.create}, rehire, contract change, hire via
 * recruitment, employee-assignment swap. Every such path calls
 * {@link #assertCanFill(UUID)} before {@code setPositionId} and pairs with
 * {@link az.millers.hcm.staffing.service.StaffingService#adjustOccupancy} to
 * keep {@code Position.occupiedHeadcount} in sync.
 *
 * <p>The gate is intentionally idempotent for "same employee, same position"
 * updates — that's how {@code EmployeeService.update} avoids tripping itself
 * when no position change happened.
 *
 * <p>Also exposes:
 * <ul>
 *   <li>{@link #report()} — full position-control dashboard payload.</li>
 *   <li>{@link #reconcile()} — recomputes {@code occupiedHeadcount} from the
 *       ground-truth employee table for every ACTIVE position; safe to run
 *       nightly. Returns the list of positions that drifted.</li>
 * </ul>
 */
@Service
public class PositionHeadcountService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "Position";

    private final PositionRepository positions;
    private final EmployeeRepository employees;
    private final VacancyRepository vacancies;
    private final AuditService audit;
    // M244 — funding gate; injected lazily by Spring so we don't create a
    // circular constructor cycle if PositionFundingService ever depends
    // back on this service.
    private final PositionFundingService fundingService;

    public PositionHeadcountService(PositionRepository positions,
                                    EmployeeRepository employees,
                                    VacancyRepository vacancies,
                                    AuditService audit,
                                    PositionFundingService fundingService) {
        this.positions = positions;
        this.employees = employees;
        this.vacancies = vacancies;
        this.audit = audit;
        this.fundingService = fundingService;
    }

    // ─── Gating ─────────────────────────────────────────────────────────────

    /**
     * Throws {@link BadRequestException} if filling this position would push
     * the occupied count over the approved cap. A null {@code positionId} is
     * a no-op — employees without a position assignment skip the gate.
     */
    @Transactional(readOnly = true)
    public void assertCanFill(UUID positionId) {
        if (positionId == null) return;
        Position p = positions.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
        // M243 — only ACTIVE positions accept new fills. CLOSED is kept
        // as an explicit case for the friendlier error message; every
        // other non-ACTIVE state (DRAFT / PENDING_APPROVAL / APPROVED /
        // FROZEN / UNDER_REVIEW / ARCHIVED) is also blocked.
        if (p.getStatus() == PositionStatus.CLOSED) {
            throw new BadRequestException(
                    "Position " + p.getCode() + " is CLOSED — cannot assign employees");
        }
        if (!p.getStatus().isFillable()) {
            throw new BadRequestException(
                    "Position " + p.getCode() + " is " + p.getStatus()
                    + " — only ACTIVE positions can be filled");
        }
        // M244 — funding gate. UNFUNDED / PENDING / EXPIRED positions are
        // ACTIVE but cannot accept new hires until funding is allocated.
        fundingService.assertCanRecruit(positionId);
        long actualOccupied = employees.countActiveByPositionId(positionId);
        if (!hasRoom(p.getApprovedHeadcount(), actualOccupied)) {
            throw new BadRequestException(
                    "Position " + p.getCode() + " is at capacity (occupied "
                    + actualOccupied + " / approved " + p.getApprovedHeadcount()
                    + "). Raise approvedHeadcount or pick a different position.");
        }
    }

    /**
     * Variant for moves: when employee X is switching from A → B, the gate
     * shouldn't count X's existing seat as blocking the move into A (a no-op)
     * or out of B back into B. Callers pass the employee's <em>current</em>
     * positionId so the gate can detect the no-op case.
     */
    @Transactional(readOnly = true)
    public void assertCanMove(UUID currentPositionId, UUID targetPositionId) {
        if (java.util.Objects.equals(currentPositionId, targetPositionId)) return;
        assertCanFill(targetPositionId);
    }

    /**
     * Vacancy creation gate — refuses to post a requisition for a position
     * that's already at (or over) capacity, counting both filled seats and
     * outstanding OPEN/PUBLISHED openings on existing vacancies.
     */
    @Transactional(readOnly = true)
    public void assertCanPostVacancy(UUID positionId, int openings) {
        if (positionId == null) return;
        if (openings < 1) {
            throw new BadRequestException("openings must be at least 1");
        }
        Position p = positions.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
        // M267 — full M243 lifecycle gate. Previously only blocked CLOSED;
        // FROZEN / DRAFT / PENDING_APPROVAL / UNDER_REVIEW / ARCHIVED would
        // all let vacancies be posted but then fail at hire time
        // (assertCanFill uses isFillable). Block at posting to avoid
        // ghost vacancies on positions that can never be filled.
        if (!p.getStatus().isFillable()) {
            throw new BadRequestException(
                    "Position " + p.getCode() + " is " + p.getStatus()
                    + " — only ACTIVE positions accept vacancies. "
                    + (p.getStatus() == PositionStatus.FROZEN
                            ? "Unfreeze the position first."
                            : "Activate the position first."));
        }
        // M267 — funding gate. Without this, you could post a vacancy
        // for an UNFUNDED position, run a full recruitment cycle, and
        // then have the hire blocked by assertCanFill at the very last
        // step. Better to surface the gate up front.
        fundingService.assertCanRecruit(positionId);

        long actualOccupied = employees.countActiveByPositionId(positionId);
        // M274 — count OPEN + PUBLISHED: both mean "actively accepting
        // candidates" against this position's headcount.
        int openVacancyOpenings = vacancies.sumOpeningsByPositionAndStatusIn(positionId,
                java.util.List.of(VacancyStatus.OPEN, VacancyStatus.PUBLISHED));
        long committed = actualOccupied + openVacancyOpenings;
        if (committed + openings > p.getApprovedHeadcount()) {
            throw new BadRequestException(
                    "Position " + p.getCode() + " cannot absorb " + openings
                    + " more opening(s). Approved=" + p.getApprovedHeadcount()
                    + ", occupied=" + actualOccupied
                    + ", already-open vacancies=" + openVacancyOpenings + ".");
        }
    }

    /** Pure-math helper — exposed for {@link az.millers.hcm.staffing.service.StaffingService} tests. */
    public static boolean hasRoom(int approved, long currentOccupied) {
        return currentOccupied < approved;
    }

    // ─── Dashboard ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public HeadcountSummary report() {
        List<Position> all = positions.findByTenantIdAndStatusOrderByOrgUnitLabelAscTitleAsc(TenantContext.current(), PositionStatus.ACTIVE);
        Map<UUID, Long> actualByPos = groundTruth();

        // Pre-cache vacancy openings counts so we don't hit the DB N times.
        Map<UUID, Integer> openVacanciesByPos = new HashMap<>();
        for (Position p : all) {
            openVacanciesByPos.put(p.getId(),
                    vacancies.sumOpeningsByPositionAndStatusIn(p.getId(),
                            java.util.List.of(VacancyStatus.OPEN, VacancyStatus.PUBLISHED)));
        }

        List<PositionHeadcountRow> rows = new ArrayList<>(all.size());
        Map<UUID, List<PositionHeadcountRow>> byOrg = new HashMap<>();
        for (Position p : all) {
            long actual = actualByPos.getOrDefault(p.getId(), 0L);
            int openVac = openVacanciesByPos.getOrDefault(p.getId(), 0);
            PositionHeadcountRow row = new PositionHeadcountRow(
                    p.getId(),
                    p.getCode(),
                    p.getTitle(),
                    p.getOrgUnitId(),
                    p.getOrgUnitLabel(),
                    p.getApprovedHeadcount(),
                    p.getOccupiedHeadcount(),
                    (int) actual,
                    openVac,
                    Math.max(0, p.getApprovedHeadcount() - (int) actual - openVac),
                    actual > p.getApprovedHeadcount(),
                    actual != p.getOccupiedHeadcount(),
                    p.getVacancyState());
            rows.add(row);
            UUID orgId = p.getOrgUnitId();
            if (orgId != null) {
                byOrg.computeIfAbsent(orgId, k -> new ArrayList<>()).add(row);
            }
        }

        List<OrgUnitRoll> rolls = new ArrayList<>();
        for (var entry : byOrg.entrySet()) {
            List<PositionHeadcountRow> orgRows = entry.getValue();
            int approved = orgRows.stream().mapToInt(PositionHeadcountRow::approvedHeadcount).sum();
            int actual = orgRows.stream().mapToInt(PositionHeadcountRow::actualOccupied).sum();
            int openVac = orgRows.stream().mapToInt(PositionHeadcountRow::openVacancyOpenings).sum();
            rolls.add(new OrgUnitRoll(
                    entry.getKey(),
                    orgRows.get(0).orgUnitLabel(),
                    orgRows.size(),
                    approved,
                    actual,
                    openVac,
                    Math.max(0, approved - actual - openVac)));
        }
        rolls.sort((a, b) -> {
            if (a.orgUnitLabel() == null && b.orgUnitLabel() == null) return 0;
            if (a.orgUnitLabel() == null) return 1;
            if (b.orgUnitLabel() == null) return -1;
            return a.orgUnitLabel().compareToIgnoreCase(b.orgUnitLabel());
        });

        int totalApproved = rows.stream().mapToInt(PositionHeadcountRow::approvedHeadcount).sum();
        int totalActual = rows.stream().mapToInt(PositionHeadcountRow::actualOccupied).sum();
        int totalOpenVac = rows.stream().mapToInt(PositionHeadcountRow::openVacancyOpenings).sum();
        long overBudget = rows.stream().filter(PositionHeadcountRow::overBudget).count();
        long drifted = rows.stream().filter(PositionHeadcountRow::driftDetected).count();

        return new HeadcountSummary(
                totalApproved,
                totalActual,
                totalOpenVac,
                Math.max(0, totalApproved - totalActual - totalOpenVac),
                (int) overBudget,
                (int) drifted,
                rows.size(),
                rows,
                rolls);
    }

    // ─── Reconciliation ─────────────────────────────────────────────────────

    /**
     * Rewrites {@code Position.occupiedHeadcount} to match the ground-truth
     * employee count for every ACTIVE position. Returns the positions that
     * were actually changed (drifted). Audits every fix.
     *
     * <p>This is the safety net behind the gate: even if a code path slips
     * past the gate (or someone updates the DB directly), the nightly job
     * reconverges. Idempotent — calling twice in a row does nothing the
     * second time.
     */
    @Transactional
    public List<Position> reconcile() {
        Map<UUID, Long> truth = groundTruth();
        List<Position> all = positions.findByTenantIdAndStatus(TenantContext.current(), PositionStatus.ACTIVE);
        List<Position> drifted = new ArrayList<>();
        for (Position p : all) {
            long actual = truth.getOrDefault(p.getId(), 0L);
            if (actual != p.getOccupiedHeadcount()) {
                int before = p.getOccupiedHeadcount();
                p.setOccupiedHeadcount((int) actual);
                positions.save(p);
                audit.record(MODULE, ENTITY, p.getId().toString(),
                        "RECONCILE_HEADCOUNT",
                        Map.of("occupiedHeadcount", before),
                        Map.of("occupiedHeadcount", (int) actual,
                                "groundTruth", actual));
                drifted.add(p);
            }
        }
        return drifted;
    }

    private Map<UUID, Long> groundTruth() {
        Map<UUID, Long> truth = new HashMap<>();
        for (Object[] row : employees.groupCountActiveByPositionId()) {
            truth.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return truth;
    }
}
