package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.payroll.domain.SalaryAdvance;
import az.millers.hcm.payroll.domain.SalaryAdvanceStatus;

public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, UUID> {

    List<SalaryAdvance> findByEmployeeIdAndStatusIn(UUID employeeId, List<SalaryAdvanceStatus> statuses);

    @Query("""
            SELECT sa FROM SalaryAdvance sa
            WHERE sa.employeeId = :empId
              AND sa.status = 'APPROVED'
              AND sa.repaymentYear = :year
              AND sa.repaymentMonth = :month
            """)
    List<SalaryAdvance> findApprovedForPeriod(
            @Param("empId") UUID employeeId,
            @Param("year") int year,
            @Param("month") int month);

    boolean existsByEmployeeIdAndStatusIn(UUID employeeId, List<SalaryAdvanceStatus> statuses);
}
