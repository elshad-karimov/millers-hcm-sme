package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.MeritMatrix;

public interface MeritMatrixRepository extends JpaRepository<MeritMatrix, UUID> {
    List<MeritMatrix> findByTenantIdAndIsActiveTrue(String tenantId);
    Optional<MeritMatrix> findByTenantIdAndCode(String tenantId, String code);
    Optional<MeritMatrix> findFirstByTenantIdAndIsActiveTrueOrderByCreatedAtAsc(String tenantId);
}
