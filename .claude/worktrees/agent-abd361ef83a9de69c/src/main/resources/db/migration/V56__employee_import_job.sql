-- M69 / Phase 1 — Bulk employee import job tracking (P1-16)
--
-- Every import attempt — whether dry-run preview or committed — gets a row
-- here. Lets HR see "what was imported when by whom" without trawling the
-- audit log. Failed imports are captured too so the validation errors stay
-- inspectable after the API call returns.

CREATE TABLE IF NOT EXISTS core_hr.employee_import_job (
    id                  UUID         PRIMARY KEY,
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ,
    started_by          VARCHAR(80)  NOT NULL,

    file_name           VARCHAR(255) NOT NULL,
    file_size_bytes     BIGINT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    dry_run             BOOLEAN      NOT NULL DEFAULT FALSE,

    rows_total          INTEGER      NOT NULL DEFAULT 0,
    rows_valid          INTEGER      NOT NULL DEFAULT 0,
    rows_invalid        INTEGER      NOT NULL DEFAULT 0,
    rows_committed      INTEGER      NOT NULL DEFAULT 0,

    -- Per-row error report as JSON — keeps the schema simple at the cost of a
    -- LOB load when inspecting an import. Acceptable for occasional-use feature.
    error_report_json   JSONB,

    -- Free-text top-level error (parser failure, missing required column, etc.)
    error_message       TEXT,

    CONSTRAINT chk_import_status CHECK (status IN (
        'PENDING','VALIDATING','PREVIEW','COMMITTED','FAILED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_import_started_at
    ON core_hr.employee_import_job (started_at DESC);
