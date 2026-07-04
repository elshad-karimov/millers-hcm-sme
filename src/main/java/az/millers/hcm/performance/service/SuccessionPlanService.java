package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.AssignmentSummary;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Band;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.BenchReport;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.BenchRow;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.CriticalRoleRow;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.CriticalRolesReport;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.DevelopmentEmployee;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.DevelopmentList;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.GridCell;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.GridEmployee;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.PotentialRatingRequest;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.SuccessionGrid;
import az.millers.hcm.performance.domain.SuccessionNomination;
import az.millers.hcm.performance.repo.SuccessionNominationRepository;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.learning.domain.LearningPathAssignment;
import az.millers.hcm.learning.domain.PathAssignmentStatus;
import az.millers.hcm.learning.repo.LearningPathAssignmentRepository;
import az.millers.hcm.learning.repo.LearningPathRepository;
import az.millers.hcm.learning.service.LearningPathAssignmentService;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;

/**
 * 9-box succession matrix service (M92).
 *
 * <p>Plots performance ({@code final_rating}) on the X axis and potential
 * ({@code potential_rating}) on the Y axis, both bucketed into three bands
 * via {@link Band#of(BigDecimal)}: LOW (1-2.49), MID (2.5-3.99), HIGH (4-5).
 * The 9 cells use the standard HR archetype labels (Solid Performer, Star,
 * etc.) — these names are convention; the math is what's stable.
 *
 * <p>Reviews missing either rating dimension are counted but not placed —
 * the response carries the counts so the UI can show "N reviews waiting
 * for calibration".
 */
