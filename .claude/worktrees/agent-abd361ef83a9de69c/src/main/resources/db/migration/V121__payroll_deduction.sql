-- V121 — Payroll deduction module (M181 / PRD §8.9.1)
--
-- Supports one-off, recurring, garnishment, and advance-recovery deductions
-- applied automatically by the PayrollEngine during each run.
-- Deduction types: ONE_OFF | RECURRING | GARNISHMENT | ADVANCE_RECOVERY

CREATE TABLE IF NOT EXISTS payroll.payroll_deduction (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID         NOT NULL,
    -- ONE_OFF   : deducted once then COMPLETED
    -- RECURRING : deducted every matching period until end_period or CANCELLED
    -- GARNISHMENT: same cadence as RECURRING; labelled for compliance reports
    -- ADVANCE_RECOVERY: installment recovery — each period deducts `amount_per_period`
    --   until recovered_amount >= total_amount, then COMPLETED
    deduction_type      VARCHAR(30)  NOT NULL
        CONSTRAINT payroll_deduction_type_ck
            CHECK (deduction_type IN ('ONE_OFF','RECURRING','GARNISHMENT','ADVANCE_RECOVERY')),
    description         VARCHAR(255) NOT NULL,
    -- Amount to deduct each payroll period (always positive)
    amount_per_period   NUMERIC(14,2) NOT NULL CHECK (amount_per_period > 0),
    -- Total principal (only meaningful for ADVANCE_RECOVERY; null for others)
    total_amount        NUMERIC(14,2),
    -- Running sum of amounts already deducted across past runs
    recovered_amount    NUMERIC(14,2) NOT NULL DEFAULT 0
        CHECK (recovered_amount >= 0),
    -- Period in which the deduction first applies (inclusive)
    start_period_year   INTEGER      NOT NULL,
    start_period_month  INTEGER      NOT NULL
        CHECK (start_period_month BETWEEN 1 AND 12),
    -- End period (inclusive, nullable → open-ended)
    end_period_year     INTEGER,
    end_period_month    INTEGER
        CHECK (end_period_month BETWEEN 1 AND 12),
    -- ACTIVE | COMPLETED | CANCELLED
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT payroll_deduction_status_ck
            CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED')),
    note                TEXT,
    created_by          VARCHAR(120),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payroll_deduction_employee
    ON payroll.payroll_deduction (employee_id, status);

-- Partial index for the PayrollEngine's hot-path query: active deductions
-- for a given employee.
CREATE INDEX IF NOT EXISTS idx_payroll_deduction_active
    ON payroll.payroll_deduction (employee_id, start_period_year, start_period_month)
    WHERE status = 'ACTIVE';
