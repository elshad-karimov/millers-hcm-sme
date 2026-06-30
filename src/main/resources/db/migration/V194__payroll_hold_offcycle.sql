-- V194 — Employee Payroll Hold (M350)
--
-- HR can hold individual employees for a specific payroll run,
-- excluding them from calculation without affecting the rest.

CREATE TABLE payroll.payroll_run_hold (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(64)  NOT NULL DEFAULT 'default',
    run_id      UUID         NOT NULL REFERENCES payroll.payroll_run (id) ON DELETE CASCADE,
    employee_id UUID         NOT NULL REFERENCES core_hr.employee (id),
    reason      VARCHAR(500),
    held_by     VARCHAR(200) NOT NULL,
    held_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    released_at TIMESTAMPTZ,

    CONSTRAINT uq_prh_run_employee UNIQUE (run_id, employee_id)
);

CREATE INDEX idx_prh_tenant_run ON payroll.payroll_run_hold (tenant_id, run_id);
CREATE INDEX idx_prh_employee   ON payroll.payroll_run_hold (employee_id);
