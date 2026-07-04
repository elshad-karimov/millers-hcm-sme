-- M343: trace leave requests to their payroll deduction rows
ALTER TABLE payroll.payroll_deduction
    ADD COLUMN IF NOT EXISTS source_leave_request_id UUID,
    ADD COLUMN IF NOT EXISTS source_leave_days       NUMERIC(8, 2);

-- unique: one deduction per leave request (idempotency guard)
CREATE UNIQUE INDEX IF NOT EXISTS uq_payroll_deduction_leave_request
    ON payroll.payroll_deduction (source_leave_request_id)
    WHERE source_leave_request_id IS NOT NULL;

-- M344: encashment flag on leave_type
ALTER TABLE leave_mgmt.leave_type
    ADD COLUMN IF NOT EXISTS encashable BOOLEAN NOT NULL DEFAULT FALSE;

-- M344: leave encashment records (one row per encashment transaction)
CREATE TABLE IF NOT EXISTS leave_mgmt.leave_encashment (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(64)  NOT NULL DEFAULT 'default',
    employee_id       UUID         NOT NULL,
    leave_type_id     UUID         NOT NULL,
    year              INT          NOT NULL,
    days_encashed     NUMERIC(8,2) NOT NULL,
    daily_rate        NUMERIC(14,4) NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    payroll_bonus_id  UUID,          -- set when the bonus is created in a payroll run
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING','PAID','REVERSED')),
    notes             TEXT,
    created_by        VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_leave_encashment_employee ON leave_mgmt.leave_encashment (employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_encashment_type_year ON leave_mgmt.leave_encashment (leave_type_id, year);
