package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.learning.event.CoursePassedEvent;
import az.millers.hcm.performance.api.dto.GoalResponse;
import az.millers.hcm.performance.domain.Goal;
import az.millers.hcm.performance.domain.GoalStatus;
import az.millers.hcm.performance.repo.GoalRepository;

/**
 * Auto-rates DEVELOPMENT goals that are linked to an LMS course when the
 * employee's enrollment reaches {@code PASSED} (M49 — PRD 8.13/8.14).
 *
 * <h3>Rating formula</h3>
 * <pre>
 *   rating = min(5.0, bestScorePercent / 20)   rounded to 1 decimal place
 * </pre>
 * Examples: 100 % → 5.0 · 80 % → 4.0 · 60 % → 3.0 · 40 % → 2.0 · 20 % → 1.0
 *
 * <h3>Status transition</h3>
 * <ul>
 *   <li>rating ≥ 3.0 → {@code ACHIEVED}</li>
 *   <li>rating &lt; 3.0 → {@code MISSED}</li>
 * </ul>
 *
 * <p>Goals already in {@code ACHIEVED}, {@code MISSED}, or {@code CANCELLED}
 * are silently skipped (idempotent).
 */
@Service
public class GoalAutoRatingService {

    private static final Logger log = LoggerFactory.getLogger(GoalAutoRatingService.class);
    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "Goal";

    private final GoalRepository goals;
    private final AuditService   audit;

    public GoalAutoRatingService(GoalRepository goals, AuditService audit) {
        this.goals = goals;
        this.audit = audit;
    }

    /**
     * Triggered whenever {@link az.millers.hcm.learning.service.EnrollmentService}
     * issues a {@link CoursePassedEvent}.  Runs in the same transaction as the
     * enrollment submit so any failure rolls back the whole operation.
     */
    @EventListener
    @Transactional
    public void onCoursePassed(CoursePassedEvent event) {
        List<Goal> candidates = goals.findOpenDevelopmentGoalsForCourse(
                event.courseId(), event.employeeId());

        if (candidates.isEmpty()) {
            log.debug("M49: no open DEVELOPMENT goals linked to course {} for employee {}",
                    event.courseId(), event.employeeId());
            return;
        }

        BigDecimal rating = computeRating(event.bestScorePercent());
        GoalStatus finalStatus = rating.compareTo(BigDecimal.valueOf(3)) >= 0
                ? GoalStatus.ACHIEVED
                : GoalStatus.MISSED;
        String note = String.format(
                "Auto-rated via course completion (score: %s%%)", event.bestScorePercent().toPlainString());

        for (Goal g : candidates) {
            GoalResponse before = GoalResponse.from(g);
            g.setRating(rating);
            g.setRatingNote(note);
            g.setStatus(finalStatus);
            goals.save(g);
            audit.record(MODULE, ENTITY, g.getId().toString(), "AUTO_RATE",
                    before, GoalResponse.from(g));
            log.info("M49: auto-rated goal {} ({}) → rating={} status={} (course={}, score={}%)",
                    g.getGoalNo(), g.getId(), rating, finalStatus,
                    event.courseId(), event.bestScorePercent());
        }
    }

    /**
     * Maps a quiz score percentage (0–100) to a 0–5 goal rating.
     * Formula: {@code min(5.0, score / 20)}, rounded to 1 decimal.
     */
    static BigDecimal computeRating(BigDecimal scorePercent) {
        BigDecimal raw = scorePercent
                .divide(BigDecimal.valueOf(20), 2, RoundingMode.HALF_UP);
        BigDecimal capped = raw.min(BigDecimal.valueOf(5));
        return capped.setScale(1, RoundingMode.HALF_UP);
    }
}
