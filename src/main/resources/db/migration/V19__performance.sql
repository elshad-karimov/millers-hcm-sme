-- Performance Management System (PRD Section 8.13).
-- Tables: review_cycle (HR-defined date windows), goal (SMART goals with
-- weight + progress + status), performance_review (per employee per cycle
-- with self + manager + final ratings), feedback (180/360 peer feedback
-- within a cycle). Review submission runs through PERFORMANCE_REVIEW_APPROVAL.

CREATE SEQUENCE performance.goal_no_seq    START WITH 1;
CREATE SEQUENCE performance.review_no_seq  START WITH 1;

-- Review cycles (PRD 8.13.1): annual, quarterly, mid-year, project-based.
CREATE TABLE performance.review_cycle (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    -- ANNUAL, MID_YEAR, QUARTERLY, PROBATION, PROJECT, ADHOC
    cycle_type      VARCHAR(24)  NOT NULL,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    self_review_due DATE,
    manager_review_due DATE,
    final_due       DATE,
    -- DRAFT, OPEN, CLOSED, CALIBRATING, COMPLETED  (PRD 8.13.2)
    status          VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    rating_scale    JSONB,        -- e.g. {"min":1,"max":5,"labels":{"1":"Needs Improvement",…}}
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120),

    CONSTRAINT chk_cycle_dates CHECK (period_end >= period_start)
);

CREATE INDEX idx_cycle_status ON performance.review_cycle (status);
CREATE INDEX idx_cycle_period ON performance.review_cycle (period_start, period_end);

-- Goals (PRD 8.13.3): SMART goals belonging to an employee for a cycle,
-- with weight (% of overall score), progress (0-100), and an optional
-- parent goal for cascading (OKR-style alignment).
CREATE TABLE performance.goal (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_no          VARCHAR(32)  NOT NULL UNIQUE,
    cycle_id         UUID         NOT NULL REFERENCES performance.review_cycle (id),
    employee_id      UUID         NOT NULL,
    parent_goal_id   UUID         REFERENCES performance.goal (id),     -- OKR cascade
    title            VARCHAR(240) NOT NULL,
    description      TEXT,
    -- COMPANY, DEPARTMENT, TEAM, INDIVIDUAL, DEVELOPMENT
    category         VARCHAR(24)  NOT NULL,
    target_metric    VARCHAR(240),
    weight_percent   NUMERIC(5, 2) NOT NULL DEFAULT 0,
    progress_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    -- DRAFT, ACTIVE, ON_TRACK, AT_RISK, BLOCKED, ACHIEVED, MISSED, CANCELLED
    status           VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    due_date         DATE,
    -- Final per-goal rating after the review closes (1..5 by default).
    rating           NUMERIC(4, 2),
    rating_note      TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(120),
    updated_by       VARCHAR(120),

    CONSTRAINT chk_goal_weight   CHECK (weight_percent >= 0 AND weight_percent <= 100),
    CONSTRAINT chk_goal_progress CHECK (progress_percent >= 0 AND progress_percent <= 100),
    CONSTRAINT chk_goal_rating   CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);

CREATE INDEX idx_goal_cycle    ON performance.goal (cycle_id);
CREATE INDEX idx_goal_employee ON performance.goal (employee_id);
CREATE INDEX idx_goal_parent   ON performance.goal (parent_goal_id);
CREATE INDEX idx_goal_status   ON performance.goal (status);

