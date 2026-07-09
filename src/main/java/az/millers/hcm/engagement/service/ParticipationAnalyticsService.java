package az.millers.hcm.engagement.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.engagement.domain.SurveyCampaign;
import az.millers.hcm.engagement.domain.SurveyTemplate;
import az.millers.hcm.engagement.repo.SurveyCampaignRepository;
import az.millers.hcm.engagement.repo.SurveyTemplateRepository;

/**
 * M480 — Participation analytics with anonymity guard.
 * Response rate by department/location; sentiment analysis on open text.
 * ENFORCES min-response threshold (default 5) before ANY group breakdown.
 */
@Service
public class ParticipationAnalyticsService {

    private static final String TENANT = "default";
    private static final String SETTING_KEY_MIN_RESPONSES = "engagement_min_group_responses";
    private static final int DEFAULT_MIN_RESPONSES = 5;

    // Simple sentiment keyword lists (extensible)
    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "great", "excellent", "good", "happy", "satisfied", "love", "fantastic",
            "amazing", "wonderful", "positive", "helpful", "thank", "appreciate");

    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "bad", "poor", "terrible", "unhappy", "disappointed", "frustrated",
            "issue", "problem", "concern", "difficult", "slow", "never", "hate");

    private final NamedParameterJdbcTemplate jdbc;
    private final SettingService settingService;
    private final SurveyCampaignRepository campaignRepo;
    private final SurveyTemplateRepository templateRepo;

    public ParticipationAnalyticsService(NamedParameterJdbcTemplate jdbc,
                                        SettingService settingService,
                                        SurveyCampaignRepository campaignRepo,
                                        SurveyTemplateRepository templateRepo) {
        this.jdbc = jdbc;
        this.settingService = settingService;
        this.campaignRepo = campaignRepo;
        this.templateRepo = templateRepo;
    }

    /**
     * Participation analytics for a campaign.
     * Returns overall rate + department breakdown (with anonymity threshold).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> participation(UUID campaignId) {
        int minResponses = Integer.parseInt(
                settingService.get(SETTING_KEY_MIN_RESPONSES, String.valueOf(DEFAULT_MIN_RESPONSES)));

        // Overall participation
        Long totalInvited = jdbc.queryForObject(
                "SELECT count(DISTINCT employee_id) FROM core_hr.employee " +
                "WHERE tenant_id = :tenant AND employment_status = 'ACTIVE'",
                new MapSqlParameterSource("tenant", TENANT),
                Long.class);

        Long totalResponded = jdbc.queryForObject(
                "SELECT count(DISTINCT employee_id) FROM engagement.survey_response " +
                "WHERE campaign_id = :campaignId",
                new MapSqlParameterSource("campaignId", campaignId),
                Long.class);

        double overallRate = totalInvited == null || totalInvited == 0 ? 0.0 :
                (totalResponded == null ? 0.0 : (double) totalResponded / totalInvited * 100);

        // Department breakdown (with anonymity guard)
        List<Map<String, Object>> byDepartment = jdbc.query(
                "SELECT e.department_name, " +
                "  count(DISTINCT e.id) AS invited, " +
                "  count(DISTINCT r.employee_id) AS responded " +
                "FROM core_hr.employee e " +
                "LEFT JOIN engagement.survey_response r ON r.employee_id = e.id AND r.campaign_id = :campaignId " +
                "WHERE e.tenant_id = :tenant AND e.employment_status = 'ACTIVE' " +
                "GROUP BY e.department_name",
                new MapSqlParameterSource()
                        .addValue("tenant", TENANT)
                        .addValue("campaignId", campaignId),
                (rs, i) -> {
                    String dept = rs.getString("department_name");
                    long invited = rs.getLong("invited");
                    long responded = rs.getLong("responded");

                    // Anonymity threshold: suppress if responded < minResponses
                    if (responded < minResponses) {
                        return Map.of(
                                "department", dept != null ? dept : "— unassigned",
                                "suppressed", true);
                    }

                    double rate = invited == 0 ? 0.0 : (double) responded / invited * 100;
                    return Map.of(
                            "department", dept != null ? dept : "— unassigned",
                            "invited", invited,
                            "responded", responded,
                            "rate", rate,
                            "suppressed", false);
                });

        return Map.of(
                "overall", Map.of(
                        "invited", totalInvited != null ? totalInvited : 0,
                        "responded", totalResponded != null ? totalResponded : 0,
                        "rate", overallRate),
                "byDepartment", byDepartment,
                "minResponsesThreshold", minResponses);
    }

    /**
     * Simple sentiment analysis on open-text answers of a campaign.
     * Returns aggregate counts ONLY (never per-response with identity).
     * <p>
     * PRIVACY GUARD: refuses sentiment for anonymous campaigns — even though
     * the method only returns aggregate counts, we enforce a hard boundary:
     * anonymous campaigns never surface any text analysis at all.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> sentiment(UUID campaignId) {
        // Block sentiment for anonymous campaigns
        SurveyCampaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new BadRequestException("Campaign not found"));
        SurveyTemplate template = templateRepo.findById(campaign.getTemplateId())
                .orElseThrow(() -> new BadRequestException("Campaign template not found"));

        if (template.isAnonymous()) {
            throw new BadRequestException("Sentiment analysis is not available for anonymous campaigns");
        }

        // Get all open-text responses for this campaign
        List<String> openTextAnswers = jdbc.query(
                "SELECT a.answer_text FROM engagement.survey_answer a " +
                "JOIN engagement.survey_response r ON a.response_id = r.id " +
                "JOIN engagement.survey_question q ON a.question_id = q.id " +
                "WHERE r.campaign_id = :campaignId " +
                "AND q.question_type IN ('TEXT_LONG', 'TEXT_SHORT') " +
                "AND a.answer_text IS NOT NULL AND a.answer_text <> ''",
                new MapSqlParameterSource("campaignId", campaignId),
                (rs, i) -> rs.getString("answer_text"));

        int positive = 0;
        int negative = 0;
        int neutral = 0;

        for (String text : openTextAnswers) {
            String lowerText = text.toLowerCase();
            boolean hasPositive = POSITIVE_KEYWORDS.stream().anyMatch(lowerText::contains);
            boolean hasNegative = NEGATIVE_KEYWORDS.stream().anyMatch(lowerText::contains);

            if (hasPositive && !hasNegative) {
                positive++;
            } else if (hasNegative && !hasPositive) {
                negative++;
            } else {
                neutral++;
            }
        }

        return Map.of(
                "total", openTextAnswers.size(),
                "positive", positive,
                "neutral", neutral,
                "negative", negative);
    }
}
