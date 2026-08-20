-- ----------------------------------------------------------------------------
-- V318 — Timesheet approval & period control
--        (PRD/timesheet-approval-control, slice 2 of 3).
--
-- Managers act on what employees submitted; HR closes the period. Still no
-- monetary column anywhere — this slice governs whether quantities can be
-- trusted, not what they are worth.
--
-- The rule it exists to enforce: payroll consumes approved, locked periods,
-- never raw employee entries.
-- ----------------------------------------------------------------------------

-- 1. Per-day approval state -------------------------------------------------
-- A month can be 31 days. Returning all of it because one day is wrong makes
-- the employee re-check 30 correct days, so a return names days and only those
-- reopen. The month cannot be approved while any day is still RETURNED.
ALTER TABLE timesheet.timesheet_day
    ADD COLUMN IF NOT EXISTS approval_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS return_reason  TEXT,
    ADD COLUMN IF NOT EXISTS returned_by    VARCHAR(120),
    ADD COLUMN IF NOT EXISTS returned_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by    VARCHAR(120),
    ADD COLUMN IF NOT EXISTS approved_at    TIMESTAMPTZ;

ALTER TABLE timesheet.timesheet_day
    DROP CONSTRAINT IF EXISTS ck_tsday_approval_state;
ALTER TABLE timesheet.timesheet_day
    ADD CONSTRAINT ck_tsday_approval_state
    CHECK (approval_state IN ('PENDING', 'APPROVED', 'RETURNED'));

CREATE INDEX IF NOT EXISTS idx_tsday_approval_state
    ON timesheet.timesheet_day (tenant_id, approval_state);

-- 2. Month-level return / reject trail --------------------------------------
ALTER TABLE timesheet.timesheet
    ADD COLUMN IF NOT EXISTS returned_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS returned_by     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS return_reason   TEXT,
    ADD COLUMN IF NOT EXISTS rejected_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- 3. Period control ----------------------------------------------------------
-- One row per tenant-period. LOCKED means no submission, no approval, no edit —
-- and is the only state in which payroll may consume the period.
CREATE TABLE timesheet.period_control (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',

    period_year   INTEGER      NOT NULL,
    period_month  INTEGER      NOT NULL,

    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT ck_period_control_status CHECK (status IN ('OPEN', 'LOCKED')),

    locked_at     TIMESTAMPTZ,
    locked_by     VARCHAR(120),
    lock_reason   TEXT,
    unlocked_at   TIMESTAMPTZ,
    unlocked_by   VARCHAR(120),
    unlock_reason TEXT,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_period_control UNIQUE (tenant_id, period_year, period_month),
    CONSTRAINT ck_period_control_month CHECK (period_month BETWEEN 1 AND 12)
);

CREATE INDEX idx_period_control_period
    ON timesheet.period_control (tenant_id, period_year, period_month);

-- 4. Correction requests -----------------------------------------------------
-- An approved or locked month is never silently edited. A request names the
-- day, what it says now, what it should say and why; approving it reopens only
-- that day. Once payroll has paid a period (slice 3) the same request becomes
-- the trigger for a retro adjustment rather than a rewrite of history.
CREATE TABLE timesheet.correction_request (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64)  NOT NULL DEFAULT 'default',

    timesheet_id   UUID         NOT NULL REFERENCES timesheet.timesheet (id) ON DELETE CASCADE,
    employee_id    UUID         NOT NULL,
    work_date      DATE         NOT NULL,

    -- Free-text snapshots rather than typed quantities: what is being corrected
    -- is whatever the day said, and a human decides whether the change is right.
    current_value  TEXT,
    requested_value TEXT        NOT NULL,
    reason         TEXT         NOT NULL,

    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT ck_correction_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),

    requested_by   VARCHAR(120) NOT NULL,
    requested_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_by     VARCHAR(120),
    decided_at     TIMESTAMPTZ,
    decision_note  TEXT,

    CONSTRAINT ck_correction_decided
        CHECK ((status = 'PENDING' AND decided_at IS NULL)
            OR (status <> 'PENDING' AND decided_at IS NOT NULL))
);

CREATE INDEX idx_correction_timesheet ON timesheet.correction_request (timesheet_id);
CREATE INDEX idx_correction_status ON timesheet.correction_request (tenant_id, status);
CREATE INDEX idx_correction_employee ON timesheet.correction_request (tenant_id, employee_id);
