package az.millers.hcm.employeerelations.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.employeerelations.domain.ErCaseNote;

/**
 * M445 — ER case note repository.
 */
public interface ErCaseNoteRepository extends JpaRepository<ErCaseNote, UUID> {

    List<ErCaseNote> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
