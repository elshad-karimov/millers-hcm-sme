package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.PayrollResult;

public interface PayrollResultRepository extends JpaRepository<PayrollResult, UUID> {

    @Query(value = "SELECT nextval('payroll.payslip_no_seq')", nativeQuery = true)
    long nextPayslipNoSequence();

    List<PayrollResult> findByRunIdOrderByEmployeeIdAsc(UUID runId);

    Optional<PayrollResult> findByRunIdAndEmployeeId(UUID runId, UUID employeeId);

    List<PayrollResult> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    void deleteByRunId(UUID runId);
}
