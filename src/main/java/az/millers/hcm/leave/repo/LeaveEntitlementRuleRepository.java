package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.leave.domain.LeaveEntitlementRule;

public interface LeaveEntitlementRuleRepository extends JpaRepository<LeaveEntitlementRule, UUID> {

    List<LeaveEntitlementRule> findByLeaveTypeIdOrderByPriorityDescCreatedAtAsc(UUID leaveTypeId);

    List<LeaveEntitlementRule> findByLeaveTypeIdAndActiveTrueOrderByPriorityDesc(UUID leaveTypeId);
}
