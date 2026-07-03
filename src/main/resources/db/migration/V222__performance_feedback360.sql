-- HCM_12 Performance Management — M395 (Phase C)
-- 360° feedback depth (PRD §13–§16): reviewer NOMINATION with approve/decline/
-- complete lifecycle (§13.2), reusable QUESTIONNAIRES (§13.3), and per-cycle
-- min/max reviewer rules (§13.4). Responses stay on the EXISTING
-- performance.feedback entity — its relationship types already cover peer (§14),
-- subordinate (§15) and project/cross-functional (§16); ANONYMOUS visibility
-- already exists (anonymous by default per the gap-check decision).

-- §13.3 — questionnaire master + questions
CREATE TABLE performance.feedback_questionnaire (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   varchar(64) NOT NULL DEFAULT 'default',
    code        varchar(40) NOT NULL,
    name        varchar(200) NOT NULL,
    description text,
    active      boolean NOT NULL DEFAULT true,
    created_by  varchar(80),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT feedback_questionnaire_code_unique UNIQUE (tenant_id, code)
);

CREATE TABLE performance.feedback_question (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        varchar(64) NOT NULL DEFAULT 'default',
    questionnaire_id uuid NOT NULL REFERENCES performance.feedback_questionnaire (id) ON DELETE CASCADE,
    question_order   int NOT NULL DEFAULT 0,
    question_text    varchar(500) NOT NULL,
    question_type    varchar(20) NOT NULL DEFAULT 'RATING',
    category         varchar(30),
    required         boolean NOT NULL DEFAULT true,
    CONSTRAINT feedback_question_type_check CHECK (question_type IN ('RATING','TEXT')),
    CONSTRAINT feedback_question_category_check CHECK (category IS NULL OR category IN
        ('COMPETENCY','BEHAVIORAL','COLLABORATION','LEADERSHIP','STRENGTHS','IMPROVEMENT','OPEN'))
);
CREATE INDEX feedback_question_questionnaire_idx
    ON performance.feedback_question (questionnaire_id, question_order);

-- §13.2 — nomination lifecycle. One request per reviewer per subject per cycle.
CREATE TABLE performance.feedback_request (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            varchar(64) NOT NULL DEFAULT 'default',
    cycle_id             uuid NOT NULL REFERENCES performance.review_cycle (id),
    subject_employee_id  uuid NOT NULL,
    reviewer_employee_id uuid NOT NULL,
    relationship         varchar(32) NOT NULL,
    questionnaire_id     uuid REFERENCES performance.feedback_questionnaire (id),
    anonymous            boolean NOT NULL DEFAULT true,
    status               varchar(20) NOT NULL DEFAULT 'NOMINATED',
    nominated_by         varchar(80),
    due_date             date,
    decline_reason       varchar(500),
    feedback_id          uuid,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT feedback_request_unique UNIQUE (cycle_id, subject_employee_id, reviewer_employee_id),
    CONSTRAINT feedback_request_status_check CHECK (status IN
        ('NOMINATED','APPROVED','DECLINED','COMPLETED','CANCELLED'))
);
CREATE INDEX feedback_request_subject_idx
    ON performance.feedback_request (tenant_id, cycle_id, subject_employee_id);
CREATE INDEX feedback_request_reviewer_idx
    ON performance.feedback_request (tenant_id, reviewer_employee_id);

-- §13.4 — tenant/cycle rules: minimum required + maximum allowed reviewers.
ALTER TABLE performance.review_cycle
    ADD COLUMN feedback_min_reviewers int,
    ADD COLUMN feedback_max_reviewers int;

COMMENT ON TABLE performance.feedback_request IS
    'HCM_12 M395 — 360° reviewer nomination (§13.2): NOMINATED→APPROVED→COMPLETED / DECLINED / CANCELLED; completed link = performance.feedback row.';
COMMENT ON TABLE performance.feedback_questionnaire IS
    'HCM_12 M395 — reusable 360° questionnaire (§13.3); answers stored on feedback.competencies JSON.';
