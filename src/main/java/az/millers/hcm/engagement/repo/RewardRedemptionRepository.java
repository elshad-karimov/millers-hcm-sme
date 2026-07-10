package az.millers.hcm.engagement.repo;

import az.millers.hcm.engagement.domain.RewardRedemption;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M481: Reward redemption repository.
 */
@Repository
public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, UUID> {

    List<RewardRedemption> findByTenantIdOrderByRequestedAtDesc(String tenantId);

    List<RewardRedemption> findByTenantIdAndStatusOrderByRequestedAtDesc(String tenantId, String status);

    List<RewardRedemption> findByTenantIdAndEmployeeIdOrderByRequestedAtDesc(String tenantId, UUID employeeId);

    Optional<RewardRedemption> findByIdAndTenantId(UUID id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RewardRedemption r WHERE r.id = :id")
    Optional<RewardRedemption> lockById(@Param("id") UUID id);
}
