package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.GoalProgressUpdate;

public interface GoalProgressUpdateRepository extends JpaRepository<GoalProgressUpdate, UUID> {

    List<GoalProgressUpdate> findByGoalIdOrderByRecordedAtDesc(UUID goalId);
}
