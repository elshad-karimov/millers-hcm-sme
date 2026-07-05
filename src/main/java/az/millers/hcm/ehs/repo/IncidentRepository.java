package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.Incident;
import az.millers.hcm.ehs.domain.IncidentStatus;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findByIdAndTenantId(UUID id, String tenantId);

    List<Incident> findByTenantIdOrderByIncidentDateDesc(String tenantId);

    List<Incident> findByTenantIdAndStatusOrderByIncidentDateDesc(String tenantId, IncidentStatus status);

    List<Incident> findByTenantIdAndReportedByEmployeeIdOrderByIncidentDateDesc(String tenantId, UUID employeeId);
}
