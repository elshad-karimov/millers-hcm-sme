package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.DeptCount;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.ManagerOnboardingView;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingJourney;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingOverview;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.OnboardingRow;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.TypeCount;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import az.millers.hcm.lifecycle.domain.ChecklistAssignment;
import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.repo.ChecklistAssignmentRepository;

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
    private final EmployeeRepository employees;
    private final EmployeeContextService employeeContext;

    public OnboardingService(ChecklistService checklistService,
                             ChecklistAssignmentRepository assignments,
                             EmployeeRepository employees,
                             EmployeeContextService employeeContext) {
        this.checklistService = checklistService;
        this.assignments = assignments;
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
}
