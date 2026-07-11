# UAT Logins — Millers HCM

These are the five Keycloak logins used for module-by-module User Acceptance
Testing. They let a tester verify the platform's confidentiality / hierarchy /
tenant invariants from the real self-service and HR surfaces.

**Where the passwords come from:** `keycloak/realm-millers-hcm.json` (each user
has one non-temporary password credential). They are the fixed dev/UAT
credentials baked into that realm import — **not** invented here. If the realm
is re-imported, these remain valid. Do not use these in production.

**How a login maps to a person:** by username. `core_hr.employee.username`
equals the Keycloak username — there is no separate mapping table. The DB links
are established/kept correct by `scripts/seed-uat.sql`.

## Login matrix

| Keycloak username | Password (from realm file) | Realm roles | Linked employee | What this persona tests in UAT |
|---|---|---|---|---|
| `admin` | `Admin#Pass123!` | `SYSTEM_ADMIN`, `HR_ADMIN` | EMP-00000 — System Administrator | Full HR-admin access; owns a team of 8 (EMP-00010/11/12/14/15/16/17/18). Baseline "sees everything" against which scoped roles are compared. |
| `hrspec` | `HRspec#2026!` | `HR_SPECIALIST` | EMP-00004 — Sara HR | **Org-unit-scoped HR access.** `scope_org_unit_id` = Engineering (ENG). Verify hrspec sees/administers only employees within their scoped org unit, not the whole tenant. |
| `manager` | `Manager#123!` | `DEPARTMENT_MANAGER`, `EMPLOYEE` | EMP-00002 — Rashad Aliyev | **Manager hierarchy scoping.** Has 3 direct reports: Aliya (EMP-00001), Elnur (EMP-00013), Ramin (EMP-00019). Verify manager sees/approves only their own team — NOT admin's team, NOT the whole company. Also has EMPLOYEE self-service for their own data. |
| `employee` | `Employee#123!` | `EMPLOYEE` | EMP-00001 — Aliya Mammadova | **Employee self-service.** Has a current salary, an ANNUAL 2026 leave balance, and appears in 3 payroll runs (PR-00001 PAID / PR-00003 / PR-00005), so payslip, leave, and profile screens have real data. Reports to `manager`, so manager's approval queue can act on Aliya's requests. |
| `mfauser` | `Mfa#Demo123!` | `EMPLOYEE` | EMP-00012 — Nigar İsmayılova | MFA-login testing (plain EMPLOYEE). Linked to a spare employee (salary + ANNUAL balance present) so post-login self-service screens are populated. |

Notes on the data set behind these logins:

- Every non-terminated employee (14 of 15) has a salary row and an ANNUAL-2026
  leave balance, so no UAT screen is empty. EMP-00003 (TERMINATED) is
  deliberately excluded.
- `manager`'s two extra reports (EMP-00013, EMP-00019) were employees whose
  `manager_id` was NULL — `admin`'s existing 8 reports are never reassigned.
- All rows belong to tenant_id `'default'`.

## How to run the seed

```bash
# From the repo root. PostgreSQL 16 is at localhost:5433, db/user/pass = hcm.
PGPASSWORD=hcm psql -h localhost -p 5433 -U hcm -d hcm -v ON_ERROR_STOP=1 \
  -f scripts/seed-uat.sql
```

The script is wrapped in a single transaction and is fully idempotent: every
UPDATE is guarded by an `IS NULL` natural-key guard and every INSERT by a
`NOT EXISTS` check on a natural/unique key, so re-running it changes **zero**
rows. It prints a login matrix + coverage counts at the end. It assumes the
base + demo seeds (`scripts/seed-demo-*.sql`) have already created the employee
rows EMP-00000..EMP-00019; it only wires roles/hierarchy/coverage on top of
them (it will RAISE if the core anchors are missing).

The five Keycloak users already exist in `keycloak/realm-millers-hcm.json`, so
**no Keycloak restart is required** — this seed only touches the database.

## Follow-ups / known gaps (deliberately left)

- **No dedicated "manager sees a peer's salary" negative row is auto-created** —
  testers exercise salary confidentiality by attempting to view a
  non-report's compensation while logged in as `manager`/`employee`.
- **`mfauser` shares EMP-00012 with admin's team** (EMP-00012 reports to admin).
  This is intentional (keeps hierarchy undisturbed) but means mfauser's manager
  is `admin`. If you need mfauser fully isolated, relink it to a standalone
  employee.
- **No new realm roles were added.** All five personas' roles already exist in
  the realm. If a future UAT scenario needs an additional role (e.g. a
  read-only auditor), add it to `keycloak/realm-millers-hcm.json` and re-import
  the realm — that is out of scope for this DB-only seed.
