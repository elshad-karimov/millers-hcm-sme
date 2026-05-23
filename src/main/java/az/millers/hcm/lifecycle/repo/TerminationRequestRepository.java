package az.millers.hcm.lifecycle.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.TerminationRequest;
import az.millers.hcm.lifecycle.domain.TerminationStatus;

public interface TerminationRequestRepository extends JpaRepository<TerminationRequest, UUID> {

    @Query(value = "SELECT nextval('lifecycle.termination_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<TerminationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<TerminationRequest> findByStatusOrderByCreatedAtDesc(TerminationStatus status, Pageable pageable);

    Page<TerminationRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);
}
