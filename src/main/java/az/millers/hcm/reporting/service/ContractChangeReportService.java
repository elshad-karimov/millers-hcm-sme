package az.millers.hcm.reporting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.PendingChangeRow;
import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.PendingContractChangesReport;
import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.PositionChangeHistoryReport;
import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.PositionChangeRow;
import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.SalaryChangeHistoryReport;
import az.millers.hcm.reporting.api.dto.ContractChangeReportDtos.SalaryChangeRow;

/**
 * Contract-change report computations (M226 / PRD §8.12.5).
 */
@Service
public class ContractChangeReportService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ContractChangeReportService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ── 1. Salary-change history ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public SalaryChangeHistoryReport salaryHistory(LocalDate from, LocalDate to) {
        String sql = """
                SELECT cc.id, cc.change_no, cc.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       cc.effective_date, cc.old_value, cc.new_value,
                       cc.reason, cc.applied_at
                FROM lifecycle.contract_change cc
                JOIN core_hr.employee e ON e.id = cc.employee_id
                WHERE cc.change_type = 'SALARY'
                  AND cc.status = 'APPLIED'
                  AND cc.effective_date BETWEEN :from AND :to
                ORDER BY cc.effective_date DESC, e.employee_no
                """;

        List<SalaryChangeRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, i) -> new SalaryChangeRow(
                        (UUID) rs.getObject("id"),
                        rs.getString("change_no"),
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getDate("effective_date").toLocalDate(),
                        parseJson(rs.getString("old_value")),
                        parseJson(rs.getString("new_value")),
                        rs.getString("reason"),
                        rs.getTimestamp("applied_at") != null
                                ? rs.getTimestamp("applied_at").toInstant()
                                    .atOffset(java.time.ZoneOffset.UTC)
                                : null));

        return new SalaryChangeHistoryReport(from, to, rows.size(), rows);
    }

    // ── 2. Position-change history ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public PositionChangeHistoryReport positionHistory(LocalDate from, LocalDate to) {
        String sql = """
                SELECT cc.id, cc.change_no, cc.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       cc.change_type, cc.effective_date,
                       cc.old_value, cc.new_value,
                       cc.reason, cc.applied_at
                FROM lifecycle.contract_change cc
                JOIN core_hr.employee e ON e.id = cc.employee_id
                WHERE cc.change_type IN (
                        'POSITION','DEPARTMENT','MANAGER','GRADE','JOB_TITLE')
                  AND cc.status = 'APPLIED'
                  AND cc.effective_date BETWEEN :from AND :to
                ORDER BY cc.effective_date DESC, e.employee_no
                """;

        List<PositionChangeRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, i) -> new PositionChangeRow(
                        (UUID) rs.getObject("id"),
                        rs.getString("change_no"),
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("change_type"),
                        rs.getDate("effective_date").toLocalDate(),
                        parseJson(rs.getString("old_value")),
                        parseJson(rs.getString("new_value")),
                        rs.getString("reason"),
                        rs.getTimestamp("applied_at") != null
                                ? rs.getTimestamp("applied_at").toInstant()
                                    .atOffset(java.time.ZoneOffset.UTC)
                                : null));

        return new PositionChangeHistoryReport(from, to, rows.size(), rows);
    }

    // ── 3. Pending contract changes ────────────────────────────────────────

    @Transactional(readOnly = true)
    public PendingContractChangesReport pending() {
        String sql = """
                SELECT cc.id, cc.change_no, cc.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       cc.change_type, cc.effective_date,
                       cc.reason, cc.created_at
                FROM lifecycle.contract_change cc
                JOIN core_hr.employee e ON e.id = cc.employee_id
                WHERE cc.status = 'PENDING'
                ORDER BY cc.effective_date ASC, cc.created_at ASC
                """;

        List<PendingChangeRow> rows = jdbc.query(sql,
                new MapSqlParameterSource(),
                (rs, i) -> new PendingChangeRow(
                        (UUID) rs.getObject("id"),
                        rs.getString("change_no"),
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("change_type"),
                        rs.getDate("effective_date").toLocalDate(),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()
                            .atOffset(java.time.ZoneOffset.UTC)));

        return new PendingContractChangesReport(rows.size(), rows);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
