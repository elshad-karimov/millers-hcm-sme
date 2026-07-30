package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.ResignationRequest;
import az.millers.hcm.lifecycle.domain.ResignationStatus;

public interface ResignationRequestRepository extends JpaRepository<ResignationRequest, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('lifecycle.resignation_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Optional<ResignationRequest> findByWorkflowInstanceId(UUID workflowInstanceId);

    Page<ResignationRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    Page<ResignationRequest> findByStatusOrderByCreatedAtDesc(ResignationStatus status, Pageable pageable);

    Page<ResignationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ResignationRequest> findByEmployeeIdAndStatusIn(UUID employeeId, List<ResignationStatus> statuses);
}
