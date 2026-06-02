package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.expiry.ExpiryAlertSource;
import az.millers.hcm.lifecycle.domain.EmploymentContract;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;

/**
 * Plugs the {@code end_date} column of {@link EmploymentContract} into the
 * M61 {@code ExpiryAlertScheduler} (M64 / P1-03).
 *
 * <p>Probation expiry is a separate concern — see
 * {@link EmploymentContractProbationSource} — so the scheduler picks up two
 * date channels for the same entity via two beans. Could have collapsed both
 * into a single source returning two adapter objects per contract, but the
 * label / display semantics differ enough ("contract expiring" vs "probation
 * ending — confirmation due") that two sources keep the notification copy
 * legible.
 */
@Component
public class EmploymentContractExpirySource implements ExpiryAlertSource {

    private final EmploymentContractRepository repository;

    public EmploymentContractExpirySource(EmploymentContractRepository repository) {
        this.repository = repository;
    }

    @Override public String moduleName() { return "LIFECYCLE"; }

    @Override public String entityName() { return "EmploymentContract"; }

    @Override
    public List<? extends EmploymentContract> findExpiringOn(LocalDate date) {
        return repository.findActiveExpiringOn(date);
    }
}
