-- ----------------------------------------------------------------------------
-- V327 — Payroll calculation profiles, balancing periods and the excess
--        accumulator (PRD/payroll-calculation-profiles).
--
-- Source of truth: the WhatsApp discussion with Emil plus the three July 2026
-- spreadsheets. Every multiplier below is transcribed from a formula in those
-- files, not chosen.
--
-- The central fact: there is no universal salary formula. An employee is
-- assigned a calculation profile and payroll dispatches to the matching engine.
-- Two employees with identical timesheets and identical base salaries are paid
-- different amounts if their profiles differ, and that is correct.
--
-- PAYROLL-AFFECTING. Nothing here is wired into PayrollEngine. It feeds a
-- read-only preview so the numbers can be checked against the spreadsheets
-- before any money moves. A live run needs human sign-off (autonomy rule 3).
--
-- THREE COLUMNS ARE DELIBERATELY NULL and make the engine refuse rather than
-- guess. See prd/payroll-calculation-profiles/BLOCKERS.md:
--   * calculation_profile.night_hours_separate_from_base  (Q1)
--   * calculation_profile.excess_multiplier for rotation (Q2)
--   * calculation_profile.derived_offshore_deducts_sick is set but unconfirmed (Q6)
-- ----------------------------------------------------------------------------

-- 1. Balancing schemes -------------------------------------------------------
-- Summarised working-time accounting: overtime is not decided monthly. Fixed
-- company-wide calendar windows, NOT four months from the employee start date.

CREATE TABLE payroll.balancing_scheme (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  VARCHAR(64) NOT NULL DEFAULT 'default',
    code       VARCHAR(40) NOT NULL,
    name       VARCHAR(160) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_balancing_scheme UNIQUE (tenant_id, code)
);

CREATE TABLE payroll.balancing_period_def (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64) NOT NULL DEFAULT 'default',
    scheme_code      VARCHAR(40) NOT NULL,

    -- 1, 2, 3 within the calendar year.
    period_seq       INTEGER     NOT NULL,
    start_month      INTEGER     NOT NULL,
    end_month        INTEGER     NOT NULL,
    -- The payroll month the accumulated excess is settled in.
    settlement_month INTEGER     NOT NULL,

    CONSTRAINT uq_balancing_period UNIQUE (tenant_id, scheme_code, period_seq),
    CONSTRAINT ck_balancing_months CHECK (
        start_month BETWEEN 1 AND 12
        AND end_month BETWEEN 1 AND 12
        AND settlement_month BETWEEN 1 AND 12
        AND start_month <= end_month)
);

INSERT INTO payroll.balancing_scheme (tenant_id, code, name, note)
VALUES ('default', 'OFFSHORE_4_MONTH', 'Offshore rotation — three fixed periods',
        'Settlement dates stated by the company: 30 April, 31 August, 31 December.');

INSERT INTO payroll.balancing_period_def
    (tenant_id, scheme_code, period_seq, start_month, end_month, settlement_month)
VALUES
    ('default', 'OFFSHORE_4_MONTH', 1, 1,  4,  4),
    ('default', 'OFFSHORE_4_MONTH', 2, 5,  8,  8),
    ('default', 'OFFSHORE_4_MONTH', 3, 9, 12, 12);

-- 2. Calculation profiles ----------------------------------------------------

