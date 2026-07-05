package az.millers.hcm.selfservice.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.selfservice.domain.HrServiceRequest;
import az.millers.hcm.selfservice.domain.ServiceRequestCategory;
import az.millers.hcm.selfservice.domain.ServiceRequestStatus;

@Repository
public interface HrServiceRequestRepository extends JpaRepository<HrServiceRequest, UUID> {

    List<HrServiceRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    List<HrServiceRequest> findByTenantIdAndStatusInOrderBySlaDueAsc(String tenantId, List<ServiceRequestStatus> statuses);

    boolean existsByCategoryAndSourceRefAndStatusIn(ServiceRequestCategory category, UUID sourceRef, List<ServiceRequestStatus> statuses);
}
