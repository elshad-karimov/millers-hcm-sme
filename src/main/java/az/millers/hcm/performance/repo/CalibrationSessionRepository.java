package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.CalibrationSession;

public interface CalibrationSessionRepository extends JpaRepository<CalibrationSession, UUID> {

    List<CalibrationSession> findByCycleIdOrderByScheduledAtDesc(UUID cycleId);
}
