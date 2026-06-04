package az.millers.hcm.attendance.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;

public interface DailySummaryRepository extends JpaRepository<DailySummary, UUID> {

    Optional<DailySummary> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    List<DailySummary> findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(
            LocalDate from, LocalDate to);

    List<DailySummary> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            UUID employeeId, LocalDate from, LocalDate to);

    List<DailySummary> findByWorkDateAndStatus(LocalDate workDate, SummaryStatus status);

    /**
     * M113 — fetch every row inside the window for a known employee set in a
     * single query. Used by {@code RosterVarianceService} when scope-restricted
     * (the manager-self-service / org-unit branch supplies its visible set).
     */
    @Query("""
            select s from DailySummary s
            where s.workDate between :from and :to
              and s.employeeId in :employeeIds
            order by s.workDate asc, s.employeeId asc
            """)
    List<DailySummary> findInWindowForEmployees(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("employeeIds") Collection<UUID> employeeIds);
}
