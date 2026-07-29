package az.millers.hcm.payroll.api;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.security.SecurityRoles;

/**
 * M464 — Loan dashboard: active loans, outstanding by department, completion %, overdue list.
 */
@RestController
@RequestMapping("/api/reports/loans")
public class LoanReportController {

    private final NamedParameterJdbcTemplate jdbc;

    public LoanReportController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public DashboardResponse getDashboard() {
        String activeCountSql = """
            SELECT COUNT(*) FROM payroll.payroll_loan
            WHERE status = 'ACTIVE'
            """;
        Long activeCount = jdbc.queryForObject(activeCountSql, Map.of(), Long.class);

        String outstandingSql = """
            SELECT COALESCE(SUM(outstanding_balance), 0) FROM payroll.payroll_loan
            WHERE status = 'ACTIVE'
            """;
        BigDecimal totalOutstanding = jdbc.queryForObject(outstandingSql, Map.of(), BigDecimal.class);

        String completedSql = """
            SELECT COUNT(*) FROM payroll.payroll_loan
            WHERE status = 'FULLY_REPAID'
            """;
        Long completedCount = jdbc.queryForObject(completedSql, Map.of(), Long.class);

        String totalSql = """
            SELECT COUNT(*) FROM payroll.payroll_loan
            WHERE status IN ('ACTIVE', 'FULLY_REPAID')
            """;
        Long totalCount = jdbc.queryForObject(totalSql, Map.of(), Long.class);

        double completionPct = totalCount > 0 ? (completedCount * 100.0 / totalCount) : 0;

        // Outstanding by department (via employee join)
        String deptSql = """
            SELECT COALESCE(d.name, 'Unknown') as department_name,
                   COUNT(DISTINCT pl.employee_id) as employee_count,
                   COALESCE(SUM(pl.outstanding_balance), 0) as total_outstanding
            FROM payroll.payroll_loan pl
            LEFT JOIN core_hr.employee e ON e.id = pl.employee_id AND e.tenant_id = :tenant
            LEFT JOIN organization.department d ON d.id = e.department_id
            WHERE pl.status = 'ACTIVE'
            GROUP BY d.name
            ORDER BY total_outstanding DESC
            """;
        List<DepartmentOutstanding> byDepartment = jdbc.query(deptSql, Map.of("tenant", TenantContext.current()),
                (rs, rowNum) -> new DepartmentOutstanding(
                        rs.getString("department_name"),
                        rs.getLong("employee_count"),
                        rs.getBigDecimal("total_outstanding")
                ));

        // Overdue installments (heuristic: due_year/month < current month)
        String overdueSql = """
            SELECT lr.request_no, e.employee_no, e.first_name, e.last_name, lr.employee_id,
                   lis.installment_number, lis.installment_amount, lis.remaining_balance
            FROM payroll.loan_installment_schedule lis
            JOIN payroll.loan_request lr ON lr.id = lis.loan_request_id AND lr.tenant_id = :tenant
            JOIN core_hr.employee e ON e.id = lr.employee_id AND e.tenant_id = :tenant
            WHERE lis.status = 'PENDING'
              AND (lis.due_year < :currentYear OR (lis.due_year = :currentYear AND lis.due_month < :currentMonth))
            ORDER BY lis.due_year, lis.due_month
            LIMIT 100
            """;
        int currentYear = java.time.LocalDate.now().getYear();
        int currentMonth = java.time.LocalDate.now().getMonthValue();
        List<OverdueInstallment> overdueList = jdbc.query(overdueSql,
                Map.of("tenant", TenantContext.current(), "currentYear", currentYear, "currentMonth", currentMonth),
                (rs, rowNum) -> new OverdueInstallment(
                        rs.getString("request_no"),
                        rs.getString("employee_no"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getInt("installment_number"),
                        rs.getBigDecimal("installment_amount"),
                        rs.getBigDecimal("remaining_balance")
                ));

        return new DashboardResponse(activeCount, totalOutstanding, completionPct, byDepartment, overdueList);
    }

    public record DashboardResponse(Long activeLoans, BigDecimal totalOutstanding, double completionPct,
                                    List<DepartmentOutstanding> byDepartment, List<OverdueInstallment> overdueList) {}
    public record DepartmentOutstanding(String departmentName, Long employeeCount, BigDecimal totalOutstanding) {}
    public record OverdueInstallment(String requestNo, String employeeNo, String employeeName, Integer installmentNumber,
                                     BigDecimal installmentAmount, BigDecimal remainingBalance) {}
}
