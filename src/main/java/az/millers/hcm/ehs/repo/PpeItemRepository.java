package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.PpeItem;

public interface PpeItemRepository extends JpaRepository<PpeItem, UUID> {

    Optional<PpeItem> findByIdAndTenantId(UUID id, String tenantId);

    List<PpeItem> findByTenantIdAndActiveOrderByName(String tenantId, boolean active);

    List<PpeItem> findByTenantIdOrderByName(String tenantId);
}
