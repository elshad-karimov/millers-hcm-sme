package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import az.millers.hcm.corehr.domain.SignatureRequest;

@Repository
public interface SignatureRequestRepository extends JpaRepository<SignatureRequest, UUID> {

    List<SignatureRequest> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Query("SELECT sr FROM SignatureRequest sr WHERE sr.tenantId = :tenantId AND sr.status IN ('PENDING') ORDER BY sr.createdAt DESC")
    List<SignatureRequest> findPendingByTenant(@Param("tenantId") String tenantId);

    Optional<SignatureRequest> findByIdAndTenantId(UUID id, String tenantId);
}
