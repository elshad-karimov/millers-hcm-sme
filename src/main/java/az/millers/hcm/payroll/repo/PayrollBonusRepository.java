package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.PayrollBonus;

public interface PayrollBonusRepository extends JpaRepository<PayrollBonus, UUID> {

    List<PayrollBonus> findByRunIdAndEmployeeId(UUID runId, UUID employeeId);

    List<PayrollBonus> findByRunId(UUID runId);
}
