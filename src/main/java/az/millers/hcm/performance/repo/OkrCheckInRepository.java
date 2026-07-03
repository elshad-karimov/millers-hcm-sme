package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.OkrCheckIn;

public interface OkrCheckInRepository extends JpaRepository<OkrCheckIn, UUID> {

    List<OkrCheckIn> findByObjectiveIdOrderByRecordedAtDesc(UUID objectiveId);
}
