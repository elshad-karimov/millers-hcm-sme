package az.millers.hcm.organization.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.expiry.ExpiryAlertSource;
import az.millers.hcm.organization.domain.OrgUnitDocument;
import az.millers.hcm.organization.repo.OrgUnitDocumentRepository;

/**
 * M147 / §31 — plugs {@link OrgUnitDocument} into the shared
 * {@link az.millers.hcm.common.expiry.ExpiryAlertScheduler}.
 *
 * <p>No scheduler changes required — the bean is auto-discovered via the
 * {@code List<ExpiryAlertSource>} injection in the scheduler.
 */
@Component
public class OrgUnitDocumentExpirySource implements ExpiryAlertSource {

    private final OrgUnitDocumentRepository repository;

    public OrgUnitDocumentExpirySource(OrgUnitDocumentRepository repository) {
        this.repository = repository;
    }

    @Override public String moduleName() { return "ORGANIZATION"; }

    @Override public String entityName() { return "OrgUnitDocument"; }

    @Override
    public List<? extends OrgUnitDocument> findExpiringOn(LocalDate date) {
        return repository.findByExpiryDate(date);
    }
}
