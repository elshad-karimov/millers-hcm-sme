-- V196 — Payroll Loan Management (M352)
--
-- Employee loans repaid via fixed monthly installments deducted from payroll.
-- PayrollEngine auto-creates LOAN_INSTALLMENT deduction rows each period
-- until outstanding_balance reaches zero.

CREATE TABLE payroll.payroll_loan (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(64)   NOT NULL DEFAULT 'default',
    employee_id           UUID          NOT NULL REFERENCES core_hr.employee (id),
    principal_amount      NUMERIC(12, 2) NOT NULL CONSTRAINT chk_pl_principal CHECK (principal_amount > 0),
    monthly_installment   NUMERIC(12, 2) NOT NULL CONSTRAINT chk_pl_installment CHECK (monthly_installment > 0),
    outstanding_balance   NUMERIC(12, 2) NOT NULL,
    amount_repaid         NUMERIC(12, 2) NOT NULL DEFAULT 0 CONSTRAINT chk_pl_repaid CHECK (amount_repaid >= 0),
    start_deduction_year  INTEGER       NOT NULL,
    start_deduction_month INTEGER       NOT NULL CONSTRAINT chk_pl_start_month CHECK (start_deduction_month BETWEEN 1 AND 12),
    status                VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
        CONSTRAINT payroll_loan_status_ck CHECK (status IN ('PENDING', 'ACTIVE', 'FULLY_REPAID', 'WRITTEN_OFF', 'CANCELLED')),
    description           VARCHAR(500),
    approved_by           VARCHAR(200),
    approved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_payroll_loan_tenant_emp ON payroll.payroll_loan (tenant_id, employee_id, status);
CREATE INDEX idx_payroll_loan_status     ON payroll.payroll_loan (status);
