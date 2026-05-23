-- Timesheet module (PRD Section 8.8).
-- Monthly per-employee header + per-day lines with one primary code each:
-- W (worked), L (annual leave), S (sick), BT (business trip),
-- P (permission), O (overtime — used as accompanying total), H (holiday /
-- non-working day), A (absent). Locking after payroll lands with Payroll.

CREATE TABLE timesheet.timesheet (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id           UUID         NOT NULL,
    period_year           INTEGER      NOT NULL,
    period_month          INTEGER      NOT NULL,

    -- DRAFT, SUBMITTED, APPROVED, LOCKED, REOPENED
    status                VARCHAR(32)  NOT NULL,
    workflow_instance_id  UUID,

    total_worked_hours    NUMERIC(9,2) NOT NULL DEFAULT 0,
    total_overtime_hours  NUMERIC(9,2) NOT NULL DEFAULT 0,
    total_leave_days      NUMERIC(9,2) NOT NULL DEFAULT 0,
    total_sick_days       NUMERIC(9,2) NOT NULL DEFAULT 0,
    total_bt_days         NUMERIC(9,2) NOT NULL DEFAULT 0,
    total_permission_hrs  NUMERIC(9,2) NOT NULL DEFAULT 0,
    total_absent_days     NUMERIC(9,2) NOT NULL DEFAULT 0,

    generated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    generated_by          VARCHAR(120),
    submitted_at          TIMESTAMPTZ,
    submitted_by          VARCHAR(120),
    approved_at           TIMESTAMPTZ,
    approved_by           VARCHAR(120),
    locked_at             TIMESTAMPTZ,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (employee_id, period_year, period_month),
    CONSTRAINT chk_ts_month CHECK (period_month BETWEEN 1 AND 12)
);

CREATE INDEX idx_ts_period ON timesheet.timesheet (period_year, period_month);
CREATE INDEX idx_ts_status ON timesheet.timesheet (status);

CREATE TABLE timesheet.timesheet_day (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    timesheet_id         UUID         NOT NULL REFERENCES timesheet.timesheet (id) ON DELETE CASCADE,
    work_date            DATE         NOT NULL,

    -- One primary code per day (PRD 8.8.6).
    -- W / L / S / BT / P / O / H / A
    primary_code         VARCHAR(4)   NOT NULL,

    worked_hours         NUMERIC(6,2) NOT NULL DEFAULT 0,
    overtime_hours       NUMERIC(6,2) NOT NULL DEFAULT 0,
    break_hours          NUMERIC(6,2) NOT NULL DEFAULT 0,
    late_minutes         INTEGER      NOT NULL DEFAULT 0,
    early_minutes        INTEGER      NOT NULL DEFAULT 0,

    source_summary_id    UUID,    -- attendance.daily_summary.id when applicable
    leave_request_id     UUID,
    bt_request_id        UUID,
    permission_request_id UUID,

    anomalies            TEXT,    -- comma-separated codes: MISSING_PUNCH, SCHEDULE_CONFLICT, ...
    correction_reason    TEXT,
    corrected_by         VARCHAR(120),
    corrected_at         TIMESTAMPTZ,

    UNIQUE (timesheet_id, work_date)
);

CREATE INDEX idx_tsday_code ON timesheet.timesheet_day (primary_code);

-- Workflow definition for monthly timesheet approval (PRD 8.8.4 chain
-- HR Specialist → Department Manager → Payroll Specialist).
-- Approximated with available roles until ROLE_DEPARTMENT_MANAGER /
-- ROLE_PAYROLL_SPECIALIST get wired to actual users.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '66666666-6666-6666-6666-666666666666',
    'TIMESHEET_APPROVAL',
    'Timesheet Approval',
    'Monthly timesheet sign-off (PRD 8.8.4): HR Specialist → Manager → Payroll Specialist. Approximated until full role catalogue lands.',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('66666666-6666-6666-6666-666666666666', 1, 'Manager review', 'ROLE_HR_SPECIALIST'),
    ('66666666-6666-6666-6666-666666666666', 2, 'Payroll sign-off', 'ROLE_HR_ADMIN');
