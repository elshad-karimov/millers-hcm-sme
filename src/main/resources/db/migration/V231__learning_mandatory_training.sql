-- HCM_14 LMS — M406 (Phase B.3)
-- Mandatory / compliance training rules (PRD 14 §9/§16). A rule ties a course to
-- an audience (department / position / work location — all-null = everyone) with
-- a recurrence: the daily sweep enrols anyone in scope who has never completed
-- the course, or whose last completion is older than recurrence_months.
-- Defaults (gap-check): safety/compliance 12 months, cybersecurity 6.

CREATE TABLE learning.mandatory_training_rule (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            varchar(64) NOT NULL DEFAULT 'default',
    course_id            uuid NOT NULL REFERENCES learning.course (id),
    name                 varchar(200) NOT NULL,
    department_name      varchar(200),
    position_id          uuid,
    work_location_id     uuid,
    recurrence_months    int,
    due_days             int NOT NULL DEFAULT 30,
    reminder_days_before int NOT NULL DEFAULT 7,
    active               boolean NOT NULL DEFAULT true,
    created_by           varchar(80),
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT mandatory_rule_recurrence_check CHECK (recurrence_months IS NULL OR recurrence_months > 0)
);
CREATE INDEX mandatory_rule_course_idx ON learning.mandatory_training_rule (tenant_id, course_id);

COMMENT ON TABLE learning.mandatory_training_rule IS
    'HCM_14 M406 — compliance-training rule: audience scope + recurrence; daily sweep auto-enrols (PRD 14 §9/§16).';
