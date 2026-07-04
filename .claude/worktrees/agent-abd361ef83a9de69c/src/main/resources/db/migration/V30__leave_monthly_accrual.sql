-- Monthly leave accrual (PRD 8.5.2, milestone 34).
--
-- Adds the two policy columns needed by LeaveAccrualService.
--
-- Until now, leave_balance.entitlement_days was set once on first touch
-- to leave_type.default_annual_entitlement_days. The Labour Code allows
-- monthly accrual instead — employees earn (default / 12) days each
-- month, with a configurable override. The scheduler walks every active
-- accrual-bearing type on the 1st of each month and bumps the matching
-- (employee, type, year) balance.
--
-- accrues_monthly         — gate flag; if false, the type is one-shot
--                          (annual grant on first touch as before).
-- monthly_accrual_days    — explicit per-month bump. If NULL and
--                          accrues_monthly=TRUE, the service falls back
--                          to default_annual_entitlement_days / 12.

ALTER TABLE leave_mgmt.leave_type
    ADD COLUMN accrues_monthly      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN monthly_accrual_days NUMERIC(6,2);

COMMENT ON COLUMN leave_mgmt.leave_type.accrues_monthly IS
    'When TRUE, LeaveAccrualService credits monthly_accrual_days (or default/12) on the 1st of each month.';
COMMENT ON COLUMN leave_mgmt.leave_type.monthly_accrual_days IS
    'Explicit per-month entitlement bump. NULL falls back to default_annual_entitlement_days / 12.';

-- Flip the seeded ANNUAL row to monthly accrual.
-- 21 days / 12 months ≈ 1.75 days per month — matches PRD 8.5.2 default.
UPDATE leave_mgmt.leave_type
SET accrues_monthly      = TRUE,
    monthly_accrual_days = 1.75
WHERE code = 'ANNUAL';
