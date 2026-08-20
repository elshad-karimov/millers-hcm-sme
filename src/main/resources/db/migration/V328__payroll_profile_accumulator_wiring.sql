-- ----------------------------------------------------------------------------
-- V328 — Wire the excess accumulator to the attendance period lock, and make
--        its inputs configuration rather than code.
--
-- V327 built the ledger but nothing populated it: ExcessAccumulatorService had
-- no production caller. This migration adds the two columns that let the
-- posting service run without inventing anything, and records on every posted
-- month exactly which categories produced it.
--
-- PAYROLL-AFFECTING. Still nothing runs, finalises, posts or reverses a
-- payroll: the accumulator records hours owed, and settlement still refuses
-- until the rotation excess multiplier is answered (BLOCKERS Q2).
-- ----------------------------------------------------------------------------

-- 1. Which categories count as "actual eligible hours" -------------------------
-- BLOCKERS Q6.1 asks whether the four-month accumulator counts offshore only,
-- or offshore + onshore + night/holiday. Seeded to mirror the monthly excess
-- sum (offshore + onshore) so the two methods agree, with night added only when
-- night_hours_separate_from_base says night hours are extra rather than a
-- subset. Changing it is a row, not a deploy — and because every posted month
-- records the categories it used, changing it never silently rewrites history.
ALTER TABLE payroll.calculation_profile
    ADD COLUMN IF NOT EXISTS accumulator_categories TEXT;

UPDATE payroll.calculation_profile
   SET accumulator_categories = 'OFFSHORE_HOURS,ONSHORE_HOURS'
 WHERE excess_method = 'BALANCING_PERIOD'
   AND accumulator_categories IS NULL;

COMMENT ON COLUMN payroll.calculation_profile.accumulator_categories IS
  'Comma-separated timesheet category codes summed into the balancing-period '
  'accumulator. BLOCKERS Q6.1 — confirm the set before the first settlement.';

-- 2. What a posted month was actually built from --------------------------------
-- Stored per row so a settlement stays traceable to the rule that produced it,
-- even after the profile is reconfigured (global rule 11).
ALTER TABLE payroll.excess_accumulator_month
    ADD COLUMN IF NOT EXISTS categories_used TEXT,
    ADD COLUMN IF NOT EXISTS source          VARCHAR(24) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE payroll.excess_accumulator_month
    DROP CONSTRAINT IF EXISTS ck_excess_month_source;
ALTER TABLE payroll.excess_accumulator_month
    ADD CONSTRAINT ck_excess_month_source
    CHECK (source IN ('PERIOD_LOCK', 'REPOST', 'MANUAL'));

COMMENT ON COLUMN payroll.excess_accumulator_month.source IS
  'PERIOD_LOCK = posted automatically when the attendance period locked. '
  'REPOST = re-posted by payroll after a correction. MANUAL = entered directly.';

-- 3. Settlement audit trail -------------------------------------------------------
-- settled_by/settled_at already exist. This records which payroll period paid
-- it, so a settlement can be tied to the run that carried it.
ALTER TABLE payroll.excess_accumulator
    ADD COLUMN IF NOT EXISTS settled_in_period_year  INTEGER,
    ADD COLUMN IF NOT EXISTS settled_in_period_month INTEGER,
    ADD COLUMN IF NOT EXISTS settlement_note         TEXT;
