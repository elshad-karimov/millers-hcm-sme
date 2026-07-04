package az.millers.hcm.apikey.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.apikey.domain.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Hot-path lookup from {@code ApiKeyAuthFilter}. Indexed via the unique constraint. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findAllByOrderByCreatedAtDesc();

    List<ApiKey> findByOwnerUserOrderByCreatedAtDesc(String ownerUser);
}
