-- V306 — Correct the AZ 2026 income-tax rule (payroll bug fix, human-signed-off).
--
-- The 2026 private-sector income tax has NO 200 AZN exemption. That 200 AZN
-- threshold belongs to DSMF social insurance only (the 3%→10% split point),
-- where the seed already uses it correctly. The V16 INCOME_TAX_AZ first bracket
-- wrongly subtracted a 200 AZN exemption, under-withholding income tax by
-- 6.00 AZN/month (200 × 3%) for every employee earning ≤ 2500 AZN/month and
-- creating a 6 AZN discontinuity at exactly 2500.
--
-- The 3% / 10% / 14% brackets and the 75 / 625 cumulative bases are correct per
-- the 2026 reform (7-year private-sector exemption ended 1 Jan 2026); only the
-- erroneous first-bracket exemption is removed so 3% applies to the full amount:
--   ≤ 2500:     3% of the whole amount            (base 0)
--   2500–8000:  75  + 10% of the part above 2500
--   > 8000:     625 + 14% of the part above 8000
-- Sources: Mercans AZ 2026 statutory alert; PwC/TaxRavens 2026 worked examples.
--
-- Surfaced by the payroll-math regression pack (StatutoryCalculatorTest) and
-- signed off before applying, per the project payroll sign-off rule.

UPDATE payroll.statutory_rule
   SET rule_json = '{"type":"PROGRESSIVE_BRACKETS","brackets":[
       {"upTo":2500,"rate":0.03,"base":0},
       {"upTo":8000,"rate":0.10,"base":75},
       {"upTo":null,"rate":0.14,"base":625}
     ]}'::jsonb
 WHERE code = 'INCOME_TAX_AZ' AND jurisdiction = 'AZ';
