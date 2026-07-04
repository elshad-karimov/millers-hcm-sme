package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.SkillVerificationRequest;
import az.millers.hcm.learning.domain.SkillVerificationRequest.VerificationStatus;

public interface SkillVerificationRequestRepository extends JpaRepository<SkillVerificationRequest, UUID> {

    List<SkillVerificationRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, VerificationStatus status);

    List<SkillVerificationRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);
}
