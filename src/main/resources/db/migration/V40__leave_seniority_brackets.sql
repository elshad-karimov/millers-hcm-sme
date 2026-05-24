-- ============================================================
-- M47: Leave seniority brackets (PRD 8.5.2)
-- ============================================================
-- Adds an optional JSONB column to leave_type so HR can configure
-- tenure-based entitlement tiers.  Example:
--
--   [{"yearsMin":0,"yearsMax":4,"annualDays":21},
--    {"yearsMin":5,"yearsMax":9,"annualDays":24},
--    {"yearsMin":10,"annualDays":28}]
--
-- When brackets are present the monthly accrual walker uses the
-- employee's years-of-service on the 1st of the target month to
-- pick the matching tier and derives the monthly bump as
-- annualDays / 12.  When the column is NULL the existing
-- monthlyAccrualDays / defaultAnnualEntitlement / 12 fallback
-- chain is used unchanged (back-compat, no data migration needed).
-- ============================================================

ALTER TABLE leave_mgmt.leave_type
    ADD COLUMN IF NOT EXISTS seniority_brackets_json JSONB;

COMMENT ON COLUMN leave_mgmt.leave_type.seniority_brackets_json IS
    'Optional seniority bracket schedule: '
    '[{"yearsMin":int, "yearsMax":int|null, "annualDays":decimal}, ...]. '
    'Overrides monthlyAccrualDays/default_annual_entitlement_days/12 '
    'as the per-employee monthly bump when non-null. '
    'yearsMax null = unbounded upper limit. '
    'Added in V40 (M47 - PRD 8.5.2 seniority bumps).';
