package az.millers.hcm.reporting.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.reporting.domain.ReportRun;

public interface ReportRunRepository extends JpaRepository<ReportRun, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('reporting.run_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<ReportRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<ReportRun> findByDefinitionIdOrderByStartedAtDesc(UUID definitionId);

    List<ReportRun> findByScheduleIdOrderByStartedAtDesc(UUID scheduleId);
}
