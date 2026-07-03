package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
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

    Optional<PayrollDeduction> findBySourceLeaveRequestId(UUID sourceLeaveRequestId);

    List<PayrollDeduction> findByEmployeeIdAndDeductionType(UUID employeeId, String deductionType);

    Page<PayrollDeduction> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    Page<PayrollDeduction> findByEmployeeIdAndStatusOrderByCreatedAtDesc(
            UUID employeeId, String status, Pageable pageable);

    /** M351: an advance is recovered exactly once, so a single row keyed by the advance is enough. */
    Optional<PayrollDeduction> findBySourceAdvanceId(UUID sourceAdvanceId);

    /** HCM_11 M378: the ACTIVE recurring deduction (if any) for a benefit enrollment. */
    Optional<PayrollDeduction> findFirstBySourceBenefitEnrollmentIdAndStatus(
            UUID sourceBenefitEnrollmentId, String status);

    /**
     * M352: a loan is recovered over many periods, so idempotency is per (loan, period).
     * One row per installment, keyed by the originating loan and the period it applies to.
     */
    Optional<PayrollDeduction> findBySourceLoanIdAndStartPeriodYearAndStartPeriodMonth(
            UUID sourceLoanId, int startPeriodYear, int startPeriodMonth);

    /**
     * M351/M352: all advance/loan deduction rows recorded for an employee in a period.
     * The engine sums these to get the post-statutory deduction, so recalculating a run
     * re-derives the same net impact from persisted rows instead of relying on freshly
     * created rows (which would otherwise return zero on a second calculation).
     */
    List<PayrollDeduction> findByEmployeeIdAndStartPeriodYearAndStartPeriodMonthAndDeductionTypeIn(
            UUID employeeId, int startPeriodYear, int startPeriodMonth, java.util.Collection<String> deductionTypes);
}
