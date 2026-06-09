package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.GrantStatus;
import az.millers.hcm.staffing.domain.PositionProfileGrant;

public interface PositionProfileGrantRepository extends JpaRepository<PositionProfileGrant, UUID> {

    List<PositionProfileGrant> findByOccupancyIdOrderByItemTypeAscLabelAsc(UUID occupancyId);
    List<PositionProfileGrant> findByEmployeeIdAndStatusOrderByCreatedAtDesc(UUID employeeId, GrantStatus status);
    List<PositionProfileGrant> findByOccupancyIdAndStatus(UUID occupancyId, GrantStatus status);
}
