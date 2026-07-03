package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.FeedbackNomination;

public interface FeedbackNominationRepository extends JpaRepository<FeedbackNomination, UUID> {

    List<FeedbackNomination> findByTenantIdAndCycleIdAndSubjectEmployeeIdOrderByCreatedAtAsc(
            String tenantId, UUID cycleId, UUID subjectEmployeeId);

    List<FeedbackNomination> findByTenantIdAndCycleIdOrderByCreatedAtAsc(String tenantId, UUID cycleId);

    List<FeedbackNomination> findByTenantIdAndReviewerEmployeeIdOrderByCreatedAtDesc(
            String tenantId, UUID reviewerEmployeeId);

    boolean existsByCycleIdAndSubjectEmployeeIdAndReviewerEmployeeId(
            UUID cycleId, UUID subjectEmployeeId, UUID reviewerEmployeeId);
}
