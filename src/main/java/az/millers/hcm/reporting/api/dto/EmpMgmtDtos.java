package az.millers.hcm.reporting.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the Employee-Management report family (M80 / P2-29-33). Each
 * record is the wire shape for one endpoint; the service builds them from
 * the existing M62-M79 repositories without any new tables.
 */
public final class EmpMgmtDtos {
    private EmpMgmtDtos() {}

    public record ProbationDueRow(
            UUID employeeId, String employeeNo, String fullName,
            UUID reviewId, LocalDate scheduledDate, String reviewType,
            String status, Integer daysUntil) {}

    public record ContractExpiringRow(
            UUID employeeId, String employeeNo, String fullName,
            UUID contractId, String contractNo, LocalDate endDate,
            String contractType, Integer daysUntil) {}

    public record CertificationExpiringRow(
            UUID employeeId, String employeeNo, String fullName,
            UUID certificationId, String certificationName,
            LocalDate expiryDate, Integer daysUntil) {}

    public record RehireRow(
            UUID employeeId, String employeeNo, String fullName,
            UUID previousEmployeeId, LocalDate hireDate, String rehireReason) {}

    /** Dashboard widget rollup — one of each card. */
    public record EmpMgmtSummary(
            long headcount,
            long onProbation,
            long onLeaveToday,
            long probationDueIn60d,
            long contractsExpiringIn60d,
            long certsExpiringIn60d,
            long unverifiedIdentifications,
            long pendingPersonalInfoChanges) {}

    /** One row in the global activity feed (M80 / P2-32). */
    public record ActivityRow(
            OffsetDateTime at,
            String actor,
            String module,
            String entityName,
            String entityId,
            String action,
            String summary) {}

    public record ProbationDueReport(LocalDate asOf, List<ProbationDueRow> rows) {}
    public record ContractExpiringReport(LocalDate asOf, int lookaheadDays, List<ContractExpiringRow> rows) {}
    public record CertificationExpiringReport(LocalDate asOf, int lookaheadDays, List<CertificationExpiringRow> rows) {}
    public record RehireReport(int limit, List<RehireRow> rows) {}
    public record ActivityFeed(int limit, List<ActivityRow> rows) {}
}
