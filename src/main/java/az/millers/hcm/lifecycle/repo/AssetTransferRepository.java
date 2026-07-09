package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import az.millers.hcm.lifecycle.domain.AssetTransfer;
import az.millers.hcm.lifecycle.domain.AssetTransferStatus;

@Repository
public interface AssetTransferRepository extends JpaRepository<AssetTransfer, UUID> {
    List<AssetTransfer> findByTenantIdAndStatusOrderByRequestedAtDesc(String tenantId, AssetTransferStatus status);
    List<AssetTransfer> findByTenantIdOrderByRequestedAtDesc(String tenantId);
    List<AssetTransfer> findByAssetIdOrderByRequestedAtDesc(UUID assetId);
}
