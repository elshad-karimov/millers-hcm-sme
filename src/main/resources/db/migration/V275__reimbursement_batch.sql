-- V275: Reimbursement batch + thin payroll bridge (M455 — HCM_28 Phase F.4)
-- Group approved expense claims into reimbursement batches for payment.
-- PRD §28.3.10 & analysis.md Phase F.

CREATE SEQUENCE business_trip.reimbursement_batch_no_seq START 1;

CREATE TABLE business_trip.reimbursement_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',

    batch_no VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,  -- DRAFT | APPROVED | PAID
    total_amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'AZN',

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    approved_by VARCHAR(120),
    approved_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    payment_ref VARCHAR(100)  -- External payment reference (bank transfer ID, etc.)
);

CREATE TABLE business_trip.reimbursement_batch_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES business_trip.reimbursement_batch(id) ON DELETE CASCADE,
    expense_claim_id UUID NOT NULL,  -- FK to business_trip.expense_claim
    amount NUMERIC(14,2) NOT NULL
);

CREATE INDEX idx_reimbursement_batch_status ON business_trip.reimbursement_batch(tenant_id, status);
CREATE INDEX idx_reimbursement_batch_item_batch ON business_trip.reimbursement_batch_item(batch_id);
CREATE INDEX idx_reimbursement_batch_item_claim ON business_trip.reimbursement_batch_item(expense_claim_id);
COMMENT ON TABLE business_trip.reimbursement_batch IS 'Expense reimbursement payment batches (M455)';
COMMENT ON TABLE business_trip.reimbursement_batch_item IS 'Expense claims included in a reimbursement batch (M455)';

-- Add reimbursed status to expense_claim (ClaimStatus enum already has PAID, but we rename semantically to REIMBURSED).
-- No schema change needed — ClaimStatus.PAID already covers this.
