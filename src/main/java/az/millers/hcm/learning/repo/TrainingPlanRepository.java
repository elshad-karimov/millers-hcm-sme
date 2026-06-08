package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.learning.domain.TrainingPlan;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, UUID> {

    List<TrainingPlan> findAllByOrderByCreatedAtDesc();

    List<TrainingPlan> findByStatusOrderByCreatedAtDesc(String status);

    List<TrainingPlan> findByOrgUnitIdOrderByCreatedAtDesc(UUID orgUnitId);

    Optional<TrainingPlan> findByPlanNo(String planNo);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(t.planNo, 4) AS int)), 0) FROM TrainingPlan t")
    int findMaxSeq();
}
