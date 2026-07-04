package az.millers.hcm.career.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import az.millers.hcm.career.domain.Idp;
import az.millers.hcm.career.repo.IdpRepository;
import az.millers.hcm.common.expiry.ExpiryAlertSource;

/**
 * Plugs ACTIVE IDP deadlines into the {@link az.millers.hcm.common.expiry.ExpiryAlertScheduler}
 * (M187 / PRD §8.9 career development).
 *
 * <p>Fires reminders at the standard alert windows (90 / 60 / 30 / 14 / 7 / 0 days)
 * for every ACTIVE IDP whose {@code target_date} falls on the queried day.
 * Cancelled and completed IDPs are intentionally excluded — only plans that are
 * still in-flight and at risk of missing their target need notifications.
 *
 * <p>The dual recipient pattern (employee + manager) is handled transparently by
 * {@link az.millers.hcm.common.expiry.ExpiryAlertScheduler#dispatch} — no extra
 * plumbing needed here.
 */
@Component
public class IdpDeadlineExpirySource implements ExpiryAlertSource {

    private final IdpRepository idps;

    public IdpDeadlineExpirySource(IdpRepository idps) {
        this.idps = idps;
    }

    @Override public String moduleName() { return "CAREER"; }

    @Override public String entityName() { return "Idp"; }

    @Override
    public List<Idp> findExpiringOn(LocalDate date) {
        return idps.findByStatusAndTargetDate("ACTIVE", date);
    }
}
