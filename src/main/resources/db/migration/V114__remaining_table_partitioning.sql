-- V114 — Remaining high-volume table partitioning (PRD §15.3)
--
-- Tables partitioned here (completing §15.3):
--   attendance.daily_summary   — PARTITION BY RANGE (work_date)   monthly
--   timesheet.timesheet_day    — PARTITION BY RANGE (work_date)   yearly
--   workflow.workflow_action   — PARTITION BY RANGE (created_at)  monthly
--
-- Pattern mirrors V112: rename legacy, create partitioned replacement with
-- composite PK, copy data, drop legacy, recreate indexes.
-- A DEFAULT partition absorbs rows outside any named range.

-- ═══════════════════════════════════════════════════════════════
-- 1. attendance.daily_summary  (monthly by work_date)
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE attendance.daily_summary
    RENAME TO daily_summary_legacy;

CREATE TABLE attendance.daily_summary (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    employee_id          UUID         NOT NULL,
    work_date            DATE         NOT NULL,
    schedule_id          UUID,
    schedule_start       TIME,
    schedule_end         TIME,
    entry_time           TIMESTAMPTZ,
    exit_time            TIMESTAMPTZ,
    raw_event_count      INTEGER      NOT NULL DEFAULT 0,
    worked_minutes       INTEGER      NOT NULL DEFAULT 0,
    late_minutes         INTEGER      NOT NULL DEFAULT 0,
    early_minutes        INTEGER      NOT NULL DEFAULT 0,
    break_minutes        INTEGER      NOT NULL DEFAULT 0,
    overtime_minutes     INTEGER      NOT NULL DEFAULT 0,
    status               VARCHAR(32)  NOT NULL,
    correction_reason    TEXT,
    corrected_by         VARCHAR(120),
    corrected_at         TIMESTAMPTZ,
    computed_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- V76 additions
    shift_id             UUID,
    source               VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULE'
                             CONSTRAINT daily_summary_source_check
                             CHECK (source IN ('SCHEDULE', 'ROSTER', 'NONE')),
    PRIMARY KEY (id, work_date),
    UNIQUE      (employee_id, work_date)
) PARTITION BY RANGE (work_date);

DO $$
DECLARE
    m DATE := DATE '2025-01-01';
BEGIN
    WHILE m < DATE '2027-07-01' LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS attendance.daily_summary_%s
             PARTITION OF attendance.daily_summary
             FOR VALUES FROM (%L) TO (%L)',
            to_char(m, 'YYYY_MM'),
            m,
            m + INTERVAL '1 month'
        );
        m := m + INTERVAL '1 month';
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS attendance.daily_summary_default
    PARTITION OF attendance.daily_summary DEFAULT;

INSERT INTO attendance.daily_summary (
    id, employee_id, work_date, schedule_id, schedule_start, schedule_end,
    entry_time, exit_time, raw_event_count, worked_minutes, late_minutes,
    early_minutes, break_minutes, overtime_minutes, status,
    correction_reason, corrected_by, corrected_at, computed_at,
    shift_id, source
)
SELECT
    id, employee_id, work_date, schedule_id, schedule_start, schedule_end,
    entry_time, exit_time, raw_event_count, worked_minutes, late_minutes,
    early_minutes, break_minutes, overtime_minutes, status,
    correction_reason, corrected_by, corrected_at, computed_at,
    shift_id, source
FROM attendance.daily_summary_legacy;

DROP TABLE attendance.daily_summary_legacy;

CREATE INDEX idx_ds_date_status   ON attendance.daily_summary (work_date, status);
CREATE INDEX idx_ds_employee_date ON attendance.daily_summary (employee_id, work_date);
CREATE INDEX idx_ds_source        ON attendance.daily_summary (source);


-- ═══════════════════════════════════════════════════════════════
-- 2. timesheet.timesheet_day  (yearly by work_date)
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE timesheet.timesheet_day
    RENAME TO timesheet_day_legacy;

