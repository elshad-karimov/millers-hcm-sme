package az.millers.hcm.selfservice.service;

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

    private static final String TENANT = "default";
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
                d.name AS department_name,
                ou.name AS org_unit_name,
                p.title AS position_title,
                e.work_email,
                e.work_phone,
                e.photo_attachment_id
            FROM core_hr.employee e
            LEFT JOIN organization.department d ON e.department_id = d.id
            LEFT JOIN organization.org_unit ou ON e.org_unit_id = ou.id
            LEFT JOIN organization.position p ON e.position_id = p.id
            WHERE e.tenant_id = ?
              AND e.status = 'ACTIVE'
              AND (
                LOWER(CONCAT(e.first_name, ' ', e.last_name)) LIKE ?
                OR LOWER(d.name) LIKE ?
                OR LOWER(ou.name) LIKE ?
                OR LOWER(p.title) LIKE ?
              )
            ORDER BY e.last_name, e.first_name
            LIMIT ?
            """;

        return jdbc.query(sql,
                new DirectoryRowMapper(),
                TENANT, pattern, pattern, pattern, pattern, MAX_RESULTS);
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
