package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.OnboardingResourceRequest;
import az.millers.hcm.lifecycle.domain.ResourceRequestStatus;

public interface OnboardingResourceRequestRepository
        extends JpaRepository<OnboardingResourceRequest, UUID> {

    boolean existsByTaskStatusId(UUID taskStatusId);

    List<OnboardingResourceRequest> findByEmployeeIdOrderByRequestedAtDesc(UUID employeeId);

    List<OnboardingResourceRequest> findByStatusInOrderByRequestedAtAsc(List<ResourceRequestStatus> statuses);

    @Query(value = "SELECT nextval('lifecycle.onboarding_resource_request_no_seq')", nativeQuery = true)
    long nextNoSequence();
}
