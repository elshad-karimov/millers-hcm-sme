-- ----------------------------------------------------------------------------
-- Demo seed for the System Administrator (username='admin', EMP-00000).
--
-- Idea: populate every section of "My Workspace" + the global HR views with
-- realistic, dated numbers so a customer demo doesn't show empty tabs.
--
-- Idempotent — safe to re-run. We use NOT EXISTS guards keyed on stable
-- natural keys (request_no, run_no, certificate_no, etc.) so reseeding
-- doesn't pile up duplicates.
--
-- Money: AZN. Hours: numeric. Dates: anchored to 2026 (the AZ-2026 holiday
-- + statutory-rule seed already there).
-- ----------------------------------------------------------------------------

BEGIN;

-- 0) Resolve the admin employee ID once and stash it for the rest of the file.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM core_hr.employee WHERE username='admin') THEN
    RAISE EXCEPTION 'admin employee not found — run the EMP-00000 seed first';
  END IF;
END $$;

-- ── 1. Compensation: a base salary so payslips can render ──────────────────
INSERT INTO payroll.employee_compensation
  (employee_id, monthly_base_salary, currency, effective_from, reason, created_by)
SELECT id, 3500.00, 'AZN', '2024-01-01', 'Demo seed', 'demo'
FROM core_hr.employee WHERE username='admin'
ON CONFLICT DO NOTHING;

-- ── 2. Leave balances — admin gets 30 ANNUAL, plus typical sick/personal ──
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin')
INSERT INTO leave_mgmt.leave_balance
  (employee_id, leave_type_id, year, entitlement_days, used_days, reserved_days)
SELECT a.eid, lt.id, 2026, x.entitle, x.used, x.reserved
FROM a, leave_mgmt.leave_type lt
JOIN (VALUES
   ('ANNUAL',        30.0::numeric,  8.0::numeric, 3.0::numeric),
   ('SICK',          14.0::numeric,  2.5::numeric, 0.0::numeric),
   ('COMPASSIONATE',  3.0::numeric,  0.0::numeric, 0.0::numeric),
   ('MARRIAGE',       3.0::numeric,  0.0::numeric, 0.0::numeric)
) AS x(code, entitle, used, reserved) ON x.code = lt.code
WHERE NOT EXISTS (
  SELECT 1 FROM leave_mgmt.leave_balance b
  WHERE b.employee_id = a.eid AND b.leave_type_id = lt.id AND b.year = 2026
);

-- ── 3. Leave requests — mix of approved + pending + rejected so list looks busy ──
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin')
INSERT INTO leave_mgmt.leave_request
  (request_no, employee_id, leave_type_id, start_date, end_date, half_day,
   total_days, reason, status, created_by, created_at, updated_at)
SELECT x.no, a.eid, lt.id, x.s::date, x.e::date, false,
       x.days, x.reason, x.status, 'admin', x.created::timestamptz, x.created::timestamptz
FROM (VALUES
  -- Approved past trip
  ('LV-1001'::varchar, 'ANNUAL'::varchar,        '2026-02-10'::text, '2026-02-14'::text, 5.0::numeric,
   'Winter holiday with family',                'APPROVED'::varchar, '2026-01-25T10:14:00+04:00'::text),
  -- Approved spring break
  ('LV-1002'::varchar, 'ANNUAL'::varchar,        '2026-04-13'::text, '2026-04-15'::text, 3.0::numeric,
   'Spring long weekend',                       'APPROVED'::varchar, '2026-03-30T09:02:00+04:00'::text),
  -- Pending upcoming
  ('LV-1003'::varchar, 'ANNUAL'::varchar,        '2026-08-03'::text, '2026-08-05'::text, 3.0::numeric,
   'Short break before Q3 push',                'PENDING'::varchar,  '2026-06-08T15:42:00+04:00'::text),
  -- Sick (approved, taken)
  ('LV-1004'::varchar, 'SICK'::varchar,          '2026-03-04'::text, '2026-03-05'::text, 2.0::numeric,
   'Flu — doctor''s note on file',              'APPROVED'::varchar, '2026-03-04T08:11:00+04:00'::text),
  -- Sick half-day later
  ('LV-1005'::varchar, 'SICK'::varchar,          '2026-05-18'::text, '2026-05-18'::text, 0.5::numeric,
   'Dental appointment',                        'APPROVED'::varchar, '2026-05-17T18:03:00+04:00'::text)
) AS x(no, tc, s, e, days, reason, status, created)
CROSS JOIN a
JOIN leave_mgmt.leave_type lt ON lt.code = x.tc
WHERE NOT EXISTS (SELECT 1 FROM leave_mgmt.leave_request lr WHERE lr.request_no = x.no);

