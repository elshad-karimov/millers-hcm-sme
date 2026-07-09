-- ----------------------------------------------------------------------------
-- M461 — HCM_30 Phase B.2: Loan Request (ESS submit → eligibility check → workflow).
--
-- LOAN_REQUEST workflow (single HR_ADMIN step, seeded below). On APPROVED:
-- create PayrollLoan + deduction via PayrollLoanService.create (AFTER_COMMIT
-- + REQUIRES_NEW listener — non-fatal, audited). Amounts CONFIDENTIAL (payroll-grade).
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS payroll.loan_request_no_seq START 1;

CREATE TABLE IF NOT EXISTS payroll.loan_request (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               varchar(80)  NOT NULL DEFAULT 'default',
    request_no              varchar(20)  NOT NULL UNIQUE,
    employee_id             uuid         NOT NULL,
    loan_type_id            uuid         NOT NULL REFERENCES payroll.loan_type(id),
    requested_amount        numeric(12,2) NOT NULL,
    requested_months        int          NOT NULL,
    purpose                 varchar(1000),
    status                  varchar(20)  NOT NULL DEFAULT 'DRAFT',
    workflow_instance_id    uuid,
    eligibility_check_passed boolean,
    eligibility_notes       text,
    payroll_loan_id         uuid,
    requested_by            varchar(80),
    requested_at            timestamptz  NOT NULL DEFAULT now(),
    approved_by             varchar(80),
    approved_at             timestamptz,
    rejected_reason         text,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              varchar(80),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              varchar(80),
    CONSTRAINT loan_request_status_check
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT loan_request_amount_check CHECK (requested_amount > 0),
    CONSTRAINT loan_request_months_check CHECK (requested_months > 0)
);

CREATE INDEX IF NOT EXISTS loan_request_tenant_idx ON payroll.loan_request (tenant_id);
CREATE INDEX IF NOT EXISTS loan_request_employee_idx ON payroll.loan_request (employee_id);
CREATE INDEX IF NOT EXISTS loan_request_loan_type_idx ON payroll.loan_request (loan_type_id);
CREATE INDEX IF NOT EXISTS loan_request_status_idx ON payroll.loan_request (status);

COMMENT ON TABLE payroll.loan_request IS
    'M461 — Employee loan requests. ESS submit → eligibility check → LOAN_REQUEST workflow. On APPROVED: create PayrollLoan.';

-- ── Seed LOAN_REQUEST workflow (HR_ADMIN step) ─────────────────────────────
INSERT INTO workflow.workflow_definition (id, code, name, description, active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'LOAN_REQUEST',
    'Loan Request Approval',
    'M461 — Employee loan request approval. Single HR_ADMIN step.',
    true,
    now(),
    now()
) ON CONFLICT (code, version) DO NOTHING;

INSERT INTO workflow.workflow_step (id, definition_id, step_order, name, approver_role)
SELECT
    gen_random_uuid(),
    wd.id,
    1,
    'HR/Payroll Approval',
    'ROLE_HR_ADMIN'
FROM workflow.workflow_definition wd
WHERE wd.code = 'LOAN_REQUEST'
ON CONFLICT (definition_id, step_order) DO NOTHING;
