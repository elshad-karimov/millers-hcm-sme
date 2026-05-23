package az.millers.hcm.recruitment.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Candidate;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    @Query(value = "SELECT nextval('recruitment.candidate_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<Candidate> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String last, String first, String email, Pageable pageable);

    Page<Candidate> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
