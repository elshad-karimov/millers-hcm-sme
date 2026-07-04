package az.millers.hcm.corehr.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.expiry.ExpiryAlertSource;
import az.millers.hcm.corehr.domain.EmployeeIdentification;
import az.millers.hcm.corehr.repo.EmployeeIdentificationRepository;

/**
 * Plugs {@link EmployeeIdentification} into the M61 expiry-alert scheduler.
 *
 * <p>Spring auto-injects every bean implementing
 * {@link ExpiryAlertSource} into {@code ExpiryAlertScheduler.sources} — this
 * class is the first concrete consumer of the M61 abstraction. The scheduler
 * already does all the work (alert windows, manager fan-out, dispatch via
 * {@code NotificationService}); we only have to point at the right query.
 *
 * <p>The query uses a partial index ({@code idx_emp_id_expiry}, V51) so the
 * 6 daily lookups (one per alert window) per source stay cheap even at
 * 10⁵-employee scale.
 */
@Component
public class EmployeeIdentificationExpirySource implements ExpiryAlertSource {

    private final EmployeeIdentificationRepository repository;

    public EmployeeIdentificationExpirySource(EmployeeIdentificationRepository repository) {
        this.repository = repository;
    }

    @Override public String moduleName() { return "CORE_HR"; }

    @Override public String entityName() { return "EmployeeIdentification"; }

    @Override
    public List<? extends EmployeeIdentification> findExpiringOn(LocalDate date) {
        return repository.findByExpiryDate(date);
    }
}