CREATE TABLE payroll.calculation_profile (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(64)  NOT NULL DEFAULT 'default',
    code                  VARCHAR(40)  NOT NULL,
    name                  VARCHAR(160) NOT NULL,

    -- How the offshore portion is priced. This is the distinction that must
    -- never be collapsed into one generic "offshore calculation":
    --   NONE              : no offshore component
    --   HOURLY            : (offshoreHours + offshoreNightHours) x rate x mult
    --   MONTHLY_BASE      : baseSalary x mult, regardless of hours worked
    --   DERIVED_FROM_NORM : rate x (norm - onshoreHours - sickHours) x mult
    offshore_salary_mode  VARCHAR(24)  NOT NULL
        CONSTRAINT ck_calc_profile_offshore_mode
        CHECK (offshore_salary_mode IN ('NONE','HOURLY','MONTHLY_BASE','DERIVED_FROM_NORM')),
    offshore_multiplier   NUMERIC(8,4),

    -- NONE | MONTHLY | BALANCING_PERIOD
    excess_method         VARCHAR(24)  NOT NULL DEFAULT 'NONE'
        CONSTRAINT ck_calc_profile_excess_method
        CHECK (excess_method IN ('NONE','MONTHLY','BALANCING_PERIOD')),

    -- BLOCKERS Q2. NULL for rotation on purpose: "2 qat ve 75% elave" is either
    -- 2 x 1.75 = 3.50 or 2 + 0.75 = 2.75, an 856 AZN difference per settlement,
    -- and the July data cannot resolve it (July is not a settlement month).
    -- An unconfigured settlement refuses to calculate.
    excess_multiplier     NUMERIC(8,4),

    -- BLOCKERS Q1. NULL on purpose, and it governs TWO things.
    --
    -- The July spreadsheets price offshore as (offshore + night) x rate x 1.75
    -- and compute excess as offshore + onshore + night - norm, i.e. night hours
    -- are a SEPARATE ADDEND. But in this system night hours are a
    -- RE-CLASSIFICATION of hours already inside offshore (V317, shift schedule),
    -- which is why the night line pays only a 0.20 top-up. If they are a subset,
    -- adding them again double-counts every night hour -- roughly 800 AZN per
    -- employee per month at 24 night hours and a 19 AZN rate, in the direction
    -- of overpayment. No July example can discriminate: the only worked offshore
    -- amount has zero night hours.
    --
    -- NULL is handled differently on the two paths, because the evidence is:
    --   * EARNINGS  -> fall back to treating night as a subset (do not add it to
    --     the 1.75 / 1.60 base). That is not a guess: it is slice 3's behaviour,
    --     pinned to the cent against the January workbook rows 13 and 14. The
    --     calculation warns that it is using the fallback.
    --   * EXCESS    -> refuse. There is no validated precedent (slice 3 never
    --     derived excess from hours) and the two readings differ by real money.
    night_hours_separate_from_base BOOLEAN,

    balancing_scheme_code VARCHAR(40),

    -- BLOCKERS Q6. Only meaningful for DERIVED_FROM_NORM.
    derived_offshore_deducts_sick BOOLEAN NOT NULL DEFAULT TRUE,

    -- Documentary: 1 ON / 1 OFF at 12h for rotation.
    planned_daily_hours   NUMERIC(5,2),
    work_pattern          VARCHAR(40),

    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    note                  TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_calculation_profile UNIQUE (tenant_id, code),
    -- A balancing profile without a scheme could never settle.
    CONSTRAINT ck_calc_profile_scheme CHECK (
        excess_method <> 'BALANCING_PERIOD' OR balancing_scheme_code IS NOT NULL),
    CONSTRAINT ck_calc_profile_offshore_mult CHECK (
        offshore_salary_mode = 'NONE' OR offshore_multiplier IS NOT NULL)
);

INSERT INTO payroll.calculation_profile
    (tenant_id, code, name, offshore_salary_mode, offshore_multiplier,
     excess_method, excess_multiplier, night_hours_separate_from_base,
     balancing_scheme_code, planned_daily_hours, work_pattern, note)
