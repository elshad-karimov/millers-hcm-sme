-- V195 — Salary Advance Management (M351)
--
-- Employees request advances against upcoming salary. Approved advances create
-- a payroll_deduction row (type SALARY_ADVANCE, source_advance_id) for automatic
-- recovery in the specified repayment period.

CREATE TABLE payroll.salary_advance (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)   NOT NULL DEFAULT 'default',
    employee_id      UUID          NOT NULL REFERENCES core_hr.employee (id),
    requested_amount NUMERIC(12, 2) NOT NULL CONSTRAINT chk_sa_requested CHECK (requested_amount > 0),
    approved_amount  NUMERIC(12, 2),
    repayment_year   INTEGER,
    repayment_month  INTEGER CONSTRAINT chk_sa_repay_month CHECK (repayment_month IS NULL OR repayment_month BETWEEN 1 AND 12),
    status           VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
        CONSTRAINT salary_advance_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'DEDUCTED', 'CANCELLED')),
    reason           VARCHAR(500),
    requested_by     VARCHAR(200)  NOT NULL,
    approved_by      VARCHAR(200),
    approved_at      TIMESTAMPTZ,
    disbursed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_salary_advance_tenant_emp ON payroll.salary_advance (tenant_id, employee_id, status);
CREATE INDEX idx_salary_advance_status     ON payroll.salary_advance (status);
