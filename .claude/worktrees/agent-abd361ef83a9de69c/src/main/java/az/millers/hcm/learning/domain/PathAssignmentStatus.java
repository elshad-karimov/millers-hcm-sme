package az.millers.hcm.learning.domain;

/**
 * Lifecycle of a {@link LearningPathAssignment} (M95). Mirrors the V69
 * CHECK constraint exactly — keep in sync.
 */
public enum PathAssignmentStatus {
    /** Just assigned; no step started yet. */
    ASSIGNED,
    /** At least one course-step has begun. */
    IN_PROGRESS,
    /** Every required step has been passed. Terminal. */
    COMPLETED,
    /** Cancelled by HR or the assignee. Terminal. */
    CANCELLED
}
