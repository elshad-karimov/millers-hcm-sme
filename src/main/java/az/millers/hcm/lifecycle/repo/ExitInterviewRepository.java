package az.millers.hcm.lifecycle.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ExitInterview;

public interface ExitInterviewRepository extends JpaRepository<ExitInterview, UUID> {

    Optional<ExitInterview> findByTerminationId(UUID terminationId);

    Optional<ExitInterview> findByCaseId(UUID caseId);
}
