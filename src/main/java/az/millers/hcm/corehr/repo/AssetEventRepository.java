package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.AssetEvent;

public interface AssetEventRepository extends JpaRepository<AssetEvent, UUID> {

    List<AssetEvent> findByAssetIdOrderByOccurredAtDesc(UUID assetId);
}
