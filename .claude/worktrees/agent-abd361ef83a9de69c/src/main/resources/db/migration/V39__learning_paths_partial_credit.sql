-- Milestone 46: Partial-credit MULTI_SELECT scoring + Learning Paths.
--
-- Part 1: Widen points columns to allow fractional awards.
--   quiz_answer.points_awarded: INTEGER → NUMERIC(5,2)
--     A MULTI_SELECT question worth 4 pts with 3 correct keys awards 1.33 per hit.
--   quiz_attempt.earned_points: INTEGER → NUMERIC(7,2)
--     Sum of potentially fractional per-answer awards.
--
-- Part 2: Learning path tables.
--   learning_path        — named, ordered sequence of courses.
--   learning_path_course — one step (course + position) in a path.
--   Completing a required step triggers auto-enrollment in the next step.

-- ── Part 1: Widen points columns ─────────────────────────────────────────────

ALTER TABLE learning.quiz_answer
    ALTER COLUMN points_awarded TYPE NUMERIC(5,2) USING points_awarded::numeric;

ALTER TABLE learning.quiz_attempt
    ALTER COLUMN earned_points  TYPE NUMERIC(7,2) USING earned_points::numeric;

-- ── Part 2: Learning path schema ─────────────────────────────────────────────

CREATE SEQUENCE learning.learning_path_no_seq START WITH 1;

CREATE TABLE learning.learning_path (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    path_no      VARCHAR(32)  NOT NULL UNIQUE,
    name         VARCHAR(240) NOT NULL,
    description  TEXT,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_lp_active ON learning.learning_path (active) WHERE active = TRUE;

CREATE TABLE learning.learning_path_course (
    id                   UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    path_id              UUID    NOT NULL REFERENCES learning.learning_path (id) ON DELETE CASCADE,
    course_id            UUID    NOT NULL REFERENCES learning.course (id) ON DELETE CASCADE,
    -- 1-based position within the path; gaps allowed (e.g. 10, 20, 30 for easy reordering).
    step_order           INTEGER NOT NULL,
    -- When TRUE, the employee must PASS this course before the engine auto-enrolls them
    -- in the next step. When FALSE the step is advisory only (no auto-enroll follows).
    required_to_advance  BOOLEAN NOT NULL DEFAULT TRUE,

    UNIQUE (path_id, step_order),
    UNIQUE (path_id, course_id),
    CONSTRAINT chk_lpc_step CHECK (step_order >= 1)
);

CREATE INDEX idx_lpc_path   ON learning.learning_path_course (path_id, step_order);
CREATE INDEX idx_lpc_course ON learning.learning_path_course (course_id);
