package az.millers.hcm.selfservice.team;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO container for the M76 manager self-service dashboard. Records only —
 * a single file keeps the related shapes together.
 */
public final class TeamDtos {
    private TeamDtos() {}

    /** One direct-report row on the team table. */
    public record TeamMember(
            UUID id,
            String employeeNo,
            String firstName,
            String lastName,
            String positionTitle,
            String departmentName,
            String employmentStatus,
            LocalDate hireDate,
            boolean onLeaveToday) {}

    /** Per-employee row inside an "Upcoming" list. */
    public record UpcomingItem(
            UUID employeeId,
            LocalDate date,
            String label) {}

    /** Rollup figures shown above the team table. */
    public record TeamSummary(
            UUID managerId,
            int headcount,
            long onLeaveToday,
            long onProbation,
            long pendingLeaveRequests,
            long contractsEndingSoon,
            List<UpcomingItem> upcomingProbationReviews,
            List<UpcomingItem> upcomingContractEnds) {}

    /** M433 — Team compensation view. */
    public record TeamCompensation(
            UUID employeeId,
            String employeeNo,
            String fullName,
            String positionTitle,
            BigDecimal monthlyBaseSalary,
            String currency) {}
}
