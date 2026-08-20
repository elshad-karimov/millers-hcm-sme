-- ----------------------------------------------------------------------------
-- V322 — Work Location, Project/Cost Code and actual-minutes overtime on the
--        daily timesheet, per the agreed UI prototype.
--
-- Three gaps between the prototype and what the system stored:
--
--  1. WORK LOCATION — the paper form's "Work Location" column (SCV, BDWJF,
--     Business Trip…). No column existed. The permitted values are a tenant
--     setting rather than a table: it is a short, rarely-changing list that
--     HR edits in Tenant Settings, and a table would add a screen nobody asked
--     for. Leave the setting empty and the field accepts free text.
--
--  2. PROJECT / COST CODE — timesheet_day.project_id and .task_code already
--     existed but were never exposed to the employee. Nothing to migrate; the
--     work is in the API and the grid.
--
--     The prototype marks it REQUIRED. That ships switched OFF
--     (timesheet.validation.require-project = false) because timesheet.project
--     is currently EMPTY — turning it on before projects are loaded would make
--     every timesheet in the system unsubmittable. Flip it once the project
--     list is populated.
--
--  3. OVERTIME IN ACTUAL MINUTES — the employee types minutes actually worked;
--     the payable figure is rounded by a company rule and is NOT editable.
--     ONSHORE_OVERTIME_MINUTES becomes the employee-entered category and
--     ONSHORE_OVERTIME_HOURS becomes system-derived from it.
--
--     The rounding step is a setting, not a constant: the prototype rounds to
--     30 minutes and says "replace with configured payroll rule", and rounding
--     overtime changes pay, so it must be visible and auditable rather than
--     buried in code.
-- ----------------------------------------------------------------------------

-- 1) Work location on the day ------------------------------------------------
ALTER TABLE timesheet.timesheet_day
    ADD COLUMN IF NOT EXISTS work_location VARCHAR(80);

COMMENT ON COLUMN timesheet.timesheet_day.work_location IS
  'Where the day was worked (free text, or one of config setting timesheet.work-locations).';

-- 2) Settings, seeded for every existing tenant ------------------------------
INSERT INTO config.tenant_setting (tenant_id, key, value, created_by, updated_by)
SELECT t.id, s.key, s.value, 'flyway-V322', 'flyway-V322'
  FROM config.tenant t
 CROSS JOIN (VALUES
        -- Starting list from the prototype; HR edits this in Tenant Settings.
        ('timesheet.work-locations',            'BDWJF,SCV,Aberdeen'),
        -- Round actual OT minutes to the nearest N minutes. 0 = no rounding.
        ('timesheet.overtime.rounding-minutes', '30'),
        -- Off until timesheet.project is populated (see header).
        ('timesheet.validation.require-project', 'false')
     ) AS s(key, value)
 WHERE NOT EXISTS (
        SELECT 1 FROM config.tenant_setting e
         WHERE e.tenant_id = t.id AND e.key = s.key);

-- 3) Overtime: employee types minutes, system derives payable hours ----------

-- Both checks predate this migration: the unit list has no MINUTES, and the
-- source list has no rule-based derivation (only calendar / roster / leave).
ALTER TABLE timesheet.time_category
    DROP CONSTRAINT IF EXISTS ck_time_category_unit;
ALTER TABLE timesheet.time_category
    ADD CONSTRAINT ck_time_category_unit
    CHECK (unit IN ('HOURS', 'DAYS', 'MINUTES'));

ALTER TABLE timesheet.time_category
    DROP CONSTRAINT IF EXISTS ck_time_category_source;
ALTER TABLE timesheet.time_category
    ADD CONSTRAINT ck_time_category_source
    CHECK (source IN ('EMPLOYEE', 'HOLIDAY_CALENDAR', 'SHIFT_SCHEDULE', 'LEAVE',
                      'OVERTIME_ROUNDING'));

INSERT INTO timesheet.time_category
       (tenant_id, code, name, unit, applies_to, derived, source, max_per_day, display_order, active)
SELECT c.tenant_id, 'ONSHORE_OVERTIME_MINUTES', 'Onshore Overtime (actual minutes)',
       'MINUTES', 'ONSHORE,REMOTE', false, 'EMPLOYEE', 720.00, 25, true
  FROM (SELECT DISTINCT tenant_id FROM timesheet.time_category) c
 WHERE NOT EXISTS (
        SELECT 1 FROM timesheet.time_category e
         WHERE e.tenant_id = c.tenant_id AND e.code = 'ONSHORE_OVERTIME_MINUTES');

-- The rounded figure is now the system's, not the employee's.
UPDATE timesheet.time_category
   SET derived = true,
       source  = 'OVERTIME_ROUNDING',
       name    = 'Onshore Overtime (rounded hours)'
 WHERE code = 'ONSHORE_OVERTIME_HOURS';

-- Carry any hours already entered by hand over to the new minutes category, so
-- an existing draft keeps its overtime instead of silently losing it.
INSERT INTO timesheet.day_quantity (tenant_id, timesheet_day_id, category_code, quantity)
SELECT q.tenant_id, q.timesheet_day_id, 'ONSHORE_OVERTIME_MINUTES', ROUND(q.quantity * 60)
  FROM timesheet.day_quantity q
 WHERE q.category_code = 'ONSHORE_OVERTIME_HOURS'
   AND q.quantity > 0
   AND NOT EXISTS (
        SELECT 1 FROM timesheet.day_quantity e
         WHERE e.timesheet_day_id = q.timesheet_day_id
           AND e.category_code = 'ONSHORE_OVERTIME_MINUTES');
