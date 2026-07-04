package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.DevelopmentAction;

public interface DevelopmentActionRepository extends JpaRepository<DevelopmentAction, UUID> {

    List<DevelopmentAction> findByPlanIdOrderByDueDateAscDescriptionAsc(UUID planId);

    void deleteByPlanId(UUID planId);
}
