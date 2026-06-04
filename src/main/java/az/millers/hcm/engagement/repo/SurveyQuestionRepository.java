package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.engagement.domain.SurveyQuestion;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, UUID> {

    List<SurveyQuestion> findByTemplateIdOrderByOrderIndexAsc(UUID templateId);

    @Modifying
    @Query("delete from SurveyQuestion q where q.templateId = :templateId")
    void deleteAllByTemplateId(UUID templateId);

    long countByTemplateId(UUID templateId);
}
