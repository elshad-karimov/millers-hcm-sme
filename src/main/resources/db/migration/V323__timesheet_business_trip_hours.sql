-- ----------------------------------------------------------------------------
-- V323 — Let a business-trip day record hours.
--
-- BUSINESS_TRIP is a selectable work type, but no time category accepted it:
-- ONSHORE_HOURS applied to ONSHORE and REMOTE only. Picking "Business trip" in
-- the grid therefore produced a day that could not carry a single hour, and the
-- server rejected any attempt with CATEGORY_WORK_TYPE_MISMATCH.
--
-- The reference timesheet (RZAYEV Aliagha, July 2026) records business-trip
-- days as 8 onshore hours — rows 27–31, "Training in Aberd." — so onshore hours
-- is the category those hours belong to. Widened to match.
--
-- Allowances follow the same reasoning: a trip day is an onshore-style day for
-- meal and transport eligibility. Kept OUT deliberately — that reference sheet
-- pays NO meal or transport on its business-trip days, and inventing an
-- entitlement would quietly add money to a payslip.
-- ----------------------------------------------------------------------------

UPDATE timesheet.time_category
   SET applies_to = 'ONSHORE,REMOTE,BUSINESS_TRIP'
 WHERE code = 'ONSHORE_HOURS'
   AND applies_to = 'ONSHORE,REMOTE';

UPDATE timesheet.time_category
   SET applies_to = 'ONSHORE,REMOTE,BUSINESS_TRIP'
 WHERE code = 'ONSHORE_OVERTIME_MINUTES'
   AND applies_to = 'ONSHORE,REMOTE';