-- ── 4. Permission types & balances + a couple of recent requests ──────────
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin')
INSERT INTO permission.permission_balance
  (employee_id, permission_type_id, year, limit_hours, used_hours, reserved_hours)
SELECT a.eid, pt.id, 2026, x.lim, x.used, x.reserved
FROM a, permission.permission_type pt
JOIN (VALUES
   ('PERSONAL', 40.0::numeric, 6.0::numeric, 0.0::numeric),
   ('BUSINESS', 80.0::numeric, 4.5::numeric, 2.0::numeric)
) AS x(code, lim, used, reserved) ON x.code = pt.code
WHERE NOT EXISTS (
  SELECT 1 FROM permission.permission_balance b
  WHERE b.employee_id = a.eid AND b.permission_type_id = pt.id AND b.year = 2026
);

WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin')
INSERT INTO permission.permission_request
  (request_no, employee_id, permission_type_id, permission_date, start_time, end_time,
   duration_hours, reason, status, created_by, created_at, updated_at)
SELECT x.no, a.eid, pt.id, x.dt::date, x.st::time, x.et::time, x.dur, x.reason, x.status,
       'admin', x.created::timestamptz, x.created::timestamptz
FROM (VALUES
  ('PERM-1001'::varchar, 'PERSONAL'::varchar, '2026-05-20'::text, '14:00'::text, '17:00'::text, 3.0::numeric,
   'Bank appointment',          'APPROVED'::varchar, '2026-05-19T09:00:00+04:00'::text),
  ('PERM-1002'::varchar, 'BUSINESS'::varchar, '2026-06-08'::text, '10:00'::text, '12:30'::text, 2.5::numeric,
   'Off-site vendor meeting',   'APPROVED'::varchar, '2026-06-05T11:10:00+04:00'::text),
  ('PERM-1003'::varchar, 'PERSONAL'::varchar, '2026-06-15'::text, '09:00'::text, '12:00'::text, 3.0::numeric,
   'Family event',              'PENDING'::varchar,  '2026-06-08T20:00:00+04:00'::text)
) AS x(no, tc, dt, st, et, dur, reason, status, created)
CROSS JOIN a
JOIN permission.permission_type pt ON pt.code = x.tc
WHERE NOT EXISTS (SELECT 1 FROM permission.permission_request pr WHERE pr.request_no = x.no);

-- ── 5. Business trips — one past, one upcoming ────────────────────────────
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin')
INSERT INTO business_trip.business_trip_request
  (trip_no, employee_id, trip_type, destination_country, destination_city, purpose, project,
   cost_centre, start_date, end_date, total_days, currency, daily_allowance, requested_advance,
   approved_advance, paid_advance, meals_provided, accommodation_provided, status,
   created_by, created_at, updated_at)
SELECT x.no, a.eid, x.tt, x.country, x.city, x.purp, x.proj, x.cc,
       x.s::date, x.e::date, x.days, 'AZN', x.daily, x.req, x.appr, x.paid,
       x.meals, x.acc, x.status, 'admin', x.created::timestamptz, x.created::timestamptz
