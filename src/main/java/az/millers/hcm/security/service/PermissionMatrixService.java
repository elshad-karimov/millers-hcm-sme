package az.millers.hcm.security.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import az.millers.hcm.security.SecurityRoles;

/**
 * M494 — Permission matrix view. Static-built matrix of role → capability
 * areas. Curated from SecurityRoles constants + module conventions. Read-only
 * for SYSTEM_ADMIN / AUDITOR / HR_ADMIN.
 */
@Service
public class PermissionMatrixService {

    /**
     * Get the full permission matrix: role → capability → access level.
     */
    public PermissionMatrix getMatrix() {
        Map<String, Map<String, AccessLevel>> matrix = new LinkedHashMap<>();

        // Build each role's capability map
        matrix.put(SecurityRoles.R_SYSTEM_ADMIN, buildSystemAdmin());
        matrix.put(SecurityRoles.R_HR_ADMIN, buildHrAdmin());
        matrix.put(SecurityRoles.R_HR_SPECIALIST, buildHrSpecialist());
        matrix.put(SecurityRoles.R_PAYROLL_SPECIALIST, buildPayrollSpecialist());
        matrix.put(SecurityRoles.R_COMPENSATION_MANAGER, buildCompensationManager());
        matrix.put(SecurityRoles.R_BENEFITS_MANAGER, buildBenefitsManager());
        matrix.put(SecurityRoles.R_DEPARTMENT_MANAGER, buildDepartmentManager());
        matrix.put(SecurityRoles.R_EMPLOYEE, buildEmployee());
        matrix.put(SecurityRoles.R_AUDITOR, buildAuditor());
        matrix.put(SecurityRoles.R_FINANCE_USER, buildFinanceUser());
        matrix.put(SecurityRoles.R_RECRUITER, buildRecruiter());
        matrix.put(SecurityRoles.R_OCCUPATIONAL_HEALTH, buildOccupationalHealth());

        return new PermissionMatrix(matrix);
    }

    // ── Role builders ──────────────────────────────────────────────────────────