-- Performance reviews (PRD 8.13.4): one per (employee, cycle).
CREATE TABLE performance.performance_review (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    review_no             VARCHAR(32)  NOT NULL UNIQUE,
    cycle_id              UUID         NOT NULL REFERENCES performance.review_cycle (id),
    employee_id           UUID         NOT NULL,
    manager_id            UUID,

    -- DRAFT, SELF_IN_PROGRESS, SELF_SUBMITTED, MANAGER_IN_PROGRESS, MANAGER_SUBMITTED,
    -- PENDING_APPROVAL, CALIBRATING, APPROVED, COMPLETED, REJECTED, CANCELLED
    status                VARCHAR(32)  NOT NULL,
    workflow_instance_id  UUID,

    -- Self-assessment
    self_rating           NUMERIC(4, 2),
    self_comments         TEXT,
    self_submitted_at     TIMESTAMPTZ,

    -- Manager assessment
    manager_rating        NUMERIC(4, 2),
    manager_comments      TEXT,
    manager_submitted_at  TIMESTAMPTZ,

    -- Final / calibrated overall rating (1..5)
    final_rating          NUMERIC(4, 2),
    final_band            VARCHAR(40),       -- e.g. "Exceeds Expectations"
    calibration_notes     TEXT,

    -- Cached goal score (weighted avg of goal ratings) for the auditor trace.
    goal_score            NUMERIC(6, 3),

    -- Recommendation tied to the bonus / merit matrix (PRD 8.13.7)
    -- e.g. PROMOTION, MERIT_INCREASE, BONUS_TIER_A/B/C, PIP, NONE
    recommendation        VARCHAR(40),
    bonus_percent         NUMERIC(5, 2),

    note                  TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(120),
    closed_at             TIMESTAMPTZ,

    UNIQUE (cycle_id, employee_id),
    CONSTRAINT chk_rev_self    CHECK (self_rating    IS NULL OR (self_rating    >= 0 AND self_rating    <= 5)),
    CONSTRAINT chk_rev_manager CHECK (manager_rating IS NULL OR (manager_rating >= 0 AND manager_rating <= 5)),
    CONSTRAINT chk_rev_final   CHECK (final_rating   IS NULL OR (final_rating   >= 0 AND final_rating   <= 5))
);

CREATE INDEX idx_review_cycle    ON performance.performance_review (cycle_id);
CREATE INDEX idx_review_employee ON performance.performance_review (employee_id);
CREATE INDEX idx_review_status   ON performance.performance_review (status);

-- 180° / 360° peer feedback (PRD 8.13.5). Captures peer / direct-report /
-- cross-functional feedback for an employee within a cycle. Anonymous
-- responses can be enabled by setting visibility = ANONYMOUS.
CREATE TABLE performance.feedback (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id          UUID         NOT NULL REFERENCES performance.review_cycle (id),
    subject_employee_id UUID       NOT NULL,                         -- who is being reviewed
    author_employee_id  UUID,                                         -- nullable when anonymous
    -- PEER, DIRECT_REPORT, MANAGER, SKIP_LEVEL, CROSS_FUNCTIONAL, EXTERNAL, SELF
    relationship      VARCHAR(32)  NOT NULL,
    -- NORMAL, ANONYMOUS
    visibility        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    overall_rating    NUMERIC(4, 2),
    strengths         TEXT,
    improvements      TEXT,
    comments          TEXT,
    competencies      JSONB,           -- {"leadership":4,"communication":3,…}
    -- DRAFT, SUBMITTED
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    submitted_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),

    CONSTRAINT chk_feedback_rating CHECK (overall_rating IS NULL OR (overall_rating >= 0 AND overall_rating <= 5))
);

CREATE INDEX idx_feedback_cycle    ON performance.feedback (cycle_id);
CREATE INDEX idx_feedback_subject  ON performance.feedback (subject_employee_id);
CREATE INDEX idx_feedback_relation ON performance.feedback (relationship);

-- PERFORMANCE_REVIEW_APPROVAL workflow (PRD 8.13.4): Manager → HR review → Executive sign-off.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'PERFORMANCE_REVIEW_APPROVAL',
    'Performance Review Approval',
    'Multi-step approval for a performance review (PRD 8.13.4). Manager / HR / Executive roles approximated until the full role catalogue lands.',
    FALSE,
    TRUE
);
INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 1, 'Manager review',     'ROLE_HR_SPECIALIST'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 2, 'HR / Calibration',   'ROLE_HR_ADMIN'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 3, 'Executive sign-off', 'ROLE_SYSTEM_ADMIN');

-- Seed default rating scale (1..5) — referenced by the UI when a cycle has no override.
INSERT INTO performance.review_cycle (id, code, name, cycle_type, period_start, period_end,
                                        self_review_due, manager_review_due, final_due, status,
                                        description, rating_scale, created_by)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'ANNUAL-2026',
    'Annual Review 2026',
    'ANNUAL',
    '2026-01-01', '2026-12-31',
    '2026-12-15', '2026-12-22', '2026-12-31',
    'OPEN',
    'Default annual review cycle for 2026. Calendar-year objectives + 360 feedback.',
    '{"min":1,"max":5,"labels":{"1":"Needs Improvement","2":"Below Expectations","3":"Meets Expectations","4":"Exceeds Expectations","5":"Outstanding"}}'::jsonb,
    'system'
);
