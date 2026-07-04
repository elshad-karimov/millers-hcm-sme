package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.TrainingFeedback;

public interface TrainingFeedbackRepository extends JpaRepository<TrainingFeedback, UUID> {

    List<TrainingFeedback> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<TrainingFeedback> findByTenantIdAndCourseIdOrderByCreatedAtDesc(String tenantId, UUID courseId);

    boolean existsBySessionIdAndEmployeeId(UUID sessionId, UUID employeeId);
}
