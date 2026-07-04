-- ----------------------------------------------------------------------------
-- M286 — Recruitment PRD §25-§27: pre-hire checks
-- (background / reference / medical / identity / education / …).
--
-- One generic table covers every check type (DRY — the project's
-- "develop once, use everywhere" rule): a check_type discriminator
-- plus generic columns (subject, provider, result, result_notes) onto
-- which the reference-specific (referee) and medical-specific
-- (fit/unfit) shapes both map. Each row hangs off an application.
--
-- Confidentiality (PRD §27): result_notes is sensitive — the service
-- redacts it for MEDICAL checks unless the caller is HR_ADMIN /
-- SYSTEM_ADMIN; recruiters and managers see only status + pass/fail.
--
-- Lifecycle: NOT_REQUIRED / REQUIRED → REQUESTED → IN_PROGRESS →
--            COMPLETED (+ result PASS/FAIL/CONDITIONAL) | REQUIRES_REVIEW
--            | CANCELLED.
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS recruitment.check_no_seq START 1;

CREATE TABLE IF NOT EXISTS recruitment.pre_hire_check (
    id              UUID PRIMARY KEY,
    check_no        VARCHAR(20)  NOT NULL UNIQUE,
    application_id  UUID         NOT NULL REFERENCES recruitment.application(id),
    check_type      VARCHAR(24)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'REQUIRED',
    -- who/what is verifying, and the subject of a reference check
    provider        VARCHAR(160),
    subject_name    VARCHAR(160),
    subject_contact VARCHAR(160),
    -- outcome
    result          VARCHAR(16),                 -- PASS / FAIL / CONDITIONAL
    result_notes    TEXT,                        -- confidential (see §27)
    attachment_id   UUID,                        -- optional supporting doc
    -- whether a FAIL on this check blocks the hire (PRD §25 business logic)
    blocks_hire     BOOLEAN      NOT NULL DEFAULT TRUE,
    requested_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120),
    CONSTRAINT chk_prehire_type CHECK (check_type IN
        ('BACKGROUND','IDENTITY','EDUCATION','EMPLOYMENT','REFERENCE',
         'CRIMINAL','CREDIT','LICENSE','WORK_AUTHORIZATION','MEDICAL')),
    CONSTRAINT chk_prehire_status CHECK (status IN
        ('NOT_REQUIRED','REQUIRED','REQUESTED','IN_PROGRESS','COMPLETED',
         'PASSED','FAILED','REQUIRES_REVIEW','CANCELLED')),
    CONSTRAINT chk_prehire_result CHECK (result IS NULL OR result IN
        ('PASS','FAIL','CONDITIONAL'))
);

CREATE INDEX IF NOT EXISTS idx_prehire_application
    ON recruitment.pre_hire_check(application_id);
