package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitClaimItem;

public interface BenefitClaimItemRepository extends JpaRepository<BenefitClaimItem, UUID> {

    List<BenefitClaimItem> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    List<BenefitClaimItem> findByClaimIdIn(List<UUID> claimIds);

    void deleteByClaimId(UUID claimId);
}
