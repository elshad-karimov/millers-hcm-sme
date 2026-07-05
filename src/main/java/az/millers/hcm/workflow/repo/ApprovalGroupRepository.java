package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.workflow.domain.ApprovalGroup;

@Repository
public interface ApprovalGroupRepository extends JpaRepository<ApprovalGroup, UUID> {

    List<ApprovalGroup> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    Optional<ApprovalGroup> findByIdAndTenantId(UUID id, String tenantId);

    Optional<ApprovalGroup> findByCodeAndTenantId(String code, String tenantId);
}
