-- ----------------------------------------------------------------------------
-- M460 — HCM_30 Phase B.1: Loan Type catalog (tenant-specific).
--
-- Eligibility rules: max_amount (nullable), max_multiple_of_net (default 3),
-- max_months (24), interest_rate_pct (0=interest-free default), min_tenure_months (6),
-- max_active_loans (1). Simple interest seam deferred (interest_rate_pct placeholder).
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS payroll.loan_type (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               varchar(80)  NOT NULL DEFAULT 'default',
    code                    varchar(60)  NOT NULL,
    name                    varchar(200) NOT NULL,
    description             text,
    max_amount              numeric(12,2),
    max_multiple_of_net     numeric(4,1) NOT NULL DEFAULT 3.0,
    max_months              int          NOT NULL DEFAULT 24,
    interest_rate_pct       numeric(5,2) NOT NULL DEFAULT 0.00,
    min_tenure_months       int          NOT NULL DEFAULT 6,
    max_active_loans        int          NOT NULL DEFAULT 1,
    active                  boolean      NOT NULL DEFAULT true,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              varchar(80),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              varchar(80),
    CONSTRAINT loan_type_uq UNIQUE (tenant_id, code),
    CONSTRAINT loan_type_max_multiple_check CHECK (max_multiple_of_net > 0),
    CONSTRAINT loan_type_max_months_check CHECK (max_months > 0),
    CONSTRAINT loan_type_interest_rate_check CHECK (interest_rate_pct >= 0),
    CONSTRAINT loan_type_min_tenure_check CHECK (min_tenure_months >= 0),
    CONSTRAINT loan_type_max_active_check CHECK (max_active_loans >= 0)
);

CREATE INDEX IF NOT EXISTS loan_type_tenant_idx ON payroll.loan_type (tenant_id);
CREATE INDEX IF NOT EXISTS loan_type_active_idx ON payroll.loan_type (tenant_id, active);

COMMENT ON TABLE payroll.loan_type IS
    'M460 — Tenant-specific loan type catalog with eligibility rules. Interest-free default (interest_rate_pct=0).';

-- ── Seed default types (GENERAL, EMERGENCY) ─────────────────────────────────
INSERT INTO payroll.loan_type (tenant_id, code, name, description, max_multiple_of_net, max_months, interest_rate_pct, min_tenure_months, max_active_loans, active)
VALUES
    ('default', 'GENERAL', 'General Loan', 'General purpose employee loan', 3.0, 24, 0.00, 6, 1, true),
    ('default', 'EMERGENCY', 'Emergency Loan', 'Emergency employee loan', 3.0, 12, 0.00, 3, 1, true)
ON CONFLICT (tenant_id, code) DO NOTHING;
