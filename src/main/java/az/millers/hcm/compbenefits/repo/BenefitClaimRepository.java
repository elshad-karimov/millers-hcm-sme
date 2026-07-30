package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.compbenefits.domain.BenefitClaim;
import az.millers.hcm.compbenefits.domain.BenefitClaimStatus;

public interface BenefitClaimRepository extends JpaRepository<BenefitClaim, UUID> {

    List<BenefitClaim> findByTenantIdOrderByClaimDateDesc(String tenantId);

    List<BenefitClaim> findByTenantIdAndStatusOrderByClaimDateDesc(String tenantId, BenefitClaimStatus status);

    List<BenefitClaim> findByTenantIdAndEmployeeIdOrderByClaimDateDesc(String tenantId, UUID employeeId);

    @Query(value = "SELECT config.next_tenant_seq('compbenefits.benefit_claim_seq')", nativeQuery = true)
    long nextClaimSeq();
}
