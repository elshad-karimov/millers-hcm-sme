-- M342 — Leave Period Lock
-- HR can lock a date range to prevent new leave submissions or cancellations
-- that touch the locked period (used for payroll close, year-end freeze, etc.)
CREATE TABLE IF NOT EXISTS leave_mgmt.leave_period_lock (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    -- NULL = applies to all leave types; set to lock only one type
    leave_type_id   UUID        REFERENCES leave_mgmt.leave_type(id),
    period_start    DATE        NOT NULL,
    period_end      DATE        NOT NULL,
    reason          TEXT,
    locked_by       VARCHAR(160),
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(160),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(160),
    CONSTRAINT leave_period_lock_dates_chk CHECK (period_end >= period_start)
);

CREATE INDEX IF NOT EXISTS ix_leave_period_lock_dates
    ON leave_mgmt.leave_period_lock (period_start, period_end)
    WHERE active = TRUE;
