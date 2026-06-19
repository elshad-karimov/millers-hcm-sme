package az.millers.hcm.lifecycle.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;

public interface OffboardingCaseRepository extends JpaRepository<OffboardingCase, UUID> {

    @Query(value = "SELECT nextval('lifecycle.offboarding_case_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Optional<OffboardingCase> findByResignationId(UUID resignationId);

    Optional<OffboardingCase> findByTerminationId(UUID terminationId);

    Page<OffboardingCase> findByCaseStatusOrderByCreatedAtDesc(OffboardingCaseStatus status, Pageable pageable);

    Page<OffboardingCase> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    Page<OffboardingCase> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCaseStatusIn(List<OffboardingCaseStatus> statuses);

    List<OffboardingCase> findByLastWorkingDateBetweenAndCaseStatusIn(
            LocalDate from, LocalDate to, List<OffboardingCaseStatus> statuses);

    /** Active = not yet completed/cancelled — used to guard duplicate case creation. */
    boolean existsByEmployeeIdAndCaseStatusNotIn(UUID employeeId, List<OffboardingCaseStatus> terminalStatuses);
}
