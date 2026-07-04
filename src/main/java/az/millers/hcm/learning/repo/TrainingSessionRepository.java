package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.TrainingSession;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    List<TrainingSession> findByTenantIdOrderByStartAtDesc(String tenantId);

    List<TrainingSession> findByTenantIdAndCourseIdOrderByStartAtDesc(String tenantId, UUID courseId);

    List<TrainingSession> findByTenantIdAndStatusOrderByStartAtAsc(String tenantId, String status);
}
