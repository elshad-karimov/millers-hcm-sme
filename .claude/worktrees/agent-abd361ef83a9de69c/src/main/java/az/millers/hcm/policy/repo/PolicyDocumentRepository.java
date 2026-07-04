package az.millers.hcm.policy.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.policy.domain.PolicyDocument;
import az.millers.hcm.policy.domain.PolicyStatus;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, UUID> {

    List<PolicyDocument> findByStatusOrderByCategoryAscTitleAsc(PolicyStatus status);

    List<PolicyDocument> findByCodeOrderByVersionDesc(String code);

    Optional<PolicyDocument> findTopByCodeOrderByVersionDesc(String code);
}
