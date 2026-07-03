package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.RatingScale;

public interface RatingScaleRepository extends JpaRepository<RatingScale, UUID> {

    List<RatingScale> findByTenantIdOrderByScaleNameAsc(String tenantId);

    List<RatingScale> findByTenantIdAndActiveTrueOrderByScaleNameAsc(String tenantId);

    Optional<RatingScale> findFirstByTenantIdAndIsDefaultTrue(String tenantId);

    boolean existsByTenantIdAndScaleCode(String tenantId, String scaleCode);
}
