package az.millers.hcm.recruitment.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    @Query(value = "SELECT nextval('recruitment.candidate_no_seq')", nativeQuery = true)
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

    /**
     * M87 — talent-pool search. Filters by pool status and (optionally) the
     * id set produced by tag intersection. Free-text matches across name +
     * email + skills column.
     */
    @Query("""
            select c from Candidate c
            where (:status is null or c.poolStatus = :status)
              and (:ids is null or c.id in :ids)
              and (
                :q is null
                or lower(c.firstName) like lower(concat('%', :q, '%'))
                or lower(c.lastName)  like lower(concat('%', :q, '%'))
                or lower(c.email)     like lower(concat('%', :q, '%'))
                or lower(coalesce(c.skills, '')) like lower(concat('%', :q, '%'))
              )
            """)
    Page<Candidate> searchPool(CandidatePoolStatus status,
                                 Collection<UUID> ids,
                                 String q,
                                 Pageable pageable);

    /** Look up candidates by id batch — used to hydrate the tag-only path. */
    List<Candidate> findByIdIn(Collection<UUID> ids);
}
