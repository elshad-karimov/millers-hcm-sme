-- M85 — Recruitment expansion: structured interview kits + per-question
-- scoring. Sits inside the existing recruitment schema so candidate /
-- application flows can reference kits without cross-schema FKs.
--
-- Four new tables:
--   interview_kit         — reusable scoring template
--   interview_question    — weighted questions inside a kit
--   interview             — one scheduled session per (application, kit, interviewer)
--   interview_score       — one row per (interview, question)

CREATE TABLE IF NOT EXISTS recruitment.interview_kit (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    -- Soft FK to staffing.job_family (M75). Null = generic kit, applies
    -- to any vacancy.
    job_family_id   UUID,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_interview_kit_active
    ON recruitment.interview_kit (active);


CREATE TABLE IF NOT EXISTS recruitment.interview_question (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    kit_id          UUID         NOT NULL REFERENCES recruitment.interview_kit (id) ON DELETE CASCADE,
    -- Free-text prompt. Multi-line allowed.
    question_text   TEXT         NOT NULL,
    -- Weight relative to other questions in the same kit. Overall score is
    -- the weighted average of (score × weight) / total_weight.
    weight          INTEGER      NOT NULL DEFAULT 1,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    required        BOOLEAN      NOT NULL DEFAULT TRUE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_interview_q_weight CHECK (weight > 0)
);

CREATE INDEX IF NOT EXISTS idx_interview_question_kit
    ON recruitment.interview_question (kit_id, sort_order, id);


CREATE TABLE IF NOT EXISTS recruitment.interview (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_no           VARCHAR(20)  NOT NULL UNIQUE,
    application_id         UUID         NOT NULL REFERENCES recruitment.application (id) ON DELETE CASCADE,
    kit_id                 UUID         NOT NULL REFERENCES recruitment.interview_kit (id),
    -- Soft FK to core_hr.employee (the interviewer).
    interviewer_employee_id UUID         NOT NULL,
    scheduled_at           TIMESTAMPTZ  NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    -- 1-5 weighted-average, computed on finalize. Null while in-progress.
    overall_score          NUMERIC(4,2),
    -- HIRE / NO_HIRE / STRONG_HIRE / STRONG_NO_HIRE — interviewer's qualitative call.
    recommendation         VARCHAR(20),
    overall_comment        TEXT,
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             VARCHAR(80),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by             VARCHAR(80),
    CONSTRAINT chk_interview_status CHECK (status IN (
        'SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW'
    )),
    CONSTRAINT chk_interview_score_range CHECK (
        overall_score IS NULL OR (overall_score >= 0 AND overall_score <= 5)
    ),
    CONSTRAINT chk_interview_recommendation CHECK (
        recommendation IS NULL OR recommendation IN (
            'STRONG_HIRE','HIRE','NO_HIRE','STRONG_NO_HIRE','MAYBE'
        )
    )
);

CREATE SEQUENCE IF NOT EXISTS recruitment.interview_no_seq START WITH 1;

CREATE INDEX IF NOT EXISTS idx_interview_application
    ON recruitment.interview (application_id, scheduled_at DESC);

CREATE INDEX IF NOT EXISTS idx_interview_interviewer
    ON recruitment.interview (interviewer_employee_id);

CREATE INDEX IF NOT EXISTS idx_interview_status
    ON recruitment.interview (status);


CREATE TABLE IF NOT EXISTS recruitment.interview_score (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id    UUID         NOT NULL REFERENCES recruitment.interview (id) ON DELETE CASCADE,
    question_id     UUID         NOT NULL REFERENCES recruitment.interview_question (id),
    score           INTEGER      NOT NULL,
    comment         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- At most one score per (interview, question) — re-scoring updates in place.
    UNIQUE (interview_id, question_id),
    CONSTRAINT chk_interview_score_value CHECK (score >= 1 AND score <= 5)
);

CREATE INDEX IF NOT EXISTS idx_interview_score_interview
    ON recruitment.interview_score (interview_id);
