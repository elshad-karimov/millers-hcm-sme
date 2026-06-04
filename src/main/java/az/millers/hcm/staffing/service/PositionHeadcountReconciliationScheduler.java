package az.millers.hcm.staffing.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import az.millers.hcm.staffing.domain.Position;

/**
 * Nightly walker that calls {@link PositionHeadcountService#reconcile()} to
 * keep {@code Position.occupiedHeadcount} consistent with the ground-truth
 * employee table (M109).
 *
 * <p>This is the safety net — every hire/term/swap path is wired through
 * {@code StaffingService.adjustOccupancy}, but if a code path slips past or
 * someone updates the DB directly, this job reconverges within a day.
 * Idempotent: a second run in the same day touches nothing.
 *
 * <p>Cron is overridable via {@code hcm.staffing.headcount.recon.cron} so
 * tests can disable it (set to a never-firing schedule) or ops can shift it
 * away from the 02:00 backup window.
 */
@Component
public class PositionHeadcountReconciliationScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(PositionHeadcountReconciliationScheduler.class);

    private final PositionHeadcountService service;

    public PositionHeadcountReconciliationScheduler(PositionHeadcountService service) {
        this.service = service;
    }

    /** 04:30 every day in the deployment-local TZ — after the 02:00 DB backup. */
    @Scheduled(cron = "${hcm.staffing.headcount.recon.cron:0 30 4 * * *}")
    public void run() {
        List<Position> drifted = service.reconcile();
        if (!drifted.isEmpty()) {
            log.warn("Position headcount reconciliation: fixed drift on {} position(s) — {}",
                    drifted.size(),
                    drifted.stream().map(Position::getCode).toList());
        }
    }
}
