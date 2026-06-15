package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.CandidateEducation;

public interface CandidateEducationRepository extends JpaRepository<CandidateEducation, UUID> {

    List<CandidateEducation> findByCandidateIdOrderByOrdinalAscCreatedAtAsc(UUID candidateId);
}
