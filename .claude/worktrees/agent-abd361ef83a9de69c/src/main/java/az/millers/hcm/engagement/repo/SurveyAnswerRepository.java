package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.engagement.domain.SurveyAnswer;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, UUID> {

    List<SurveyAnswer> findByResponseId(UUID responseId);

    /**
     * All answers across all responses of a campaign, joined to the
     * question for type-aware aggregation. One query, no N+1 walk.
     */
    @Query("""
            select a from SurveyAnswer a
            where a.responseId in (
                select r.id from SurveyResponse r where r.campaignId = :campaignId
            )
            """)
    List<SurveyAnswer> findByCampaignId(@Param("campaignId") UUID campaignId);
}
