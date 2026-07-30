package az.millers.hcm.reporting.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.reporting.domain.ReportSchedule;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('reporting.schedule_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<ReportSchedule> findAllByOrderByNameAsc();

    List<ReportSchedule> findByActiveTrueAndNextRunAtBeforeOrderByNextRunAtAsc(OffsetDateTime cutoff);
}
