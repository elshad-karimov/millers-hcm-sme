-- Scheduled reports + exports (PRD Section 10).
-- Builds on Milestone 14 (the synchronous /api/reports/* aggregations) by
-- adding: saved report definitions, cron-driven schedules, and a run
-- history that links each generated file back to core_hr.attachment so
-- downloads piggy-back on MinIO + the existing attachment download
-- endpoint.

CREATE SEQUENCE reporting.definition_no_seq START WITH 1;
CREATE SEQUENCE reporting.schedule_no_seq   START WITH 1;
CREATE SEQUENCE reporting.run_no_seq        START WITH 1;

-- A saved report config: which report type + parameters + output format.
-- The "live" /api/reports/* endpoints still work without a definition;
-- definitions exist so HR can re-run the same parameterised report on a
-- schedule.
CREATE TABLE reporting.report_definition (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_no   VARCHAR(32)  NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    -- HEADCOUNT, ATTRITION, PAYROLL, LEAVE, ATTENDANCE, TRAINING,
    -- PERFORMANCE, RECRUITMENT
    report_type     VARCHAR(32)  NOT NULL,
    -- PDF, XLSX  (synchronous JSON downloads continue via /api/reports/*)
    default_format  VARCHAR(8)   NOT NULL DEFAULT 'XLSX',
    -- Parameters as JSON: e.g. {"year":2026,"from":"2026-05-01","to":"2026-05-31"}
    parameters      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    description     TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120),

    CONSTRAINT chk_def_format CHECK (default_format IN ('PDF', 'XLSX'))
);

CREATE INDEX idx_definition_type ON reporting.report_definition (report_type);

-- Cron schedule pointing at a saved definition.
CREATE TABLE reporting.report_schedule (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_no          VARCHAR(32)  NOT NULL UNIQUE,
    name                 VARCHAR(200) NOT NULL,
    definition_id        UUID         NOT NULL REFERENCES reporting.report_definition (id),
    -- Spring-style cron expression (6 fields: sec min hour dom mon dow)
    cron                 VARCHAR(120) NOT NULL,
    -- Comma-separated email list (delivery hook not implemented yet — the
    -- generated file is stored in MinIO and downloadable from the run history;
    -- email/Slack are PRD 10.5 follow-ons).
    recipients           TEXT,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    last_run_at          TIMESTAMPTZ,
    next_run_at          TIMESTAMPTZ,
    last_status          VARCHAR(16),                -- SUCCESS / FAILED
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(120),
    updated_by           VARCHAR(120)
);

CREATE INDEX idx_schedule_active_next ON reporting.report_schedule (active, next_run_at);
CREATE INDEX idx_schedule_definition ON reporting.report_schedule (definition_id);

-- Execution log. Stores parameters as they were resolved at run time so a
-- later audit can reconstruct exactly what was reported, even if the
-- definition's parameters were edited.
CREATE TABLE reporting.report_run (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_no               VARCHAR(32)  NOT NULL UNIQUE,
    definition_id        UUID         REFERENCES reporting.report_definition (id),
    schedule_id          UUID         REFERENCES reporting.report_schedule (id),
    report_type          VARCHAR(32)  NOT NULL,
    format               VARCHAR(8)   NOT NULL,
    parameters           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- Soft pointer to core_hr.attachment.id (intentionally not FK so soft
    -- deletes there don't break this row).
    attachment_id        UUID,
    file_name            VARCHAR(255),
    size_bytes           BIGINT,
    -- RUNNING, SUCCESS, FAILED
    status               VARCHAR(16)  NOT NULL,
    error_message        TEXT,
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at          TIMESTAMPTZ,
    triggered_by         VARCHAR(120),
    -- MANUAL, SCHEDULED
    trigger_source       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',

    CONSTRAINT chk_run_format CHECK (format IN ('PDF', 'XLSX')),
    CONSTRAINT chk_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_run_definition  ON reporting.report_run (definition_id);
CREATE INDEX idx_run_schedule    ON reporting.report_run (schedule_id);
CREATE INDEX idx_run_started_at  ON reporting.report_run (started_at DESC);
