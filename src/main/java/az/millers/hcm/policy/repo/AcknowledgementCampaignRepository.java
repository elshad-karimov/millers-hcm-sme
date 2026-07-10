package az.millers.hcm.policy.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import az.millers.hcm.policy.domain.AcknowledgementCampaign;
import az.millers.hcm.policy.domain.CampaignStatus;

@Repository
public interface AcknowledgementCampaignRepository extends JpaRepository<AcknowledgementCampaign, UUID> {

    List<AcknowledgementCampaign> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<AcknowledgementCampaign> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, CampaignStatus status);

    Optional<AcknowledgementCampaign> findByIdAndTenantId(UUID id, String tenantId);

    @Query("SELECT c FROM AcknowledgementCampaign c WHERE c.policyId = :policyId AND c.policyVersion = :policyVersion AND c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    List<AcknowledgementCampaign> findByPolicyAndVersion(UUID policyId, int policyVersion, String tenantId);
}
