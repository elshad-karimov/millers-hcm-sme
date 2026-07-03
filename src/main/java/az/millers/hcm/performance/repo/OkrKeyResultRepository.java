package az.millers.hcm.performance.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.OkrKeyResult;

public interface OkrKeyResultRepository extends JpaRepository<OkrKeyResult, UUID> {

    List<OkrKeyResult> findByObjectiveIdOrderByCreatedAtAsc(UUID objectiveId);

    List<OkrKeyResult> findByObjectiveIdInOrderByCreatedAtAsc(Collection<UUID> objectiveIds);
}
