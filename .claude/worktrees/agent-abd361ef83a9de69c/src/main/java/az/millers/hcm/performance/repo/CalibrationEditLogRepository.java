package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.CalibrationEditLog;

public interface CalibrationEditLogRepository extends JpaRepository<CalibrationEditLog, UUID> {

    List<CalibrationEditLog> findBySessionIdOrderByEditedAtDesc(UUID sessionId);

    List<CalibrationEditLog> findByReviewIdOrderByEditedAtDesc(UUID reviewId);
}
