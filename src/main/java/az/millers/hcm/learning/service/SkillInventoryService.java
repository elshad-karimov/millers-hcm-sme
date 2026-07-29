package az.millers.hcm.learning.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.learning.api.dto.SkillInventoryReportDto;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * M420: Skill inventory reports — by department, critical skills, certifications.
 */
@Service
public class SkillInventoryService {


    private final NamedParameterJdbcTemplate jdbc;
    private final AccessScopeService accessScope;

    public SkillInventoryService(NamedParameterJdbcTemplate jdbc,
                                  AccessScopeService accessScope) {
        this.jdbc = jdbc;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public List<SkillInventoryReportDto.ByDepartmentRow> inventoryByDepartment() {
        Optional<Set<UUID>> scope = accessScope.scopeForCurrentUser();

        String sql = """
            SELECT
                ou.name AS department,
                c.name AS competency_name,
                COUNT(DISTINCT ec.employee_id) AS employee_count,
                AVG(ec.proficiency) AS avg_level
            FROM learning.employee_competency ec
            JOIN learning.competency c ON c.id = ec.competency_id
            JOIN core_hr.employee e ON e.id = ec.employee_id
            LEFT JOIN organization.org_unit ou ON ou.id = e.department_id
            WHERE e.tenant_id = :tenantId
            """ + (scope.isPresent() && !scope.get().isEmpty()
                    ? "AND e.id IN (:scopeIds)" : "") + """
            GROUP BY ou.name, c.name
            ORDER BY ou.name, competency_name
            """;

        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", TenantContext.current());
        if (scope.isPresent() && !scope.get().isEmpty()) {
            params.addValue("scopeIds", scope.get().stream().map(UUID::toString).toList());
        }

        return jdbc.query(sql, params, (rs, i) -> new SkillInventoryReportDto.ByDepartmentRow(
                rs.getString("department"),
                rs.getString("competency_name"),
                rs.getInt("employee_count"),
                rs.getDouble("avg_level")
        ));
    }

    @Transactional(readOnly = true)
    public List<SkillInventoryReportDto.CriticalSkillRow> criticalSkillsCoverage() {
        Optional<Set<UUID>> scope = accessScope.scopeForCurrentUser();

        String sql = """
            WITH required_skills AS (
                SELECT DISTINCT
                    pr.competency_id,
                    pr.required_proficiency AS required_level,
                    c.name AS competency_name
                FROM learning.position_competency_requirement pr
                JOIN learning.competency c ON c.id = pr.competency_id
                WHERE pr.mandatory = true
            ),
            current_coverage AS (
                SELECT
                    rs.competency_id,
                    COUNT(DISTINCT ec.employee_id) AS covered_count
                FROM required_skills rs
                LEFT JOIN learning.employee_competency ec
                    ON ec.competency_id = rs.competency_id
                    AND ec.proficiency >= rs.required_level
                JOIN core_hr.employee e ON e.id = ec.employee_id
                WHERE e.tenant_id = :tenantId
                """ + (scope.isPresent() && !scope.get().isEmpty()
                        ? "AND e.id IN (:scopeIds)" : "") + """
                GROUP BY rs.competency_id
            )
            SELECT
                rs.competency_name,
                rs.required_level,
                COALESCE(cc.covered_count, 0) AS covered_employees
            FROM required_skills rs
            LEFT JOIN current_coverage cc ON cc.competency_id = rs.competency_id
            ORDER BY rs.competency_name
            """;

        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", TenantContext.current());
        if (scope.isPresent() && !scope.get().isEmpty()) {
            params.addValue("scopeIds", scope.get().stream().map(UUID::toString).toList());
        }

        return jdbc.query(sql, params, (rs, i) -> new SkillInventoryReportDto.CriticalSkillRow(
                rs.getString("competency_name"),
                rs.getInt("required_level"),
                rs.getInt("covered_employees")
        ));
    }

    @Transactional(readOnly = true)
    public List<SkillInventoryReportDto.CertificationRow> certificationCoverage() {
        Optional<Set<UUID>> scope = accessScope.scopeForCurrentUser();
        LocalDate today = LocalDate.now();
        LocalDate within90Days = today.plusDays(90);

        String sql = """
            SELECT
                ec.certification_name,
                COUNT(*) AS total_count,
                SUM(CASE WHEN ec.expiry_date IS NOT NULL AND ec.expiry_date < :today THEN 1 ELSE 0 END) AS expired_count,
                SUM(CASE WHEN ec.expiry_date IS NOT NULL
                         AND ec.expiry_date >= :today
                         AND ec.expiry_date <= :within90 THEN 1 ELSE 0 END) AS expiring_soon_count
            FROM core_hr.employee_certification ec
            JOIN core_hr.employee e ON e.id = ec.employee_id
            WHERE e.tenant_id = :tenantId
            """ + (scope.isPresent() && !scope.get().isEmpty()
                    ? "AND e.id IN (:scopeIds)" : "") + """
            GROUP BY ec.certification_name
            ORDER BY ec.certification_name
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.current())
                .addValue("today", today)
                .addValue("within90", within90Days);
        if (scope.isPresent() && !scope.get().isEmpty()) {
            params.addValue("scopeIds", scope.get().stream().map(UUID::toString).toList());
        }

        return jdbc.query(sql, params, (rs, i) -> new SkillInventoryReportDto.CertificationRow(
                rs.getString("certification_name"),
                rs.getInt("total_count"),
                rs.getInt("expired_count"),
                rs.getInt("expiring_soon_count")
        ));
    }
}
