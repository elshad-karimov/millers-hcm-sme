-- ============================================================================
-- scripts/seed-uat.sql — Repeatable, idempotent UAT tenant seed
-- ============================================================================
-- Purpose
--   Make module-by-module User Acceptance Testing reproducible by guaranteeing
--   a known-good state for the five Keycloak logins that already exist in
--   keycloak/realm-millers-hcm.json. UAT needs multi-role logins to exercise
--   the platform's confidentiality / hierarchy / tenant invariants:
--
--     employee  → sees ONLY own self-service data
--     manager   → sees ONLY employees in their reporting hierarchy
--     hrspec    → org-unit-scoped HR access (HR_SPECIALIST)
--     admin     → SYSTEM_ADMIN + HR_ADMIN (unchanged here)
--     mfauser   → plain EMPLOYEE, used for MFA-login testing
--
--   User <-> employee is linked by core_hr.employee.username == Keycloak
--   username (there is no separate mapping table). All business tables carry
--   tenant_id = 'default'; every row here belongs to that single tenant.
--
-- What this sets up (invariants it makes TESTABLE)
--   1. Manager hierarchy scoping — 'manager' (EMP-00002, Rashad Aliyev) gets a
--      SMALL team of direct reports (Aliya EMP-00001 already reports to him,
--      plus Elnur EMP-00013 and Ramin EMP-00019) so a tester can verify
--      "manager sees only their own team, not admin's team, not the whole co."
--   2. Employee self-service — 'employee' (EMP-00001, Aliya) has salary, an
--      ANNUAL leave balance, and existing payslips (already true; re-asserted).
--   3. HR-specialist org-unit scoping — 'hrspec' (EMP-00004) linked with a
--      scope_org_unit_id (ENG) so org-unit-scoped HR access is exercisable.
--   4. Coverage — every non-terminated employee that appears in UAT has a
--      salary row and an ANNUAL leave balance so screens are never empty.
--
-- Safe to re-run
--   100% idempotent. Every UPDATE is guarded by "... IS NULL" natural-key
--   guards and every INSERT by NOT EXISTS on a natural/unique key, so a second
--   run changes ZERO rows. Wrapped in a single BEGIN; ... COMMIT; transaction.
--
-- Deliberate scoping decisions (see also scripts/UAT_LOGINS.md)
--   * The 'manager' persona STAYS on EMP-00002 because username='manager' is
--     already on that row and usernames are 1:1 with Keycloak users; we do NOT
--     move it to an "Engineering Manager"-titled row (that would duplicate the
--     username). We give EMP-00002 a title + reports instead.
--   * Manager's two extra reports (EMP-00013, EMP-00019) were chosen because
--     their manager_id was NULL — we never reassign admin's existing reports
--     (EMP-00010/11/12/14/15/16/17/18 from seed-demo-admin-team.sql).
--   * EMP-00003 (Nigar Hasanli, TERMINATED) is intentionally left WITHOUT a
--     salary or leave backfill — terminated staff should not populate active
--     UAT screens or salary reports.
--   * 'mfauser' is linked to EMP-00012 (a spare EMPLOYEE persona) so its
--     self-service screens are populated; only its username is set, so the
--     hierarchy (EMP-00012 still reports to admin) is undisturbed.
--
-- Money: AZN. Year anchor: 2026 (matches the AZ-2026 holiday/statutory seed).
-- ============================================================================

BEGIN;

-- 0) Sanity: the five UAT employee anchors must exist. (They are seeded by the
--    base + demo seeds; this seed only wires roles/coverage on top of them.)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM core_hr.employee WHERE employee_no = 'EMP-00001') THEN
    RAISE EXCEPTION 'EMP-00001 (employee/Aliya) not found — run the base + demo seeds first';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM core_hr.employee WHERE employee_no = 'EMP-00002') THEN
    RAISE EXCEPTION 'EMP-00002 (manager/Rashad) not found — run the base + demo seeds first';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM core_hr.employee WHERE employee_no = 'EMP-00004') THEN
    RAISE EXCEPTION 'EMP-00004 (hrspec/Sara) not found — run the base + demo seeds first';
  END IF;
END $$;

