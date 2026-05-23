package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.PayrollAllowance;

public interface PayrollAllowanceRepository extends JpaRepository<PayrollAllowance, UUID> {

    List<PayrollAllowance> findByRunIdAndEmployeeIdOrderByAllowanceTypeCodeAsc(UUID runId, UUID employeeId);

    List<PayrollAllowance> findByRunIdOrderByEmployeeIdAscAllowanceTypeCodeAsc(UUID runId);

    /**
     * Wiped at the top of {@code PayrollEngine.calculate()} so recalc
     * stays idempotent — same delete-and-rebuild pattern as
     * {@code PayrollResultRepository.deleteByRunId}.
     */
    @Modifying
    @Query("delete from PayrollAllowance a where a.runId = :runId")
    void deleteByRunId(UUID runId);
}
