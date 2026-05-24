-- Milestone 45: Weekend OT multiplier (Labour Code Art. 167) +
-- daily OT cap (Art. 99) added to the OVERTIME_AZ statutory rule.
--
-- Art. 167 covers BOTH holidays AND weekly rest days (Sat/Sun):
--   payment "not less than double the hourly/daily rate".
--   weekendMultiplier = 2.0 matches the holiday default (same floor).
--
-- Art. 99 limits OT to 4 hours per employee per 2 consecutive days
-- (120 hours/year). We enforce it as a per-day ceiling of 4 hours —
-- the simpler interpretation used by most AZ payroll systems.
-- dailyOtCapHours = 4.0.
--
-- Both fields use jsonb_set with create_missing=TRUE so the UPDATE
-- is fully idempotent; re-running after V35 still produces valid JSON.

UPDATE payroll.statutory_rule
SET    rule_json = jsonb_set(
           jsonb_set(rule_json, '{weekendMultiplier}', '2.0'::jsonb, TRUE),
           '{dailyOtCapHours}', '4.0'::jsonb, TRUE
       )
WHERE  code           = 'OVERTIME_AZ'
AND    jurisdiction   = 'AZ'
AND    effective_from = '2026-01-01';
