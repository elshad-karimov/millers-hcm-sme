package az.millers.hcm.employeerelations.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.employeerelations.domain.ErEvidence;

/**
 * M445 — ER evidence repository.
 */
public interface ErEvidenceRepository extends JpaRepository<ErEvidence, UUID> {

    List<ErEvidence> findByInvestigationIdOrderByUploadedAtAsc(UUID investigationId);
}
