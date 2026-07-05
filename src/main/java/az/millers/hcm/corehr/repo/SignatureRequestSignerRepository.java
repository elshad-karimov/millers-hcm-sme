package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import az.millers.hcm.corehr.domain.SignatureRequestSigner;

@Repository
public interface SignatureRequestSignerRepository extends JpaRepository<SignatureRequestSigner, UUID> {

    List<SignatureRequestSigner> findByRequestIdOrderByUsername(UUID requestId);

    @Query("SELECT s FROM SignatureRequestSigner s WHERE s.tenantId = :tenantId AND s.username = :username AND s.status = 'PENDING' ORDER BY s.id")
    List<SignatureRequestSigner> findPendingByUsername(@Param("tenantId") String tenantId, @Param("username") String username);

    Optional<SignatureRequestSigner> findByIdAndTenantId(UUID id, String tenantId);

    long countByRequestIdAndStatus(UUID requestId, SignatureRequestSigner.SignerStatus status);
}
