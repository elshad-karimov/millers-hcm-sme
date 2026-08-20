-- ----------------------------------------------------------------------------
-- V320 — Per-employee pay-rule overrides + the third pay basis.
--
-- The January 2026 workbook does not price every employee the same way. Rather
-- than hardcode the exceptions, each becomes configuration:
--
--   * MONTHLY_SALARY_MULTIPLE — row 9 pays offshore as salary x 1.75 regardless
--     of hours. Now a basis, not a special case.
--   * time_pay_rule_override  — one employee, one category, a different
--     multiplier or basis, effective-dated.
--
-- Selected defaults (see PRD/timesheet-payroll-inputs/BLOCKERS.md):
--   1. Deviating rows  -> the canonical hourly rule is the default; the three
--      workbook exceptions are expressible as overrides but are NOT seeded,
--      because seeding them would silently bless a possible typo.
--   2. Excess / MEWA   -> PERCENT_OF_ONSHORE is the default method.
--   3. Income tax      -> the workbook's oil-and-gas 14/25 regime, selected by
--      a tenant setting so switching later is data, not a deploy.
--
-- Still payroll-affecting; still not wired into PayrollEngine.
-- ----------------------------------------------------------------------------

-- 1. Third basis on the catalog ----------------------------------------------
ALTER TABLE payroll.time_pay_rule
    DROP CONSTRAINT IF EXISTS ck_time_pay_rule_basis;
ALTER TABLE payroll.time_pay_rule
    ADD CONSTRAINT ck_time_pay_rule_basis
    CHECK (basis IN ('HOURLY_RATE', 'OVERTIME_RATE', 'FLAT_PER_UNIT', 'MONTHLY_SALARY_MULTIPLE'));

-- 2. Per-employee override ----------------------------------------------------
-- Effective-dated so a pay-basis change is a dated fact, not an edit that
-- rewrites what someone was already paid.
CREATE TABLE payroll.time_pay_rule_override (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64)   NOT NULL DEFAULT 'default',
    employee_id    UUID          NOT NULL REFERENCES core_hr.employee (id),
    category_code  VARCHAR(60)   NOT NULL,

    basis          VARCHAR(30)
        CONSTRAINT ck_override_basis
        CHECK (basis IS NULL OR basis IN
               ('HOURLY_RATE', 'OVERTIME_RATE', 'FLAT_PER_UNIT', 'MONTHLY_SALARY_MULTIPLE')),
    multiplier     NUMERIC(8,4),
    flat_amount    NUMERIC(12,2),

    effective_from DATE          NOT NULL,
    effective_to   DATE,
    -- Not optional: an exception to the pay rules must say why it exists.
    reason         TEXT          NOT NULL,

    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),

    CONSTRAINT ck_override_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT uq_override UNIQUE (tenant_id, employee_id, category_code, effective_from)
);

CREATE INDEX idx_override_employee
    ON payroll.time_pay_rule_override (tenant_id, employee_id, category_code);

-- 3. Which statutory regime prices this tenant --------------------------------
-- AZ_OIL_GAS_2026    : the workbook's 14% / 25% with a 200 AZN exemption.
-- AZ_PRIVATE_2026    : the 3% / 10% / 14% rates already in StatutoryCalculator.
-- Defaulted to the workbook's regime because the workbook is what this customer
-- actually pays from. Change the setting, not the code, to switch.
INSERT INTO config.tenant_setting (tenant_id, key, value, updated_at)
VALUES ('default', 'payroll.timepay.tax-regime', 'AZ_OIL_GAS_2026', now())
ON CONFLICT (tenant_id, key) DO NOTHING;
