package az.millers.hcm.employeerelations.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.employeerelations.domain.ErInvestigation;

/**
 * M445 — ER investigation repository.
 */
public interface ErInvestigationRepository extends JpaRepository<ErInvestigation, UUID> {

    Optional<ErInvestigation> findByIdAndTenantId(UUID id, String tenantId);

    List<ErInvestigation> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
