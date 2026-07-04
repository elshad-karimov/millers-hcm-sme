-- Holiday-OT multiplier (PRD 8.9.4 — milestone 39).
--
-- Closes the M38 hook: TimesheetGenerator now emits the
-- WORKED_ON_HOLIDAY anomaly on any day where the employee logged
-- worked minutes AND the date is in core_hr.holiday. This migration
-- wires the multiplier into the OT rule_json so PayrollEngine pays
-- holiday OT at the configured premium.
--
-- Labour Code reference: Article 167 — work on weekly days off, public
-- holidays, and days of mourning is compensated at "not less than"
-- double the standard hourly rate. The 2.0 here is the legal floor;
-- collective agreements can raise it without schema changes.
--
-- We use jsonb_set so the migration is idempotent — re-running it on
-- a row that already has the key is a no-op.

UPDATE payroll.statutory_rule
SET rule_json = jsonb_set(rule_json, '{holidayMultiplier}', '2.0'::jsonb, TRUE)
WHERE code            = 'OVERTIME_AZ'
  AND jurisdiction    = 'AZ'
  AND effective_from  = DATE '2026-01-01';

COMMENT ON COLUMN payroll.statutory_rule.rule_json IS
    'JSONB rule body. For OVERTIME_AZ keys are firstHoursPerDay, firstMultiplier, afterMultiplier, expectedMonthlyHours, and (M39) holidayMultiplier applied to OT hours on dates in core_hr.holiday — picks up the WORKED_ON_HOLIDAY anomaly emitted by TimesheetGenerator.';
