package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import az.millers.hcm.payroll.domain.LoanInstallmentSchedule;

@Repository
public interface LoanInstallmentScheduleRepository extends JpaRepository<LoanInstallmentSchedule, UUID> {
    List<LoanInstallmentSchedule> findByLoanRequestIdOrderByInstallmentNumber(UUID loanRequestId);
    List<LoanInstallmentSchedule> findByPayrollLoanIdOrderByInstallmentNumber(UUID payrollLoanId);
}
