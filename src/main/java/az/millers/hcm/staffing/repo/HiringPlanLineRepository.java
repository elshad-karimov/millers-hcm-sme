package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.HiringPlanLine;

public interface HiringPlanLineRepository extends JpaRepository<HiringPlanLine, UUID> {

    List<HiringPlanLine> findByTenantIdAndWorkforcePlanIdOrderByTargetStartDateAsc(String tenantId, UUID workforcePlanId);
}
