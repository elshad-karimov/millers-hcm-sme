package az.millers.hcm.payroll.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.BankAccount;
import az.millers.hcm.payroll.repo.BankAccountRepository;

@Service
public class BankAccountService {

    private static final String MODULE = "PAYROLL";

    private final BankAccountRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;

    public BankAccountService(BankAccountRepository repository,
                               EmployeeRepository employees,
                               AuditService audit) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Optional<BankAccount> findForEmployee(UUID employeeId) {
        return repository.findByEmployeeId(employeeId);
    }

    @Transactional
    public BankAccount upsert(BankAccount incoming) {
        if (!employees.existsById(incoming.getEmployeeId())) {
            throw new BadRequestException("Employee not found: " + incoming.getEmployeeId());
        }
        BankAccount target = repository.findByEmployeeId(incoming.getEmployeeId())
                .orElse(incoming);
        target.setEmployeeId(incoming.getEmployeeId());
        target.setBankCode(incoming.getBankCode());
        target.setBankName(incoming.getBankName());
        target.setIban(incoming.getIban());
        target.setAccountNumber(incoming.getAccountNumber());
        target.setCurrency(incoming.getCurrency() == null ? "AZN" : incoming.getCurrency());
        target.setActive(incoming.isActive());
        BankAccount saved = repository.save(target);
        audit.record(MODULE, "BankAccount", saved.getId().toString(),
                "UPSERT", null,
                Map.of("employeeId", saved.getEmployeeId().toString(),
                        "bankCode", saved.getBankCode() == null ? "" : saved.getBankCode(),
                        "iban", saved.getIban() == null ? "" : saved.getIban()));
        return saved;
    }
}
