package az.millers.hcm.attendance.repo;

import az.millers.hcm.attendance.domain.ShiftSwapRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M482: Shift swap request repository.
 */
@Repository
public interface ShiftSwapRequestRepository extends JpaRepository<ShiftSwapRequest, UUID> {

    List<ShiftSwapRequest> findByTenantIdOrderByRequestedAtDesc(String tenantId);

    List<ShiftSwapRequest> findByTenantIdAndStatusOrderByRequestedAtDesc(String tenantId, String status);

    Optional<ShiftSwapRequest> findByIdAndTenantId(UUID id, String tenantId);
}
