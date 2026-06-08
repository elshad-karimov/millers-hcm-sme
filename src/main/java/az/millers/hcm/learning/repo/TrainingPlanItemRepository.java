package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.TrainingPlanItem;

public interface TrainingPlanItemRepository extends JpaRepository<TrainingPlanItem, UUID> {

    List<TrainingPlanItem> findByPlan_IdOrderBySortOrderAsc(UUID planId);

    void deleteByPlan_IdAndCourseId(UUID planId, UUID courseId);
}
