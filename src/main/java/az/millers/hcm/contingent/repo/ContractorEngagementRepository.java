package az.millers.hcm.contingent.repo;

import az.millers.hcm.contingent.domain.ContractorEngagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractorEngagementRepository extends JpaRepository<ContractorEngagement, UUID> {
    List<ContractorEngagement> findByTenantIdOrderByContractStartDesc(String tenantId);
    List<ContractorEngagement> findByTenantIdAndStatusOrderByContractStartDesc(String tenantId, String status);
    Optional<ContractorEngagement> findByIdAndTenantId(UUID id, String tenantId);
    Optional<ContractorEngagement> findByTenantIdAndEmployeeId(String tenantId, UUID employeeId);
}
