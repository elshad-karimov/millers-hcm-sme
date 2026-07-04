package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.PipCheckpoint;

public interface PipCheckpointRepository extends JpaRepository<PipCheckpoint, UUID> {

    List<PipCheckpoint> findByPipIdOrderByCheckpointDateDesc(UUID pipId);
}
