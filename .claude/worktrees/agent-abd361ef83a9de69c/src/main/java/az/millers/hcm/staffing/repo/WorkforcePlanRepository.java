package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.WorkforcePlan;
import az.millers.hcm.staffing.domain.WorkforcePlanStatus;

public interface WorkforcePlanRepository extends JpaRepository<WorkforcePlan, UUID> {

    List<WorkforcePlan> findByLegalEntityIdOrderByEffectiveFromDesc(UUID legalEntityId);
    List<WorkforcePlan> findByStatusOrderByCreatedAtDesc(WorkforcePlanStatus status);
    Optional<WorkforcePlan> findByLegalEntityIdAndVersionCode(UUID legalEntityId, String versionCode);
}
