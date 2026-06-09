package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.FundingStatus;
import az.millers.hcm.staffing.domain.PositionFunding;

/** M244 — singleton funding row per position. */
public interface PositionFundingRepository extends JpaRepository<PositionFunding, UUID> {

    List<PositionFunding> findByStatus(FundingStatus status);
}
