package az.millers.hcm.apikey.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.apikey.domain.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Hot-path lookup from {@code ApiKeyAuthFilter}. Indexed via the unique constraint. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Multi-tenancy: resolve which tenant a key belongs to BEFORE a tenant is
     * bound. Native SQL so it bypasses the {@code @TenantId} discriminator (a key
     * hash is a global secret, unique across tenants) — the auth filter binds
     * TenantContext to the result, then {@link #findByKeyHash} resolves normally.
     */
    @Query(value = "SELECT tenant_id FROM security.api_key WHERE key_hash = :hash", nativeQuery = true)
    Optional<String> findTenantIdByKeyHash(@Param("hash") String hash);

    List<ApiKey> findAllByOrderByCreatedAtDesc();

    List<ApiKey> findByOwnerUserOrderByCreatedAtDesc(String ownerUser);
}
