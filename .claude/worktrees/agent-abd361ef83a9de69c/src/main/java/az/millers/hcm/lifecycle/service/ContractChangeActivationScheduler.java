package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import az.millers.hcm.lifecycle.domain.ContractChange;
import az.millers.hcm.lifecycle.domain.ContractChangeStatus;
import az.millers.hcm.lifecycle.repo.ContractChangeRepository;

/**
 * Daily scheduler that activates APPROVED contract changes whose effective
 * date has arrived (PRD §8.12.6 AC: "When the effective date arrives,
 * Then the system activates the new salary").
 *
 * <p>Runs at 00:15 AM UTC every day. For each APPROVED change with
 * {@code effectiveDate ≤ today} it delegates to
 * {@link ContractChangeService#apply(java.util.UUID)} which handles all
 * change types (salary, position, department, manager, etc.).
 *
 * <p>Failures on individual records are caught and logged so a single
 * bad record never blocks the rest of the batch.
 */
@Component
public class ContractChangeActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContractChangeActivationScheduler.class);

    private final ContractChangeRepository changes;
    private final ContractChangeService contractChangeService;

    public ContractChangeActivationScheduler(ContractChangeRepository changes,
                                              ContractChangeService contractChangeService) {
        this.changes = changes;
        this.contractChangeService = contractChangeService;
    }

    @Scheduled(cron = "0 15 0 * * *")   // 00:15 UTC daily
    public void activateDueChanges() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<ContractChange> due = changes.findByStatusAndEffectiveDateLessThanEqual(
                ContractChangeStatus.APPROVED, today);

        if (due.isEmpty()) return;

        log.info("ContractChangeActivationScheduler: activating {} due changes for {}",
                due.size(), today);

        int applied = 0;
        int failed  = 0;
        for (ContractChange c : due) {
            try {
                contractChangeService.apply(c.getId());
                applied++;
            } catch (Exception ex) {
                failed++;
                log.warn("ContractChangeActivationScheduler: failed to apply {} ({}): {}",
                        c.getChangeNo(), c.getId(), ex.getMessage());
            }
        }
        log.info("ContractChangeActivationScheduler: applied={} failed={}", applied, failed);
    }
}
