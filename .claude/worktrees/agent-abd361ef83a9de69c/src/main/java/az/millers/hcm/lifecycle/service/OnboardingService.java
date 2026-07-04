package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.AssignmentResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TaskStatusResponse;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.DeptAnalytics;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.DeptCount;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.ManagerOnboardingView;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingAnalyticsReport;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingJourney;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingOverview;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingRow;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.TaskTypeAnalytics;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.TemplateAnalytics;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.TypeCount;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import az.millers.hcm.lifecycle.domain.ChecklistAssignment;
import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.ChecklistTemplate;
import az.millers.hcm.lifecycle.repo.ChecklistAssignmentRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTemplateRepository;

/**
 * Onboarding journey hub + HR console read models (M300 — Onboarding Phase A.3).
 *
 * <p>An onboarding-specific projection layer <em>on top of</em> the generic
 * {@link ChecklistService} engine — it does not re-implement task assembly, it
 * reuses {@code activeByFlow}/{@code getAssignment} and enriches the result with
 * employee context (department, join date) and operational signals (overdue
 * tasks, next due date, pending-by-type). The persona dashboards (Phase E)
 * layer analytics on the same projections.
 */
@Service
public class OnboardingService {

    /** Upcoming-join windows for the console stats. */
    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;

    private final ChecklistService checklistService;
    private final ChecklistAssignmentRepository assignments;
    private final ChecklistTaskStatusRepository taskStatuses;
    private final ChecklistTemplateRepository templates;
    private final EmployeeRepository employees;
    private final EmployeeContextService employeeContext;

    public OnboardingService(ChecklistService checklistService,
                             ChecklistAssignmentRepository assignments,
                             ChecklistTaskStatusRepository taskStatuses,
                             ChecklistTemplateRepository templates,
                             EmployeeRepository employees,
                             EmployeeContextService employeeContext) {
        this.checklistService = checklistService;
        this.assignments = assignments;
        this.taskStatuses = taskStatuses;
        this.templates = templates;
        this.employees = employees;
        this.employeeContext = employeeContext;
    }

    @Transactional(readOnly = true)
    public OnboardingOverview overview() {
        LocalDate today = LocalDate.now();
        List<AssignmentResponse> active =
                checklistService.activeByFlow(ChecklistFlowType.ONBOARDING);

        // Bulk-load employees once (avoid N+1 over the assignment list).
        List<UUID> empIds = active.stream().map(AssignmentResponse::employeeId).distinct().toList();
        Map<UUID, Employee> empById = new LinkedHashMap<>();
        employees.findAllById(empIds).forEach(e -> empById.put(e.getId(), e));

        List<OnboardingRow> rows = new ArrayList<>(active.size());
        Map<String, long[]> pendingByType = new LinkedHashMap<>();   // type -> {pending}
        Map<String, long[]> byDept = new LinkedHashMap<>();          // dept -> {onboardings, overdueTasks}
        long joiningWeek = 0, joiningMonth = 0, withOverdue = 0, totalOverdue = 0;

        for (AssignmentResponse a : active) {
            Employee e = empById.get(a.employeeId());
            String dept = e == null || e.getDepartmentName() == null ? "—" : e.getDepartmentName();
            LocalDate joinDate = a.anchorDate();
            Long daysToJoin = joinDate == null ? null : ChronoUnit.DAYS.between(today, joinDate);

            int overdue = 0;
            LocalDate nextDue = null;
            for (TaskStatusResponse t : a.tasks()) {
                boolean open = t.status() != ChecklistTaskStatusValue.DONE
                        && t.status() != ChecklistTaskStatusValue.SKIPPED;
                if (open) {
                    pendingByType.computeIfAbsent(t.taskType(), k -> new long[1])[0]++;
                    if (t.dueDate() != null) {
                        if (t.dueDate().isBefore(today)) overdue++;
                        if (nextDue == null || t.dueDate().isBefore(nextDue)) nextDue = t.dueDate();
                    }
                }
            }
            totalOverdue += overdue;
            if (overdue > 0) withOverdue++;
            if (joinDate != null && !joinDate.isBefore(today)) {
                if (!joinDate.isAfter(today.plusDays(WEEK_DAYS))) joiningWeek++;
                if (!joinDate.isAfter(today.plusDays(MONTH_DAYS))) joiningMonth++;
            }

            long[] d = byDept.computeIfAbsent(dept, k -> new long[2]);
            d[0]++; d[1] += overdue;

            rows.add(new OnboardingRow(
                    a.id(), a.employeeId(),
                    e == null ? null : e.getEmployeeNo(),
                    a.employeeName(), dept, a.templateName(),
                    joinDate, daysToJoin, a.status(),
                    a.totalTasks(), a.completedTasks(),
                    a.requiredTotal(), a.requiredCompleted(),
                    a.progressPercent(), overdue, nextDue));
        }

        // Most-pressing first: overdue desc, then soonest next-due.
        rows.sort(Comparator
                .comparingInt(OnboardingRow::overdueTaskCount).reversed()
                .thenComparing(r -> r.nextDueDate() == null ? LocalDate.MAX : r.nextDueDate()));

        List<TypeCount> typeCounts = pendingByType.entrySet().stream()
                .map(en -> new TypeCount(en.getKey(), en.getValue()[0]))
                .sorted(Comparator.comparingLong(TypeCount::pending).reversed())
                .toList();
        List<DeptCount> deptCounts = byDept.entrySet().stream()
                .map(en -> new DeptCount(en.getKey(), en.getValue()[0], en.getValue()[1]))
                .sorted(Comparator.comparingLong(DeptCount::onboardings).reversed())
                .toList();

        return new OnboardingOverview(
                active.size(), joiningWeek, joiningMonth, withOverdue, totalOverdue,
                rows, typeCounts, deptCounts);
    }

