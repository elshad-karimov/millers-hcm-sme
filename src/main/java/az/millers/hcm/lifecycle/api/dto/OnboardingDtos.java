package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.AssignmentResponse;
import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;

/**
 * Read models for the onboarding journey hub + HR onboarding console
 * (M300 — Onboarding Phase A.3). These are onboarding-specific projections
 * over the generic M105 checklist engine, enriched with employee context
 * (department, join date) and operational signals (overdue tasks, next due).
 */
public final class OnboardingDtos {
    private OnboardingDtos() {}

    /** One active onboarding in the HR console. */
    public record OnboardingRow(
            UUID assignmentId,
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String department,
            String templateName,
            /** Hire/join date — the checklist anchor for ONBOARDING. */
            LocalDate joinDate,
            /** Days until join (negative = already joined), null if no join date. */
            Long daysToJoin,
            ChecklistAssignmentStatus status,
            int totalTasks,
            int completedTasks,
            int requiredTotal,
            int requiredCompleted,
            int progressPercent,
            int overdueTaskCount,
            LocalDate nextDueDate) {}

    /** Pending (not done/skipped) task count by task type — feeds the persona widgets. */
    public record TypeCount(String taskType, long pending) {}

    /** Per-department onboarding load + overdue pressure. */
    public record DeptCount(String department, long onboardings, long overdueTasks) {}

    /**
     * The HR onboarding console: top-line stats + the active onboarding rows
     * + pending-by-type and by-department breakdowns. "Joining this week/month"
     * count <em>upcoming</em> joins (join date within the next 7 / 30 days).
     */
    public record OnboardingOverview(
            long active,
            long joiningThisWeek,
            long joiningThisMonth,
            long assignmentsWithOverdue,
            long totalOverdueTasks,
            List<OnboardingRow> rows,
            List<TypeCount> pendingByType,
            List<DeptCount> byDepartment) {}

    /**
     * The new-hire onboarding journey: employee context + their single active
     * onboarding assignment (with its tasks), or null if none is running.
     */
    public record OnboardingJourney(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String department,
            LocalDate joinDate,
            Long daysToJoin,
            boolean hasActiveOnboarding,
            AssignmentResponse assignment) {}

    // ── M304 — policy / contract / document acknowledgement (Phase B.4) ──

    /** Record an acknowledgement against a checklist task; marks it DONE. */
    public record AcknowledgeRequest(
            UUID taskStatusId,
            String statement,
            String reference) {}

    public record AcknowledgementResponse(
            UUID id,
            UUID employeeId,
            UUID assignmentId,
            UUID taskStatusId,
            String kind,
            String statement,
            String reference,
            String acknowledgedBy,
            OffsetDateTime acknowledgedAt) {}
}
