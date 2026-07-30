package az.millers.hcm.businesstrip.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.businesstrip.domain.MileageClaim;
import az.millers.hcm.businesstrip.domain.MileageClaimStatus;

public interface MileageClaimRepository extends JpaRepository<MileageClaim, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('business_trip.mileage_claim_no_seq')", nativeQuery = true)
    long nextClaimNoSequence();

    Optional<MileageClaim> findByIdAndTenantId(UUID id, String tenantId);

    List<MileageClaim> findByTenantIdAndEmployeeIdOrderByClaimDateDesc(String tenantId, UUID employeeId);

    List<MileageClaim> findByTenantIdAndStatusOrderByClaimDateDesc(String tenantId, MileageClaimStatus status);

    List<MileageClaim> findByTenantIdAndEmployeeIdInAndStatusOrderByClaimDateDesc(
            String tenantId, Collection<UUID> employeeIds, MileageClaimStatus status);

    List<MileageClaim> findByTenantIdAndEmployeeIdInOrderByClaimDateDesc(
            String tenantId, Collection<UUID> employeeIds);
}
