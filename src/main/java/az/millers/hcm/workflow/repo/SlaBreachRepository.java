package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.workflow.domain.SlaBreach;

public interface SlaBreachRepository extends JpaRepository<SlaBreach, UUID> {

    /** Idempotency probe: did the scheduler already record this step's breach? */
    Optional<SlaBreach> findByInstanceIdAndStepIndex(UUID instanceId, int stepIndex);

    List<SlaBreach> findByInstanceIdOrderByBreachedAtDesc(UUID instanceId);

    /** HR dashboard — newest first. */
    List<SlaBreach> findAllByOrderByBreachedAtDesc();
}
