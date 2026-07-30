package az.millers.hcm.reporting.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.reporting.domain.ReportDefinition;
import az.millers.hcm.reporting.domain.ReportType;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('reporting.definition_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<ReportDefinition> findAllByOrderByNameAsc();

    List<ReportDefinition> findByReportTypeOrderByNameAsc(ReportType type);

    List<ReportDefinition> findByActiveTrueOrderByNameAsc();
}
