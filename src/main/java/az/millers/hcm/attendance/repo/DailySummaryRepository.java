package az.millers.hcm.attendance.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;

public interface DailySummaryRepository extends JpaRepository<DailySummary, UUID> {

    Optional<DailySummary> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    List<DailySummary> findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(
            LocalDate from, LocalDate to);

    List<DailySummary> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            UUID employeeId, LocalDate from, LocalDate to);

    List<DailySummary> findByWorkDateAndStatus(LocalDate workDate, SummaryStatus status);
}
