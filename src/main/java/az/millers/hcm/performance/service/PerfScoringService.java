package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.domain.Goal;
import az.millers.hcm.performance.domain.KpiAssignment;
import az.millers.hcm.performance.domain.PerfCompetencyAssessment;
import az.millers.hcm.performance.domain.PerfSectionType;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.repo.GoalRepository;
import az.millers.hcm.performance.repo.KpiAssignmentRepository;
import az.millers.hcm.performance.repo.PerfCompetencyAssessmentRepository;
import az.millers.hcm.performance.repo.PerfTemplateSectionRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_12 M394 — §18 weighted review scoring.
 *
 * <p>Section scores (all on the 1..5 scale):
 * <ul>
 *   <li>GOALS — reuses the V19 weighted goal score;</li>
 *   <li>KPI — weighted average of MEASURED KPI-assignment ratings (M390);</li>
 *   <li>COMPETENCY — average final level of the M393 assessment;</li>
 *   <li>VALUES — manager-entered.</li>
 * </ul>
 *
 * <p>§18.2 overall = Σ(section score × template weight) / Σ(weights of sections
 * that HAVE a score) — missing sections don't drag the overall to zero, they are
 * re-normalised out. Weights come from the review's frozen template (fallback:
 * the cycle's template, then the Phase-A default 50/25/20/5).
 *
 * <p>§18.3 band: overall × 20 → percentage → rating-scale band (cycle's scale,
 * fallback tenant default). §18.4 override: HR-only, reason required, the
 * original computed rating is preserved and every change audited.
 */
