package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.engagement.domain.SurveyResponse;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {

    List<SurveyResponse> findByCampaignIdOrderBySubmittedAtDesc(UUID campaignId);

    Optional<SurveyResponse> findByCampaignIdAndEmployeeId(UUID campaignId, UUID employeeId);

    long countByCampaignId(UUID campaignId);
}
