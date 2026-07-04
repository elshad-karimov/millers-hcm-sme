-- M41: Allowances → payroll integration (PRD 8.15.4 / 8.9).
--
-- Until now comp_benefits.employee_allowance rows existed but the payroll
-- engine ignored them (PayrollEngine.calculate hard-coded
-- BigDecimal allowance = ZERO). This migration adds the snapshot table the
-- engine writes to during a run so each payslip has an immutable allowance
-- breakdown — same pattern as payroll.payroll_bonus, with three additions:
--
--   * taxable flag is snapshotted from allowance_type at calc time so the
--     historical payslip stays consistent even if the catalogue changes;
--   * allowance_type_code + allowance_type_name are denormalised so the
--     payslip can label each line without joining the catalogue;
--   * employee_allowance_id is a soft ref (no FK) into comp_benefits — keeps
--     the schema separation but lets audit trace back to the source row.
--
-- Why a snapshot and not a live join? Audit. Once a run is CALCULATED,
-- editing the source allowance must not retroactively rewrite history.
-- Recalculating an open run is fine — the snapshot rows are wiped and
-- rebuilt by PayrollEngine (same delete-and-rewrite pattern as
-- payroll_result).

CREATE TABLE payroll.payroll_allowance (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                  UUID         NOT NULL REFERENCES payroll.payroll_run (id) ON DELETE CASCADE,
    employee_id             UUID         NOT NULL,

    -- Soft refs into comp_benefits (no FK so we don't fight schema split).
    employee_allowance_id   UUID,
    allowance_type_id       UUID,
    allowance_type_code     VARCHAR(40),
    allowance_type_name     VARCHAR(160),

    amount                  NUMERIC(14,2) NOT NULL,
    currency                VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    -- TRUE  = added to gross, taxed + DSMF/MMI/unempl applied
    -- FALSE = added to net only (post-stat) — keeps employee whole without
    --         inflating the state contribution base
    taxable                 BOOLEAN      NOT NULL DEFAULT TRUE,

    note                    TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_pa_amount CHECK (amount >= 0)
);

CREATE INDEX idx_pa_run_emp   ON payroll.payroll_allowance (run_id, employee_id);
CREATE INDEX idx_pa_type      ON payroll.payroll_allowance (allowance_type_id);

-- Run-level total alongside the existing total_gross / total_net / etc.
-- so the runs list page can show the combined allowance spend at a glance.
ALTER TABLE payroll.payroll_run
    ADD COLUMN total_allowance NUMERIC(14,2) NOT NULL DEFAULT 0;
