package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class WorkspaceDtos {

    public record AttendanceWorkspaceSummary(
            LocalDate date,
            int totalEmployees,
            int presentCount,
            int absentCount,
            int lateCount,
            int missingClockOutCount,
            int pendingCorrections,
            int pendingOtRequests,
            int openExceptions,
            List<WorkspaceEmployeeRow> lateEmployees,
            List<WorkspaceEmployeeRow> absentEmployees) {
    }

    public record WorkspaceEmployeeRow(
            UUID employeeId,
            String employeeName,
            String status,
            int lateMinutes) {
    }
}