-- ── 1. Login <-> employee links ────────────────────────────────────────────
-- Set the Keycloak username on the intended employee row, but ONLY where the
-- username is still NULL. This never clobbers an existing (possibly different)
-- username, so it is a safe no-op on this DB (where the first four are already
-- linked) yet self-heals a fresh DB.
UPDATE core_hr.employee SET username='employee', updated_at=now()
  WHERE employee_no='EMP-00001' AND username IS NULL;
UPDATE core_hr.employee SET username='manager',  updated_at=now()
  WHERE employee_no='EMP-00002' AND username IS NULL;
UPDATE core_hr.employee SET username='hrspec',   updated_at=now()
  WHERE employee_no='EMP-00004' AND username IS NULL;
-- mfauser: a plain EMPLOYEE self-service persona linked to a spare employee.
UPDATE core_hr.employee SET username='mfauser',  updated_at=now()
  WHERE employee_no='EMP-00012' AND username IS NULL;

-- ── 2. Manager persona (EMP-00002): give it a title + a small team ─────────
-- 2a. Cosmetic: a title/department so the manager's own profile isn't blank.
UPDATE core_hr.employee
   SET position_title = COALESCE(position_title, 'Department Manager'),
       department_name = COALESCE(department_name, 'Operations'),
       updated_at = now()
 WHERE employee_no = 'EMP-00002'
   AND (position_title IS NULL OR department_name IS NULL);

