package az.millers.hcm.engagement.service;
import az.millers.hcm.common.tenant.TenantContext;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.engagement.domain.EmployeeRecognition;
import az.millers.hcm.engagement.domain.RecognitionValueTag;
import az.millers.hcm.engagement.domain.RecognitionVisibility;
import az.millers.hcm.engagement.repo.EmployeeRecognitionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M483: Service anniversary auto-recognition.
 * Daily job finds employees whose hire anniversary is today (whole years >= 1)
 * and creates EmployeeRecognition entries (system sender, SERVICE_ANNIVERSARY tag, PUBLIC).
 */
@Service
public class ServiceAnniversaryService {

    private static final String MODULE = "engagement";
    private static final UUID SYSTEM_SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001"); // placeholder

    private final EmployeeRecognitionRepository recognitionRepo;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public ServiceAnniversaryService(EmployeeRecognitionRepository recognitionRepo,
                                    NamedParameterJdbcTemplate jdbc,
                                    AuditService audit) {
        this.recognitionRepo = recognitionRepo;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /**
     * Daily at 06:00 UTC: process service anniversaries.
     */
    @Scheduled(cron = "0 0 6 * * ?")
    @Transactional
    public void processAnniversaries() {
        // Check tenant setting toggle
        Boolean enabled = getTenantSetting("engagement_service_awards", true);
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        // Find employees whose hire_date day+month matches today and years_of_service >= 1
        String sql = """
            SELECT e.id, e.first_name, e.last_name, e.hire_date,
                   EXTRACT(YEAR FROM AGE(:today, e.hire_date)) AS years
            FROM core_hr.employee e
            WHERE e.tenant_id = :tenantId
              AND e.employment_status = 'ACTIVE'
              AND EXTRACT(MONTH FROM e.hire_date) = EXTRACT(MONTH FROM :today::date)
              AND EXTRACT(DAY FROM e.hire_date) = EXTRACT(DAY FROM :today::date)
              AND EXTRACT(YEAR FROM AGE(:today, e.hire_date)) >= 1
            """;

        List<Map<String, Object>> anniversaries = jdbc.queryForList(sql,
            new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("tenantId", TenantContext.current()));

        for (Map<String, Object> emp : anniversaries) {
            UUID employeeId = (UUID) emp.get("id");
            int years = ((Number) emp.get("years")).intValue();
            String name = emp.get("first_name") + " " + emp.get("last_name");

            // Idempotent check: has recognition already been created for this employee+year?
            if (hasAnniversaryRecognition(employeeId, years, today.getYear())) {
                continue;
            }

            // Create recognition
            EmployeeRecognition recognition = new EmployeeRecognition();
            recognition.setTenantId(TenantContext.current());
            recognition.setFromEmployeeId(SYSTEM_SENDER_ID); // System sender
            recognition.setToEmployeeId(employeeId);
            recognition.setValueTag(RecognitionValueTag.SERVICE_ANNIVERSARY);
            recognition.setMessage(String.format("Congratulations on %d %s of service!",
                years, years == 1 ? "year" : "years"));
            recognition.setVisibility(RecognitionVisibility.PUBLIC);

            recognitionRepo.save(recognition);

            audit.record(MODULE, "ServiceAnniversary", employeeId.toString(), "CREATE", null,
                Map.of("years", years, "name", name, "date", today.toString()));
        }
    }

    /**
     * Idempotent check: has a SERVICE_ANNIVERSARY recognition been created for this
     * employee in this calendar year?
     * (Prevents duplicates if the job runs multiple times on the same day.)
     */
    private boolean hasAnniversaryRecognition(UUID employeeId, int years, int currentYear) {
        String checkSql = """
            SELECT COUNT(*) FROM engagement.recognition
            WHERE tenant_id = :tenantId
              AND to_employee_id = :employeeId
              AND value_tag = 'SERVICE_ANNIVERSARY'
              AND EXTRACT(YEAR FROM created_at) = :year
            """;

        Long count = jdbc.queryForObject(checkSql,
            new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.current())
                .addValue("employeeId", employeeId)
                .addValue("year", currentYear),
            Long.class);

        return count != null && count > 0;
    }

    /**
     * Read tenant setting with default fallback.
     */
    private Boolean getTenantSetting(String key, Boolean defaultValue) {
        try {
            String value = jdbc.queryForObject(
                "SELECT value FROM config.tenant_setting WHERE tenant_id = :tenantId AND key = :key",
                new MapSqlParameterSource()
                    .addValue("tenantId", TenantContext.current())
                    .addValue("key", key),
                String.class
            );
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
