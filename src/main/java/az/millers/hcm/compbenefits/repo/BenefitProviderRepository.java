package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitProvider;

public interface BenefitProviderRepository extends JpaRepository<BenefitProvider, UUID> {

    List<BenefitProvider> findByTenantIdOrderByNameAsc(String tenantId);

    List<BenefitProvider> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
