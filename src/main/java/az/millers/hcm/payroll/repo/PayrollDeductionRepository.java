package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.payroll.domain.PayrollDeduction;

public interface PayrollDeductionRepository extends JpaRepository<PayrollDeduction, UUID> {

    /**
     * Returns all ACTIVE deductions for an employee that are eligible for the
     * given payroll period (started by or before this period, and either
     * open-ended or not yet past their end period).
     */
    @Query("""
            SELECT d FROM PayrollDeduction d
            WHERE d.employeeId = :empId
              AND d.status = 'ACTIVE'
              AND (d.startPeriodYear * 100 + d.startPeriodMonth) <= (:year * 100 + :month)
              AND (
                  d.endPeriodYear IS NULL
                  OR (d.endPeriodYear * 100 + d.endPeriodMonth) >= (:year * 100 + :month)
              )
            """)
    List<PayrollDeduction> findActiveForPeriod(
            @Param("empId") UUID employeeId,
            @Param("year") int year,
            @Param("month") int month);

    Page<PayrollDeduction> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    Page<PayrollDeduction> findByEmployeeIdAndStatusOrderByCreatedAtDesc(
            UUID employeeId, String status, Pageable pageable);
}
