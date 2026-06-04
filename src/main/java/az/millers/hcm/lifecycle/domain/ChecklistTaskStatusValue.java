package az.millers.hcm.lifecycle.domain;

/** Per-task status on a {@link ChecklistAssignment} (M105/M106). */
public enum ChecklistTaskStatusValue {
    PENDING,
    IN_PROGRESS,
    DONE,
    SKIPPED
}
