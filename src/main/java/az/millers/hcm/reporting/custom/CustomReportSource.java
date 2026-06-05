package az.millers.hcm.reporting.custom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * M119 — curated catalogue of data sources the custom-report builder
 * exposes. Each source carries:
 * <ul>
 *   <li>a hard-coded FROM clause (with any joins) — never user-supplied,</li>
 *   <li>an ordered field whitelist — only these can be selected/filtered/sorted,</li>
 *   <li>the SQL expression for the employee-id column used by ABAC scoping
 *       (null when the source isn't employee-anchored).</li>
 * </ul>
 *
 * <p>Adding a new source means:
 * <ol>
 *   <li>append an enum constant with its FROM + fields,</li>
 *   <li>decide whether {@link #scopeEmployeeIdExpr} should be set so
 *       {@link CustomReportSqlBuilder} can append the
 *       {@code AND emp IN (:scopeIds)} clause for restricted callers.</li>
 * </ol>
 */
public enum CustomReportSource {

    EMPLOYEES(
        "Employees",
        "core_hr.employee e",
        "e.id",
        List.of(
            FieldSpec.of("employee_no",       "Employee #",       "e.employee_no",       FieldType.STRING),
            FieldSpec.of("first_name",        "First name",       "e.first_name",        FieldType.STRING),
            FieldSpec.of("last_name",         "Last name",        "e.last_name",         FieldType.STRING),
            FieldSpec.of("email",             "Email",            "e.email",             FieldType.STRING),
            FieldSpec.of("phone",             "Phone",            "e.phone",             FieldType.STRING),
            FieldSpec.of("gender",            "Gender",           "e.gender",            FieldType.STRING),
            FieldSpec.of("birth_date",        "Birth date",       "e.birth_date",        FieldType.DATE),
            FieldSpec.of("hire_date",         "Hire date",        "e.hire_date",         FieldType.DATE),
            FieldSpec.of("employment_status", "Status",           "e.employment_status", FieldType.STRING),
            FieldSpec.of("department_name",   "Department",       "e.department_name",   FieldType.STRING),
            FieldSpec.of("position_title",    "Position",         "e.position_title",    FieldType.STRING),
            FieldSpec.of("cost_centre",       "Cost centre",      "e.cost_centre",       FieldType.STRING),
            FieldSpec.of("manager_id",        "Manager ID",       "e.manager_id",        FieldType.UUID),
            FieldSpec.of("created_at",        "Created at",       "e.created_at",        FieldType.DATETIME))),

    LEAVE_REQUESTS(
        "Leave requests",
        "leave_mgmt.leave_request lr "
            + "JOIN leave_mgmt.leave_type lt ON lt.id = lr.leave_type_id "
            + "JOIN core_hr.employee e ON e.id = lr.employee_id",
        "lr.employee_id",
        List.of(
            FieldSpec.of("request_no",   "Request #",        "lr.request_no",     FieldType.STRING),
            FieldSpec.of("employee_no",  "Employee #",       "e.employee_no",     FieldType.STRING),
            FieldSpec.of("employee_name","Employee",         "e.first_name || ' ' || e.last_name", FieldType.STRING),
            FieldSpec.of("department",   "Department",       "e.department_name", FieldType.STRING),
            FieldSpec.of("leave_type",   "Leave type",       "lt.name",           FieldType.STRING),
            FieldSpec.of("start_date",   "Start",            "lr.start_date",     FieldType.DATE),
            FieldSpec.of("end_date",     "End",              "lr.end_date",       FieldType.DATE),
            FieldSpec.of("total_days",   "Days",             "lr.total_days",     FieldType.DECIMAL),
            FieldSpec.of("status",       "Status",           "lr.status",         FieldType.STRING),
            FieldSpec.of("reason",       "Reason",           "lr.reason",         FieldType.STRING),
            FieldSpec.of("created_at",   "Submitted",        "lr.created_at",     FieldType.DATETIME))),

    ATTENDANCE_DAILY(
        "Attendance — daily summaries",
        "attendance.daily_summary ds "
            + "JOIN core_hr.employee e ON e.id = ds.employee_id",
        "ds.employee_id",
        List.of(
            FieldSpec.of("employee_no",     "Employee #",   "e.employee_no",     FieldType.STRING),
            FieldSpec.of("employee_name",   "Employee",     "e.first_name || ' ' || e.last_name", FieldType.STRING),
            FieldSpec.of("department",      "Department",   "e.department_name", FieldType.STRING),
            FieldSpec.of("work_date",       "Date",         "ds.work_date",      FieldType.DATE),
            FieldSpec.of("status",          "Status",       "ds.status",         FieldType.STRING),
            FieldSpec.of("worked_minutes",  "Worked min",   "ds.worked_minutes", FieldType.INTEGER),
            FieldSpec.of("late_minutes",    "Late min",     "ds.late_minutes",   FieldType.INTEGER),
            FieldSpec.of("early_minutes",   "Early min",    "ds.early_minutes", FieldType.INTEGER),
            FieldSpec.of("overtime_minutes","OT min",       "ds.overtime_minutes", FieldType.INTEGER),
            FieldSpec.of("entry_time",      "First in",     "ds.entry_time",     FieldType.DATETIME),
            FieldSpec.of("exit_time",       "Last out",     "ds.exit_time",      FieldType.DATETIME))),

    PAYROLL_RESULTS(
        "Payroll — payslips",
        "payroll.payroll_result pr "
            + "JOIN payroll.payroll_run prn ON prn.id = pr.run_id "
            + "JOIN core_hr.employee e ON e.id = pr.employee_id",
        "pr.employee_id",
        List.of(
            FieldSpec.of("payslip_no",      "Payslip #",     "pr.payslip_no",        FieldType.STRING),
            FieldSpec.of("run_no",          "Run #",         "prn.run_no",           FieldType.STRING),
            FieldSpec.of("period_year",     "Year",          "prn.period_year",      FieldType.INTEGER),
            FieldSpec.of("period_month",    "Month",         "prn.period_month",     FieldType.INTEGER),
            FieldSpec.of("employee_no",     "Employee #",    "e.employee_no",        FieldType.STRING),
            FieldSpec.of("employee_name",   "Employee",      "e.first_name || ' ' || e.last_name", FieldType.STRING),
            FieldSpec.of("department",      "Department",    "e.department_name",    FieldType.STRING),
            FieldSpec.of("base_salary",     "Base salary",   "pr.base_salary",       FieldType.DECIMAL),
            FieldSpec.of("worked_hours",    "Worked hours",  "pr.worked_hours",      FieldType.DECIMAL),
            FieldSpec.of("overtime_pay",    "OT pay",        "pr.overtime_pay",      FieldType.DECIMAL),
            FieldSpec.of("bonus_amount",    "Bonus",         "pr.bonus_amount",      FieldType.DECIMAL),
            FieldSpec.of("allowance_amount","Allowances",    "pr.allowance_amount",  FieldType.DECIMAL),
            FieldSpec.of("deduction_amount","Deductions",    "pr.deduction_amount",  FieldType.DECIMAL),
            FieldSpec.of("gross_amount",    "Gross",         "pr.gross_amount",      FieldType.DECIMAL),
            FieldSpec.of("income_tax",      "Income tax",    "pr.income_tax",        FieldType.DECIMAL),
            FieldSpec.of("net_amount",      "Net",           "pr.net_amount",        FieldType.DECIMAL)));

    private final String label;
    private final String fromClause;
    private final String scopeEmployeeIdExpr;
    private final Map<String, FieldSpec> fieldsByKey;
    private final List<FieldSpec> fields;

    CustomReportSource(String label, String fromClause, String scopeEmployeeIdExpr,
                       List<FieldSpec> fields) {
        this.label = label;
        this.fromClause = fromClause;
        this.scopeEmployeeIdExpr = scopeEmployeeIdExpr;
        this.fields = List.copyOf(fields);
        Map<String, FieldSpec> map = new LinkedHashMap<>();
        for (FieldSpec f : fields) map.put(f.key(), f);
        this.fieldsByKey = Map.copyOf(map);
    }

    public String label()      { return label; }
    public String fromClause() { return fromClause; }
    public List<FieldSpec> fields() { return fields; }

    /** Employee-id SQL expression for ABAC scoping (null when source isn't employee-anchored). */
    public String scopeEmployeeIdExpr() { return scopeEmployeeIdExpr; }

    public Optional<FieldSpec> field(String key) {
        return Optional.ofNullable(fieldsByKey.get(key));
    }

    /** Lookup by enum name with a friendlier error than {@link #valueOf}. */
    public static Optional<CustomReportSource> findByKey(String key) {
        if (key == null) return Optional.empty();
        for (CustomReportSource s : values()) {
            if (s.name().equalsIgnoreCase(key)) return Optional.of(s);
        }
        return Optional.empty();
    }
}
