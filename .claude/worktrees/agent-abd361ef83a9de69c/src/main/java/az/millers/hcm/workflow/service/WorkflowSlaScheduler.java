package az.millers.hcm.workflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * M126 — fires the SLA breach detector on a fixed cadence. Default 5
 * minutes — fast enough that a breach is noticed before the end of the
 * working day, infrequent enough that we don't hammer the DB. The
 * cadence is the only knob; the detection logic + escalation actions
 * live in {@link WorkflowSlaService}.
 */
@Component
public class WorkflowSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSlaScheduler.class);

    private final WorkflowSlaService service;

    public WorkflowSlaScheduler(WorkflowSlaService service) {
        this.service = service;
    }

    /** Every 5 minutes, with a 1-minute jitter to dodge cron sync storms. */
    @Scheduled(fixedDelayString = "300000", initialDelayString = "60000")
    public void sweep() {
        try {
            int newBreaches = service.detectAndNotify();
            if (newBreaches > 0) {
                log.info("Workflow SLA sweep: recorded {} new breach(es)", newBreaches);
            } else {
                log.debug("Workflow SLA sweep: no new breaches");
            }
        } catch (RuntimeException e) {
            // Never let an exception kill the scheduler thread —
            // Spring's @Scheduled would otherwise stop firing.
            log.error("Workflow SLA sweep failed", e);
        }
    }
}
