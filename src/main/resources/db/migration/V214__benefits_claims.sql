-- HCM_11 Benefits Administration — M381/M382 (Phase D)
-- Benefit claims (reimbursement) mirroring the business-trip ExpenseClaim state machine:
-- DRAFT → SUBMITTED → APPROVED / REJECTED → PAID (+ CANCELLED). Payout is tracking-only
-- (markPaid stamps a reference) — NOT auto-pushed to payroll, avoiding the payroll_bonus
-- run_id NOT-NULL trap; wiring a reimbursement to payroll is a documented later seam.

CREATE SEQUENCE IF NOT EXISTS compbenefits.benefit_claim_seq START 1;

CREATE TABLE compbenefits.benefit_claim (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         varchar(64) NOT NULL DEFAULT 'default',
    claim_no          varchar(40) NOT NULL,
    employee_id       uuid NOT NULL,
    enrollment_id     uuid REFERENCES compbenefits.benefit_enrollment (id),
    plan_id           uuid,
    claim_date        date NOT NULL,
    currency          varchar(10) NOT NULL DEFAULT 'AZN',
    total_amount      numeric(14,2) NOT NULL DEFAULT 0,
    approved_amount   numeric(14,2),
    status            varchar(20) NOT NULL DEFAULT 'DRAFT',
    description       text,
    submitted_by      varchar(80),
    submitted_at      timestamptz,
    reviewed_by       varchar(80),
    reviewed_at       timestamptz,
    review_notes      text,
    paid_by           varchar(80),
    paid_at           timestamptz,
    payment_reference varchar(120),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_claim_status_check
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PAID','CANCELLED')),
    CONSTRAINT benefit_claim_no_unique UNIQUE (tenant_id, claim_no)
);
CREATE INDEX benefit_claim_emp_idx ON compbenefits.benefit_claim (tenant_id, employee_id, status);

CREATE TABLE compbenefits.benefit_claim_item (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    varchar(64) NOT NULL DEFAULT 'default',
    claim_id     uuid NOT NULL REFERENCES compbenefits.benefit_claim (id) ON DELETE CASCADE,
    service_date date,
    description  varchar(300) NOT NULL,
    amount       numeric(14,2) NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX benefit_claim_item_claim_idx ON compbenefits.benefit_claim_item (claim_id);

COMMENT ON TABLE compbenefits.benefit_claim IS
    'HCM_11 M381 — benefit reimbursement claim (DRAFT→SUBMITTED→APPROVED/REJECTED→PAID).';
