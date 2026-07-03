package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.FeedbackQuestion;

public interface FeedbackQuestionRepository extends JpaRepository<FeedbackQuestion, UUID> {

    List<FeedbackQuestion> findByQuestionnaireIdOrderByQuestionOrderAsc(UUID questionnaireId);

    void deleteByQuestionnaireId(UUID questionnaireId);
}