    private Map<String, AccessLevel> buildSystemAdmin() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        // SYSTEM_ADMIN has ADMIN on everything
        caps.put("Core HR", AccessLevel.ADMIN);
        caps.put("Payroll", AccessLevel.ADMIN);
        caps.put("Compensation", AccessLevel.ADMIN);
        caps.put("Benefits", AccessLevel.ADMIN);
        caps.put("Leave/Attendance", AccessLevel.ADMIN);
        caps.put("Recruitment", AccessLevel.ADMIN);
        caps.put("Performance", AccessLevel.ADMIN);
        caps.put("Learning", AccessLevel.ADMIN);
        caps.put("Talent/Succession", AccessLevel.ADMIN);
        caps.put("ER/Compliance", AccessLevel.ADMIN);
        caps.put("EHS", AccessLevel.ADMIN);
        caps.put("Budgets/GL", AccessLevel.ADMIN);
        caps.put("Engagement", AccessLevel.ADMIN);
        caps.put("Admin/Integrations", AccessLevel.ADMIN);
        return caps;
    }

    private Map<String, AccessLevel> buildHrAdmin() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.WRITE);
        caps.put("Payroll", AccessLevel.WRITE);
        caps.put("Compensation", AccessLevel.WRITE);
        caps.put("Benefits", AccessLevel.WRITE);
        caps.put("Leave/Attendance", AccessLevel.WRITE);
        caps.put("Recruitment", AccessLevel.WRITE);
        caps.put("Performance", AccessLevel.WRITE);
        caps.put("Learning", AccessLevel.WRITE);
        caps.put("Talent/Succession", AccessLevel.WRITE);
        caps.put("ER/Compliance", AccessLevel.WRITE);
        caps.put("EHS", AccessLevel.WRITE);
        caps.put("Budgets/GL", AccessLevel.READ);
        caps.put("Engagement", AccessLevel.WRITE);
        caps.put("Admin/Integrations", AccessLevel.READ);
        return caps;
    }

    private Map<String, AccessLevel> buildHrSpecialist() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.WRITE);
        caps.put("Payroll", AccessLevel.READ);
        caps.put("Compensation", AccessLevel.READ);
        caps.put("Benefits", AccessLevel.READ);
        caps.put("Leave/Attendance", AccessLevel.WRITE);
        caps.put("Recruitment", AccessLevel.WRITE);
        caps.put("Performance", AccessLevel.READ);
        caps.put("Learning", AccessLevel.READ);
        caps.put("Talent/Succession", AccessLevel.READ);
        caps.put("ER/Compliance", AccessLevel.READ);
        caps.put("EHS", AccessLevel.READ);
        caps.put("Budgets/GL", AccessLevel.NONE);
        caps.put("Engagement", AccessLevel.READ);
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildPayrollSpecialist() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.READ);
        caps.put("Payroll", AccessLevel.WRITE);
        caps.put("Compensation", AccessLevel.READ);
        caps.put("Benefits", AccessLevel.READ);
        caps.put("Leave/Attendance", AccessLevel.READ);
        caps.put("Recruitment", AccessLevel.NONE);
        caps.put("Performance", AccessLevel.NONE);
        caps.put("Learning", AccessLevel.NONE);
        caps.put("Talent/Succession", AccessLevel.NONE);
        caps.put("ER/Compliance", AccessLevel.NONE);
        caps.put("EHS", AccessLevel.NONE);
        caps.put("Budgets/GL", AccessLevel.READ);
        caps.put("Engagement", AccessLevel.NONE);
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildCompensationManager() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.READ);
        caps.put("Payroll", AccessLevel.NONE);
        caps.put("Compensation", AccessLevel.WRITE);
        caps.put("Benefits", AccessLevel.NONE);
        caps.put("Leave/Attendance", AccessLevel.NONE);
        caps.put("Recruitment", AccessLevel.NONE);
        caps.put("Performance", AccessLevel.READ);
        caps.put("Learning", AccessLevel.NONE);
        caps.put("Talent/Succession", AccessLevel.READ);
        caps.put("ER/Compliance", AccessLevel.NONE);
        caps.put("EHS", AccessLevel.NONE);
        caps.put("Budgets/GL", AccessLevel.READ);
        caps.put("Engagement", AccessLevel.NONE);
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildBenefitsManager() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.READ);
        caps.put("Payroll", AccessLevel.NONE);
        caps.put("Compensation", AccessLevel.NONE);
        caps.put("Benefits", AccessLevel.WRITE);
        caps.put("Leave/Attendance", AccessLevel.NONE);
        caps.put("Recruitment", AccessLevel.NONE);
        caps.put("Performance", AccessLevel.NONE);
        caps.put("Learning", AccessLevel.NONE);
        caps.put("Talent/Succession", AccessLevel.NONE);
        caps.put("ER/Compliance", AccessLevel.NONE);
        caps.put("EHS", AccessLevel.NONE);
        caps.put("Budgets/GL", AccessLevel.READ);
        caps.put("Engagement", AccessLevel.NONE);
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildDepartmentManager() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.READ); // Team members only
        caps.put("Payroll", AccessLevel.NONE);
        caps.put("Compensation", AccessLevel.NONE);
        caps.put("Benefits", AccessLevel.READ); // Team members only
        caps.put("Leave/Attendance", AccessLevel.WRITE); // Team approvals
        caps.put("Recruitment", AccessLevel.READ); // Interview participation
        caps.put("Performance", AccessLevel.WRITE); // Team reviews
        caps.put("Learning", AccessLevel.READ); // Team progress
        caps.put("Talent/Succession", AccessLevel.READ);
        caps.put("ER/Compliance", AccessLevel.READ); // Team cases
        caps.put("EHS", AccessLevel.READ);
        caps.put("Budgets/GL", AccessLevel.NONE);
        caps.put("Engagement", AccessLevel.READ); // Team surveys
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildEmployee() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.READ); // Own profile only
        caps.put("Payroll", AccessLevel.READ); // Own payslips only
        caps.put("Compensation", AccessLevel.READ); // Own data only
        caps.put("Benefits", AccessLevel.READ); // Own enrolments only
        caps.put("Leave/Attendance", AccessLevel.WRITE); // Own requests
        caps.put("Recruitment", AccessLevel.NONE);
        caps.put("Performance", AccessLevel.WRITE); // Own goals/reviews
        caps.put("Learning", AccessLevel.WRITE); // Own enrolments
        caps.put("Talent/Succession", AccessLevel.NONE);
        caps.put("ER/Compliance", AccessLevel.READ); // Own cases
        caps.put("EHS", AccessLevel.READ); // Own incidents
        caps.put("Budgets/GL", AccessLevel.NONE);
        caps.put("Engagement", AccessLevel.WRITE); // Own surveys/recognition
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildAuditor() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        // AUDITOR has READ on everything for audit trails
        caps.put("Core HR", AccessLevel.READ);
        caps.put("Payroll", AccessLevel.READ);
        caps.put("Compensation", AccessLevel.READ);
        caps.put("Benefits", AccessLevel.READ);
        caps.put("Leave/Attendance", AccessLevel.READ);
        caps.put("Recruitment", AccessLevel.READ);
        caps.put("Performance", AccessLevel.READ);
        caps.put("Learning", AccessLevel.READ);
        caps.put("Talent/Succession", AccessLevel.READ);
        caps.put("ER/Compliance", AccessLevel.READ);
        caps.put("EHS", AccessLevel.READ);
        caps.put("Budgets/GL", AccessLevel.READ);
        caps.put("Engagement", AccessLevel.READ);
        caps.put("Admin/Integrations", AccessLevel.READ);
        return caps;
    }

    private Map<String, AccessLevel> buildFinanceUser() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.NONE);
        caps.put("Payroll", AccessLevel.READ); // Bank files, GL postings
        caps.put("Compensation", AccessLevel.READ); // Salary data
        caps.put("Benefits", AccessLevel.READ); // Benefit costs
        caps.put("Leave/Attendance", AccessLevel.NONE);
        caps.put("Recruitment", AccessLevel.NONE);
        caps.put("Performance", AccessLevel.NONE);
        caps.put("Learning", AccessLevel.NONE);
        caps.put("Talent/Succession", AccessLevel.NONE);
        caps.put("ER/Compliance", AccessLevel.NONE);
        caps.put("EHS", AccessLevel.NONE);
        caps.put("Budgets/GL", AccessLevel.WRITE); // Budget + GL management
        caps.put("Engagement", AccessLevel.NONE);
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildRecruiter() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.READ);
        caps.put("Payroll", AccessLevel.NONE);
        caps.put("Compensation", AccessLevel.NONE);
        caps.put("Benefits", AccessLevel.NONE);
        caps.put("Leave/Attendance", AccessLevel.NONE);
        caps.put("Recruitment", AccessLevel.WRITE);
        caps.put("Performance", AccessLevel.NONE);
        caps.put("Learning", AccessLevel.NONE);
        caps.put("Talent/Succession", AccessLevel.READ);
        caps.put("ER/Compliance", AccessLevel.NONE);
        caps.put("EHS", AccessLevel.NONE);
        caps.put("Budgets/GL", AccessLevel.NONE);
        caps.put("Engagement", AccessLevel.NONE);
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    private Map<String, AccessLevel> buildOccupationalHealth() {
        Map<String, AccessLevel> caps = new LinkedHashMap<>();
        caps.put("Core HR", AccessLevel.READ);
        caps.put("Payroll", AccessLevel.NONE);
        caps.put("Compensation", AccessLevel.NONE);
        caps.put("Benefits", AccessLevel.READ); // Health benefits
        caps.put("Leave/Attendance", AccessLevel.READ); // Sick leave
        caps.put("Recruitment", AccessLevel.NONE);
        caps.put("Performance", AccessLevel.NONE);
        caps.put("Learning", AccessLevel.NONE);
        caps.put("Talent/Succession", AccessLevel.NONE);
        caps.put("ER/Compliance", AccessLevel.READ); // Health cases
        caps.put("EHS", AccessLevel.WRITE); // Incidents, assessments
        caps.put("Budgets/GL", AccessLevel.NONE);
        caps.put("Engagement", AccessLevel.NONE);
        caps.put("Admin/Integrations", AccessLevel.NONE);
        return caps;
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    public record PermissionMatrix(Map<String, Map<String, AccessLevel>> matrix) {
        /**
         * Get a flattened list for table rendering.
         */
        public List<PermissionRow> toRows() {
            List<PermissionRow> rows = new ArrayList<>();
            matrix.forEach((role, capabilities) -> {
                capabilities.forEach((capability, level) -> {
                    rows.add(new PermissionRow(role, capability, level));
                });
            });
            return rows;
        }
    }

    public record PermissionRow(String role, String capability, AccessLevel level) {}

    public enum AccessLevel {
        NONE,
        READ,
        WRITE,
        ADMIN
    }
}
