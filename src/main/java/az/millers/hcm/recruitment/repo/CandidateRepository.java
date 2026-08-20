package az.millers.hcm.recruitment.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;

public interface CandidateRepository
        extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    @Query(value = "SELECT config.next_tenant_seq('recruitment.candidate_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<Candidate> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String last, String first, String email, Pageable pageable);

    Page<Candidate> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * M280 — duplicate detection for public portal applications (PRD
     * §12: "one candidate, many applications"). Oldest row wins when
     * historical data already has duplicates.
     */
    java.util.Optional<Candidate> findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(String email);

    /** Look up candidates by id batch — used to hydrate the tag-only path. */
    List<Candidate> findByIdIn(Collection<UUID> ids);

    // ── M293 — duplicate detection + merge ─────────────────────────────

    /** All live (non-merged) candidates. */
    List<Candidate> findByMergedIntoIdIsNull();

    /**
     * Live candidates that still hold PII — input to the duplicate scan
     * (M293) and the retention sweep (M294).
     */
    List<Candidate> findByMergedIntoIdIsNullAndAnonymizedAtIsNull();

    /** Default candidate list, excluding merged-away records. */
    Page<Candidate> findByMergedIntoIdIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** Free-text candidate search, excluding merged-away records. */
    @Query("""
            select c from Candidate c
            where c.mergedIntoId is null
              and (
                lower(c.firstName) like lower(concat('%', :q, '%'))
                or lower(c.lastName) like lower(concat('%', :q, '%'))
                or lower(coalesce(c.email, '')) like lower(concat('%', :q, '%'))
              )
            """)
    Page<Candidate> searchActive(String q, Pageable pageable);
}
