package az.millers.hcm.employeerelations.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.employeerelations.domain.ErInvestigationInterview;

/**
 * M445 — ER investigation interview repository.
 */
public interface ErInvestigationInterviewRepository extends JpaRepository<ErInvestigationInterview, UUID> {

    List<ErInvestigationInterview> findByInvestigationIdOrderByInterviewDateAsc(UUID investigationId);
}
