package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitCategory;

public interface BenefitCategoryRepository extends JpaRepository<BenefitCategory, UUID> {

    List<BenefitCategory> findByTenantIdOrderByDisplayOrderAscNameAsc(String tenantId);

    List<BenefitCategory> findByTenantIdAndActiveTrueOrderByDisplayOrderAscNameAsc(String tenantId);

    Optional<BenefitCategory> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
