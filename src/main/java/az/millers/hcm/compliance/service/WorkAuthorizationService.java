package az.millers.hcm.compliance.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M471 — Work authorization (visa/work permit) expiry tracking.
 */
@Service
public class WorkAuthorizationService {

    private final NamedParameterJdbcTemplate jdbc;

    public WorkAuthorizationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Get employees with work authorization expiring within the next X days.
     */
    @Transactional(readOnly = true)
    public List<ExpiringWorkAuth> getExpiring(int daysAhead) {
        String tenantId = "default";
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(daysAhead);

        String sql = """
            SELECT
              e.id,
              e.employee_no,
              e.first_name,
              e.last_name,
              e.work_authorized_until,
              CAST(e.work_authorized_until - CURRENT_DATE AS INTEGER) as days_until_expiry
            FROM core_hr.employee e
            WHERE e.tenant_id = :tenantId
              AND e.work_authorized_until IS NOT NULL
              AND e.work_authorized_until <= :horizon
              AND e.employment_status IN ('ACTIVE', 'PROBATION')
            ORDER BY e.work_authorized_until ASC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("horizon", horizon);

        return jdbc.query(sql, params, (rs, rowNum) ->
            new ExpiringWorkAuth(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("employee_no"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getDate("work_authorized_until").toLocalDate(),
                rs.getInt("days_until_expiry")
            )
        );
    }

    public record ExpiringWorkAuth(
            java.util.UUID id,
            String employeeNo,
            String firstName,
            String lastName,
            LocalDate expiryDate,
            int daysUntilExpiry
    ) {}
}
