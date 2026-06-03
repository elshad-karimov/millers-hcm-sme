package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Band;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.GridCell;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.GridEmployee;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.PotentialRatingRequest;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.SuccessionGrid;
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

    public SuccessionPlanService(ReviewCycleRepository cycles,
                                  PerformanceReviewRepository reviews,
                                  EmployeeRepository employees,
                                  AuditService audit) {
        this.cycles = cycles;
        this.reviews = reviews;
        this.employees = employees;
        this.audit = audit;
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