    @Transactional(readOnly = true)
    public ManagerOnboardingView managerView() {
        Employee manager = employeeContext.currentEmployee();
        String managerName = ((manager.getFirstName() == null ? "" : manager.getFirstName()) + " "
                + (manager.getLastName() == null ? "" : manager.getLastName())).trim();

        List<Employee> directs = employees.findDirectReports(manager.getId());
        Set<UUID> directIds = directs.stream().map(Employee::getId).collect(Collectors.toSet());
        if (directIds.isEmpty()) {
            return new ManagerOnboardingView(managerName, 0, 0, 0, List.of());
        }

        LocalDate today = LocalDate.now();
        Map<UUID, Employee> empById = new LinkedHashMap<>();
        directs.forEach(e -> empById.put(e.getId(), e));

        List<AssignmentResponse> allActive = checklistService.activeByFlow(ChecklistFlowType.ONBOARDING);
        List<OnboardingRow> rows = new ArrayList<>();
        long overdueCount = 0;

        for (AssignmentResponse a : allActive) {
            if (!directIds.contains(a.employeeId())) continue;
            Employee e = empById.get(a.employeeId());
            String dept = e == null || e.getDepartmentName() == null ? "—" : e.getDepartmentName();
            LocalDate joinDate = a.anchorDate();
            Long daysToJoin = joinDate == null ? null : ChronoUnit.DAYS.between(today, joinDate);

            int overdue = 0;
            LocalDate nextDue = null;
            for (TaskStatusResponse t : a.tasks()) {
                boolean open = t.status() != ChecklistTaskStatusValue.DONE
                        && t.status() != ChecklistTaskStatusValue.SKIPPED;
                if (open && t.dueDate() != null) {
                    if (t.dueDate().isBefore(today)) overdue++;
                    if (nextDue == null || t.dueDate().isBefore(nextDue)) nextDue = t.dueDate();
                }
            }
            if (overdue > 0) overdueCount++;

            rows.add(new OnboardingRow(
                    a.id(), a.employeeId(),
                    e == null ? null : e.getEmployeeNo(),
                    a.employeeName(), dept, a.templateName(),
                    joinDate, daysToJoin, a.status(),
                    a.totalTasks(), a.completedTasks(),
                    a.requiredTotal(), a.requiredCompleted(),
                    a.progressPercent(), overdue, nextDue));
        }

        rows.sort(Comparator
                .comparingInt(OnboardingRow::overdueTaskCount).reversed()
                .thenComparing(r -> r.nextDueDate() == null ? LocalDate.MAX : r.nextDueDate()));

        return new ManagerOnboardingView(managerName, directs.size(), rows.size(), overdueCount, rows);
    }

