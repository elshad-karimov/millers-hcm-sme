package az.millers.hcm.compliance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compliance.domain.PrivacyRequest;

public interface PrivacyRequestRepository extends JpaRepository<PrivacyRequest, UUID> {

    List<PrivacyRequest> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<PrivacyRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);
}
