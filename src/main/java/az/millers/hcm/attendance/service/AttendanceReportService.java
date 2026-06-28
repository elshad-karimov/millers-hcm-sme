package az.millers.hcm.attendance.service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.repo.AttendanceCorrectionRepository;
import az.millers.hcm.attendance.repo.AttendanceExceptionRepository;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.repo.LegalEntityRepository;

/**
 * M336: Attendance report service.
 *
 * <p>Generates various attendance reports: daily roll call, monthly summary,
 * lateness analysis, absence trends, overtime analysis, exception reports, etc.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceReportService {

    private final DailySummaryRepository summaryRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceExceptionRepository exceptionRepository;
    private final EmployeeRepository employeeRepository;
    private final LegalEntityRepository legalEntities;

    public AttendanceReportService(DailySummaryRepository summaryRepository,
                                    AttendanceCorrectionRepository correctionRepository,
                                    AttendanceExceptionRepository exceptionRepository,
                                    EmployeeRepository employeeRepository,
                                    LegalEntityRepository legalEntities) {
        this.summaryRepository = summaryRepository;
        this.correctionRepository = correctionRepository;
        this.exceptionRepository = exceptionRepository;
        this.employeeRepository = employeeRepository;
        this.legalEntities = legalEntities;
    }

    public List<Map<String, Object>> getReport(String reportType,
                                                 LocalDate from, LocalDate to, UUID departmentId) {
        UUID tenantId = defaultTenantId();
        return switch (reportType.toUpperCase()) {
            case "DAILY_ROLL_CALL" -> dailyRollCall(tenantId, from, to, departmentId);
            case "MONTHLY_SUMMARY" -> monthlySummary(tenantId, from, to, departmentId);
            case "LATENESS_ANALYSIS" -> latenessAnalysis(tenantId, from, to, departmentId);
            case "ABSENCE_TREND" -> absenceTrend(tenantId, from, to, departmentId);
            case "OT_ANALYSIS" -> overtimeAnalysis(tenantId, from, to, departmentId);
            case "EXCEPTION_REPORT" -> exceptionReport(tenantId, from, to);
            case "CORRECTION_HISTORY" -> correctionHistory(tenantId, from, to);
            default -> List.of();
        };
    }

    private List<Map<String, Object>> dailyRollCall(UUID tenantId, LocalDate from, LocalDate to, UUID departmentId) {
        List<DailySummary> summaries = summaryRepository.findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(from, to);
        List<Map<String, Object>> result = new ArrayList<>();

        for (DailySummary summary : summaries) {
            Employee employee = employeeRepository.findById(summary.getEmployeeId()).orElse(null);
            if (employee == null) continue;
            if (departmentId != null && !departmentId.equals(employee.getOrgUnitId())) continue;

            Map<String, Object> row = new HashMap<>();
            row.put("date", summary.getWorkDate());
            row.put("employeeId", summary.getEmployeeId());
            row.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
            row.put("status", summary.getStatus().name());
            row.put("clockIn", summary.getEntryTime());
            row.put("clockOut", summary.getExitTime());
            row.put("lateMinutes", summary.getLateMinutes());
            row.put("overtimeMinutes", summary.getOvertimeMinutes());
            result.add(row);
        }

        return result;
    }

    private List<Map<String, Object>> monthlySummary(UUID tenantId, LocalDate from, LocalDate to, UUID departmentId) {
        List<DailySummary> summaries = summaryRepository.findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(from, to);
        Map<UUID, Map<String, Object>> employeeStats = new HashMap<>();

        for (DailySummary summary : summaries) {
            Employee employee = employeeRepository.findById(summary.getEmployeeId()).orElse(null);
            if (employee == null) continue;
            if (departmentId != null && !departmentId.equals(employee.getOrgUnitId())) continue;

            Map<String, Object> stats = employeeStats.computeIfAbsent(summary.getEmployeeId(), id -> {
                Map<String, Object> s = new HashMap<>();
                s.put("employeeId", id);
                s.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
                s.put("daysWorked", 0);
                s.put("absentDays", 0);
                s.put("totalLateMin", 0);
                s.put("totalOTMin", 0);
                return s;
            });

            if ("PRESENT".equals(summary.getStatus().name())) {
                stats.put("daysWorked", (int) stats.get("daysWorked") + 1);
            } else if ("ABSENT".equals(summary.getStatus().name())) {
                stats.put("absentDays", (int) stats.get("absentDays") + 1);
            }

            stats.put("totalLateMin", (int) stats.get("totalLateMin") + summary.getLateMinutes());
            stats.put("totalOTMin", (int) stats.get("totalOTMin") + summary.getOvertimeMinutes());
        }

        return new ArrayList<>(employeeStats.values());
    }

    private List<Map<String, Object>> latenessAnalysis(UUID tenantId, LocalDate from, LocalDate to, UUID departmentId) {
        List<DailySummary> summaries = summaryRepository.findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(from, to);
        Map<UUID, Map<String, Object>> employeeStats = new HashMap<>();

        for (DailySummary summary : summaries) {
            if (summary.getLateMinutes() <= 0) continue;

            Employee employee = employeeRepository.findById(summary.getEmployeeId()).orElse(null);
            if (employee == null) continue;
            if (departmentId != null && !departmentId.equals(employee.getOrgUnitId())) continue;

            Map<String, Object> stats = employeeStats.computeIfAbsent(summary.getEmployeeId(), id -> {
                Map<String, Object> s = new HashMap<>();
                s.put("employeeId", id);
                s.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
                s.put("lateCount", 0);
                s.put("totalLateMinutes", 0);
                s.put("mostLateDate", summary.getWorkDate());
                s.put("maxLateMinutes", 0);
                return s;
            });

            stats.put("lateCount", (int) stats.get("lateCount") + 1);
            stats.put("totalLateMinutes", (int) stats.get("totalLateMinutes") + summary.getLateMinutes());

            if (summary.getLateMinutes() > (int) stats.get("maxLateMinutes")) {
                stats.put("maxLateMinutes", summary.getLateMinutes());
                stats.put("mostLateDate", summary.getWorkDate());
            }
        }

        for (Map<String, Object> stats : employeeStats.values()) {
            int total = (int) stats.get("totalLateMinutes");
            int count = (int) stats.get("lateCount");
            stats.put("avgLateMinutes", count > 0 ? total / count : 0);
        }

        return new ArrayList<>(employeeStats.values());
    }

    private List<Map<String, Object>> absenceTrend(UUID tenantId, LocalDate from, LocalDate to, UUID departmentId) {
        List<DailySummary> summaries = summaryRepository.findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(from, to);
        Map<LocalDate, Map<String, Object>> dailyStats = new HashMap<>();

        for (DailySummary summary : summaries) {
            if (!"ABSENT".equals(summary.getStatus().name())) continue;

            Employee employee = employeeRepository.findById(summary.getEmployeeId()).orElse(null);
            if (employee == null) continue;
            if (departmentId != null && !departmentId.equals(employee.getOrgUnitId())) continue;

            Map<String, Object> stats = dailyStats.computeIfAbsent(summary.getWorkDate(), date -> {
                Map<String, Object> s = new HashMap<>();
                s.put("date", date);
                s.put("absentCount", 0);
                s.put("absentEmployeeIds", new ArrayList<UUID>());
                return s;
            });

            stats.put("absentCount", (int) stats.get("absentCount") + 1);
            ((List<UUID>) stats.get("absentEmployeeIds")).add(summary.getEmployeeId());
        }

        return new ArrayList<>(dailyStats.values());
    }

    private List<Map<String, Object>> overtimeAnalysis(UUID tenantId, LocalDate from, LocalDate to, UUID departmentId) {
        List<DailySummary> summaries = summaryRepository.findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(from, to);
        Map<UUID, Map<String, Object>> employeeStats = new HashMap<>();

        for (DailySummary summary : summaries) {
            if (summary.getOvertimeMinutes() <= 0) continue;

            Employee employee = employeeRepository.findById(summary.getEmployeeId()).orElse(null);
            if (employee == null) continue;
            if (departmentId != null && !departmentId.equals(employee.getOrgUnitId())) continue;

            Map<String, Object> stats = employeeStats.computeIfAbsent(summary.getEmployeeId(), id -> {
                Map<String, Object> s = new HashMap<>();
                s.put("employeeId", id);
                s.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
                s.put("totalOTMinutes", 0);
                return s;
            });

            stats.put("totalOTMinutes", (int) stats.get("totalOTMinutes") + summary.getOvertimeMinutes());
        }

        return new ArrayList<>(employeeStats.values());
    }

    private List<Map<String, Object>> exceptionReport(UUID tenantId, LocalDate from, LocalDate to) {
        var exceptions = exceptionRepository.findByTenantIdOrderByWorkDateDescCreatedAtDesc(tenantId).stream()
                .filter(e -> !e.getWorkDate().isBefore(from) && !e.getWorkDate().isAfter(to))
                .collect(Collectors.toList());

        Map<String, Map<String, Object>> typeStats = new HashMap<>();

        for (var exception : exceptions) {
            Map<String, Object> stats = typeStats.computeIfAbsent(exception.getExceptionType(), type -> {
                Map<String, Object> s = new HashMap<>();
                s.put("exceptionType", type);
                s.put("count", 0);
                s.put("severity", exception.getSeverity());
                s.put("openCount", 0);
                return s;
            });

            stats.put("count", (int) stats.get("count") + 1);
            if ("OPEN".equals(exception.getStatus())) {
                stats.put("openCount", (int) stats.get("openCount") + 1);
            }
        }

        return new ArrayList<>(typeStats.values());
    }

    private List<Map<String, Object>> correctionHistory(UUID tenantId, LocalDate from, LocalDate to) {
        var corrections = correctionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(c -> !c.getWorkDate().isBefore(from) && !c.getWorkDate().isAfter(to))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();

        for (var correction : corrections) {
            Employee employee = employeeRepository.findById(correction.getEmployeeId()).orElse(null);

            Map<String, Object> row = new HashMap<>();
            row.put("correctionId", correction.getId());
            row.put("employeeId", correction.getEmployeeId());
            row.put("employeeName", employee != null ? employee.getFirstName() + " " + employee.getLastName() : "");
            row.put("workDate", correction.getWorkDate());
            row.put("correctionType", correction.getCorrectionType());
            row.put("workflowStatus", correction.getWorkflowStatus());
            row.put("decision", correction.getDecision());
            row.put("createdAt", correction.getCreatedAt());
            row.put("decidedAt", correction.getDecidedAt());
            result.add(row);
        }

        return result;
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found")).getId();
    }
}
