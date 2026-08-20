-- ============================================================================
-- scripts/seed-sme-base-users.sql — base employee records for the SME edition
-- ============================================================================
-- Why this exists
--   Keycloak ships the dev logins (keycloak/realm-millers-hcm.json), but the
--   app links a login to a person by core_hr.employee.username == the Keycloak
--   username — there is no mapping table. On a FRESH database nothing creates
--   EMP-00000..EMP-00004: seed-demo-global.sql starts at EMP-00010, and
--   seed-uat.sql aborts with "run the base + demo seeds first" because those
--   base rows are missing. So a fresh SME instance has working logins that
--   resolve to no employee, and every self-service screen comes up empty.
--
--   This seeds the two personas needed to actually use the app:
--     admin    → EMP-00000, System Administrator (SYSTEM_ADMIN + HR_ADMIN)
--     employee → EMP-00001, reports to admin (plain EMPLOYEE self-service)
--
--   Run the richer seeds afterwards for demo data:
--     seed-demo-global.sql → seed-uat.sql → seed-demo-admin*.sql
--
-- Idempotent — keyed on employee_no, safe to re-run.
--
-- Usage (SME edition — container, so there is no chance of hitting the
-- ENTERPRISE database on port 5433):
--   docker exec -i sme-postgres psql -U hcm -d hcm < scripts/seed-sme-base-users.sql
-- ============================================================================

BEGIN;

-- ── 1. The two people ──────────────────────────────────────────────────────
INSERT INTO core_hr.employee
  (tenant_id, employee_no, first_name, last_name, hire_date, employment_status,
   email, position_title, department_name, username, created_by, updated_by)
SELECT x.tenant, x.no, x.fn, x.ln, x.hd::date, x.status,
       x.email, x.title, x.dept, x.username, 'sme-base-seed', 'sme-base-seed'
FROM (VALUES
  ('default', 'EMP-00000', 'System', 'Administrator', '2020-01-06', 'ACTIVE',
   'admin@millers.local',    'System Administrator', 'IT',          'admin'),
  ('default', 'EMP-00001', 'Aliya',  'Mammadova',     '2023-03-01', 'ACTIVE',
   'aliya@millers.local',    'HR Officer',           'Human Resources', 'employee')
) AS x(tenant, no, fn, ln, hd, status, email, title, dept, username)
WHERE NOT EXISTS (
  SELECT 1 FROM core_hr.employee e WHERE e.employee_no = x.no
);

-- ── 2. Hierarchy: the employee reports to the admin ────────────────────────
-- Gives the manager-scoped views something real to resolve, and lets the
-- employee's requests land in an approver's queue.
UPDATE core_hr.employee e
   SET manager_id = a.id,
       updated_at = now()
  FROM core_hr.employee a
 WHERE a.employee_no = 'EMP-00000'
   AND e.employee_no = 'EMP-00001'
   AND e.manager_id IS DISTINCT FROM a.id;

-- ── 3. Move the employee-number counter past what we just seeded ───────────
-- The app draws new numbers from config.tenant_sequence. Seeding rows with
-- explicit numbers leaves that counter at 0, so the first employee created
-- through the UI re-draws EMP-00001 and dies on the (tenant_id, employee_no)
-- unique constraint.
--
-- Worse, it cannot recover on its own: the counter is bumped by a plain UPDATE
-- inside the caller's transaction, so the failed INSERT rolls the bump back too
-- and every subsequent attempt re-draws the very same number. Creating an
-- employee stays broken until someone moves this counter by hand.
INSERT INTO config.tenant_sequence (tenant_id, seq_name, next_val)
SELECT 'default', 'core_hr.employee_no_seq', MAX(SUBSTRING(employee_no FROM '\d+')::BIGINT)
  FROM core_hr.employee
 WHERE tenant_id = 'default' AND employee_no ~ '^EMP-\d+$'
ON CONFLICT (tenant_id, seq_name) DO UPDATE
  SET next_val = GREATEST(config.tenant_sequence.next_val, EXCLUDED.next_val);

-- ── 4. A legal entity ──────────────────────────────────────────────────────
-- Attendance, payroll and reporting all resolve a default legal entity and
-- throw "No legal entity found" without one, which surfaces as a 500 on
-- screens that look unrelated (attendance corrections, periods, overtime).
-- Nothing creates it — the enterprise instance got one through the UI.
--
-- Deliberately minimal: registration number, tax ID and bank details are left
-- NULL rather than invented, because they end up on payroll output and bank
-- files. An HR admin must fill them in before the first payroll run.
INSERT INTO organization.legal_entity
  (id, tenant_id, code, name, country, currency, active, created_by, updated_by)
SELECT gen_random_uuid(), 'default', 'MAIN', 'Main Legal Entity', 'AZ', 'AZN',
       TRUE, 'seed', 'seed'
 WHERE NOT EXISTS (
   SELECT 1 FROM organization.legal_entity WHERE tenant_id = 'default'
 );

COMMIT;

-- ── Verify ────────────────────────────────────────────────────────────────
SELECT employee_no, username, first_name || ' ' || last_name AS name,
       employment_status,
       (SELECT m.employee_no FROM core_hr.employee m WHERE m.id = e.manager_id) AS manager
  FROM core_hr.employee e
 WHERE username IN ('admin', 'employee')
 ORDER BY employee_no;
