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
