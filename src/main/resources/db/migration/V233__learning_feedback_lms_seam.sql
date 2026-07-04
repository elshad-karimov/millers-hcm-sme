-- HCM_14 LMS — M408 (Phase B.5)
-- Training feedback (PRD 14 §20 — anonymous by default; instructor rating rolls
-- up onto learning.instructor.rating) + the external-LMS integration seam
-- (PRD 14 §21 — config only; live sync is a documented later seam).

CREATE TABLE learning.training_feedback (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         varchar(64) NOT NULL DEFAULT 'default',
    session_id        uuid REFERENCES learning.training_session (id) ON DELETE CASCADE,
    course_id         uuid REFERENCES learning.course (id),
    employee_id       uuid NOT NULL,
    overall_rating    int NOT NULL,
    content_rating    int,
    instructor_rating int,
    comment           varchar(2000),
    anonymous         boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT training_feedback_target_check CHECK (session_id IS NOT NULL OR course_id IS NOT NULL),
    CONSTRAINT training_feedback_session_unique UNIQUE (session_id, employee_id),
    CONSTRAINT training_feedback_ratings_check CHECK (
        overall_rating BETWEEN 1 AND 5
        AND (content_rating IS NULL OR content_rating BETWEEN 1 AND 5)
        AND (instructor_rating IS NULL OR instructor_rating BETWEEN 1 AND 5))
);
CREATE INDEX training_feedback_session_idx ON learning.training_feedback (session_id);
CREATE INDEX training_feedback_course_idx ON learning.training_feedback (tenant_id, course_id);

-- §21 — external LMS seam: config only, no live calls yet.
CREATE TABLE learning.lms_integration_config (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        varchar(64) NOT NULL DEFAULT 'default',
    name             varchar(120) NOT NULL,
    endpoint_url     varchar(500),
    auth_type        varchar(20) NOT NULL DEFAULT 'NONE',
    sync_enrollments boolean NOT NULL DEFAULT false,
    sync_completions boolean NOT NULL DEFAULT false,
    active           boolean NOT NULL DEFAULT false,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT lms_config_auth_check CHECK (auth_type IN ('NONE','API_KEY','OAUTH'))
);

COMMENT ON TABLE learning.training_feedback IS
    'HCM_14 M408 — post-training feedback, anonymous by default (PRD 14 §20).';
COMMENT ON TABLE learning.lms_integration_config IS
    'HCM_14 M408 — external-LMS integration SEAM (PRD 14 §21): configuration only, sync deferred.';
