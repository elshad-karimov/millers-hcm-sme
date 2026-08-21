package az.millers.hcm.analytics.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.analytics.domain.AttritionRisk;
import az.millers.hcm.analytics.repo.AttritionRiskRepository;

/**
 * M476 — Attrition risk heuristic service.
 * Nightly sweep computes risk score for active employees.
 *
 * Heuristic weights (documented here as single source of truth):
 * - +30: tenure < 12 months
 * - +25: no salary change in last 24 months
 * - +25: low engagement (latest non-anonymous survey response is detractor or avg < 3)
 * - +20: org unit changed in last 6 months
 *
 * HR-only access, never shown to employees/managers.
 */
@Service
public class AttritionRiskService {

    private static final Logger log = LoggerFactory.getLogger(AttritionRiskService.class);

    // Heuristic weights
    private static final int WEIGHT_LOW_TENURE = 30;
    private static final int WEIGHT_NO_SALARY_CHANGE = 25;
    private static final int WEIGHT_LOW_ENGAGEMENT = 25;
    private static final int WEIGHT_ORG_CHANGE = 20;

    private final AttritionRiskRepository repository;
    private final NamedParameterJdbcTemplate jdbc;

    public AttritionRiskService(AttritionRiskRepository repository,
                               NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AttritionRisk> listAll() {
        return repository.findByTenantIdOrderByScoreDesc(TenantContext.current());
    }

    /**
     * Nightly sweep at 04:30 to recompute all attrition risk scores.
     */
    @Scheduled(cron = "0 30 4 * * ?")
    @Transactional
    public void nightlyRecompute() {
        log.info("Starting nightly attrition risk recompute for tenant {}", TenantContext.current());
        recomputeAll();
        log.info("Completed attrition risk recompute");
    }

    /**
     * Manual recompute trigger (HR_ADMIN endpoint).
     */
    @Transactional
    public void recomputeAll() {
        // Get all active employees
        List<UUID> activeEmployees = jdbc.query(
                "SELECT id FROM core_hr.employee WHERE tenant_id = :tenant AND employment_status = 'ACTIVE'",
                new MapSqlParameterSource("tenant", TenantContext.current()),
                (rs, i) -> UUID.fromString(rs.getString("id")));

        for (UUID employeeId : activeEmployees) {
            computeRisk(employeeId);
        }
    }

    @Transactional
    public AttritionRisk computeRisk(UUID employeeId) {
        int score = 0;
        List<String> factors = new ArrayList<>();

        // Factor 1: Tenure < 12 months (+30)
        LocalDate hireDate = jdbc.queryForObject(
                "SELECT hire_date FROM core_hr.employee WHERE id = :id",
                new MapSqlParameterSource("id", employeeId),
                (rs, i) -> rs.getObject("hire_date", LocalDate.class));

        if (hireDate != null) {
            long tenureMonths = ChronoUnit.MONTHS.between(hireDate, LocalDate.now());
            if (tenureMonths < 12) {
                score += WEIGHT_LOW_TENURE;
                factors.add("low_tenure(<12mo)");
            }
        }

        // Factor 2: No salary change in last 24 months (+25)
        OffsetDateTime twoYearsAgo = OffsetDateTime.now().minusMonths(24);
        Long salaryChanges = jdbc.queryForObject(
                // Table is salary_change_request, and the decision timestamp is
                // decided_at — there is no compensation.salary_change and no
                // approved_at, so this threw and took the whole risk
                // recompute down with it.
                "SELECT count(*) FROM compensation.salary_change_request " +
                "WHERE tenant_id = :tenant AND employee_id = :employeeId " +
                "AND decided_at > :since AND status = 'APPROVED'",
                new MapSqlParameterSource()
                        .addValue("tenant", TenantContext.current())
                        .addValue("employeeId", employeeId)
                        .addValue("since", twoYearsAgo),
                Long.class);

        if (salaryChanges != null && salaryChanges == 0) {
            score += WEIGHT_NO_SALARY_CHANGE;
            factors.add("no_salary_change(24mo)");
        }

        // Factor 3: Low engagement (+25) — CURRENTLY INERT, ON PURPOSE.
        //
        // Both queries below are wrong in every column they name: the rating
        // lives on survey_answer.rating_value, not survey_response.value;
        // question_id is on the answer, not the response; the campaign and
        // question join on template_id, not survey_template_id; and the
        // timestamp is submitted_at, not created_at. They have therefore never
        // returned anything — the catch swallows the exception at debug level,
        // so this factor has silently contributed zero to every risk score
        // since it was written.
        //
        // Not repaired here, because the last predicate cannot be repaired
        // mechanically: `c.anonymous = false` filters to responses the employee
        // knew were linkable, and there is no anonymity column on
        // survey_campaign — the schema does not model the distinction at all.
        // Dropping the filter would start scoring individuals on survey answers
        // they may have given believing them anonymous, which is a decision
        // about employee privacy, not a typo. Left inert until someone decides
        // whether campaigns carry an anonymity flag; inert is the safe state,
        // and is exactly what it already does today.
        //
        // Only use linkable (non-anonymous) responses
        try {
            Integer latestRating = jdbc.queryForObject(
                    "SELECT r.value::int FROM engagement.survey_response r " +
                    "JOIN engagement.survey_question q ON r.question_id = q.id " +
                    "JOIN engagement.survey_campaign c ON q.survey_template_id = c.survey_template_id " +
                    "WHERE r.employee_id = :employeeId AND c.anonymous = false " +
                    "AND q.question_type = 'RATING_1_10' " +
                    "ORDER BY r.created_at DESC LIMIT 1",
                    new MapSqlParameterSource("employeeId", employeeId),
                    Integer.class);

            if (latestRating != null && latestRating <= 6) {
                score += WEIGHT_LOW_ENGAGEMENT;
                factors.add("low_engagement(detractor)");
            }
        } catch (Exception e) {
            // No survey responses or schema doesn't match - skip this factor
            log.debug("Could not retrieve engagement data for employee {}: {}", employeeId, e.getMessage());
        }

        // Alternate engagement check: average rating < 3 from non-anonymous responses
        if (!factors.contains("low_engagement(detractor)")) {
            try {
                Double avgRating = jdbc.queryForObject(
                        "SELECT AVG(r.value::numeric) FROM engagement.survey_response r " +
                        "JOIN engagement.survey_question q ON r.question_id = q.id " +
                        "JOIN engagement.survey_campaign c ON q.survey_template_id = c.survey_template_id " +
                        "WHERE r.employee_id = :employeeId AND c.anonymous = false " +
                        "AND q.question_type IN ('RATING_1_5', 'RATING_1_10') " +
                        "AND r.created_at > CURRENT_DATE - INTERVAL '12 months'",
                        new MapSqlParameterSource("employeeId", employeeId),
                        Double.class);

                if (avgRating != null && avgRating < 3.0) {
                    score += WEIGHT_LOW_ENGAGEMENT;
                    factors.add("low_engagement(avg<3)");
                }
            } catch (Exception e) {
                log.debug("Could not compute average engagement for employee {}: {}", employeeId, e.getMessage());
            }
        }

        // Factor 4: Org unit changed in last 6 months (+20)
        OffsetDateTime sixMonthsAgo = OffsetDateTime.now().minusMonths(6);
        Long orgChanges = jdbc.queryForObject(
                // There is no core_hr.employee_history, and no field_name/
                // changed_at column anywhere: employment change is recorded as
                // effective-dated SLICES in employee_employment_history, one row
                // per state rather than one row per edited field. So an org-unit
                // change is a slice whose org_unit_id differs from the slice
                // before it, which is what the window function finds.
                "SELECT count(*) FROM (" +
                "  SELECT effective_from, org_unit_id, " +
                "         lag(org_unit_id) OVER (ORDER BY effective_from) AS prev_org_unit_id " +
                "  FROM core_hr.employee_employment_history " +
                "  WHERE tenant_id = :tenant AND employee_id = :employeeId" +
                ") s " +
                "WHERE s.effective_from > :since " +
                "  AND s.prev_org_unit_id IS NOT NULL " +
                "  AND s.org_unit_id IS DISTINCT FROM s.prev_org_unit_id",
                new MapSqlParameterSource()
                        .addValue("tenant", TenantContext.current())
                        .addValue("employeeId", employeeId)
                        .addValue("since", sixMonthsAgo),
                Long.class);

        if (orgChanges != null && orgChanges > 0) {
            score += WEIGHT_ORG_CHANGE;
            factors.add("org_change(6mo)");
        }

        // Save or update attrition risk
        AttritionRisk risk = repository.findByTenantIdAndEmployeeId(TenantContext.current(), employeeId)
                .orElse(new AttritionRisk());
        risk.setTenantId(TenantContext.current());
        risk.setEmployeeId(employeeId);
        risk.setScore(score);
        risk.setFactors(String.join(", ", factors));
        risk.setComputedAt(OffsetDateTime.now());

        return repository.save(risk);
    }
}
