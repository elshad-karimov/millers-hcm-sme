package az.millers.hcm.lifecycle.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.OffboardingSettlement;

public interface OffboardingSettlementRepository extends JpaRepository<OffboardingSettlement, UUID> {
    Optional<OffboardingSettlement> findByCaseId(UUID caseId);
}
