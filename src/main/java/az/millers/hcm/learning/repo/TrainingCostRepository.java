package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.TrainingCost;

public interface TrainingCostRepository extends JpaRepository<TrainingCost, UUID> {

    List<TrainingCost> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<TrainingCost> findByTenantIdAndCourseIdOrderByCreatedAtDesc(String tenantId, UUID courseId);

    List<TrainingCost> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
