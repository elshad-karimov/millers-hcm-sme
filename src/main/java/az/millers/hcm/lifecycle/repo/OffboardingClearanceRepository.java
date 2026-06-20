package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ClearanceDepartment;
import az.millers.hcm.lifecycle.domain.ClearanceStatus;
import az.millers.hcm.lifecycle.domain.OffboardingClearance;

public interface OffboardingClearanceRepository extends JpaRepository<OffboardingClearance, UUID> {

    List<OffboardingClearance> findByCaseIdOrderByDepartment(UUID caseId);

    Optional<OffboardingClearance> findByCaseIdAndDepartment(UUID caseId, ClearanceDepartment department);

    boolean existsByCaseIdAndStatusNotIn(UUID caseId, List<ClearanceStatus> resolvedStatuses);
}
