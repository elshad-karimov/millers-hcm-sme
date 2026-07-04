package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.PositionReplacement;
import az.millers.hcm.staffing.domain.ReplacementStatus;

public interface PositionReplacementRepository extends JpaRepository<PositionReplacement, UUID> {

    List<PositionReplacement> findByPositionIdOrderByCreatedAtDesc(UUID positionId);
    List<PositionReplacement> findByLeavingEmployeeIdOrderByCreatedAtDesc(UUID leavingEmployeeId);
    List<PositionReplacement> findByStatusOrderByCreatedAtDesc(ReplacementStatus status);
}
