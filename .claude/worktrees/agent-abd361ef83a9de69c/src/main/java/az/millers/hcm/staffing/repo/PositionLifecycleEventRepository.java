package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.PositionLifecycleEvent;

/** M243 — read history for a position, newest first. */
public interface PositionLifecycleEventRepository
        extends JpaRepository<PositionLifecycleEvent, UUID> {

    List<PositionLifecycleEvent> findByPositionIdOrderByOccurredAtDesc(UUID positionId);
}
