package az.millers.hcm.selfservice.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.selfservice.domain.HrServiceRequestComment;

/**
 * M437 — HR service request comment repository.
 */
public interface HrServiceRequestCommentRepository extends JpaRepository<HrServiceRequestComment, UUID> {

    List<HrServiceRequestComment> findByTenantIdAndRequestIdOrderByCreatedAtAsc(String tenantId, UUID requestId);
}
