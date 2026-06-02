package az.millers.hcm.corehr.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.expiry.ExpiryAlertSource;
import az.millers.hcm.corehr.domain.EmployeeHealth;
import az.millers.hcm.corehr.repo.EmployeeHealthRepository;

/**
 * Plugs {@link EmployeeHealth#getNextExamDate()} into the M61 expiry-alert
 * scheduler (M65 / P1-14).
 *
 * <p>The scheduler routes alerts via {@code NotificationService.notifyAll} —
 * the recipient is the employee + their manager. Health-specific routing
 * (e.g. notify occupational-health staff rather than the line manager) is a
 * Phase 2 enhancement; for now the manager fan-out is correct because they're
 * the ones who must release the employee for the exam.
 */
@Component
public class EmployeeHealthExpirySource implements ExpiryAlertSource {

    private final EmployeeHealthRepository repository;

    public EmployeeHealthExpirySource(EmployeeHealthRepository repository) {
        this.repository = repository;
    }

    @Override public String moduleName() { return "CORE_HR"; }

    @Override public String entityName() { return "EmployeeHealth"; }

    @Override
    public List<? extends EmployeeHealth> findExpiringOn(LocalDate date) {
        return repository.findByNextExamDate(date);
    }
}