CREATE TABLE timesheet.timesheet_day (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    timesheet_id          UUID         NOT NULL,
    work_date             DATE         NOT NULL,
    primary_code          VARCHAR(4)   NOT NULL,
    worked_hours          NUMERIC(6,2) NOT NULL DEFAULT 0,
    overtime_hours        NUMERIC(6,2) NOT NULL DEFAULT 0,
    break_hours           NUMERIC(6,2) NOT NULL DEFAULT 0,
    late_minutes          INTEGER      NOT NULL DEFAULT 0,
    early_minutes         INTEGER      NOT NULL DEFAULT 0,
    source_summary_id     UUID,
    leave_request_id      UUID,
    bt_request_id         UUID,
    permission_request_id UUID,
    anomalies             TEXT,
    correction_reason     TEXT,
    corrected_by          VARCHAR(120),
    corrected_at          TIMESTAMPTZ,
    PRIMARY KEY (id, work_date)
) PARTITION BY RANGE (work_date);

CREATE TABLE IF NOT EXISTS timesheet.timesheet_day_2024
    PARTITION OF timesheet.timesheet_day
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE IF NOT EXISTS timesheet.timesheet_day_2025
    PARTITION OF timesheet.timesheet_day
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

CREATE TABLE IF NOT EXISTS timesheet.timesheet_day_2026
    PARTITION OF timesheet.timesheet_day
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE TABLE IF NOT EXISTS timesheet.timesheet_day_2027
    PARTITION OF timesheet.timesheet_day
    FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');

CREATE TABLE IF NOT EXISTS timesheet.timesheet_day_default
    PARTITION OF timesheet.timesheet_day DEFAULT;

INSERT INTO timesheet.timesheet_day (
    id, timesheet_id, work_date, primary_code, worked_hours, overtime_hours,
    break_hours, late_minutes, early_minutes, source_summary_id,
    leave_request_id, bt_request_id, permission_request_id,
    anomalies, correction_reason, corrected_by, corrected_at
)
SELECT
    id, timesheet_id, work_date, primary_code, worked_hours, overtime_hours,
    break_hours, late_minutes, early_minutes, source_summary_id,
    leave_request_id, bt_request_id, permission_request_id,
    anomalies, correction_reason, corrected_by, corrected_at
FROM timesheet.timesheet_day_legacy;

DROP TABLE timesheet.timesheet_day_legacy;

CREATE INDEX idx_tsd_timesheet  ON timesheet.timesheet_day (timesheet_id);
CREATE INDEX idx_tsd_work_date  ON timesheet.timesheet_day (work_date);


-- ═══════════════════════════════════════════════════════════════
-- 3. workflow.workflow_action  (monthly by created_at)
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE workflow.workflow_action
    RENAME TO workflow_action_legacy;

CREATE TABLE workflow.workflow_action (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    instance_id  UUID         NOT NULL,
    step_index   INTEGER      NOT NULL,
    step_name    VARCHAR(160),
    action       VARCHAR(32)  NOT NULL,
    actor        VARCHAR(120) NOT NULL,
    comment      TEXT,
    ip_address   VARCHAR(64),
    document_ref VARCHAR(1024),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

DO $$
DECLARE
    m TIMESTAMPTZ := DATE_TRUNC('month', NOW() - INTERVAL '12 months');
BEGIN
    WHILE m < DATE_TRUNC('month', NOW() + INTERVAL '13 months') LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS workflow.workflow_action_%s
             PARTITION OF workflow.workflow_action
             FOR VALUES FROM (%L) TO (%L)',
            to_char(m, 'YYYY_MM'),
            m,
            m + INTERVAL '1 month'
        );
        m := m + INTERVAL '1 month';
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS workflow.workflow_action_default
    PARTITION OF workflow.workflow_action DEFAULT;

INSERT INTO workflow.workflow_action (
    id, instance_id, step_index, step_name, action, actor,
    comment, ip_address, document_ref, created_at
)
SELECT
    id, instance_id, step_index, step_name, action, actor,
    comment, ip_address, document_ref, created_at
FROM workflow.workflow_action_legacy;

DROP TABLE workflow.workflow_action_legacy;

CREATE INDEX idx_wf_action_instance ON workflow.workflow_action (instance_id);
CREATE INDEX idx_wf_action_actor    ON workflow.workflow_action (actor);
