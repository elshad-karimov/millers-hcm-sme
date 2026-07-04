package az.millers.hcm.recruitment.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.AgencySubmission;
import az.millers.hcm.recruitment.domain.SubmissionStatus;

public interface AgencySubmissionRepository extends JpaRepository<AgencySubmission, UUID> {

    @Query(value = "SELECT nextval('recruitment.agency_submission_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<AgencySubmission> findAllByOrderByCreatedAtDesc();

    List<AgencySubmission> findByStatusOrderByCreatedAtDesc(SubmissionStatus status);

    /** The candidate's current ACTIVE submission (ownership holder), if any. */
    Optional<AgencySubmission> findFirstByCandidateIdAndStatusOrderBySubmittedAtDesc(
            UUID candidateId, SubmissionStatus status);

    /** ACTIVE submissions whose ownership window has lapsed — to expire. */
    List<AgencySubmission> findByStatusAndOwnershipUntilLessThan(
            SubmissionStatus status, OffsetDateTime asOf);
}
