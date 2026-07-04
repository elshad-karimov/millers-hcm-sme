package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    @Query(value = "SELECT nextval('recruitment.application_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<Application> findByVacancyIdOrderByCreatedAtAsc(UUID vacancyId);

    List<Application> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

    long countByVacancyIdAndStatus(UUID vacancyId, ApplicationStatus status);

    /** M280 — PRD §70: one application per candidate per requisition. */
    boolean existsByVacancyIdAndCandidateId(UUID vacancyId, UUID candidateId);

    /** M282 — anonymous tracking lookup; the token is the credential. */
    java.util.Optional<Application> findByTrackingToken(String trackingToken);

    /** M288 — SLA evaluation walks the in-flight applications. */
    List<Application> findByStatus(ApplicationStatus status);
}
