-- Attendance module (PRD Section 8.4).
-- This iteration ships fixed weekly schedules (FIVE_DAY / SIX_DAY),
-- REST + CSV event ingest, the daily-summary engine, and manual
-- correction. Shift / rotational / summarized accounting and a holiday
-- calendar are deliberate later-work seams.

CREATE TABLE attendance.work_schedule (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code                     VARCHAR(64)  NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,

    -- FIVE_DAY, SIX_DAY, SHIFT, FLEXIBLE, ROTATIONAL, NIGHT, PART_TIME (PRD 8.4.3)
    schedule_type            VARCHAR(32)  NOT NULL,

    work_start               TIME         NOT NULL,
    work_end                 TIME         NOT NULL,
    break_minutes            INTEGER      NOT NULL DEFAULT 0,
    grace_period_minutes     INTEGER      NOT NULL DEFAULT 0,

    -- Mon-Sun as 7 char bit-string. "1111100" = Mon..Fri work days.
    work_days                VARCHAR(7)   NOT NULL,

    -- Overtime daily threshold in minutes (defaults to (work_end - work_start) - break).
    -- Anything beyond this on a work day is counted as overtime by the engine.
    overtime_threshold_minutes INTEGER,

    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),
    updated_by               VARCHAR(120),

    CONSTRAINT chk_work_days_len   CHECK (length(work_days) = 7),
    CONSTRAINT chk_work_days_bits  CHECK (work_days ~ '^[01]{7}$'),
    CONSTRAINT chk_break_minutes   CHECK (break_minutes >= 0),
    CONSTRAINT chk_grace_minutes   CHECK (grace_period_minutes >= 0)
);

CREATE TABLE attendance.schedule_assignment (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID         NOT NULL,
    schedule_id     UUID         NOT NULL REFERENCES attendance.work_schedule (id),
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),

    CONSTRAINT chk_assignment_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_assignment_employee ON attendance.schedule_assignment (employee_id);
CREATE INDEX idx_assignment_schedule ON attendance.schedule_assignment (schedule_id);

CREATE TABLE attendance.attendance_event (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id           UUID         NOT NULL,
    device_employee_code  VARCHAR(64),
    event_time            TIMESTAMPTZ  NOT NULL,
    event_type            VARCHAR(8)   NOT NULL,   -- IN / OUT
    device_id             VARCHAR(64),
    location              VARCHAR(160),
    source                VARCHAR(32)  NOT NULL,   -- REST / CSV / SFTP / ...
    imported_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_event_type CHECK (event_type IN ('IN', 'OUT'))
);

CREATE INDEX idx_event_employee_time ON attendance.attendance_event (employee_id, event_time);
CREATE INDEX idx_event_time          ON attendance.attendance_event (event_time);

CREATE TABLE attendance.daily_summary (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id          UUID         NOT NULL,
    work_date            DATE         NOT NULL,
    schedule_id          UUID         REFERENCES attendance.work_schedule (id),
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

    -- PRESENT, PARTIAL, ABSENT, NON_WORKING_DAY, NO_SCHEDULE
    status               VARCHAR(32)  NOT NULL,

    correction_reason    TEXT,
    corrected_by         VARCHAR(120),
    corrected_at         TIMESTAMPTZ,

    computed_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (employee_id, work_date)
);

CREATE INDEX idx_summary_date_status ON attendance.daily_summary (work_date, status);
CREATE INDEX idx_summary_employee    ON attendance.daily_summary (employee_id, work_date);
