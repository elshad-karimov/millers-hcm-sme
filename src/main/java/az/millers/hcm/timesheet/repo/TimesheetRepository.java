package az.millers.hcm.timesheet.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetStatus;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {

    Optional<Timesheet> findByEmployeeIdAndPeriodYearAndPeriodMonth(
            UUID employeeId, int year, int month);

    List<Timesheet> findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(int year, int month);

    List<Timesheet> findByPeriodYearAndPeriodMonthAndStatus(int year, int month, TimesheetStatus status);

    List<Timesheet> findByEmployeeIdOrderByPeriodYearDescPeriodMonthDesc(UUID employeeId);

    /** Scope-bounded equivalent for ABAC-filtered period view (PRD 14.9). */
    List<Timesheet> findByPeriodYearAndPeriodMonthAndEmployeeIdInOrderByEmployeeIdAsc(
            int year, int month, Collection<UUID> employeeIds);

    /** Used by the auto-submit scheduler (M189): find DRAFT or REOPENED timesheets for a period. */
    List<Timesheet> findByPeriodYearAndPeriodMonthAndStatusIn(
            int year, int month, Collection<TimesheetStatus> statuses);

    /** Manager approval queue, already narrowed to the caller's hierarchy. */
    List<Timesheet> findByPeriodYearAndPeriodMonthAndStatusInAndEmployeeIdInOrderByEmployeeIdAsc(
            int year, int month, Collection<TimesheetStatus> statuses, Collection<UUID> employeeIds);

    /** True while anything in the period is still awaiting a decision — blocks the lock. */
    boolean existsByPeriodYearAndPeriodMonthAndStatusIn(
            int year, int month, Collection<TimesheetStatus> statuses);
}
