package az.millers.hcm.selfservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Lightweight DTOs specific to the self-service surface. */
public final class SelfDtos {

    private SelfDtos() {}

    public record SelfProfile(
            UUID id,
            String employeeNo,
            String username,
            String firstName,
            String lastName,
            String middleName,
            String email,
            String phone,
            LocalDate hireDate,
            String employmentStatus,
            String departmentName,
            String positionTitle,
            UUID orgUnitId,
            UUID positionId,
            UUID managerId) {
    }

    /**
     * At-a-glance summary for the My Workspace dashboard. Each field can be
     * null when no data is available — clients render "—" in that case.
     */
    public record SelfSummary(
            UUID employeeId,
            String employeeNo,
            String fullName,
            String employmentStatus,
            int year,
            // Leave: annual remaining + how many requests are pending
            BigDecimal annualLeaveRemaining,
            long leaveRequestsPending,
            // Permission: hours remaining
            BigDecimal permissionHoursRemaining,
            long permissionRequestsPending,
            // Timesheet: current period status
            String currentTimesheetStatus,
            // Payslip: last issued
            OffsetDateTime lastPayslipAt,
            BigDecimal lastPayslipNet,
            // Learning: mandatory courses pending
            long mandatoryCoursesPending,
            long certificatesHeld,
            // Performance: current cycle review status
            String activeCycleCode,
            String activeReviewStatus) {
    }
}
