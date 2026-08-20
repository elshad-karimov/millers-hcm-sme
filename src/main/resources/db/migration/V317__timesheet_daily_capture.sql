-- ----------------------------------------------------------------------------
-- V317 — Timesheet daily capture (prd/timesheet-daily-capture, slice 1 of 3).
--
-- Employees record what they did day by day in OPERATIONAL terms — hours by
-- work type, allowance eligibility. Nothing here is monetary: no rate, amount,
-- gross or net. Slice 3 prices these quantities; this slice only produces them.
--
-- Catalog-driven on purpose. A new pay-relevant quantity is a row in
-- time_category, not a migration, and slice 3 binds a salary component to a
-- category CODE rather than hardcoding one customer's workbook columns.
--
-- Nothing existing is dropped or narrowed: timesheet_day keeps every column it
-- had, so attendance-driven generation (TimesheetGenerator) and PayrollEngine
-- keep working untouched.
-- ----------------------------------------------------------------------------

-- 1. Configurable catalog of the quantities a timesheet can carry ------------
CREATE TABLE timesheet.time_category (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64)  NOT NULL DEFAULT 'default',

    -- Stable key. Payroll (slice 3) references THIS, never the display name.
    code           VARCHAR(60)  NOT NULL,
    name           VARCHAR(200) NOT NULL,

    unit           VARCHAR(10)  NOT NULL
        CONSTRAINT ck_time_category_unit CHECK (unit IN ('HOURS', 'DAYS')),

    -- CSV of work types this category may be entered against; empty = any.
    applies_to     VARCHAR(300) NOT NULL DEFAULT '',

    -- TRUE = the system computes it (holiday, night, leave). The employee never
    -- types a derived category, which is what keeps payroll field names out of
    -- the employee's face and the quantities consistent with their source.
    derived        BOOLEAN      NOT NULL DEFAULT FALSE,
    source         VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE'
        CONSTRAINT ck_time_category_source
        CHECK (source IN ('EMPLOYEE', 'HOLIDAY_CALENDAR', 'SHIFT_SCHEDULE', 'LEAVE')),

    -- Validation ceiling for one calendar day (24 h, or 1 allowance day).
    max_per_day    NUMERIC(6,2) NOT NULL DEFAULT 24,

    display_order  INTEGER      NOT NULL DEFAULT 100,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_time_category_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_time_category_tenant ON timesheet.time_category (tenant_id, active, display_order);

-- 2. Per-day quantities ------------------------------------------------------
-- One row per (day, category) that has a non-zero quantity. Sparse by design:
-- a day with 12 offshore hours is one row, not 14 zeroes.
CREATE TABLE timesheet.day_quantity (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    -- timesheet_day is RANGE-partitioned on work_date, so its PK is the
    -- composite (id, work_date) and a FK to timesheet_day(id) is not possible
    -- (same situation V193 documented for payroll_result). The reference is a
    -- plain column; TimesheetEntryService owns the cascade on day delete.
    timesheet_day_id UUID         NOT NULL,

    category_code    VARCHAR(60)  NOT NULL,
    quantity         NUMERIC(9,2) NOT NULL DEFAULT 0
        CONSTRAINT ck_day_quantity_non_negative CHECK (quantity >= 0),

    -- Where a derived value came from, so a system-computed quantity is
    -- visibly not employee-typed: HOLIDAY_CALENDAR / SHIFT_SCHEDULE / LEAVE.
    derived_from     VARCHAR(20),
    -- Set when the employee overrode a derived value — approver must see why.
    override_reason  TEXT,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_day_quantity UNIQUE (timesheet_day_id, category_code)
);

CREATE INDEX idx_day_quantity_category ON timesheet.day_quantity (tenant_id, category_code);
CREATE INDEX idx_day_quantity_day ON timesheet.day_quantity (timesheet_day_id);

-- 3. Monthly aggregate = THE payroll input contract --------------------------
-- Recomputed from day_quantity on every change. Slice 3 reads only this table,
-- never the individual days, so the payroll input surface is one narrow seam.
CREATE TABLE timesheet.timesheet_month_total (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64)  NOT NULL DEFAULT 'default',
    timesheet_id   UUID         NOT NULL
        REFERENCES timesheet.timesheet (id) ON DELETE CASCADE,

    category_code  VARCHAR(60)  NOT NULL,
    quantity       NUMERIC(11,2) NOT NULL DEFAULT 0
        CONSTRAINT ck_month_total_non_negative CHECK (quantity >= 0),

    computed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_month_total UNIQUE (timesheet_id, category_code)
);

CREATE INDEX idx_month_total_category ON timesheet.timesheet_month_total (tenant_id, category_code);

