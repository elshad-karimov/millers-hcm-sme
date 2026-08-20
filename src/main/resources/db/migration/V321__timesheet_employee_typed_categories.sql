-- ----------------------------------------------------------------------------
-- V321 — Employee-typed nightshift / holiday / leave hours, per the paper
--        timesheet the crews already fill in (Saipem "TS" template).
--
-- Until now the system OWNED six categories: it derived nightshift hours from
-- the shift schedule, public-holiday hours from the holiday calendar, and
-- vacation/sick hours from approved leave, and refused any employee-supplied
-- value ("… is calculated by the system and cannot be entered").
--
-- The paper timesheet types all six by hand, because the crew knows the rota
-- better than the roster does: offshore swaps happen mid-hitch and the schedule
-- is not always updated. Making them read-only forces exactly the workaround
-- the system is meant to remove.
--
-- The system still computes its own value. It now becomes a CROSS-CHECK: when
-- the employee's number disagrees with the derived one, the day carries a
-- SYSTEM_VALUE_MISMATCH warning to the approver rather than being silently
-- overwritten. When the employee leaves it blank, the derived value is still
-- used, so nothing regresses for tenants relying on derivation.
--
-- Also adds the two leave categories the paper form has and the system lacked.
-- ----------------------------------------------------------------------------

-- 1) Hand the six categories back to the employee. Tenant-wide: every tenant's
--    copy of the catalog, not just 'default'.
UPDATE timesheet.time_category
   SET derived = false,
       source  = 'EMPLOYEE'
 WHERE code IN ('OFFSHORE_NIGHT_HOURS',
                'QUAYSIDE_NIGHT_HOURS',
                'OFFSHORE_HOLIDAY_HOURS',
                'QUAYSIDE_HOLIDAY_HOURS',
                'VACATION_HOURS',
                'SICK_LEAVE_HOURS');

-- 2) Categories present on the paper form but missing from the catalog.
--    Seeded for every tenant that already has a catalog, so a multi-tenant
--    install does not end up with one tenant's form missing two columns.
INSERT INTO timesheet.time_category
       (tenant_id, code, name, unit, applies_to, derived, source, max_per_day, display_order, active)
SELECT c.tenant_id, x.code, x.name, 'HOURS', x.applies_to, false, 'EMPLOYEE', 24.00, x.display_order, true
  FROM (SELECT DISTINCT tenant_id FROM timesheet.time_category) c
 CROSS JOIN (VALUES
        ('EDUCATION_VACATION_HOURS', 'Education Vacation Hours', 'LEAVE', 135),
        ('UNPAID_VACATION_HOURS',    'Unpaid Vacation Hours',    'LEAVE', 136)
     ) AS x(code, name, applies_to, display_order)
 WHERE NOT EXISTS (
        SELECT 1 FROM timesheet.time_category e
         WHERE e.tenant_id = c.tenant_id AND e.code = x.code);