FROM a, (VALUES
  -- TripStatus enum: DRAFT · PENDING · APPROVED · REJECTED · CANCELLED · COMPLETED
  ('BT-1001'::varchar, 'INTERNATIONAL'::varchar, 'TR'::varchar, 'Istanbul'::varchar,
   'Customer demo + HRTech conference'::text, 'Sales – EU expansion'::varchar, 'CC-SALES'::varchar,
   '2026-04-22'::text, '2026-04-25'::text, 4::int,
   80.00::numeric, 600.00::numeric, 600.00::numeric, 600.00::numeric,
   false::bool, true::bool, 'COMPLETED'::varchar, '2026-04-08T12:00:00+04:00'::text),
  ('BT-1002'::varchar, 'DOMESTIC'::varchar, 'AZ'::varchar, 'Ganja'::varchar,
   'Regional office launch'::text, 'Ops – regional rollout'::varchar, 'CC-OPS'::varchar,
   '2026-07-15'::text, '2026-07-17'::text, 3::int,
   50.00::numeric, 220.00::numeric, NULL::numeric, 0.00::numeric,
   true::bool, true::bool, 'PENDING'::varchar, '2026-06-09T10:30:00+04:00'::text)
) AS x(no, tt, country, city, purp, proj, cc, s, e, days, daily, req, appr, paid, meals, acc, status, created)
WHERE NOT EXISTS (SELECT 1 FROM business_trip.business_trip_request t WHERE t.trip_no = x.no);

-- ── 6. Payroll: attach admin to existing PR-00001 (May 2026, PAID) ───────
-- This drives the "Latest payslip net" KPI + the Payslips tab.
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin'),
     r AS (SELECT id AS rid FROM payroll.payroll_run WHERE run_no = 'PR-00001')
INSERT INTO payroll.payroll_result
  (run_id, employee_id, payslip_no, period_start,
   base_salary, worked_hours, expected_monthly_hours, pro_ration_factor,
   gross_amount, income_tax, dsmf_employee, mmi_employee, unempl_employee,
   dsmf_employer, mmi_employer, unempl_employer, net_amount,
   created_at, updated_at)
SELECT r.rid, a.eid, 'PAY-2026-05-ADM', '2026-05-01',
       3500.00, 160.0, 160.0, 1.0,
       3500.00, 525.00, 105.00, 7.00, 17.50,
       735.00, 7.00, 17.50, 2845.50,
       '2026-05-31T18:00:00+04:00', '2026-05-31T18:00:00+04:00'
FROM a, r
WHERE NOT EXISTS (
  SELECT 1 FROM payroll.payroll_result pr
  WHERE pr.payslip_no = 'PAY-2026-05-ADM'
);

-- ── 7. Learning: enroll admin into the published Info-Security course ─────
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin'),
     c AS (SELECT id AS cid FROM learning.course WHERE code = 'INFOSEC-101' AND status='PUBLISHED' LIMIT 1)
INSERT INTO learning.enrollment
  (enrollment_no, course_id, employee_id, status, enrolled_at, started_at, completed_at,
   attempts_used, best_score_percent, last_score_percent, last_attempt_at,
   enrolled_via, assigned_by)
-- EnrollmentStatus enum: ENROLLED · IN_PROGRESS · PASSED · FAILED · WITHDRAWN · EXPIRED
-- Use PASSED for a completed-with-cert state (COMPLETED is a UI label, not an enum value).
SELECT 'EN-1001', c.cid, a.eid, 'PASSED',
       '2026-01-12T09:00:00+04:00', '2026-01-12T10:30:00+04:00', '2026-01-20T16:45:00+04:00',
       1, 92.0, 92.0, '2026-01-20T16:45:00+04:00',
       'ASSIGNED', 'hr'
FROM a, c
WHERE NOT EXISTS (SELECT 1 FROM learning.enrollment e WHERE e.enrollment_no = 'EN-1001');

-- Matching certificate so the Certificates KPI on My Workspace shows 1.
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin'),
     e AS (SELECT id AS enid, course_id AS cid FROM learning.enrollment WHERE enrollment_no='EN-1001')
INSERT INTO learning.certificate
  (certificate_no, enrollment_id, course_id, employee_id, issued_at, valid_until, score_percent)
SELECT 'CERT-1001', e.enid, e.cid, a.eid,
       '2026-01-20T17:00:00+04:00', '2027-01-20'::date, 92.0
FROM a, e
WHERE NOT EXISTS (SELECT 1 FROM learning.certificate c WHERE c.certificate_no = 'CERT-1001');

