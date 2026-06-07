package az.millers.hcm.policy.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.policy.domain.PolicyAcknowledgement;

public interface PolicyAcknowledgementRepository
        extends JpaRepository<PolicyAcknowledgement, UUID> {

    Optional<PolicyAcknowledgement> findByPolicyIdAndEmployeeId(UUID policyId, UUID employeeId);

    List<PolicyAcknowledgement> findByEmployeeId(UUID employeeId);

    /** Used by /api/policies/{id}/pending — "which employees haven't acked yet?". */
    List<PolicyAcknowledgement> findByPolicyId(UUID policyId);

    /** Bulk membership probe — handy for self-service "did I ack any of these?". */
    List<PolicyAcknowledgement> findByEmployeeIdAndPolicyIdIn(UUID employeeId, Set<UUID> policyIds);
}
