package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.InjuryReport;

public interface InjuryReportRepository extends JpaRepository<InjuryReport, UUID> {

    Optional<InjuryReport> findByIdAndTenantId(UUID id, String tenantId);

    List<InjuryReport> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<InjuryReport> findByTenantIdAndIncidentIdOrderByCreatedAtDesc(String tenantId, UUID incidentId);

    List<InjuryReport> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);
}
