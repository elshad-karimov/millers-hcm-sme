package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.payroll.domain.PayrollRunHold;

public interface PayrollRunHoldRepository extends JpaRepository<PayrollRunHold, UUID> {

    List<PayrollRunHold> findByRunId(UUID runId);

    Optional<PayrollRunHold> findByRunIdAndEmployeeId(UUID runId, UUID employeeId);

    boolean existsByRunIdAndEmployeeIdAndReleasedAtIsNull(UUID runId, UUID employeeId);
}
