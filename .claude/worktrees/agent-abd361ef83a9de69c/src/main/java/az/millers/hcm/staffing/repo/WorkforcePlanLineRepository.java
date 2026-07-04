package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.WorkforcePlanLine;

public interface WorkforcePlanLineRepository extends JpaRepository<WorkforcePlanLine, UUID> {

    List<WorkforcePlanLine> findByWorkforcePlanIdOrderByLineNoAsc(UUID workforcePlanId);
    void deleteByWorkforcePlanId(UUID workforcePlanId);
}
