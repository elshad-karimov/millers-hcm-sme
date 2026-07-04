package az.millers.hcm.reporting.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for the three contract-change reports mandated by PRD §8.12.5
 * (M226).
 */
public final class ContractChangeReportDtos {

    private ContractChangeReportDtos() {}

    // ── Salary-change history ─────────────────────────────────────────────

    public record SalaryChangeRow(
            UUID changeId,
            String changeNo,
            UUID employeeId,
            String employeeNo,
            String fullName,
            LocalDate effectiveDate,
            /** Raw old_value JSON map (contains salary, currency, etc.). */
            Map<String, Object> oldValue,
            /** Raw new_value JSON map. */
            Map<String, Object> newValue,
            String reason,
            OffsetDateTime appliedAt) {}

    public record SalaryChangeHistoryReport(
            LocalDate from,
            LocalDate to,
            int count,
            List<SalaryChangeRow> rows) {}

    // ── Position-change history ────────────────────────────────────────────

    public record PositionChangeRow(
            UUID changeId,
            String changeNo,
            UUID employeeId,
            String employeeNo,
            String fullName,
            String changeType,
            LocalDate effectiveDate,
            Map<String, Object> oldValue,
            Map<String, Object> newValue,
            String reason,
            OffsetDateTime appliedAt) {}

    public record PositionChangeHistoryReport(
            LocalDate from,
            LocalDate to,
            int count,
            List<PositionChangeRow> rows) {}

    // ── Pending contract changes ───────────────────────────────────────────

    public record PendingChangeRow(
            UUID changeId,
            String changeNo,
            UUID employeeId,
            String employeeNo,
            String fullName,
            String changeType,
            LocalDate effectiveDate,
            String reason,
            OffsetDateTime createdAt) {}

    public record PendingContractChangesReport(
            int count,
            List<PendingChangeRow> rows) {}
}