-- 4. The work-type dimension on a day ----------------------------------------
ALTER TABLE timesheet.timesheet_day
    ADD COLUMN IF NOT EXISTS work_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS entry_source VARCHAR(20) NOT NULL DEFAULT 'ATTENDANCE',
    ADD COLUMN IF NOT EXISTS employee_note TEXT,
    ADD COLUMN IF NOT EXISTS attendance_variance_hours NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS variance_explanation TEXT;

ALTER TABLE timesheet.timesheet_day
    DROP CONSTRAINT IF EXISTS ck_tsday_work_type;
ALTER TABLE timesheet.timesheet_day
    ADD CONSTRAINT ck_tsday_work_type CHECK (work_type IS NULL OR work_type IN (
        'ONSHORE', 'OFFSHORE', 'QUAYSIDE', 'BUSINESS_TRIP',
        'REMOTE', 'LEAVE', 'SICK', 'NON_WORKING'));

ALTER TABLE timesheet.timesheet_day
    DROP CONSTRAINT IF EXISTS ck_tsday_entry_source;
ALTER TABLE timesheet.timesheet_day
    ADD CONSTRAINT ck_tsday_entry_source CHECK (entry_source IN (
        'EMPLOYEE', 'ATTENDANCE', 'LEAVE', 'HOLIDAY', 'HR'));

-- Existing rows were all attendance-generated; leave work_type NULL rather than
-- guessing ONSHORE, so "never classified" stays distinguishable from "onshore".

-- 5. Employee submission trail on the header ---------------------------------
-- submitted_at/by already exist. These record the employee's own confirmation
-- (§14 of the design: "I confirm the submitted working time is accurate").
ALTER TABLE timesheet.timesheet
    ADD COLUMN IF NOT EXISTS employee_confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS employee_comment TEXT,
    ADD COLUMN IF NOT EXISTS validation_warnings TEXT;

-- 6. Seed the categories the current workbook needs ---------------------------
-- Quantities only. Rates and amounts belong to payroll and arrive in slice 3.
INSERT INTO timesheet.time_category
    (tenant_id, code, name, unit, applies_to, derived, source, max_per_day, display_order)
VALUES
    ('default', 'OFFSHORE_HOURS',           'Offshore Hours',              'HOURS', 'OFFSHORE',         FALSE, 'EMPLOYEE',         24, 10),
    ('default', 'ONSHORE_HOURS',            'Onshore Working Hours',       'HOURS', 'ONSHORE,REMOTE',   FALSE, 'EMPLOYEE',         24, 20),
    ('default', 'ONSHORE_OVERTIME_HOURS',   'Onshore Overtime Hours',      'HOURS', 'ONSHORE,REMOTE',   FALSE, 'EMPLOYEE',         12, 30),
    ('default', 'QUAYSIDE_HOURS',           'Quayside Hours',              'HOURS', 'QUAYSIDE',         FALSE, 'EMPLOYEE',         24, 40),
    ('default', 'EXCESS_HOURS',             'Excess Hours',                'HOURS', 'ONSHORE,OFFSHORE,QUAYSIDE', FALSE, 'EMPLOYEE', 12, 50),
    ('default', 'MEAL_ALLOWANCE_DAYS',      'Meal Allowance',              'DAYS',  'ONSHORE,REMOTE',   FALSE, 'EMPLOYEE',          1, 60),
    ('default', 'TRANSPORT_ALLOWANCE_DAYS', 'Transport Allowance',         'DAYS',  'ONSHORE,REMOTE',   FALSE, 'EMPLOYEE',          1, 70),
    ('default', 'HOTEL_QUARANTINE_HOURS',   'Hotel Quarantine Hours',      'HOURS', 'OFFSHORE',         FALSE, 'EMPLOYEE',         24, 80),
    ('default', 'OFFSHORE_NIGHT_HOURS',     'Offshore Nightshift Hours',   'HOURS', 'OFFSHORE',         TRUE,  'SHIFT_SCHEDULE',   12, 90),
    ('default', 'QUAYSIDE_NIGHT_HOURS',     'Quayside Nightshift Hours',   'HOURS', 'QUAYSIDE',         TRUE,  'SHIFT_SCHEDULE',   12, 100),
    ('default', 'OFFSHORE_HOLIDAY_HOURS',   'Offshore Public Holiday Hours','HOURS','OFFSHORE',         TRUE,  'HOLIDAY_CALENDAR', 24, 110),
    ('default', 'QUAYSIDE_HOLIDAY_HOURS',   'Quayside Public Holiday Hours','HOURS','QUAYSIDE',         TRUE,  'HOLIDAY_CALENDAR', 24, 120),
    ('default', 'VACATION_HOURS',           'Vacation Hours',              'HOURS', 'LEAVE',            TRUE,  'LEAVE',            24, 130),
    ('default', 'SICK_LEAVE_HOURS',         'Sick Leave Hours',            'HOURS', 'SICK',             TRUE,  'LEAVE',            24, 140);
