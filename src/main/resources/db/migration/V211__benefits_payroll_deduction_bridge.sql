-- HCM_11 Benefits Administration — M378 (Phase B.3) — PAYROLL-IMPACTING
-- Payroll deduction bridge: when an enrollment becomes ACTIVE, a RECURRING payroll_deduction
-- is created for the employee contribution snapshot; on suspend/terminate it is CANCELLED.
-- The PayrollEngine already picks up RECURRING deductions (findActiveForPeriod) and subtracts
-- them from taxable gross — i.e. benefit employee-contributions are treated PRE-TAX.
--
-- payroll_deduction (V121) predates the tenant_id convention; add it here, plus a soft-ref
-- source column mirroring source_advance_id / source_loan_id for clean idempotency.

ALTER TABLE payroll.payroll_deduction
    ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default',
    ADD COLUMN source_benefit_enrollment_id uuid;

CREATE INDEX idx_payroll_deduction_source_benefit
    ON payroll.payroll_deduction (source_benefit_enrollment_id);

COMMENT ON COLUMN payroll.payroll_deduction.source_benefit_enrollment_id IS
    'HCM_11 M378 — benefit enrollment that originated this recurring contribution deduction.';
