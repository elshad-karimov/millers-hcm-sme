package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.CandidateDocument;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, UUID> {

    List<CandidateDocument> findByCandidateIdOrderByCreatedAtAsc(UUID candidateId);

    boolean existsByCandidateIdAndDocumentTypeId(UUID candidateId, UUID documentTypeId);
}
