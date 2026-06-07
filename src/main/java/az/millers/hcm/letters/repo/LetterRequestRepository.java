package az.millers.hcm.letters.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.letters.domain.LetterRequest;
import az.millers.hcm.letters.domain.LetterStatus;

public interface LetterRequestRepository extends JpaRepository<LetterRequest, UUID> {

    @Query(value = "SELECT nextval('hr_letters.request_no_seq')", nativeQuery = true)
    long nextRequestNoSequence();

    boolean existsByRequestNo(String requestNo);

    /** My requests — used by self-service. */
    List<LetterRequest> findByEmployeeIdOrderByRequestedAtDesc(UUID employeeId);

    /** HR queue — every request filtered to a status. */
    Page<LetterRequest> findByStatusOrderByRequestedAtDesc(
            LetterStatus status, Pageable pageable);

    /** Scope-restricted list — managers / scoped HR specialists. */
    Page<LetterRequest> findByEmployeeIdInOrderByRequestedAtDesc(
            Collection<UUID> employeeIds, Pageable pageable);

    Page<LetterRequest> findByEmployeeIdInAndStatusOrderByRequestedAtDesc(
            Collection<UUID> employeeIds, LetterStatus status, Pageable pageable);

    /** Full list for HR — unscoped. */
    Page<LetterRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    /** M139 — public verification endpoint resolves the letter by token. */
    java.util.Optional<LetterRequest> findByVerificationToken(String token);
}
