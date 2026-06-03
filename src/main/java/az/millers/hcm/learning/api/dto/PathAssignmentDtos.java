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
