package az.millers.hcm.payroll.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.BankAccount;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Optional<BankAccount> findByEmployeeId(UUID employeeId);
}
