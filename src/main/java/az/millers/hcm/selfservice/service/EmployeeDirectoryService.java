package az.millers.hcm.selfservice.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.selfservice.api.dto.EmployeeDirectoryResponse;

/**
 * M504 — Employee directory search (PUBLIC fields only, tenant-scoped).
 * Uses JDBC projection to avoid loading encrypted PII fields.
 */
@Service
public class EmployeeDirectoryService {

    private static final int MAX_RESULTS = 50;

    private final JdbcTemplate jdbc;

    public EmployeeDirectoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Searches employees by name, department, or position.
     * Returns PUBLIC fields only: NO salary, national_id, bank, DOB, home address, medical.
     */
    @Transactional(readOnly = true)
    public List<EmployeeDirectoryResponse> search(String query) {
        String pattern = "%" + (query != null ? query.toLowerCase() : "") + "%";

        String sql = """
            SELECT
                e.id,
                CONCAT(e.first_name, ' ', e.last_name) AS full_name,
                e.department_name AS department_name,
                ou.name AS org_unit_name,
                p.title AS position_title,
                e.work_email,
                e.work_phone,
                -- No employee photo is stored anywhere in this schema, and
                -- nothing else in the codebase references one. Selecting a
                -- typed NULL keeps the response shape the DTO promises instead
                -- of naming a column that has never existed.
                CAST(NULL AS uuid) AS photo_attachment_id
            FROM core_hr.employee e
            -- Was: LEFT JOIN organization.department d ON e.department_id = d.id
            -- Neither side exists — there is no department table, and the
            -- employee's own department is the denormalized department_name.
            LEFT JOIN organization.org_unit ou ON e.org_unit_id = ou.id
            LEFT JOIN staffing.position p ON e.position_id = p.id
            WHERE e.tenant_id = ?
              AND e.employment_status = 'ACTIVE'
              AND (
                LOWER(CONCAT(e.first_name, ' ', e.last_name)) LIKE ?
                OR LOWER(e.department_name) LIKE ?
                OR LOWER(ou.name) LIKE ?
                OR LOWER(p.title) LIKE ?
              )
            ORDER BY e.last_name, e.first_name
            LIMIT ?
            """;

        return jdbc.query(sql,
                new DirectoryRowMapper(),
                TenantContext.current(), pattern, pattern, pattern, pattern, MAX_RESULTS);
    }

    private static class DirectoryRowMapper implements RowMapper<EmployeeDirectoryResponse> {
        @Override
        public EmployeeDirectoryResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            String photoStr = rs.getString("photo_attachment_id");
            UUID photoId = photoStr != null ? UUID.fromString(photoStr) : null;

            return new EmployeeDirectoryResponse(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("full_name"),
                    rs.getString("department_name"),
                    rs.getString("org_unit_name"),
                    rs.getString("position_title"),
                    rs.getString("work_email"),
                    rs.getString("work_phone"),
                    photoId
            );
        }
    }
}
