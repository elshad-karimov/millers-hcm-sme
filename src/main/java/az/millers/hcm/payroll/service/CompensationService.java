package az.millers.hcm.payroll.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class CompensationService {

    private static final String MODULE = "PAYROLL";

    private final EmployeeCompensationRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CompensationService(EmployeeCompensationRepository repository,
                                EmployeeRepository employees,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompensation> historyFor(UUID employeeId) {
        return repository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    @Transactional
    public EmployeeCompensation upsert(EmployeeCompensation incoming) {
        if (!employees.existsById(incoming.getEmployeeId())) {
            throw new BadRequestException("Employee not found: " + incoming.getEmployeeId());
        }
        if (incoming.getMonthlyBaseSalary() == null
                || incoming.getMonthlyBaseSalary().signum() < 0) {
            throw new BadRequestException("monthlyBaseSalary must be non-negative");
        }
        // Close any open (effective_to=null) prior record by setting its end the day
        // before the new effective_from. Simple but effective for monthly salaries.
        repository.findActiveOn(incoming.getEmployeeId(), incoming.getEffectiveFrom())
                .filter(c -> c.getEffectiveTo() == null
                        && !c.getEffectiveFrom().equals(incoming.getEffectiveFrom()))
                .ifPresent(c -> {
                    c.setEffectiveTo(incoming.getEffectiveFrom().minusDays(1));
                    repository.save(c);
                });
        incoming.setCreatedBy(currentRequest.username());
        EmployeeCompensation saved = repository.save(incoming);
        audit.record(MODULE, "EmployeeCompensation", saved.getId().toString(),
                "CREATE", null,
                Map.of("employeeId", saved.getEmployeeId().toString(),
                        "monthlyBaseSalary", saved.getMonthlyBaseSalary().toPlainString(),
                        "effectiveFrom", saved.getEffectiveFrom().toString(),
                        "reason", saved.getReason() == null ? "" : saved.getReason()));
        return saved;
    }

    @Transactional(readOnly = true)
    public EmployeeCompensation getActiveOn(UUID employeeId, LocalDate date) {
        return repository.findActiveOn(employeeId, date)
                .orElseThrow(() -> new BadRequestException(
                        "No compensation defined for employee " + employeeId + " on " + date));
    }
}