    @Transactional(readOnly = true)
    public OnboardingJourney journey(UUID employeeId) {
        Employee e = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        Optional<ChecklistAssignment> active = assignments.findByEmployeeIdAndFlowTypeAndStatus(
                employeeId, ChecklistFlowType.ONBOARDING, ChecklistAssignmentStatus.IN_PROGRESS);
        AssignmentResponse ar = active.map(a -> checklistService.getAssignment(a.getId())).orElse(null);

        LocalDate joinDate = ar != null ? ar.anchorDate() : e.getHireDate();
        Long daysToJoin = joinDate == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), joinDate);
        String name = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();

        return new OnboardingJourney(
                e.getId(), e.getEmployeeNo(), name, e.getDepartmentName(),
                joinDate, daysToJoin, ar != null, ar);
    }

    /**
     * M312 — Onboarding analytics report for a date window.
     * 2-query design: bulk-load assignments, then bulk-load all their tasks — avoids N+1.
     */
    @Transactional(readOnly = true)
    public OnboardingAnalyticsReport analyticsReport(LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toDt   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        List<ChecklistAssignment> asgns = assignments
                .findByFlowTypeAndStartedAtBetweenOrderByStartedAtDesc(
                        ChecklistFlowType.ONBOARDING, fromDt, toDt);

        if (asgns.isEmpty()) {
            return new OnboardingAnalyticsReport(from, to, 0, 0, 0, 0, 0.0,
                    List.of(), List.of(), List.of());
        }

        Set<UUID> empIds = asgns.stream().map(ChecklistAssignment::getEmployeeId)
                .collect(Collectors.toSet());
        Map<UUID, Employee> empById = new LinkedHashMap<>();
        employees.findAllById(empIds).forEach(e -> empById.put(e.getId(), e));

        Set<UUID> templateIds = asgns.stream().map(ChecklistAssignment::getTemplateId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ChecklistTemplate> tplById = new LinkedHashMap<>();
        templates.findAllById(templateIds).forEach(t -> tplById.put(t.getId(), t));

        List<UUID> asnIds = asgns.stream().map(ChecklistAssignment::getId).toList();
        Map<UUID, List<ChecklistTaskStatus>> tasksByAssignment = taskStatuses
                .findByAssignmentIdIn(asnIds).stream()
                .collect(Collectors.groupingBy(ChecklistTaskStatus::getAssignmentId));

        // dept → [started, completed, daysSum, daysCount]
        Map<String, long[]> deptMap = new LinkedHashMap<>();
        // templateId → [started, completed, daysSum, daysCount]
        Map<UUID, long[]> tplMap = new LinkedHashMap<>();
        // taskType → [total, done]
        Map<String, long[]> typeMap = new LinkedHashMap<>();

        int totalCompleted = 0;
        double totalDaysSum = 0;
        int totalDaysCount = 0;

        for (ChecklistAssignment a : asgns) {
            Employee emp = empById.get(a.getEmployeeId());
            String dept = emp == null || emp.getDepartmentName() == null ? "—" : emp.getDepartmentName();
            boolean completed = a.getStatus() == ChecklistAssignmentStatus.COMPLETED;

            double daysToComplete = 0;
            boolean hasDays = false;
            if (completed && a.getCompletedAt() != null && a.getStartedAt() != null) {
                daysToComplete = ChronoUnit.DAYS.between(a.getStartedAt(), a.getCompletedAt());
                hasDays = true;
                totalDaysSum += daysToComplete;
                totalDaysCount++;
                totalCompleted++;
            }

            long[] d = deptMap.computeIfAbsent(dept, k -> new long[4]);
            d[0]++;
            if (hasDays) { d[1]++; d[2] += (long) daysToComplete; d[3]++; }
            else if (completed) d[1]++;

            UUID tplId = a.getTemplateId();
            if (tplId != null) {
                long[] t = tplMap.computeIfAbsent(tplId, k -> new long[4]);
                t[0]++;
                if (hasDays) { t[1]++; t[2] += (long) daysToComplete; t[3]++; }
                else if (completed) t[1]++;
            }

            for (ChecklistTaskStatus ts : tasksByAssignment.getOrDefault(a.getId(), List.of())) {
                String type = ts.getTaskType() == null ? "MANUAL_TASK" : ts.getTaskType().name();
                long[] tv = typeMap.computeIfAbsent(type, k -> new long[2]);
                tv[0]++;
                if (ts.getStatus() == ChecklistTaskStatusValue.DONE) tv[1]++;
            }
        }

        int totalStarted = asgns.size();
        int completionRatePct = (int) Math.round(100.0 * totalCompleted / totalStarted);
        double avgDays = totalDaysCount == 0 ? 0.0
                : Math.round(10.0 * totalDaysSum / totalDaysCount) / 10.0;

        List<DeptAnalytics> byDept = deptMap.entrySet().stream().map(en -> {
            long[] v = en.getValue();
            int rate = v[0] == 0 ? 0 : (int) Math.round(100.0 * v[1] / v[0]);
            double avg = v[3] == 0 ? 0.0 : Math.round(10.0 * v[2] / v[3]) / 10.0;
            return new DeptAnalytics(en.getKey(), (int) v[0], (int) v[1], rate, avg);
        }).sorted(Comparator.comparingInt(DeptAnalytics::started).reversed()).toList();

        List<TemplateAnalytics> byTemplate = tplMap.entrySet().stream().map(en -> {
            long[] v = en.getValue();
            ChecklistTemplate tpl = tplById.get(en.getKey());
            String name = tpl == null ? en.getKey().toString() : tpl.getName();
            int rate = v[0] == 0 ? 0 : (int) Math.round(100.0 * v[1] / v[0]);
            double avg = v[3] == 0 ? 0.0 : Math.round(10.0 * v[2] / v[3]) / 10.0;
            return new TemplateAnalytics(name, (int) v[0], (int) v[1], rate, avg);
        }).sorted(Comparator.comparingInt(TemplateAnalytics::started).reversed()).toList();

        List<TaskTypeAnalytics> taskBottlenecks = typeMap.entrySet().stream().map(en -> {
            long[] v = en.getValue();
            int rate = v[0] == 0 ? 0 : (int) Math.round(100.0 * v[1] / v[0]);
            return new TaskTypeAnalytics(en.getKey(), (int) v[0], (int) v[1], rate);
        }).sorted(Comparator.comparingInt(TaskTypeAnalytics::completionRatePct)).toList();

        return new OnboardingAnalyticsReport(from, to, totalStarted, totalCompleted,
                totalStarted - totalCompleted, completionRatePct, avgDays,
                byDept, byTemplate, taskBottlenecks);
    }
}
