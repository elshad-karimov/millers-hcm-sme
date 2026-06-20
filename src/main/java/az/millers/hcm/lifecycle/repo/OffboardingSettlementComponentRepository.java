package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.OffboardingSettlementComponent;

public interface OffboardingSettlementComponentRepository extends JpaRepository<OffboardingSettlementComponent, UUID> {
    List<OffboardingSettlementComponent> findBySettlementIdOrderByIsDeductionAscCreatedAt(UUID settlementId);
}
