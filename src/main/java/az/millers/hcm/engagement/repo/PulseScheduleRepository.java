package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import az.millers.hcm.engagement.domain.PulseSchedule;

/**
 * M477 — Pulse schedule repository.
 */
public interface PulseScheduleRepository extends JpaRepository<PulseSchedule, UUID> {

    List<PulseSchedule> findByTenantIdAndActiveOrderByCreatedAtDesc(String tenantId, Boolean active);
}
