package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.CandidateNote;

public interface CandidateNoteRepository extends JpaRepository<CandidateNote, UUID> {

    List<CandidateNote> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
}