VALUES
    ('default', 'ONSHORE_FIXED', 'Onshore contract — 5/2, 40h week',
     'NONE', NULL, 'NONE', NULL, NULL, NULL, 8.00, '5/2',
     'Normal Azerbaijani working calendar. A full norm month reproduces base salary.'),

    ('default', 'ONSHORE_RANDOM_OFFSHORE', 'Onshore contract with random offshore trips',
     'HOURLY', 1.75, 'MONTHLY', 1.75, NULL, NULL, 8.00, '5/2',
     'Offshore hours priced hourly at 1.75. Excess settled in the same month as '
     || 'the trip and priced as offshore work (1.75), not at the 2x overtime rate. '
     || 'night_hours_separate_from_base is NULL — BLOCKERS Q1.'),

    ('default', 'OFFSHORE_ROTATION', 'Offshore rotation — 1 ON / 1 OFF, 12h days',
     'MONTHLY_BASE', 1.75, 'BALANCING_PERIOD', NULL, NULL, 'OFFSHORE_4_MONTH', 12.00, '1/1',
     'baseSalary x 1.75 every qualifying month regardless of offshore days worked. '
     || 'This is the pay basis behind January workbook row 9 (2984 x 1.75 = 5222.00), '
     || 'which slice 3 could not explain. excess_multiplier is NULL — BLOCKERS Q2.'),

    ('default', 'OFFSHORE_RANDOM_ONSHORE', 'Offshore contract with random onshore hours',
     'DERIVED_FROM_NORM', 1.75, 'NONE', NULL, NULL, NULL, 12.00, '1/1',
     'Offshore hours are DERIVED from the norm, not read from the timesheet: '
     || 'rate x (norm - onshore - sick) x 1.75. Verified against base 2428 / norm 184 / '
     || '8 onshore = 4064.26 + 105.57. BLOCKERS Q6.');

-- 3. Which profile an employee is on, and since when --------------------------
-- Effective-dated: changing someone's pay basis is a dated fact, not an edit
-- that rewrites what they were already paid.

CREATE TABLE payroll.employee_calculation_profile (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64) NOT NULL DEFAULT 'default',
    employee_id    UUID        NOT NULL,
    profile_code   VARCHAR(40) NOT NULL,
    effective_from DATE        NOT NULL,
    effective_to   DATE,
    reason         TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),

    CONSTRAINT ck_emp_calc_profile_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_emp_calc_profile_employee
    ON payroll.employee_calculation_profile (tenant_id, employee_id, effective_from DESC);

-- 4. MEWA — split out of employee_excess_rule --------------------------------
-- MEWA is not excess. Slice 3 conflated them because the January workbook shows
-- both in one column: PERCENT_OF_ONSHORE rows were MEWA, UNITS_AT_RATE rows
-- were a fixed excess quantity. They are different earnings with different
-- drivers and must not share a rule row.

CREATE TABLE payroll.employee_mewa_rule (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64)   NOT NULL DEFAULT 'default',
    employee_id    UUID          NOT NULL,

    -- Only ONSHORE_EARNING is observed. Named rather than assumed so a second
    -- basis is a row, not a code change.
    basis          VARCHAR(30)   NOT NULL DEFAULT 'ONSHORE_EARNING'
        CONSTRAINT ck_mewa_basis CHECK (basis IN ('ONSHORE_EARNING')),

    -- 0.3000 and 0.6000 are observed. Per employee: there is no global rule and
    -- none will be invented (BLOCKERS Q7).
    rate           NUMERIC(6,4)  NOT NULL,

    effective_from DATE          NOT NULL,
    effective_to   DATE,
    reason         TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),

    CONSTRAINT ck_mewa_rate CHECK (rate >= 0 AND rate <= 5),
    CONSTRAINT ck_mewa_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_emp_mewa_employee
    ON payroll.employee_mewa_rule (tenant_id, employee_id, effective_from DESC);

-- Carry the existing percentage rules across. The UNITS_AT_RATE rows stay in
-- employee_excess_rule: those are a fixed excess quantity, not MEWA.
INSERT INTO payroll.employee_mewa_rule
    (tenant_id, employee_id, basis, rate, effective_from, effective_to, reason, created_by)
