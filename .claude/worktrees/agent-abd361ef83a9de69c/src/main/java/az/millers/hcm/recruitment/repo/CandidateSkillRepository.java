package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.CandidateSkill;

public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, UUID> {

    List<CandidateSkill> findByCandidateIdOrderByOrdinalAscCreatedAtAsc(UUID candidateId);

    Optional<CandidateSkill> findByCandidateIdAndNameIgnoreCase(UUID candidateId, String name);
}
