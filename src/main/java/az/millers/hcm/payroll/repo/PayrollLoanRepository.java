package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.payroll.domain.PayrollLoan;
import az.millers.hcm.payroll.domain.PayrollLoanStatus;

public interface PayrollLoanRepository extends JpaRepository<PayrollLoan, UUID> {

    @Query("""
            SELECT pl FROM PayrollLoan pl
            WHERE pl.employeeId = :empId
              AND pl.status = 'ACTIVE'
              AND ((pl.startDeductionYear * 100 + pl.startDeductionMonth) <= (:year * 100 + :month))
              AND pl.outstandingBalance > 0
            """)
    List<PayrollLoan> findActiveForPeriod(
            @Param("empId") UUID employeeId,
            @Param("year") int year,
            @Param("month") int month);

    List<PayrollLoan> findByEmployeeIdAndStatus(UUID employeeId, PayrollLoanStatus status);

    @Query("""
            SELECT pl FROM PayrollLoan pl
            WHERE pl.employeeId = :empId
              AND pl.status = 'ACTIVE'
            """)
    List<PayrollLoan> findActiveByEmployee(@Param("empId") UUID employeeId);
}
