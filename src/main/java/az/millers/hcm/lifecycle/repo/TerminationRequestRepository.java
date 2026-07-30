package az.millers.hcm.lifecycle.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.TerminationRequest;
import az.millers.hcm.lifecycle.domain.TerminationStatus;

public interface TerminationRequestRepository extends JpaRepository<TerminationRequest, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('lifecycle.termination_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<TerminationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<TerminationRequest> findByStatusOrderByCreatedAtDesc(TerminationStatus status, Pageable pageable);

    Page<TerminationRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    /**
     * PROCESSED terminations whose effective date is on or before {@code asOf}
     * and whose Keycloak account has not yet been disabled.
     * Used by the EOD access-revocation scheduler (M185 / PRD §8.11.6).
     */
    @Query("SELECT t FROM TerminationRequest t " +
           "WHERE t.status = az.millers.hcm.lifecycle.domain.TerminationStatus.PROCESSED " +
           "AND t.effectiveDate <= :asOf " +
           "AND t.systemAccessRevokedAt IS NULL")
    List<TerminationRequest> findPendingAccessRevocation(LocalDate asOf);
}
