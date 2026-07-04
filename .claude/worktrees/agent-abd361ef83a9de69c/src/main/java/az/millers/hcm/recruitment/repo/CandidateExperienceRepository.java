package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.CandidateExperience;

public interface CandidateExperienceRepository extends JpaRepository<CandidateExperience, UUID> {

    List<CandidateExperience> findByCandidateIdOrderByOrdinalAscCreatedAtAsc(UUID candidateId);
}
