package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.CriticalPosition;

public interface CriticalPositionRepository extends JpaRepository<CriticalPosition, UUID> {

    List<CriticalPosition> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<CriticalPosition> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(String tenantId);

    Optional<CriticalPosition> findByTenantIdAndPositionId(String tenantId, UUID positionId);

    boolean existsByTenantIdAndPositionId(String tenantId, UUID positionId);
}