-- ── 8. Performance: a review row in the open ANNUAL-2026 cycle + 3 goals ──
WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin'),
     c AS (SELECT id AS cid FROM performance.review_cycle WHERE code = 'ANNUAL-2026' LIMIT 1)
INSERT INTO performance.performance_review
  (review_no, cycle_id, employee_id, manager_id, status,
   self_rating, self_comments, self_submitted_at,
   goal_score, created_at, updated_at)
SELECT 'PR-2026-001', c.cid, a.eid, NULL, 'SELF_SUBMITTED',
       4.2, 'Led the HCM platform rollout, mentored two new hires, championed AZ-locale launch.',
       '2026-05-30T17:20:00+04:00',
       NULL, '2026-01-15T09:00:00+04:00', '2026-05-30T17:20:00+04:00'
FROM a, c
WHERE NOT EXISTS (SELECT 1 FROM performance.performance_review r WHERE r.review_no = 'PR-2026-001');

WITH a AS (SELECT id AS eid FROM core_hr.employee WHERE username='admin'),
     c AS (SELECT id AS cid FROM performance.review_cycle WHERE code = 'ANNUAL-2026' LIMIT 1)
INSERT INTO performance.goal
  (goal_no, cycle_id, employee_id, title, description, category,
   weight_percent, progress_percent, status, due_date, created_at, updated_at, created_by)
SELECT x.no, c.cid, a.eid, x.title, x.descr, x.cat, x.weight, x.prog, x.status, x.due::date,
       '2026-01-15T09:00:00+04:00', '2026-06-05T12:00:00+04:00', 'admin'
FROM a, c, (VALUES
  -- GoalCategory enum: COMPANY · DEPARTMENT · TEAM · INDIVIDUAL · DEVELOPMENT
  -- GoalStatus   enum: DRAFT · ACTIVE · ON_TRACK · AT_RISK · BLOCKED · ACHIEVED · MISSED · CANCELLED
  ('G-2026-01'::varchar,
    'Ship Azerbaijani localisation across SPA'::varchar,
    'Reach ≥80% page coverage with AZ default, EN fallback, language switcher.'::text,
    'COMPANY'::varchar, 40::numeric, 85::numeric, 'ON_TRACK'::varchar, '2026-09-30'::text),
  ('G-2026-02'::varchar,
    'Reduce payroll cycle time'::varchar,
    'Bring monthly payroll close from 5 business days down to 3.'::text,
    'DEPARTMENT'::varchar, 30::numeric, 60::numeric, 'ON_TRACK'::varchar, '2026-12-31'::text),
  ('G-2026-03'::varchar,
    'Manager NPS uplift'::varchar,
    'Raise manager-self-service NPS from +18 to +35 via inbox + delegation polish.'::text,
    'DEVELOPMENT'::varchar, 30::numeric, 45::numeric, 'ON_TRACK'::varchar, '2026-12-31'::text)
) AS x(no, title, descr, cat, weight, prog, status, due)
WHERE NOT EXISTS (SELECT 1 FROM performance.goal g WHERE g.goal_no = x.no);

COMMIT;

-- Summary
SELECT 'leave_balance'      AS table, count(*) FROM leave_mgmt.leave_balance      WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin') AND year=2026
UNION ALL SELECT 'leave_request',       count(*) FROM leave_mgmt.leave_request    WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
UNION ALL SELECT 'permission_balance',  count(*) FROM permission.permission_balance WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin') AND year=2026
UNION ALL SELECT 'permission_request',  count(*) FROM permission.permission_request WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
UNION ALL SELECT 'business_trip',       count(*) FROM business_trip.business_trip_request WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
UNION ALL SELECT 'payroll_result',      count(*) FROM payroll.payroll_result       WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
UNION ALL SELECT 'enrollment',          count(*) FROM learning.enrollment          WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
UNION ALL SELECT 'certificate',         count(*) FROM learning.certificate         WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
UNION ALL SELECT 'performance_review',  count(*) FROM performance.performance_review WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
UNION ALL SELECT 'goal',                count(*) FROM performance.goal             WHERE employee_id = (SELECT id FROM core_hr.employee WHERE username='admin')
;
