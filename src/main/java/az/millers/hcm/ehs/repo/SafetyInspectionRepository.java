package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.InspectionStatus;
import az.millers.hcm.ehs.domain.SafetyInspection;

public interface SafetyInspectionRepository extends JpaRepository<SafetyInspection, UUID> {

    Optional<SafetyInspection> findByIdAndTenantId(UUID id, String tenantId);

    List<SafetyInspection> findByTenantIdOrderByInspectionDateDesc(String tenantId);

    List<SafetyInspection> findByTenantIdAndStatusOrderByInspectionDateDesc(String tenantId, InspectionStatus status);
}
