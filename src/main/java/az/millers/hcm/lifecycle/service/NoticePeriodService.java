package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.lifecycle.domain.NoticePeriodRule;
import az.millers.hcm.lifecycle.repo.NoticePeriodRuleRepository;

@Service
@Transactional
public class NoticePeriodService {

    private final NoticePeriodRuleRepository rules;

    public NoticePeriodService(NoticePeriodRuleRepository rules) {
        this.rules = rules;
    }

    /** All active rules for HR admin view. */
    @Transactional(readOnly = true)
    public List<NoticePeriodRule> listActive() {
        return rules.findByActiveOrderByEmploymentTypeAscOnProbationAsc(true);
    }

    @Transactional(readOnly = true)
    public NoticePeriodRule get(UUID id) {
        return rules.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice period rule not found: " + id));
    }

    public NoticePeriodRule upsert(NoticePeriodRule rule) {
        return rules.save(rule);
    }

    public void deactivate(UUID id) {
        NoticePeriodRule rule = get(id);
        rule.setActive(false);
        rules.save(rule);
    }

    /**
     * Calculate the notice period in calendar days for an employee.
     * Probation takes priority over employment type (AZ Labour Code art.71).
     */
    public int noticeDaysFor(Employee employee) {
        boolean onProbation = EmploymentStatus.ON_PROBATION.equals(employee.getEmploymentStatus());
        String type = employee.getEmploymentType() != null ? employee.getEmploymentType().name() : null;
        return rules.findBestMatch(type, onProbation)
                .map(NoticePeriodRule::getNoticeDays)
                .orElse(30); // Labour Code art.70 fallback if no rule configured
    }

    /**
     * Returns the calculated last working date = resignationDate + noticeDays.
     */
    public LocalDate calculateLastWorkingDate(Employee employee, LocalDate resignationDate) {
        return resignationDate.plusDays(noticeDaysFor(employee));
    }
}
