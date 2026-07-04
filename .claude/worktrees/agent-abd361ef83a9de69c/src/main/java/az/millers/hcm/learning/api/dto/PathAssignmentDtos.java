package az.millers.hcm.learning.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.learning.domain.PathAssignmentStatus;

/** DTOs for learning path assignment (M95). */
public final class PathAssignmentDtos {
    private PathAssignmentDtos() {}

    /** Inbound payload for "assign this employee to this path". */
    public record AssignRequest(
            UUID employeeId,
            LocalDate targetCompletionDate,
            String notes) {}

    /** Inbound payload for "cancel this assignment". */
    public record CancelRequest(String reason) {}

    /** Per-step progress derived from {@code Enrollment} rows. */
    public record StepProgress(
            UUID stepId,
            int stepOrder,
            UUID courseId,
            String courseCode,
            String courseTitle,
            boolean requiredToAdvance,
            String status,            // EnrollmentStatus name, or "NOT_STARTED"
            boolean completed) {}

    /** Inbound payload for bulk-assigning a path to multiple employees (M101). */
    public record BulkAssignRequest(
            java.util.List<java.util.UUID> employeeIds,
            java.time.LocalDate targetCompletionDate,
            String notes) {}

    /** Per-employee outcome row for a bulk assign (M101). */
    public record BulkAssignRow(
            java.util.UUID employeeId,
            /** null on success; message on skip/error. */
            String outcome,
            /** true iff the assignment was created. */
            boolean success) {}

    /** Top-level bulk assign result (M101). */
    public record BulkAssignResult(
            int requested,
            int succeeded,
            int skipped,
            int failed,
            java.util.List<BulkAssignRow> rows) {}

    /**
     * Bucketed home-dashboard summary of active path assignments (M99).
     *
     * <p>Named {@code PathBacklogSummary} (not just {@code AssignmentSummary})
     * because {@code SuccessionGridDtos.AssignmentSummary} is a different
     * concept (per-employee assignment snapshot for the M96 drill).
     */
    public record PathBacklogSummary(
            /** ASSIGNED or IN_PROGRESS with a target date set. */
            long active,
            /** Active assignments with no target date — no reminder fires. */
            long noTarget,
            /** target_completion_date < today. */
            long overdue,
            /** today &le; target_completion_date &le; today+7. */
            long dueWithin7,
            /** today+8 &le; target_completion_date &le; today+30. */
            long dueWithin30) {}

    /** A learning path suggested for an employee, ranked by competency-gap coverage (M98). */
    public record SuggestedPath(
            UUID pathId,
            String pathCode,
            String pathName,
            int totalSteps,
            /** Distinct competencies this path's courses can close for the employee. */
            int competenciesCovered,
            /** Sum across covered competencies of how many levels the path advances. */
            int totalLevelLift,
            /** Competency names covered — small list for the UI tooltip. */
            java.util.List<String> coveredCompetencyNames,
            /** True if the employee already has an active assignment for this path. */
            boolean alreadyAssigned) {}

    /** Top-level assignment view, with derived progress. */
    public record AssignmentResponse(
            UUID id,
            UUID pathId,
            String pathName,
            UUID employeeId,
            String employeeName,
            PathAssignmentStatus status,
            OffsetDateTime assignedAt,
            String assignedBy,
            LocalDate targetCompletionDate,
            OffsetDateTime completedAt,
            OffsetDateTime cancelledAt,
            String cancellationReason,
            String notes,
            int totalSteps,
            int completedSteps,
            int requiredSteps,
            int requiredCompleted,
            int progressPercent,
            List<StepProgress> steps) {}
}
