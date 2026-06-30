package az.millers.hcm.payroll.api.dto;

import java.util.List;
import java.util.UUID;

public record PayrollPreFlightResponse(
        Summary summary,
        List<EmployeeIssue> noCompensation,
        List<EmployeeIssue> noTimesheet,
        List<EmployeeIssue> onHold,
        List<EmployeeIssue> pendingAdvances,
        List<EmployeeIssue> retroactiveSalaryChange
) {
    public record Summary(
            int totalIssues,
            int employeesInScope
    ) {}

    public record EmployeeIssue(
            UUID employeeId,
            String employeeNo,
            String name
    ) {}
}
