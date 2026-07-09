package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.lifecycle.domain.AssetCategory;

/**
 * M456 — Asset category repository.
 */
@Repository
public interface AssetCategoryRepository extends JpaRepository<AssetCategory, UUID> {
    List<AssetCategory> findByTenantIdAndActiveOrderByName(String tenantId, Boolean active);
    List<AssetCategory> findByTenantIdOrderByName(String tenantId);
}
