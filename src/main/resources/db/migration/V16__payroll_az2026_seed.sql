-- Azerbaijan 2026 statutory rules (PRD 8.9.4). All values are configurable;
-- the JSON shape below is the contract the payroll engine reads.

-- INCOME TAX — non-oil private sector, monthly:
--   ≤ 2500 AZN: 3% of the portion above the 200 AZN exemption
--   2500–8000:  75 AZN + 10% of the portion above 2500
--   > 8000:     625 AZN + 14% of the portion above 8000
-- The 200 AZN exemption only applies when income ≤ 2500 (the notch is
-- intentional in the legislation — at exactly 2500 the exemption stops).
INSERT INTO payroll.statutory_rule (code, jurisdiction, name, effective_from, rule_json, active) VALUES
('INCOME_TAX_AZ', 'AZ', 'Azerbaijan income tax (2026)', '2026-01-01',
 '{"type":"PROGRESSIVE_BRACKETS","brackets":[
     {"upTo":2500,"rate":0.03,"exemption":200,"exemptionAppliesOnlyBelow":2500,"base":0},
     {"upTo":8000,"rate":0.10,"base":75},
     {"upTo":null,"rate":0.14,"base":625}
   ]}'::jsonb, TRUE);

-- DSMF social insurance:
--   ≤ 8000 (split):  employee 3% of first 200 + 10% of remainder;
--                    employer 22% of first 200 + 15% of remainder
--   > 8000 (extra):  employee +10% of the portion above 8000;
--                    employer +11% of the portion above 8000
INSERT INTO payroll.statutory_rule (code, jurisdiction, name, effective_from, rule_json, active) VALUES
('DSMF_AZ', 'AZ', 'Azerbaijan DSMF social insurance (2026)', '2026-01-01',
 '{"type":"DSMF_AZ_2026",
   "lowerBound":200,
   "thresholdAbove":8000,
   "employee":{"belowFirst":0.03,"betweenFirstAndThreshold":0.10,"aboveThreshold":0.10},
   "employer":{"belowFirst":0.22,"betweenFirstAndThreshold":0.15,"aboveThreshold":0.11}}'::jsonb, TRUE);

-- Mandatory medical insurance:
--   ≤ 2500: 2% employee + 2% employer
--   > 2500: 2% on first 2500 + 0.5% on remainder, each side
INSERT INTO payroll.statutory_rule (code, jurisdiction, name, effective_from, rule_json, active) VALUES
('MMI_AZ', 'AZ', 'Azerbaijan mandatory medical insurance (2026)', '2026-01-01',
 '{"type":"BANDED_PCT",
   "bands":[
     {"upTo":2500,"employee":0.02,"employer":0.02},
     {"upTo":null,"employee":0.005,"employer":0.005}
   ]}'::jsonb, TRUE);

-- Unemployment insurance — flat 0.5% each side.
INSERT INTO payroll.statutory_rule (code, jurisdiction, name, effective_from, rule_json, active) VALUES
('UNEMPLOYMENT_AZ', 'AZ', 'Azerbaijan unemployment insurance (2026)', '2026-01-01',
 '{"type":"FLAT_PCT","employee":0.005,"employer":0.005}'::jsonb, TRUE);

-- Overtime multipliers (Labour Code): 1.5× for the first 2 hours per day,
-- 2× thereafter (PRD 8.9.4 last paragraph).
INSERT INTO payroll.statutory_rule (code, jurisdiction, name, effective_from, rule_json, active) VALUES
('OVERTIME_AZ', 'AZ', 'Azerbaijan overtime multipliers (Labour Code)', '2026-01-01',
 '{"type":"OT_MULTIPLIERS","firstHoursPerDay":2,"firstMultiplier":1.5,"afterMultiplier":2.0,
   "expectedMonthlyHours":160}'::jsonb, TRUE);
