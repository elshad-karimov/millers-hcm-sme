-- ----------------------------------------------------------------------------
-- V319 — Pricing rules for timesheet quantities
--        (PRD/timesheet-payroll-inputs, slice 3 of 3).
--
-- Source of truth: "Copy of Payroll calculation 2026 January 2.xlsm", sheet
-- "For JX". Every multiplier and rate below is transcribed from a formula in
-- that workbook, not chosen.
--
-- PAYROLL-AFFECTING. Nothing here is wired into PayrollEngine: this migration
-- only adds the rule catalog the new calculator reads for a read-only preview.
-- Wiring it into a live run needs human sign-off (CLAUDE.md autonomy rule 3).
-- ----------------------------------------------------------------------------

-- 1. How one time category turns into money ----------------------------------
CREATE TABLE payroll.time_pay_rule (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64)   NOT NULL DEFAULT 'default',

    -- Matches timesheet.time_category.code. The quantity comes from the
    -- approved month total; this row says what it is worth.
    category_code   VARCHAR(60)   NOT NULL,

    -- HOURLY_RATE   : quantity x (baseSalary / normHours) x multiplier
    -- OVERTIME_RATE : quantity x (baseSalary / normHours) x 2 x multiplier
    -- FLAT_PER_UNIT : quantity x flat_amount              (allowances)
    basis           VARCHAR(20)   NOT NULL
        CONSTRAINT ck_time_pay_rule_basis
        CHECK (basis IN ('HOURLY_RATE', 'OVERTIME_RATE', 'FLAT_PER_UNIT')),

    -- Absolute, NOT a premium: offshore 1.75 pays 1.75x the hourly rate in
    -- total, not the rate plus 75%.
    multiplier      NUMERIC(8,4)  NOT NULL DEFAULT 1,
    flat_amount     NUMERIC(12,2),

    -- Per unit of quantity that is exempt from every contribution base, while
    -- still being paid in full. The workbook pays meal at 12 AZN/day and
    -- subtracts (days x 5) from the tax, SPF, unemployment and insurance bases.
    exempt_per_unit NUMERIC(12,2) NOT NULL DEFAULT 0,

    display_order   INTEGER       NOT NULL DEFAULT 100,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    note            TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_time_pay_rule UNIQUE (tenant_id, category_code),
    CONSTRAINT ck_time_pay_rule_flat
        CHECK (basis <> 'FLAT_PER_UNIT' OR flat_amount IS NOT NULL)
);

CREATE INDEX idx_time_pay_rule_tenant ON payroll.time_pay_rule (tenant_id, active, display_order);

-- 2. Excess / MEWA — per employee, because the workbook has no single rule ----
-- Four different formulas appear across four employees, and the declared
-- "Excess hours" input is zero in every row, so the quantity does not drive the
-- amount at all. Rather than guess, the rule is configured per person and an
-- employee without one simply earns no excess. See PRD §3.1.
CREATE TABLE payroll.employee_excess_rule (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64)   NOT NULL DEFAULT 'default',
    employee_id    UUID          NOT NULL REFERENCES core_hr.employee (id),

    -- PERCENT_OF_ONSHORE : onshoreAmount x percentage      (rows 10, 11)
    -- UNITS_AT_RATE      : units x hourlyRate x multiplier  (rows 8, 12)
    method         VARCHAR(20)   NOT NULL
        CONSTRAINT ck_excess_method
        CHECK (method IN ('PERCENT_OF_ONSHORE', 'UNITS_AT_RATE')),

    percentage     NUMERIC(6,4),
    units          NUMERIC(9,2),
    multiplier     NUMERIC(8,4)  NOT NULL DEFAULT 1.6,

    effective_from DATE          NOT NULL,
    effective_to   DATE,
    reason         TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),

    CONSTRAINT ck_excess_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_excess_percent
        CHECK (method <> 'PERCENT_OF_ONSHORE' OR percentage IS NOT NULL),
    CONSTRAINT ck_excess_units
        CHECK (method <> 'UNITS_AT_RATE' OR units IS NOT NULL),
    CONSTRAINT uq_excess_employee_from UNIQUE (tenant_id, employee_id, effective_from)
);

CREATE INDEX idx_excess_employee ON payroll.employee_excess_rule (tenant_id, employee_id);

-- 3. Norm working hours per period -------------------------------------------
-- The workbook keeps this in a single cell (F3 = 151 for January 2026). It
-- drives every hourly rate, so it is dated rather than constant.
CREATE TABLE payroll.period_norm_hours (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    period_year   INTEGER      NOT NULL,
    period_month  INTEGER      NOT NULL,
    norm_hours    NUMERIC(8,2) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_period_norm UNIQUE (tenant_id, period_year, period_month),
    CONSTRAINT ck_period_norm_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT ck_period_norm_positive CHECK (norm_hours > 0)
);

-- 4. Seed the rules the January 2026 workbook uses ---------------------------
INSERT INTO payroll.time_pay_rule
    (tenant_id, category_code, basis, multiplier, flat_amount, exempt_per_unit, display_order, note)
VALUES
    ('default', 'OFFSHORE_HOURS',           'HOURLY_RATE',   1.75, NULL,  0, 10, 'Workbook col T'),
    ('default', 'QUAYSIDE_HOURS',           'HOURLY_RATE',   1.60, NULL,  0, 20, 'Workbook col U'),
    ('default', 'ONSHORE_HOURS',            'HOURLY_RATE',   1.00, NULL,  0, 30, 'Workbook col V'),
    ('default', 'ONSHORE_OVERTIME_HOURS',   'OVERTIME_RATE', 1.00, NULL,  0, 40, 'Workbook col W — overtime rate is 2x hourly'),
    ('default', 'MEAL_ALLOWANCE_DAYS',      'FLAT_PER_UNIT', 1.00, 12.00, 5, 50, 'Workbook col Z — 12 AZN/day paid, 5 AZN/day exempt from every contribution base'),
    ('default', 'TRANSPORT_ALLOWANCE_DAYS', 'FLAT_PER_UNIT', 1.00, 10.00, 0, 60, 'Workbook col AA'),
    ('default', 'HOTEL_QUARANTINE_HOURS',   'HOURLY_RATE',   1.75, NULL,  0, 70, 'Workbook col AB'),
    ('default', 'OFFSHORE_NIGHT_HOURS',     'HOURLY_RATE',   0.20, NULL,  0, 80, 'Workbook col AC — top-up on hours already paid offshore'),
    ('default', 'QUAYSIDE_NIGHT_HOURS',     'HOURLY_RATE',   0.20, NULL,  0, 90, 'Workbook col AD'),
    ('default', 'OFFSHORE_HOLIDAY_HOURS',   'HOURLY_RATE',   1.75, NULL,  0, 100, 'Workbook col AE'),
    ('default', 'QUAYSIDE_HOLIDAY_HOURS',   'HOURLY_RATE',   1.60, NULL,  0, 110, 'Workbook col AF');

-- EXCESS_HOURS is deliberately absent: see employee_excess_rule and PRD §3.1.
-- VACATION_HOURS and SICK_LEAVE_HOURS are absent too — the workbook carries
-- their AZN values as manual entries (cols AH/AI), not as a rate x quantity.

INSERT INTO payroll.period_norm_hours (tenant_id, period_year, period_month, norm_hours)
VALUES ('default', 2026, 1, 151);
