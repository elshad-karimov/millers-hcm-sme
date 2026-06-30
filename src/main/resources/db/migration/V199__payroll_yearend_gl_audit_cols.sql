-- V199 — Reconcile year-end + GL entity audit columns (M354/M355 follow-up)
--
-- The M354/M355 entities carry a few audit/aggregate columns that V197/V198 did
-- not create (V197/V198 are already applied and immutable). This corrective
-- migration adds them so Hibernate schema-validation passes and the services that
-- populate them work as written.

-- Year-end annual summary: bonus total + audit timestamps
ALTER TABLE payroll.annual_payroll_summary
    ADD COLUMN IF NOT EXISTS total_bonuses NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now();

-- Annual tax certificate: link to the stored PDF + audit columns
ALTER TABLE payroll.annual_tax_certificate
    ADD COLUMN IF NOT EXISTS pdf_attachment_id UUID,
    ADD COLUMN IF NOT EXISTS generated_by      VARCHAR(160),
    ADD COLUMN IF NOT EXISTS created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at         TIMESTAMPTZ NOT NULL DEFAULT now();

-- GL account mapping: created_at audit column
ALTER TABLE payroll.gl_account_mapping
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
