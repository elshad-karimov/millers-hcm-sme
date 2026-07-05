-- V274: Expense policy validation engine (M454 — HCM_28 Phase F.3)
-- Policy limits and validation rules for expense claims.
-- PRD §28.3.9 & analysis.md Phase F.

CREATE TABLE business_trip.expense_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',

    -- Matching dimensions
    category VARCHAR(40) NOT NULL,  -- Must match ExpenseCategory enum values
    employee_grade VARCHAR(40),     -- NULL = all grades

    -- Limits
    max_per_transaction NUMERIC(10,2),  -- NULL = no limit
    max_daily NUMERIC(10,2),            -- NULL = no limit
    receipt_required_above NUMERIC(10,2) NOT NULL DEFAULT 20.00,  -- AZN

    blocked BOOLEAN NOT NULL DEFAULT false,  -- Category completely blocked
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_expense_policy_tenant_cat ON business_trip.expense_policy(tenant_id, category);
CREATE INDEX idx_expense_policy_active ON business_trip.expense_policy(tenant_id, active) WHERE active;
COMMENT ON TABLE business_trip.expense_policy IS 'Expense policy validation rules (M454)';

-- Seed AZ expense policy defaults (PRD analysis.md Phase F).
INSERT INTO business_trip.expense_policy
    (category, employee_grade, max_per_transaction, max_daily, receipt_required_above, effective_from)
VALUES
    -- Meals: 50 AZN/day
    ('MEALS', NULL, NULL, 50.00, 20.00, '2026-01-01'),

    -- Accommodation: 150 AZN domestic, 250 AZN international (simplified: one entry, international handled via trip type seam)
    ('ACCOMMODATION', NULL, 150.00, NULL, 20.00, '2026-01-01'),

    -- Transport: 100 AZN per transaction
    ('TRANSPORT', NULL, 100.00, NULL, 20.00, '2026-01-01'),

    -- Flight: no default limit (large amounts expected)
    ('FLIGHT', NULL, NULL, NULL, 20.00, '2026-01-01'),

    -- Visa/registration: 100 AZN per transaction
    ('VISA_FEES', NULL, 100.00, NULL, 20.00, '2026-01-01'),
    ('REGISTRATION', NULL, 100.00, NULL, 20.00, '2026-01-01'),

    -- Communication: 50 AZN/day
    ('COMMUNICATION', NULL, NULL, 50.00, 20.00, '2026-01-01'),

    -- Other: 100 AZN per transaction
    ('OTHER', NULL, 100.00, NULL, 20.00, '2026-01-01');

-- Add policy_flags column to expense_item to store validation verdicts (M454).
ALTER TABLE business_trip.expense_item ADD COLUMN policy_flags VARCHAR(500);
COMMENT ON COLUMN business_trip.expense_item.policy_flags IS 'Pipe-separated policy validation flags: WARNING|RECEIPT_REQUIRED|BLOCKED';
