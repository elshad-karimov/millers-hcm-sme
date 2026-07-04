package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.PositionTransfer;
import az.millers.hcm.staffing.domain.TransferStatus;

/** M260 — position transfer workflow repo (PRD §40). */
public interface PositionTransferRepository extends JpaRepository<PositionTransfer, UUID> {

    /** All transfers on a position — most recent first, for the panel timeline. */
    List<PositionTransfer> findByPositionIdOrderByCreatedAtDesc(UUID positionId);

    /** In-flight transfers on a position (gate against double-submit). */
    List<PositionTransfer> findByPositionIdAndStatusIn(UUID positionId, List<TransferStatus> statuses);
}
