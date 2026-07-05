package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.workflow.domain.ApprovalGroupMember;

@Repository
public interface ApprovalGroupMemberRepository extends JpaRepository<ApprovalGroupMember, UUID> {

    List<ApprovalGroupMember> findByGroupIdOrderByUsername(UUID groupId);

    List<ApprovalGroupMember> findByTenantIdAndUsername(String tenantId, String username);

    void deleteByGroupIdAndUsername(UUID groupId, String username);
}
