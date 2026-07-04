package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.TalentReview;

public interface TalentReviewRepository extends JpaRepository<TalentReview, UUID> {

    List<TalentReview> findByCycleIdOrderByDecidedAtDesc(UUID cycleId);

    Optional<TalentReview> findByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    List<TalentReview> findByCycleIdAndHipoDecisionTrue(UUID cycleId);

    List<TalentReview> findByCycleIdAndRetentionRiskOrderByDecidedAtDesc(UUID cycleId, String risk);
}
