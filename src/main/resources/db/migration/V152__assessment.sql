-- ----------------------------------------------------------------------------
-- M287 — Recruitment PRD §22: assessment & test management.
--
-- An assessment is a test assigned to an application (technical,
-- language, cognitive, personality, practical, …). Manual result
-- entry now; an external-provider integration is a later seam (the
-- provider + external_ref columns reserve space for it).
--
-- Lifecycle: ASSIGNED → IN_PROGRESS → COMPLETED (+ score + PASS/FAIL)
--            | EXPIRED | CANCELLED.
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS recruitment.assessment_no_seq START 1;

CREATE TABLE IF NOT EXISTS recruitment.assessment (
    id              UUID PRIMARY KEY,
    assessment_no   VARCHAR(20)  NOT NULL UNIQUE,
    application_id  UUID         NOT NULL REFERENCES recruitment.application(id),
    assessment_type VARCHAR(24)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    provider        VARCHAR(160),
    external_ref    VARCHAR(160),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ASSIGNED',
    score           NUMERIC(6,2),
    max_score       NUMERIC(6,2),
    passing_score   NUMERIC(6,2),
    result          VARCHAR(8),                  -- PASS / FAIL
    notes           TEXT,
    attachment_id   UUID,
    -- whether a FAIL blocks the hire (PRD §22 / §70)
    blocks_hire     BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_at     TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    valid_until     DATE,                        -- assessment validity period
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120),
    CONSTRAINT chk_assessment_type CHECK (assessment_type IN
        ('TECHNICAL','LANGUAGE','COGNITIVE','PERSONALITY','JOB_SIMULATION',
         'PRACTICAL','CASE_STUDY','TYPING','OTHER')),
    CONSTRAINT chk_assessment_status CHECK (status IN
        ('ASSIGNED','IN_PROGRESS','COMPLETED','EXPIRED','CANCELLED')),
    CONSTRAINT chk_assessment_result CHECK (result IS NULL OR result IN ('PASS','FAIL'))
);

CREATE INDEX IF NOT EXISTS idx_assessment_application
    ON recruitment.assessment(application_id);
