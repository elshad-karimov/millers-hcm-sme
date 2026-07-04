package az.millers.hcm.compbenefits.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.compbenefits.api.dto.BonusRunGenerateRequest;
import az.millers.hcm.compbenefits.domain.BonusRunStatus;
import az.millers.hcm.compbenefits.repo.BonusRunRepository;
import az.millers.hcm.performance.event.ReviewCycleCompletedEvent;

/**
 * Auto-generates a {@link az.millers.hcm.compbenefits.domain.BonusRun} when
 * a performance review cycle completes (M184 / PRD §8.13 AC: "writes the bonus
 * line into the next payroll run").
 *
 * <p>On {@link ReviewCycleCompletedEvent}:
 * <ol>
 *   <li>Guard: skip if a GENERATED or PUSHED bonus run already exists for the
 *       cycle (idempotent — re-completing a cycle does not duplicate runs).</li>
 *   <li>Derive the bonus payment period as the month <em>after</em> the cycle's
 *       {@code periodEnd} (e.g. cycle ending 2025-12-31 → period 2026-01).</li>
 *   <li>Call {@link BonusRunService#generate} with {@code currency = "AZN"} and
 *       a note explaining the auto-generation.</li>
 * </ol>
 *
 * <p>The push to a specific payroll run remains a manual HR step so that
 * payroll managers can choose the correct run ID before committing.
 *
 * <p>{@code @Async} ensures a generation failure never rolls back the cycle
 * status transition.
 */
@Component
public class BonusAutoGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(BonusAutoGenerationListener.class);

    private final BonusRunRepository bonusRuns;
    private final BonusRunService bonusRunService;

    public BonusAutoGenerationListener(BonusRunRepository bonusRuns,
                                        BonusRunService bonusRunService) {
        this.bonusRuns = bonusRuns;
        this.bonusRunService = bonusRunService;
    }

    @Async
    @EventListener
    public void onCycleCompleted(ReviewCycleCompletedEvent event) {
        try {
            // Guard: don't create a duplicate if one already exists (any live status).
            if (bonusRuns.existsByCycleIdAndStatusIn(event.cycleId(),
                    List.of(BonusRunStatus.GENERATED, BonusRunStatus.PENDING_APPROVAL,
                            BonusRunStatus.APPROVED, BonusRunStatus.PUSHED))) {
                log.info("BonusAutoGenerationListener: bonus run already exists for cycle {} — skipping",
                        event.cycleCode());
                return;
            }

            // Bonus paid in the month after cycle period-end.
            LocalDate payPeriod = event.periodEnd().plusMonths(1).withDayOfMonth(1);
            int periodYear  = payPeriod.getYear();
            int periodMonth = payPeriod.getMonthValue();

            BonusRunGenerateRequest req = new BonusRunGenerateRequest(
                    "Auto — " + event.cycleName(),
                    event.cycleId(),
                    periodYear,
                    periodMonth,
                    "AZN",
                    "Auto-generated on cycle completion (" + event.cycleCode() + ")");

            bonusRunService.generate(req);
            log.info("BonusAutoGenerationListener: bonus run generated for cycle {} (pay period {}-{})",
                    event.cycleCode(), periodYear, periodMonth);

        } catch (Exception ex) {
            log.warn("BonusAutoGenerationListener: failed to auto-generate bonus run for cycle {}: {}",
                    event.cycleCode(), ex.getMessage());
        }
    }
}