@Service
public class PerfScoringService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "PerformanceReview";
    private static final BigDecimal FIVE = BigDecimal.valueOf(5);
    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);

    /** Phase-A gap-check defaults when neither review nor cycle carries a template. */
    private static final Map<PerfSectionType, BigDecimal> DEFAULT_WEIGHTS = Map.of(
            PerfSectionType.GOALS, BigDecimal.valueOf(50),
            PerfSectionType.KPI, BigDecimal.valueOf(25),
            PerfSectionType.COMPETENCY, BigDecimal.valueOf(20),
            PerfSectionType.VALUES, BigDecimal.valueOf(5));

    private final PerformanceReviewRepository reviews;
    private final ReviewCycleRepository cycles;
    private final GoalRepository goals;
    private final KpiAssignmentRepository kpiAssignments;
    private final PerfCompetencyAssessmentRepository competencyAssessments;
    private final PerfTemplateSectionRepository templateSections;
    private final RatingScaleService ratingScales;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PerfScoringService(PerformanceReviewRepository reviews,
                              ReviewCycleRepository cycles,
                              GoalRepository goals,
                              KpiAssignmentRepository kpiAssignments,
                              PerfCompetencyAssessmentRepository competencyAssessments,
                              PerfTemplateSectionRepository templateSections,
                              RatingScaleService ratingScales,
                              AccessScopeService accessScope,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.reviews = reviews;
        this.cycles = cycles;
        this.goals = goals;
        this.kpiAssignments = kpiAssignments;
        this.competencyAssessments = competencyAssessments;
        this.templateSections = templateSections;
        this.ratingScales = ratingScales;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /**
     * Recompute the section scores + §18.2 overall + §18.3 band. {@code valuesScore}
     * (nullable) sets the manager-entered VALUES section; when null the stored value
     * is kept.
     */
    @Transactional
    public PerformanceReview computeScores(UUID reviewId, BigDecimal valuesScore) {
        PerformanceReview r = requireAccessible(reviewId);
        if (valuesScore != null) {
            if (valuesScore.signum() < 0 || valuesScore.compareTo(FIVE) > 0) {
                throw new BadRequestException("valuesScore must be between 0 and 5");
            }
            r.setValuesScore(valuesScore);
        }

        r.setGoalScore(goalScore(r));
        r.setKpiScore(kpiScore(r));
        r.setCompetencyScore(competencyScore(r));

        Map<PerfSectionType, BigDecimal> weights = sectionWeights(r);
        Map<PerfSectionType, BigDecimal> scores = new EnumMap<>(PerfSectionType.class);
        if (r.getGoalScore() != null) scores.put(PerfSectionType.GOALS, r.getGoalScore());
        if (r.getKpiScore() != null) scores.put(PerfSectionType.KPI, r.getKpiScore());
        if (r.getCompetencyScore() != null) scores.put(PerfSectionType.COMPETENCY, r.getCompetencyScore());
        if (r.getValuesScore() != null) scores.put(PerfSectionType.VALUES, r.getValuesScore());

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (var e : scores.entrySet()) {
            BigDecimal w = weights.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (w.signum() <= 0) continue;
            weightedSum = weightedSum.add(e.getValue().multiply(w));
            weightTotal = weightTotal.add(w);
        }
        BigDecimal overall = weightTotal.signum() > 0
                ? weightedSum.divide(weightTotal, 3, RoundingMode.HALF_UP)
                : null;
        r.setOverallScore(overall);
        r.setOverallBand(bandFor(r, overall));

        // The computed overall becomes the final rating unless calibration has
        // locked it or HR has manually overridden it (§18.4 keeps the override).
        if (overall != null && !r.isCalibrationLocked() && r.getOverriddenAt() == null) {
            r.setFinalRating(overall);
            r.setFinalBand(r.getOverallBand());
        }

        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, reviewId.toString(), "COMPUTE_SCORES", null, Map.of(
                "goal", String.valueOf(saved.getGoalScore()),
                "kpi", String.valueOf(saved.getKpiScore()),
                "competency", String.valueOf(saved.getCompetencyScore()),
                "values", String.valueOf(saved.getValuesScore()),
                "overall", String.valueOf(saved.getOverallScore()),
                "band", String.valueOf(saved.getOverallBand())));
        return saved;
    }

    /** §18.4 — HR override; reason required, original preserved, audited. */
    @Transactional
    public PerformanceReview override(UUID reviewId, BigDecimal newRating, String reason) {
        if (newRating == null || newRating.signum() < 0 || newRating.compareTo(FIVE) > 0) {
            throw new BadRequestException("Override rating must be between 0 and 5");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("An override reason is required");
        }
        PerformanceReview r = requireAccessible(reviewId);
        // Preserve the FIRST computed rating forever (§18.4 / §37.12 pattern).
        if (r.getOriginalRating() == null) {
            r.setOriginalRating(r.getFinalRating() != null ? r.getFinalRating() : r.getOverallScore());
        }
        BigDecimal before = r.getFinalRating();
        r.setFinalRating(newRating);
        r.setFinalBand(bandFor(r, newRating));
        r.setOverrideReason(reason.trim());
        r.setOverriddenBy(currentRequest.username());
        r.setOverriddenAt(OffsetDateTime.now());
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, reviewId.toString(), "OVERRIDE_RATING",
                String.valueOf(before),
                Map.of("newRating", newRating.toPlainString(), "reason", reason.trim(),
                        "original", String.valueOf(saved.getOriginalRating())));
        return saved;
    }

    // ── Section scores ───────────────────────────────────────────────────────

    private BigDecimal goalScore(PerformanceReview r) {
        return weightedAverage(goals.findByCycleIdAndEmployeeIdOrderByCreatedAt(
                        r.getCycleId(), r.getEmployeeId()).stream()
                .filter(g -> g.getRating() != null)
                .map(g -> new BigDecimal[] { g.getRating(),
                        g.getWeightPercent() == null ? BigDecimal.ZERO : g.getWeightPercent() })
                .toList());
    }

    private BigDecimal kpiScore(PerformanceReview r) {
        return weightedAverage(kpiAssignments
                .findByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(
                        r.getTenantId(), r.getCycleId(), r.getEmployeeId()).stream()
                .filter(a -> "MEASURED".equals(a.getStatus()) && a.getRating() != null)
                .map(a -> new BigDecimal[] { a.getRating(),
                        a.getWeightPercent() == null ? BigDecimal.ZERO : a.getWeightPercent() })
                .toList());
    }

    private BigDecimal competencyScore(PerformanceReview r) {
        List<PerfCompetencyAssessment> rows =
                competencyAssessments.findByReviewIdOrderByCreatedAtAsc(r.getId());
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (PerfCompetencyAssessment a : rows) {
            if (a.getFinalLevel() == null) continue;
            sum = sum.add(BigDecimal.valueOf(a.getFinalLevel()));
            n++;
        }
        return n == 0 ? null : sum.divide(BigDecimal.valueOf(n), 3, RoundingMode.HALF_UP);
    }

    /** (value, weight) pairs → weighted average; all-zero weights → flat average. */
    private static BigDecimal weightedAverage(List<BigDecimal[]> items) {
        if (items.isEmpty()) return null;
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        BigDecimal flatSum = BigDecimal.ZERO;
        for (BigDecimal[] it : items) {
            weightedSum = weightedSum.add(it[0].multiply(it[1]));
            weightTotal = weightTotal.add(it[1]);
            flatSum = flatSum.add(it[0]);
        }
        if (weightTotal.signum() == 0) {
            return flatSum.divide(BigDecimal.valueOf(items.size()), 3, RoundingMode.HALF_UP);
        }
        return weightedSum.divide(weightTotal, 3, RoundingMode.HALF_UP);
    }

    // ── Template weights + band conversion ───────────────────────────────────

    private Map<PerfSectionType, BigDecimal> sectionWeights(PerformanceReview r) {
        UUID templateId = r.getTemplateId();
        if (templateId == null) {
            templateId = cycles.findById(r.getCycleId())
                    .map(ReviewCycle::getTemplateId).orElse(null);
        }
        if (templateId == null) return DEFAULT_WEIGHTS;
        Map<PerfSectionType, BigDecimal> weights = new EnumMap<>(PerfSectionType.class);
        templateSections.findByTemplateIdOrderBySectionOrderAsc(templateId).stream()
                .filter(s -> s.getSectionType().isScoring())
                .forEach(s -> weights.merge(s.getSectionType(),
                        s.getWeightPercent() == null ? BigDecimal.ZERO : s.getWeightPercent(),
                        BigDecimal::add));
        return weights.isEmpty() ? DEFAULT_WEIGHTS : weights;
    }

    /** §18.3 — 1..5 score → ×20 percentage → rating-scale band label. */
    private String bandFor(PerformanceReview r, BigDecimal score) {
        if (score == null) return null;
        UUID scaleId = cycles.findById(r.getCycleId())
                .map(ReviewCycle::getRatingScaleId).orElse(null);
        if (scaleId == null) {
            scaleId = ratingScales.defaultScale()
                    .map(az.millers.hcm.performance.domain.RatingScale::getId).orElse(null);
        }
        if (scaleId == null) return null;
        BigDecimal percentage = score.multiply(TWENTY).setScale(2, RoundingMode.HALF_UP);
        return ratingScales.convertScore(scaleId, percentage)
                .map(v -> v.getRatingLabel()).orElse(null);
    }

    private PerformanceReview requireAccessible(UUID reviewId) {
        PerformanceReview r = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        if (!accessScope.isAccessible(r.getEmployeeId())) {
            throw new AccessDeniedException("Review is outside your access scope");
        }
        return r;
    }
}
