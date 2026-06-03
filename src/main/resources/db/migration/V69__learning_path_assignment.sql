-- M95 — Learning path assignments (Individual Development Plans).
--
-- A LearningPath is a template; an assignment is "this employee is on this
-- path, with this target completion date, and may have been auto-assigned
-- because they landed in the under-development bucket of M94's 9-box, or
-- manually nominated by HR." Per-step progress is derived at read time
-- from existing Enrollment rows (one Enrollment per course-step), so this
-- table only needs to carry the lifecycle metadata.

CREATE TABLE learning.learning_path_assignment (
    id                       uuid PRIMARY KEY,
    path_id                  uuid NOT NULL REFERENCES learning.learning_path(id),
    employee_id              uuid NOT NULL,  -- ref to core_hr.employee (no FK across schemas in this codebase)
    assigned_by              varchar(80),
    assigned_at              timestamptz NOT NULL DEFAULT now(),
    target_completion_date   date,
    status                   varchar(20) NOT NULL DEFAULT 'ASSIGNED',
    completed_at             timestamptz,
    cancelled_at             timestamptz,
    cancellation_reason      text,
    notes                    text,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT learning_path_assignment_status_check
        CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT learning_path_assignment_terminal_dates
        CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
            OR (status NOT IN ('COMPLETED', 'CANCELLED')))
);

-- An employee can have at most ONE active (non-terminal) assignment per path.
-- Re-assignment after cancellation is allowed (filtered with the partial index).
CREATE UNIQUE INDEX learning_path_assignment_active_unique
    ON learning.learning_path_assignment (path_id, employee_id)
    WHERE status IN ('ASSIGNED', 'IN_PROGRESS');

CREATE INDEX learning_path_assignment_employee_idx
    ON learning.learning_path_assignment (employee_id, status);

CREATE INDEX learning_path_assignment_path_idx
    ON learning.learning_path_assignment (path_id, status);

COMMENT ON TABLE learning.learning_path_assignment IS
    'M95 — Tracks an employee being assigned to a LearningPath template. Progress is derived from Enrollment rows per course-step.';
COMMENT ON COLUMN learning.learning_path_assignment.status IS
    'ASSIGNED → IN_PROGRESS (any step started) → COMPLETED (all required steps passed) | CANCELLED.';
