package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.TrainingRoom;

public interface TrainingRoomRepository extends JpaRepository<TrainingRoom, UUID> {

    List<TrainingRoom> findByTenantIdOrderByCodeAsc(String tenantId);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