@Service
public class SuccessionPlanService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "PerformanceReview";

    private final ReviewCycleRepository cycles;
    private final PerformanceReviewRepository reviews;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final LearningPathAssignmentRepository pathAssignments;
    private final LearningPathRepository paths;
    private final LearningPathAssignmentService pathAssignmentService;
    // M257 — critical-roles-at-risk report depends on staffing + nominations.
    private final PositionRepository positions;
    private final SuccessionNominationRepository nominations;

    public SuccessionPlanService(ReviewCycleRepository cycles,
                                  PerformanceReviewRepository reviews,
                                  EmployeeRepository employees,
                                  AuditService audit,
                                  LearningPathAssignmentRepository pathAssignments,
                                  LearningPathRepository paths,
                                  LearningPathAssignmentService pathAssignmentService,
                                  PositionRepository positions,
                                  SuccessionNominationRepository nominations) {
        this.cycles = cycles;
        this.reviews = reviews;
        this.employees = employees;
        this.audit = audit;
        this.pathAssignments = pathAssignments;
        this.paths = paths;
        this.pathAssignmentService = pathAssignmentService;
        this.positions = positions;
        this.nominations = nominations;
    }

    /**
     * Map a (performance, potential) cell to a {@link Readiness} tier (M94).
     *
     * <p>Stars (HIGH/HIGH) are ready immediately. Future Stars and High
     * Performers are promotable in 1-2 years. Core Players and Trusted Pros
     * round out the long-term bench. Everything with LOW on either axis
     * needs development before they appear as a successor.
     */
    static Readiness readinessFor(Band performance, Band potential) {
        if (performance == Band.HIGH && potential == Band.HIGH) return Readiness.READY_NOW;
        if (performance == Band.MID  && potential == Band.HIGH) return Readiness.READY_SOON;
        if (performance == Band.HIGH && potential == Band.MID)  return Readiness.READY_SOON;
        if (performance == Band.MID  && potential == Band.MID)  return Readiness.READY_LONG_TERM;
        if (performance == Band.HIGH && potential == Band.LOW)  return Readiness.READY_LONG_TERM;
        return Readiness.UNDER_DEVELOPMENT;
    }

    /** Canonical 9-box archetype labels (performance × potential). */
    static String labelFor(Band performance, Band potential) {
        // Convention from the HR / talent-management literature.
        return switch (potential) {
            case HIGH -> switch (performance) {
                case LOW  -> "Enigma";
                case MID  -> "Future Star";
                case HIGH -> "Star";
            };
            case MID -> switch (performance) {
                case LOW  -> "Inconsistent Player";
                case MID  -> "Core Player";
                case HIGH -> "High Performer";
            };
            case LOW -> switch (performance) {
                case LOW  -> "Iceberg";
                case MID  -> "Solid Performer";
                case HIGH -> "Trusted Pro";
            };
        };
    }

    @Transactional(readOnly = true)
    public SuccessionGrid grid(UUID cycleId) {
        ReviewCycle cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle not found: " + cycleId));
        List<PerformanceReview> all = reviews.findByCycleIdOrderByCreatedAtDesc(cycleId);

        // Two-pass: classify + count, then materialise cells in canonical order.
        Map<Band, Map<Band, List<GridEmployee>>> bucket = new EnumMap<>(Band.class);
        for (Band p : Band.values()) {
            Map<Band, List<GridEmployee>> row = new EnumMap<>(Band.class);
            for (Band q : Band.values()) row.put(q, new ArrayList<>());
            bucket.put(p, row);
        }

        int placed = 0, missingPerf = 0, missingPot = 0;
        for (PerformanceReview r : all) {
            BigDecimal perf = r.getFinalRating();
            BigDecimal pot = r.getPotentialRating();
            if (perf == null) { missingPerf++; }
            if (pot == null)  { missingPot++; }
            if (perf == null || pot == null) continue;

            Band perfBand = Band.of(perf);
            Band potBand  = Band.of(pot);
            GridEmployee ge = buildEmployee(r);
            bucket.get(perfBand).get(potBand).add(ge);
            placed++;
        }

        List<GridCell> cells = new ArrayList<>(9);
        // Render in row-major order from high-potential top-left through to
        // low-potential bottom-right. The UI re-orders for display.
        for (Band pot : new Band[]{Band.HIGH, Band.MID, Band.LOW}) {
            for (Band perf : new Band[]{Band.LOW, Band.MID, Band.HIGH}) {
                List<GridEmployee> es = bucket.get(perf).get(pot);
                es.sort(Comparator.comparing(GridEmployee::employeeName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));
                cells.add(new GridCell(perf, pot, labelFor(perf, pot), es.size(), es));
            }
        }

        return new SuccessionGrid(
                cycleId, cycle.getName(),
                all.size(), placed, missingPerf, missingPot, cells);
    }

    /**
     * Per-manager succession bench depth (M94).
     *
     * <p>For every manager who has at least one direct report in the cycle,
     * count how many of those reports are placed at each readiness tier.
     * Sorted by ready-now desc, then ready-soon desc — the leadership
     * conversation usually starts at the deepest benches.
     *
     * <p>The manager set is derived from the employees' {@code managerId} —
     * NOT from the review's recorded managerId (that's a snapshot at review
     * time and can drift). A manager with no placed reports still appears
     * with zeros so HR can see who's flying blind.
     */
    @Transactional(readOnly = true)
    public BenchReport benchDepth(UUID cycleId) {
        ReviewCycle cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle not found: " + cycleId));
        List<PerformanceReview> all = reviews.findByCycleIdOrderByCreatedAtDesc(cycleId);

        // Group reviews by the employee's current manager. We load each
        // employee once and cache the result — typical cycle has 100s of
        // reviews against 10s of managers, so the N+1 risk is moderate;
        // a future optimisation can pre-join via a custom query.
        Map<UUID, List<PerformanceReview>> byManager = new LinkedHashMap<>();
        Map<UUID, Employee> empCache = new HashMap<>();
        for (PerformanceReview r : all) {
            Employee emp = empCache.computeIfAbsent(r.getEmployeeId(),
                    id -> employees.findById(id).orElse(null));
            if (emp == null || emp.getManagerId() == null) continue;
            byManager.computeIfAbsent(emp.getManagerId(), m -> new ArrayList<>()).add(r);
        }

        List<BenchRow> rows = new ArrayList<>(byManager.size());
        for (Map.Entry<UUID, List<PerformanceReview>> e : byManager.entrySet()) {
            UUID managerId = e.getKey();
            List<PerformanceReview> reports = e.getValue();
            int total = reports.size();
            int placed = 0, readyNow = 0, readySoon = 0, readyLong = 0, underDev = 0;
            for (PerformanceReview r : reports) {
                if (r.getFinalRating() == null || r.getPotentialRating() == null) continue;
                placed++;
                Readiness tier = readinessFor(
                        Band.of(r.getFinalRating()),
                        Band.of(r.getPotentialRating()));
                switch (tier) {
                    case READY_NOW         -> readyNow++;
                    case READY_SOON        -> readySoon++;
                    case READY_LONG_TERM   -> readyLong++;
                    case UNDER_DEVELOPMENT -> underDev++;
                }
            }
            String mgrName = employees.findById(managerId)
                    .map(emp ->
                            ((emp.getFirstName() == null ? "" : emp.getFirstName()) + " "
                                    + (emp.getLastName() == null ? "" : emp.getLastName())).trim())
                    .orElse("(unknown manager)");
            rows.add(new BenchRow(managerId, mgrName, total, placed,
                    readyNow, readySoon, readyLong, underDev));
        }

        // Deepest bench first: ready-now desc, then ready-soon desc.
        rows.sort(Comparator
                .comparingInt(BenchRow::readyNow).reversed()
                .thenComparing(Comparator.comparingInt(BenchRow::readySoon).reversed())
                .thenComparing(Comparator.comparing(BenchRow::managerName,
                        Comparator.nullsLast(String::compareToIgnoreCase))));
        return new BenchReport(cycleId, cycle.getName(), rows.size(), rows);
    }

    /**
     * M257 — Critical Roles at Risk report (PRD §31 wired into M103).
     *
     * <p>Loads every {@code critical_flag = TRUE} position (set in the
     * M254 form), then for each one counts the active nominations from
     * M103 bucketed by readiness tier. A row is {@code atRisk} when:
     * <ul>
     *   <li>{@code successor_required = TRUE} and no nominations exist
     *       at all — "named successor required but none picked"; or
     *   <li>position is critical and no {@code READY_NOW} successor —
     *       "no immediate cover if the seat empties tomorrow".
     * </ul>
     *
     * <p>Cycle-agnostic — this is a snapshot of "current succession
     * pipeline for critical roles" not a cycle-bound bench report.
     * Operators run this between cycles to find gaps to close.
     */
    @Transactional(readOnly = true)
    public CriticalRolesReport criticalRolesAtRisk() {
        List<Position> critical = positions
                .findByTenantIdAndCriticalFlagTrueOrderByBusinessImpactScoreDescTitleAsc("default");

        List<CriticalRoleRow> rows = new ArrayList<>(critical.size());
        int atRiskCount = 0;
        for (Position p : critical) {
            List<SuccessionNomination> active = nominations
                    .findByTenantIdAndPositionIdAndCancelledAtIsNullOrderByCreatedAtDesc("default", p.getId());
            int total = active.size();
            int readyNow = 0, readySoon = 0, readyLong = 0;
            for (SuccessionNomination n : active) {
                switch (n.getReadinessTier()) {
                    case READY_NOW         -> readyNow++;
                    case READY_SOON        -> readySoon++;
                    case READY_LONG_TERM   -> readyLong++;
                    case UNDER_DEVELOPMENT -> { /* not counted as bench depth */ }
                }
            }

            // At-risk classification — see Javadoc.
            String reason = null;
            if (p.isSuccessorRequired() && total == 0) {
                reason = "Successor required but none nominated";
            } else if (readyNow == 0 && total == 0) {
                reason = "No nominees at all";
            } else if (readyNow == 0) {
                reason = "No READY_NOW successor — only future readiness";
            }
            boolean atRisk = reason != null;
            if (atRisk) atRiskCount++;

            rows.add(new CriticalRoleRow(
                    p.getId(),
                    p.getCode(),
                    p.getTitle(),
                    p.getOrgUnitLabel(),
                    p.getBusinessImpactScore(),
                    p.getRiskCategory(),
                    p.isKeySkillConcentration(),
                    p.isSuccessorRequired(),
                    total, readyNow, readySoon, readyLong,
                    atRisk,
                    reason));
        }

        // At-risk first, then by descending business impact, then by title.
        // Already pre-sorted by impact/title from the repo, so just shuffle
        // the at-risk rows to the top while preserving the rest's order.
        rows.sort(Comparator
                .comparing(CriticalRoleRow::atRisk).reversed());
        return new CriticalRolesReport(rows.size(), atRiskCount, rows);
    }

    /**
     * Drill from a {@link Readiness#UNDER_DEVELOPMENT} bench cell to the
     * employees it contains, with their currently active learning-path
     * assignments inlined (M96).
     *
     * <p>Connects M94's bench-depth bucket → M95's path assignment surface
     * so HR can go from "manager X has 4 people who need development" to
     * "here they are; one already has a path; assign these other three to
     * the Leadership Foundations path" in two clicks.
     *
     * @param managerId optional — when present, narrow to that manager's
     *                  direct reports; otherwise return the whole bucket
     *                  for the cycle.
     */
    @Transactional(readOnly = true)
    public DevelopmentList developmentBucket(UUID cycleId, UUID managerId) {
        ReviewCycle cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle not found: " + cycleId));
        List<PerformanceReview> all = reviews.findByCycleIdOrderByCreatedAtDesc(cycleId);

        Map<UUID, Employee> empCache = new HashMap<>();
        List<DevelopmentEmployee> out = new ArrayList<>();
        for (PerformanceReview r : all) {
            if (r.getFinalRating() == null || r.getPotentialRating() == null) continue;
            Readiness tier = readinessFor(
                    Band.of(r.getFinalRating()), Band.of(r.getPotentialRating()));
            if (tier != Readiness.UNDER_DEVELOPMENT) continue;
            Employee emp = empCache.computeIfAbsent(r.getEmployeeId(),
                    id -> employees.findById(id).orElse(null));
            if (emp == null) continue;
            // Optional manager-id filter — by the employee's CURRENT manager,
            // matching the bench-depth grouping for consistency.
            if (managerId != null && !managerId.equals(emp.getManagerId())) continue;

            String fullName = ((emp.getFirstName() == null ? "" : emp.getFirstName()) + " "
                    + (emp.getLastName() == null ? "" : emp.getLastName())).trim();
            out.add(new DevelopmentEmployee(
                    r.getId(),
                    r.getEmployeeId(),
                    fullName,
                    emp.getDepartmentName(),
                    r.getFinalRating(),
                    r.getPotentialRating(),
                    r.getRecommendation(),
                    activeAssignmentsFor(r.getEmployeeId())));
        }

        out.sort(Comparator.comparing(DevelopmentEmployee::employeeName,
                Comparator.nullsLast(String::compareToIgnoreCase)));

        String managerName = null;
        if (managerId != null) {
            managerName = employees.findById(managerId)
                    .map(e -> ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                            + (e.getLastName() == null ? "" : e.getLastName())).trim())
                    .orElse(null);
        }
        return new DevelopmentList(cycleId, cycle.getName(), managerId, managerName,
                out.size(), out);
    }

    /**
     * Look up an employee's non-terminal path assignments and compute their
     * progress percent via the shared {@code LearningPathAssignmentService}
     * (so the drill-down's progress always matches the assignments page —
     * one source of truth).
     */
    private List<AssignmentSummary> activeAssignmentsFor(UUID employeeId) {
        List<LearningPathAssignment> raw = pathAssignments
                .findByEmployeeIdOrderByAssignedAtDesc(employeeId);
        List<AssignmentSummary> out = new ArrayList<>();
        for (LearningPathAssignment a : raw) {
            // Only show non-terminal — completed/cancelled clutter the drill.
            if (a.getStatus() == PathAssignmentStatus.COMPLETED
                    || a.getStatus() == PathAssignmentStatus.CANCELLED) continue;
            // toResponse() carries the derived progress — calling the
            // shared service keeps the calc canonical.
            var resp = pathAssignmentService.toResponse(a);
            out.add(new AssignmentSummary(
                    resp.id(), resp.pathId(),
                    resp.pathName() == null
                            ? paths.findById(a.getPathId()).map(p -> p.getName()).orElse("(deleted)")
                            : resp.pathName(),
                    resp.status().name(),
                    resp.progressPercent()));
        }
        return out;
    }

    /**
     * Set / update an employee's potential rating during calibration.
     * Audits the change (calibration is a sensitive HR decision).
     */
    @Transactional
    public PerformanceReview setPotential(UUID reviewId, PotentialRatingRequest req) {
        if (req.potentialRating() == null) {
            throw new BadRequestException("potentialRating is required");
        }
        double v = req.potentialRating().doubleValue();
        if (v < 1.0 || v > 5.0) {
            throw new BadRequestException("potentialRating must be between 1 and 5");
        }
        PerformanceReview r = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));

        BigDecimal oldRating = r.getPotentialRating();
        r.setPotentialRating(req.potentialRating());
        r.setPotentialNotes(req.potentialNotes());
        reviews.save(r);

        audit.record(MODULE, ENTITY, reviewId.toString(), "SET_POTENTIAL",
                Map.of("old", oldRating == null ? "" : oldRating.toString()),
                Map.of(
                        "rating", req.potentialRating().toString(),
                        "notes", req.potentialNotes() == null ? "" : req.potentialNotes()));
        return r;
    }

    private GridEmployee buildEmployee(PerformanceReview r) {
        Optional<Employee> emp = employees.findById(r.getEmployeeId());
        String name = emp.map(e ->
                ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                        + (e.getLastName() == null ? "" : e.getLastName())).trim())
                .orElse("(unknown employee)");
        String dept = emp.map(Employee::getDepartmentName).orElse(null);
        return new GridEmployee(
                r.getId(), r.getEmployeeId(), name, dept,
                r.getFinalRating(), r.getPotentialRating(), r.getRecommendation());
    }
}
