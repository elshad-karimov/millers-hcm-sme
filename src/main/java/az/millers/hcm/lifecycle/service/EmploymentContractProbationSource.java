package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.expiry.ExpiryAlertSource;
import az.millers.hcm.common.expiry.ExpiryTrackable;
import az.millers.hcm.lifecycle.domain.EmploymentContract;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;

/**
 * Plugs the {@code probation_end_date} column of {@link EmploymentContract}
 * into the M61 {@code ExpiryAlertScheduler} (M64 / P1-03).
 *
 * <p>The same entity has two date channels (contract end + probation end),
 * each with different notification copy. We adapt rather than reimplement:
 * each row from the repo is wrapped in a tiny {@link ExpiryTrackable} that
 * projects {@code probationEndDate} as the expiry date and uses a probation-
 * specific label.
 *
 * <p>This way {@code ExpiryAlertScheduler.dispatch} produces:
 * <pre>
 *   Title:  "Probation review due in 7 days"
 *   Body:   "Probation review for contract CT-00042 — confirmation due by 2026-06-09."
 * </pre>
 * instead of the contract-expiry copy, without any scheduler change.
 */
@Component
public class EmploymentContractProbationSource implements ExpiryAlertSource {

    private final EmploymentContractRepository repository;

    public EmploymentContractProbationSource(EmploymentContractRepository repository) {
        this.repository = repository;
    }

    @Override public String moduleName() { return "LIFECYCLE"; }

    @Override public String entityName() { return "EmploymentContractProbation"; }

    @Override
    public List<? extends ExpiryTrackable> findExpiringOn(LocalDate date) {
        return repository.findActiveProbationEndingOn(date).stream()
                .map(ProbationView::new)
                .toList();
    }

    /**
     * Read-only projection of {@link EmploymentContract} that swaps the
     * {@link ExpiryTrackable#getExpiryDate()} channel to {@code probationEndDate}
     * and the labels to "Probation review". Carries the same id as the
     * underlying contract so dashboards / inboxes can deep-link.
     */
    private static final class ProbationView implements ExpiryTrackable {
        private final EmploymentContract delegate;
        ProbationView(EmploymentContract delegate) { this.delegate = delegate; }
        @Override public UUID getId() { return delegate.getId(); }
        @Override public UUID getEmployeeId() { return delegate.getEmployeeId(); }
        @Override public LocalDate getExpiryDate() { return delegate.getProbationEndDate(); }
        @Override public String getEntityLabel() { return "Probation review"; }
        @Override public String getDisplayName() { return delegate.getContractNo(); }
    }
}