SELECT tenant_id, employee_id, 'ONSHORE_EARNING', percentage, effective_from, effective_to,
       COALESCE(reason, '') || ' (migrated from employee_excess_rule by V327 — '
       || 'PERCENT_OF_ONSHORE was MEWA, not excess)',
       created_by
  FROM payroll.employee_excess_rule
 WHERE method = 'PERCENT_OF_ONSHORE'
   AND percentage IS NOT NULL;

-- 5. The excess accumulator ---------------------------------------------------
-- A rotation employee needs a visible running balance, one row per month.
-- Append-only: payroll data is never physically deleted (global rule 12), and a
-- settlement must be traceable to the months that produced it.

CREATE TABLE payroll.excess_accumulator (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(64)  NOT NULL DEFAULT 'default',
    employee_id          UUID         NOT NULL,
    scheme_code          VARCHAR(40)  NOT NULL,

    period_year          INTEGER      NOT NULL,
    period_seq           INTEGER      NOT NULL,
    period_start         DATE         NOT NULL,
    period_end           DATE         NOT NULL,
    settlement_year      INTEGER      NOT NULL,
    settlement_month     INTEGER      NOT NULL,

    status               VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT ck_excess_accumulator_status CHECK (status IN ('OPEN','SETTLED')),

    -- Running total of (actual - norm) across the period. May be negative
    -- mid-period; only the settled figure is floored at zero.
    balance_hours        NUMERIC(10,2) NOT NULL DEFAULT 0,

    settled_excess_hours NUMERIC(10,2),
    settled_multiplier   NUMERIC(8,4),
    settled_amount       NUMERIC(14,2),
    settled_at           TIMESTAMPTZ,
    settled_by           VARCHAR(120),

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_excess_accumulator UNIQUE (tenant_id, employee_id, period_year, period_seq),
    CONSTRAINT ck_excess_settled_non_negative CHECK (
        settled_excess_hours IS NULL OR settled_excess_hours >= 0),
    -- A settled row must carry the whole story of how it was settled.
    CONSTRAINT ck_excess_settled_complete CHECK (
        status <> 'SETTLED' OR (settled_excess_hours IS NOT NULL
                                AND settled_multiplier IS NOT NULL
                                AND settled_amount IS NOT NULL
                                AND settled_at IS NOT NULL))
);

CREATE INDEX ix_excess_accumulator_employee
    ON payroll.excess_accumulator (tenant_id, employee_id, period_year DESC, period_seq DESC);

CREATE TABLE payroll.excess_accumulator_month (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64)   NOT NULL DEFAULT 'default',
    accumulator_id  UUID          NOT NULL
        REFERENCES payroll.excess_accumulator (id),
    employee_id     UUID          NOT NULL,

    period_year     INTEGER       NOT NULL,
    period_month    INTEGER       NOT NULL,
    actual_hours    NUMERIC(10,2) NOT NULL,
    norm_hours      NUMERIC(10,2) NOT NULL,
    delta_hours     NUMERIC(10,2) NOT NULL,
    -- The balance after this month. Stored, not recomputed, so a settlement can
    -- always be read back exactly as it was decided.
    running_balance NUMERIC(10,2) NOT NULL,

    recorded_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    recorded_by     VARCHAR(120),

    CONSTRAINT uq_excess_month UNIQUE (tenant_id, accumulator_id, period_year, period_month),
    CONSTRAINT ck_excess_month_range CHECK (period_month BETWEEN 1 AND 12)
);

CREATE INDEX ix_excess_month_accumulator
    ON payroll.excess_accumulator_month (tenant_id, accumulator_id, period_year, period_month);

-- 6. July 2026 norm hours ------------------------------------------------------
INSERT INTO payroll.period_norm_hours (tenant_id, period_year, period_month, norm_hours)
VALUES ('default', 2026, 7, 184)
ON CONFLICT (tenant_id, period_year, period_month) DO NOTHING;
