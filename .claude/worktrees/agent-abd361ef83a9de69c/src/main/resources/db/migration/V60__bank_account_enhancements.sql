-- M74 / Phase 2 — Banking enhancements (P2-06/07)
--
-- The pre-M74 bank_account schema treated the relationship as 1:1
-- (employee_id UNIQUE). The gap audit flagged three blockers:
--   1. No SWIFT/BIC — international transfers + SEPA bank files fail.
--   2. No salary split — employees can't allocate 70% to one account,
--      30% to another (common request from senior staff).
--   3. No approval workflow — bank changes hit payroll directly.
--
-- This migration:
--   * Drops the 1:1 unique constraint
--   * Adds swift_bic, salary_split_percent, is_primary, notes,
--     last_change_workflow_id
--   * Partial unique index enforces "exactly one primary per employee"
--   * CHECK constraint enforces 0 < salary_split_percent <= 100
--
-- Existing rows are backfilled: each one becomes is_primary=TRUE with
-- salary_split_percent=100 — preserves pre-M74 behaviour for any tenant
-- that hasn't enabled the split feature.

ALTER TABLE payroll.bank_account
    DROP CONSTRAINT IF EXISTS bank_account_employee_id_key;

ALTER TABLE payroll.bank_account
    ADD COLUMN IF NOT EXISTS swift_bic             VARCHAR(11),
    ADD COLUMN IF NOT EXISTS salary_split_percent  NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    ADD COLUMN IF NOT EXISTS is_primary            BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notes                 TEXT,
    -- Workflow integration — populated while a change is awaiting approval.
    -- M77 will wire the BANK_ACCOUNT_CHANGE_APPROVAL workflow against this.
    ADD COLUMN IF NOT EXISTS last_change_workflow_id UUID,
    ADD COLUMN IF NOT EXISTS created_by            VARCHAR(80),
    ADD COLUMN IF NOT EXISTS updated_by            VARCHAR(80);

-- SWIFT/BIC is 8 or 11 alphanumerics (ISO 9362). Tolerate whitespace
-- the user might paste (service strips before insert).
ALTER TABLE payroll.bank_account
    DROP CONSTRAINT IF EXISTS chk_bank_swift_bic;
ALTER TABLE payroll.bank_account
    ADD CONSTRAINT chk_bank_swift_bic
    CHECK (swift_bic IS NULL OR length(swift_bic) IN (8, 11));

ALTER TABLE payroll.bank_account
    DROP CONSTRAINT IF EXISTS chk_bank_split_percent;
ALTER TABLE payroll.bank_account
    ADD CONSTRAINT chk_bank_split_percent
    CHECK (salary_split_percent > 0 AND salary_split_percent <= 100);

-- Index for the common "primary account for employee X" lookup —
-- payroll engine reads this on every payslip.
CREATE INDEX IF NOT EXISTS idx_bank_employee_active
    ON payroll.bank_account (employee_id, active);

-- At most one primary account per employee — partial unique index. Same
-- pattern used for emergency_contact.is_primary (M63) and other "exactly
-- one X per employee" invariants across the codebase.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bank_one_primary_per_employee
    ON payroll.bank_account (employee_id)
    WHERE is_primary = TRUE AND active = TRUE;
