package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.FeedbackQuestionnaire;

public interface FeedbackQuestionnaireRepository extends JpaRepository<FeedbackQuestionnaire, UUID> {

    List<FeedbackQuestionnaire> findByTenantIdOrderByNameAsc(String tenantId);

    List<FeedbackQuestionnaire> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
