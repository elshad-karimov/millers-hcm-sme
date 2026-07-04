package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.SuccessionNomination;

public interface SuccessionNominationRepository extends JpaRepository<SuccessionNomination, UUID> {

    List<SuccessionNomination> findByPositionIdAndCancelledAtIsNullOrderByCreatedAtDesc(UUID positionId);

    List<SuccessionNomination> findByNomineeEmployeeIdAndCancelledAtIsNullOrderByCreatedAtDesc(UUID nomineeEmployeeId);

    Optional<SuccessionNomination> findByPositionIdAndNomineeEmployeeIdAndCancelledAtIsNull(
            UUID positionId, UUID nomineeEmployeeId);
}
