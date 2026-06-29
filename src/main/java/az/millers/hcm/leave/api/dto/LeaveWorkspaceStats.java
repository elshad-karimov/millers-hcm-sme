package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record LeaveWorkspaceStats(
        long pendingApprovals,
        long approvedThisMonth,
        long rejectedThisMonth,
        long cancelledThisMonth,
        BigDecimal totalDaysTakenThisYear,
        List<TypeBreakdown> byType,
        List<MonthlyTrend> monthlyTrend,
        List<AbsenceHotspot> absenceHotspots
) {
    public record TypeBreakdown(String typeCode, String typeName, long requestCount, BigDecimal totalDays) {}
    public record MonthlyTrend(int year, int month, long approved, BigDecimal totalDays) {}
    public record AbsenceHotspot(String employeeNo, String employeeName, long absenceDays) {}
}
