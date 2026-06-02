package az.millers.hcm.corehr.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.expiry.ExpiryAlertSource;
import az.millers.hcm.corehr.domain.EmployeeCertification;
import az.millers.hcm.corehr.repo.EmployeeCertificationRepository;

/**
 * Plugs {@link EmployeeCertification} into the M61 expiry-alert scheduler
 * (M65 / P1-13). One of the final two sources — after this and the health
 * source, every Phase-1 expiry-bearing entity (identification, contract,
 * probation, certification, health) is wired in with zero scheduler changes.
 */
@Component
public class EmployeeCertificationExpirySource implements ExpiryAlertSource {

    private final EmployeeCertificationRepository repository;

    public EmployeeCertificationExpirySource(EmployeeCertificationRepository repository) {
        this.repository = repository;
    }

    @Override public String moduleName() { return "CORE_HR"; }

    @Override public String entityName() { return "EmployeeCertification"; }

    @Override
    public List<? extends EmployeeCertification> findExpiringOn(LocalDate date) {
        return repository.findByExpiryDate(date);
    }
}
