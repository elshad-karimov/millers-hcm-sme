package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.leave.domain.LeaveGroupEntitlement;

public interface LeaveGroupEntitlementRepository
        extends JpaRepository<LeaveGroupEntitlement, UUID> {

    Optional<LeaveGroupEntitlement> findByLeaveGroupIdAndLeaveTypeId(
            UUID leaveGroupId, UUID leaveTypeId);

    List<LeaveGroupEntitlement> findByLeaveGroupId(UUID leaveGroupId);
}