-- 2b. Direct reports: attach EMP-00013 (Elnur) + EMP-00019 (Ramin) to manager,
--     but ONLY where they currently have no manager (never steal admin's team).
--     Aliya (EMP-00001) already reports to EMP-00002, giving 3 reports total.
UPDATE core_hr.employee e
   SET manager_id = (SELECT id FROM core_hr.employee WHERE employee_no='EMP-00002'),
       updated_at = now()
 WHERE e.employee_no IN ('EMP-00013','EMP-00019')
   AND e.manager_id IS NULL;

-- ── 3. HR-specialist org-unit scope (EMP-00004) ───────────────────────────
-- Ensure scope_org_unit_id is populated so org-unit-scoped HR access is
-- exercisable. Reuse an existing org_unit (ENG). Guarded: only sets it when
-- still NULL (already set on this DB → no-op).
UPDATE core_hr.employee
   SET scope_org_unit_id = (SELECT id FROM organization.org_unit
                             WHERE code='ENG' ORDER BY id LIMIT 1),
       updated_at = now()
 WHERE employee_no = 'EMP-00004'
   AND scope_org_unit_id IS NULL
   AND EXISTS (SELECT 1 FROM organization.org_unit WHERE code='ENG');

-- ── 4. Salary coverage: backfill missing employee_compensation rows ────────
-- Only the non-terminated employees that currently have NO compensation row
-- (EMP-00002 manager, EMP-00004 hrspec). Terminated EMP-00003 is excluded by
-- design. Guard: NOT EXISTS any comp row for that employee → idempotent.
INSERT INTO payroll.employee_compensation
  (employee_id, monthly_base_salary, currency, effective_from, reason, created_by)
SELECT e.id, x.salary, 'AZN', e.hire_date, 'UAT seed', 'uat'
FROM core_hr.employee e
JOIN (VALUES
  ('EMP-00002', 4800.00::numeric),   -- manager  / Department Manager
  ('EMP-00004', 3900.00::numeric)    -- hrspec   / HR Business Partner
) AS x(no, salary) ON x.no = e.employee_no
WHERE e.employment_status <> 'TERMINATED'
  AND NOT EXISTS (
    SELECT 1 FROM payroll.employee_compensation ec WHERE ec.employee_id = e.id
  );

-- ── 5. ANNUAL leave-balance coverage ──────────────────────────────────────
-- Give every non-terminated employee that lacks an ANNUAL 2026 balance a
-- reasonable one (28 days entitlement). Guarded by the natural unique key
-- (employee_id, leave_type_id, year) → idempotent. Covers EMP-00010..00019
-- (and self-heals anyone else who is missing one).
INSERT INTO leave_mgmt.leave_balance
  (employee_id, leave_type_id, year, entitlement_days,
   carried_forward_days, adjustment_days, used_days, reserved_days)
SELECT e.id, lt.id, 2026, 28.0, 0.0, 0.0, 0.0, 0.0
FROM core_hr.employee e
CROSS JOIN leave_mgmt.leave_type lt
WHERE lt.code = 'ANNUAL'
  AND e.employment_status <> 'TERMINATED'
  AND NOT EXISTS (
    SELECT 1 FROM leave_mgmt.leave_balance b
    WHERE b.employee_id = e.id AND b.leave_type_id = lt.id AND b.year = 2026
  );

COMMIT;

-- ============================================================================
-- VERIFICATION — the UAT login matrix + coverage counts
-- ============================================================================

-- 5.1  Login → employee_no → role → #direct_reports → data-readiness matrix.
--      realm_roles are sourced from keycloak/realm-millers-hcm.json (roles live
--      in Keycloak, not the DB) and shown here as literals for a full picture.
\echo '===== UAT LOGIN MATRIX ====='
SELECT u.username,
       e.employee_no,
       (e.first_name || ' ' || e.last_name)                    AS full_name,
       COALESCE(e.position_title, '—')                          AS title,
       u.realm_roles,
       (SELECT count(*) FROM core_hr.employee r
          WHERE r.manager_id = e.id)                            AS direct_reports,
       CASE WHEN EXISTS (SELECT 1 FROM payroll.employee_compensation ec
                          WHERE ec.employee_id = e.id)
            THEN 'yes' ELSE 'NO' END                            AS has_salary,
       CASE WHEN EXISTS (SELECT 1 FROM leave_mgmt.leave_balance b
                          JOIN leave_mgmt.leave_type lt ON lt.id=b.leave_type_id
                          WHERE b.employee_id=e.id AND lt.code='ANNUAL' AND b.year=2026)
            THEN 'yes' ELSE 'NO' END                            AS annual_2026,
       CASE WHEN EXISTS (SELECT 1 FROM payroll.payroll_result pr
                          WHERE pr.employee_id = e.id)
            THEN 'yes' ELSE 'no' END                            AS in_payroll,
       COALESCE(ou.code, '—')                                   AS hr_scope_unit
FROM (VALUES
  ('admin',    'SYSTEM_ADMIN,HR_ADMIN'),
  ('hrspec',   'HR_SPECIALIST'),
  ('manager',  'DEPARTMENT_MANAGER,EMPLOYEE'),
  ('employee', 'EMPLOYEE'),
  ('mfauser',  'EMPLOYEE')
) AS u(username, realm_roles)
LEFT JOIN core_hr.employee e        ON e.username = u.username
LEFT JOIN organization.org_unit ou  ON ou.id = e.scope_org_unit_id
ORDER BY CASE u.username
           WHEN 'admin' THEN 1 WHEN 'hrspec' THEN 2 WHEN 'manager' THEN 3
           WHEN 'employee' THEN 4 WHEN 'mfauser' THEN 5 END;

-- 5.2  Manager's team (proves hierarchy scoping is populated & bounded).
\echo '===== MANAGER (EMP-00002) DIRECT REPORTS ====='
SELECT e.employee_no, (e.first_name||' '||e.last_name) AS report, e.department_name
FROM core_hr.employee e
WHERE e.manager_id = (SELECT id FROM core_hr.employee WHERE username='manager')
ORDER BY e.employee_no;

-- 5.3  Coverage counts (non-terminated population).
\echo '===== COVERAGE (non-terminated employees) ====='
SELECT 'employees_non_terminated' AS metric,
       count(*)::text AS value
FROM core_hr.employee WHERE employment_status <> 'TERMINATED'
UNION ALL
SELECT 'with_salary_row',
       count(*)::text
FROM core_hr.employee e
WHERE e.employment_status <> 'TERMINATED'
  AND EXISTS (SELECT 1 FROM payroll.employee_compensation ec WHERE ec.employee_id=e.id)
UNION ALL
SELECT 'with_annual_2026_balance',
       count(*)::text
FROM core_hr.employee e
WHERE e.employment_status <> 'TERMINATED'
  AND EXISTS (SELECT 1 FROM leave_mgmt.leave_balance b
              JOIN leave_mgmt.leave_type lt ON lt.id=b.leave_type_id
              WHERE b.employee_id=e.id AND lt.code='ANNUAL' AND b.year=2026)
UNION ALL
SELECT 'admin_direct_reports (should stay 8)',
       count(*)::text
FROM core_hr.employee e
WHERE e.manager_id = (SELECT id FROM core_hr.employee WHERE username='admin');
