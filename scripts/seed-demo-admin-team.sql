-- ----------------------------------------------------------------------------
-- Demo seed — populate admin's "My Team" view.
--
-- /my/team renders direct reports where employee.manager_id = admin.id.
-- The summary tiles also need: ON_PROBATION rows, an APPROVED leave that
-- spans today (for "On leave today"), and PENDING leaves (for the queue).
--
-- Idempotent — re-running is safe; the UPDATEs only touch rows whose
-- manager_id is still NULL, and the INSERTs are keyed on request_no.
-- ----------------------------------------------------------------------------

BEGIN;

-- ── 1. Assign 8 of the seeded employees to admin as direct reports ────────
WITH a AS (SELECT id AS admin_id FROM core_hr.employee WHERE username='admin')
UPDATE core_hr.employee e
SET manager_id = a.admin_id,
    updated_at = now()
FROM a
WHERE e.employee_no IN (
  'EMP-00010',  -- Leyla Quliyeva    – Senior Software Engineer
  'EMP-00011',  -- Tural Hüseynov    – Engineering Manager
  'EMP-00012',  -- Nigar İsmayılova  – Product Designer
  'EMP-00014',  -- Aysel Bayramova   – Finance Analyst
  'EMP-00015',  -- Murad Əliyev      – Data Engineer
  'EMP-00016',  -- Günel Məmmədova   – HR Business Partner
  'EMP-00017',  -- Rauf Hacıyev      – DevOps Engineer
  'EMP-00018'   -- Səbinə Nəzərova   – Customer Success Lead
)
AND e.manager_id IS NULL;

-- ── 2. Put one report on probation (drives "On probation" tile) ───────────
UPDATE core_hr.employee
SET employment_status = 'ON_PROBATION', updated_at = now()
WHERE employee_no = 'EMP-00017'  -- Rauf — most recent hire among reports
  AND employment_status = 'ACTIVE';

-- ── 3. Pending leave requests on direct reports — fills the queue tile ───
INSERT INTO leave_mgmt.leave_request
  (request_no, employee_id, leave_type_id, start_date, end_date, half_day,
   total_days, reason, status, created_by, created_at, updated_at)
SELECT x.no, e.id, lt.id, x.s::date, x.e::date, false,
       x.days, x.reason, 'PENDING', e.username, x.created::timestamptz, x.created::timestamptz
FROM (VALUES
  ('LV-2001'::varchar, 'EMP-00010'::varchar, 'ANNUAL'::varchar,
   '2026-07-06'::text, '2026-07-10'::text, 5.0::numeric,
   'Family holiday — Antalya',           '2026-06-08T09:30:00+04:00'::text),
  ('LV-2002'::varchar, 'EMP-00012'::varchar, 'ANNUAL'::varchar,
   '2026-08-17'::text, '2026-08-21'::text, 5.0::numeric,
   'Annual leave',                       '2026-06-09T11:10:00+04:00'::text),
  ('LV-2003'::varchar, 'EMP-00015'::varchar, 'SICK'::varchar,
   '2026-06-11'::text, '2026-06-12'::text, 2.0::numeric,
   'Doctor''s note pending upload',      '2026-06-09T08:00:00+04:00'::text)
) AS x(no, empno, tc, s, e, days, reason, created)
JOIN core_hr.employee e ON e.employee_no = x.empno
JOIN leave_mgmt.leave_type lt ON lt.code = x.tc
WHERE NOT EXISTS (SELECT 1 FROM leave_mgmt.leave_request lr WHERE lr.request_no = x.no);

-- ── 4. One APPROVED leave that covers today → "On leave today" = 1 ───────
INSERT INTO leave_mgmt.leave_request
  (request_no, employee_id, leave_type_id, start_date, end_date, half_day,
   total_days, reason, status, created_by, created_at, updated_at)
SELECT 'LV-2004', e.id, lt.id, '2026-06-08'::date, '2026-06-10'::date, false,
       3.0, 'Pre-approved short break', 'APPROVED', e.username,
       '2026-05-30T10:00:00+04:00', '2026-06-01T09:00:00+04:00'
FROM core_hr.employee e
JOIN leave_mgmt.leave_type lt ON lt.code = 'ANNUAL'
WHERE e.employee_no = 'EMP-00018'  -- Səbinə
  AND NOT EXISTS (SELECT 1 FROM leave_mgmt.leave_request lr WHERE lr.request_no = 'LV-2004');

COMMIT;

-- Verify
WITH a AS (SELECT id FROM core_hr.employee WHERE username='admin')
SELECT 'direct_reports' AS metric, count(*)::text AS value
FROM core_hr.employee e, a WHERE e.manager_id = a.id
UNION ALL
SELECT 'reports_on_probation', count(*)::text
FROM core_hr.employee e, a
WHERE e.manager_id = a.id AND e.employment_status='ON_PROBATION'
UNION ALL
SELECT 'pending_team_leave', count(*)::text
FROM leave_mgmt.leave_request lr
JOIN core_hr.employee e ON e.id = lr.employee_id
WHERE e.manager_id = (SELECT id FROM core_hr.employee WHERE username='admin')
  AND lr.status = 'PENDING'
UNION ALL
SELECT 'on_leave_today', count(*)::text
FROM leave_mgmt.leave_request lr
JOIN core_hr.employee e ON e.id = lr.employee_id
WHERE e.manager_id = (SELECT id FROM core_hr.employee WHERE username='admin')
  AND lr.status='APPROVED'
  AND current_date BETWEEN lr.start_date AND lr.end_date;
