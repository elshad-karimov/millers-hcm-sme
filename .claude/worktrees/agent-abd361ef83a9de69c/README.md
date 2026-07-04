# Millers HCM

Enterprise Human Capital Management platform, built to the PRD at
`/Users/elshad/Documents/Claude/Projects/PRD/PRD_HCM_v1.docx`.

This first milestone is a **vertical slice** through the Core HR module:
employee master data with effective-dated audit history, JWT auth with an
RBAC skeleton, REST API, and a React 18 web UI. The rest of the 24 PRD
modules will plug into the same modular-monolith shell.

## Stack

| Layer       | Technology                                                |
|-------------|-----------------------------------------------------------|
| Backend     | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA |
| Database    | PostgreSQL 16 (Docker), Flyway migrations                 |
| Auth        | Keycloak 24 OIDC (PKCE), Spring Security OAuth2 Resource Server |
| Storage     | MinIO (S3-compatible) for attachments + scheduled reports |
| Web         | React 18, TypeScript, Vite, Ant Design v5, axios, keycloak-js |
| Mobile      | Flutter (planned, Phase 2+)                               |

Aligned with PRD Section 13 (Recommended Technology Stack).

## Repository layout

```
.
├── pom.xml                          parent Maven project (Spring Boot 3.4)
├── docker-compose.yml               Postgres 16 service
├── src/main/java/az/millers/hcm/
│   ├── HcmApplication.java          entry point
│   ├── common/                      cross-cutting (exceptions, page envelope)
│   ├── config/SecurityConfig.java   OIDC resource-server chain (Keycloak)
│   ├── security/                    /me endpoint + token-accepted audit hook
│   ├── audit/                       AuditService, AuditLog entity
│   └── corehr/                      Employee module (domain, repo, service, API)
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/                Flyway: V1 employee, V2 audit_log
└── web/                             React 18 web app (Vite + TS + AntD)
```

## Local development

### One-time

```bash
# JDK 21 (via Homebrew). Maven and Node 20 are also required.
brew install openjdk@21 maven node@20
```

### Run the platform

```bash
# 1) Postgres 16 (5433), MinIO (9000/9001), Keycloak (8090)
docker compose up -d
#    Keycloak imports realm-millers-hcm.json on first boot — client
#    `hcm-web`, 3 users (admin / hrspec / employee) with the same
#    passwords as the legacy in-memory accounts, brute-force protection
#    enabled (5 attempts → 15 min lock).

# 2) Backend on http://localhost:8082
export JAVA_HOME=/usr/local/opt/openjdk@21
mvn package -DskipTests
"$JAVA_HOME/bin/java" -jar target/hcm-platform-0.1.0-SNAPSHOT.jar

# 3) Web on http://localhost:5180  (proxies /api → :8082)
export PATH="/usr/local/opt/node@20/bin:$PATH"
cd web && npm install && npm run dev
#    Visiting / redirects to Keycloak; after sign-in the SPA returns
#    with an access token via Authorization Code + PKCE.
```

> Ports — Postgres uses host `5433` to coexist with a local Postgres 14;
> the backend uses `8082` because another local container occupies `8080`;
> Vite uses `5180` because Docker holds a wildcard listener on `5173` for
> the Millers ERP container (Vite would silently share the port and the
> browser would land on the wrong app).

### Demo accounts (Keycloak `millers-hcm` realm)

Imported automatically from `keycloak/realm-millers-hcm.json` on first
container boot. Sign in via the Keycloak login page that the SPA
redirects to.

| Username  | Password         | Realm roles                                    | Notes                                           |
|-----------|------------------|------------------------------------------------|-------------------------------------------------|
| `admin`   | `Admin#Pass123!` | `SYSTEM_ADMIN`, `HR_ADMIN`                     | full HR + admin scope                           |
| `hrspec`  | `HRspec#2026!`   | `HR_SPECIALIST`                                | first-line HR approver                          |
| `employee`| `Employee#123!`  | `EMPLOYEE`                                     | links to `EMP-00001` (Aliya); self-service only |
| `manager` | `Manager#123!`   | `DEPARTMENT_MANAGER`, `EMPLOYEE`               | links to `EMP-00002` (Rashad); ABAC: sees self + reports |
| `mfauser` | `Mfa#Demo123!`   | `EMPLOYEE`                                     | required action `CONFIGURE_TOTP` — first login forces TOTP enrollment via Keycloak's hosted page |

Passwords satisfy the realm password policy
(`length(12) and digits(1) and upperCase(1) and lowerCase(1) and
specialChars(1) and notUsername and passwordHistory(3)`) introduced in
milestone 23. The Direct Access Grants flow refuses to issue a token
for `mfauser` until TOTP is enrolled in the browser flow.

Keycloak's master admin console lives at <http://localhost:8090>
(`admin` / `admin`).

## API surface

### Milestone 1 — Core HR

| Method | Path                                | Role                                                |
|--------|-------------------------------------|-----------------------------------------------------|
| GET    | `/api/auth/me`                      | authenticated — returns `{username, roles, email, name, issuedAt, expiresAt}` from the OIDC bearer (milestone 19) |
| GET    | `/api/employees`                    | `SYSTEM_ADMIN`, `HR_ADMIN`, `HR_SPECIALIST`, `AUDITOR` |
| POST   | `/api/employees`                    | `HR_ADMIN`, `HR_SPECIALIST`                         |
| GET    | `/api/employees/{id}`               | `SYSTEM_ADMIN`, `HR_ADMIN`, `HR_SPECIALIST`, `AUDITOR` |
| PUT    | `/api/employees/{id}`               | `HR_ADMIN`, `HR_SPECIALIST`                         |
| POST   | `/api/employees/{id}/status`        | `HR_ADMIN`, `HR_SPECIALIST`                         |
| GET    | `/api/employees/{id}/audit`         | `SYSTEM_ADMIN`, `HR_ADMIN`, `AUDITOR`               |

Creating an employee fires the onboarding workflow stub
(`OnboardingWorkflow.start`) and writes a `CREATE` audit row with the
full new value and acting user IP — the Core HR acceptance criterion in
PRD 8.1.4.

### Milestone 2 — Organizational Structure (PRD 8.2)

| Method | Path                                  | Notes                                      |
|--------|---------------------------------------|--------------------------------------------|
| GET    | `/api/org/versions`                   | list versions                              |
| GET    | `/api/org/versions/active`            | current ACTIVE version, if any             |
| GET    | `/api/org/versions/{id}`              | single version                             |
| GET    | `/api/org/versions/{id}/units`        | flat unit list                             |
| GET    | `/api/org/versions/{id}/tree`         | nested tree (root → leaves)                |
| POST   | `/api/org/versions/draft`             | new DRAFT (HR_ADMIN/HR_SPECIALIST)         |
| POST   | `/api/org/versions/{id}/submit`       | DRAFT → PENDING_APPROVAL                   |
| POST   | `/api/org/versions/{id}/approve`      | PENDING_APPROVAL → APPROVED                |
| POST   | `/api/org/versions/{id}/reject`       | PENDING_APPROVAL → REJECTED                |
| POST   | `/api/org/versions/{id}/activate`     | APPROVED → ACTIVE, archives previous       |
| POST   | `/api/org/versions/rollback`          | clones a past version into a new PENDING_APPROVAL (PRD 8.2.7) |
| POST   | `/api/org/versions/{id}/units`        | add a unit (only when version is DRAFT)    |
| PUT    | `/api/org/units/{unitId}`             | update unit (DRAFT only)                   |
| DELETE | `/api/org/units/{unitId}`             | remove unit (DRAFT only, leaf-only)        |

Every transition writes an audit row (`CREATE_DRAFT`, `SUBMIT_FOR_APPROVAL`,
`APPROVE`, `REJECT`, `ACTIVATE`, `ARCHIVE`, `ROLLBACK`, `ADD_UNIT`,
`UPDATE_UNIT`, `REMOVE_UNIT`) with old/new value JSON.

### Milestone 2 — Staffing Table (PRD 8.3)

| Method | Path                                       | Notes                                |
|--------|--------------------------------------------|--------------------------------------|
| GET    | `/api/positions`                           | paginated; filter by org_unit / state |
| GET    | `/api/positions/{id}`                      | single position                      |
| POST   | `/api/positions`                           | create (auto code `POS-00001`)       |
| PUT    | `/api/positions/{id}`                      | update; re-derives vacancy state     |
| POST   | `/api/positions/{id}/vacancy-state`        | manual override (e.g. FROZEN)        |
| POST   | `/api/positions/{id}/close`                | CLOSE (only when no occupied head)   |

Vacancy state is derived from `approved_headcount` vs `occupied_headcount`:
`PLANNED` (approved=0) → `VACANT` (occupied=0) → `PARTIALLY_OCCUPIED` →
`OCCUPIED`. `FROZEN` and `CANCELLED` are explicit overrides.

### Milestone 3 — Workflow Engine (PRD Section 9)

| Method | Path                                              | Notes                                       |
|--------|---------------------------------------------------|---------------------------------------------|
| GET    | `/api/workflow/definitions`                       | seeded sequences (ORG_STRUCTURE_APPROVAL, …)|
| GET    | `/api/workflow/inbox`                             | instances waiting on the caller's roles     |
| GET    | `/api/workflow/initiated`                         | instances the caller started                |
| GET    | `/api/workflow/instances?module=&entity=&id=`     | instances bound to a subject                |
| GET    | `/api/workflow/instances/{id}`                    | one instance                                |
| GET    | `/api/workflow/instances/{id}/history`            | append-only action log                      |
| POST   | `/api/workflow/instances/{id}/actions`            | `{ action, comment }` — APPROVE / REJECT / RETURN / COMMENT / CANCEL |

How it integrates:

- Submitting an org-structure DRAFT now starts an `ORG_STRUCTURE_APPROVAL`
  instance (PRD 8.2.4 default chain: HR review → Executive sign-off).
- When the workflow reaches a terminal state, a `WorkflowCompletedEvent`
  fires and the org module updates the version (`APPROVED`, `REJECTED`,
  or `DRAFT` on `RETURNED`/`CANCELLED`).
- Segregation of duties (PRD 14.9) — the initiator cannot APPROVE their
  own request; role enforcement on every step.

This iteration ships **sequential** approvals only. Parallel, conditional,
delegation, SLA-based escalation, and substitute approvers are deliberate
seams — the data model already has the columns.

### Milestone 4 — Attendance (PRD Section 8.4)

| Method | Path                                                | Notes                                          |
|--------|-----------------------------------------------------|------------------------------------------------|
| GET    | `/api/attendance/schedules`                         | list work schedules                            |
| POST   | `/api/attendance/schedules`                         | create (`workDays` is a 7-char Mon..Sun bit string) |
| PUT    | `/api/attendance/schedules/{id}`                    | update                                         |
| GET    | `/api/attendance/assignments?employeeId=…`          | schedule assignments for an employee           |
| POST   | `/api/attendance/assignments`                       | assign a schedule with effective dates         |
| POST   | `/api/attendance/events`                            | single event (REST source)                     |
| POST   | `/api/attendance/events/csv` (multipart)            | bulk import a turnstile CSV                    |
| GET    | `/api/attendance/events?fromDate=&toDate=&employeeId=` | paginated events                             |
| POST   | `/api/attendance/summary/run`                       | compute summaries for a date range / employee  |
| GET    | `/api/attendance/summary?fromDate=&toDate=&employeeId=` | daily summaries                              |
| POST   | `/api/attendance/summary/{id}/correct`              | manual correction (reason required, audited)   |

How the engine works:

- Each working day, first `IN` and last `OUT` are matched. `worked = (last_out − first_in) − break`.
- `late = max(0, first_in − schedule_start − grace_period)`. `early = max(0, schedule_end − last_out)`. `overtime = max(0, last_out − schedule_end)`.
- Status: `PRESENT` (both punches), `PARTIAL` (one), `ABSENT` (none on a work day), `NON_WORKING_DAY` (weekend), `NO_SCHEDULE` (no active assignment).
- Idempotent — re-running upserts. Manual corrections are **sticky**: once `corrected_at` is set, the engine never overwrites that row, so the engine is safe to schedule on a cron.

CSV format (header required):

```
employee_no,event_time,event_type,device_id,location
EMP-00001,2026-05-19T09:02:30+04:00,IN,DEV-01,Baku HQ
EMP-00001,2026-05-19T18:14:05+04:00,OUT,DEV-01,Baku HQ
```

### Milestone 5 — Leave Management (PRD Section 8.5)

| Method | Path                                       | Notes                                                |
|--------|--------------------------------------------|------------------------------------------------------|
| GET    | `/api/leave/types?activeOnly=`             | configurable leave types (seeded for Azerbaijan)     |
| POST   | `/api/leave/types`                         | create (`HR_ADMIN` / `SYSTEM_ADMIN`)                 |
| PUT    | `/api/leave/types/{id}`                    | update                                               |
| GET    | `/api/leave/balances?employeeId=&year=`    | balances; lazy-init on first touch                   |
| POST   | `/api/leave/balances/adjust`               | HR adjustment (signed delta + reason, audited)       |
| GET    | `/api/leave/requests?employeeId=&status=`  | paginated requests                                   |
| GET    | `/api/leave/requests/{id}`                 | single request                                       |
| POST   | `/api/leave/requests/submit`               | starts `LEAVE_REQUEST_APPROVAL` workflow             |

**Seeded leave types** (Republic of Azerbaijan defaults — all configurable):

| Code           | Paid | Default entitlement | Carry-fwd | Notes                                       |
|----------------|------|---------------------|-----------|---------------------------------------------|
| ANNUAL         | yes  | 21 days             | 5 days    | Replacement required                        |
| SICK           | yes  | no bank             | 0         | Attachment required                         |
| MATERNITY      | yes  | 126 days            | unlimited | 70 before + 56 after birth (PRD 8.9.4)      |
| MARRIAGE       | yes  | 3 days              | 0         |                                             |
| COMPASSIONATE  | yes  | 3 days              | 0         |                                             |
| EDUCATIONAL    | yes  | no bank             | 0         | Attachment required                         |
| SOCIAL         | yes  | no bank             | 0         |                                             |
| UNPAID         | no   | 0                   | 0         | No balance enforcement                      |
| PATERNITY      | yes  | 14 days             | 0         | **Disabled by default** — abolished by AZ Labour Code amendment effective 16 Jan 2026 (PRD 8.5.1) |

**Balance math** (PRD 8.5.2):

```
remaining = entitlement + carriedForward + adjustment − used − reserved
```

- **Submit** reserves `totalDays` (enforces balance only when the type has a non-zero default entitlement).
- **Workflow APPROVED** → reserved → used.
- **REJECTED / RETURNED / CANCELLED** → reservation released.

**Workflow** (`LEAVE_REQUEST_APPROVAL`, seeded in V10):

```
Step 1 — Manager review      ROLE_HR_SPECIALIST   ← approximates direct-manager until ABAC ships
Step 2 — HR approval         ROLE_HR_ADMIN
```

`LeaveRequestWorkflowListener` consumes `WorkflowCompletedEvent` and
commits / releases the balance accordingly.

PRD 8.5.7 acceptance criterion verified end-to-end (entitlement 21 −
adjustment 9 = 12 available → submit 5 days → reserved 5, remaining 7 →
approve → used 5, remaining 7).

### Milestone 6 — Business Trip + Permission (PRD 8.6, 8.7)

**Business Trip** (`/api/business-trips`):

| Method | Path                                       | Notes                                                |
|--------|--------------------------------------------|------------------------------------------------------|
| GET    | `/api/business-trips?employeeId=&status=`  | paginated trip list                                  |
| GET    | `/api/business-trips/{id}`                 | single trip                                          |
| POST   | `/api/business-trips/submit`               | starts `BUSINESS_TRIP_APPROVAL` workflow             |
| POST   | `/api/business-trips/{id}/reconcile`       | post-trip actuals → `COMPLETED` (audited)            |

- Trip types: `DOMESTIC` / `INTERNATIONAL`.
- Advance tracked as `requestedAdvance → approvedAdvance → paidAdvance → actualExpense`; `advanceBalance = approved − actual` (positive = surplus owed back to the company).
- Workflow `BUSINESS_TRIP_APPROVAL` (3 steps, approximating PRD 8.6.2): Manager (`ROLE_HR_SPECIALIST`) → Finance/HR (`ROLE_HR_ADMIN`) → Executive (`ROLE_SYSTEM_ADMIN`). Dedicated Finance role lands when the role catalogue grows.
- PRD 8.6.7 verified — 4-day Istanbul, $800 requested → workflow approves all 3 steps → `approvedAdvance = 800`, `status = APPROVED`; reconcile actual 720 → `advanceBalance = 80 USD` returnable.

**Permission / hourly absence** (`/api/permission`):

| Method | Path                                                 | Notes                                          |
|--------|------------------------------------------------------|------------------------------------------------|
| GET    | `/api/permission/types?activeOnly=`                  | configurable types (seeded)                    |
| POST   | `/api/permission/types`                              | create (`HR_ADMIN`/`SYSTEM_ADMIN`)             |
| PUT    | `/api/permission/types/{id}`                         | update                                         |
| GET    | `/api/permission/balances?employeeId=&year=`         | balances in **hours**                          |
| POST   | `/api/permission/balances/adjust`                    | signed delta + reason (audited)                |
| GET    | `/api/permission/requests?employeeId=&status=`       | paginated requests                             |
| POST   | `/api/permission/requests/submit`                    | starts `PERMISSION_APPROVAL` workflow          |

Seeded permission types (PRD 8.7.1):

| Code     | Annual cap | Paid | Notes                               |
|----------|------------|------|-------------------------------------|
| PERSONAL | 24 h/year  | yes  | PRD 8.7.4 acceptance example        |
| BUSINESS | unlimited  | yes  |                                     |
| HOURLY   | 24 h/year  | yes  | shorter outings                     |
| FULL_DAY | 16 h/year  | yes  | attachment required                 |

Balance math (analogous to Leave but in hours):

```
remaining = limit + adjustment − used − reserved
```

PRD 8.7.4 verified — limit 24 − adjustment 8 = 16 → submit 4h → `reserved = 4, remaining = 12`.

Workflow `PERMISSION_APPROVAL` is single-step (manager review at `ROLE_HR_SPECIALIST`) per PRD 8.7.3.

### Milestone 7 — Timesheet (PRD Section 8.8)

| Method | Path                                       | Notes                                                     |
|--------|--------------------------------------------|-----------------------------------------------------------|
| GET    | `/api/timesheets?year=&month=`             | per-employee summaries for a period                       |
| GET    | `/api/timesheets/{id}`                     | header + per-day grid                                     |
| POST   | `/api/timesheets/generate`                 | (re)build for `(employeeId, periodYear, periodMonth)`     |
| POST   | `/api/timesheets/{id}/submit`              | starts `TIMESHEET_APPROVAL` workflow                      |
| POST   | `/api/timesheets/{id}/days/{dayId}/correct`| manual day correction (audited)                           |

**Timesheet codes** (PRD 8.8.2):

```
W  — worked          O  — overtime indicator (kept as a separate hours column)
L  — annual leave    H  — holiday / non-working day
S  — sick leave      A  — absent
BT — business trip   P  — permission
```

**Code precedence** (one primary per day, PRD 8.8.6):

```
BT  >  L / S  >  W (any worked hours)  >  P  >  A  >  H
```

**Sources merged** by the generator:

1. `attendance.schedule_assignment` — what was scheduled for the day
2. `attendance.daily_summary` — worked / OT / late / early / break minutes
3. `leave.leave_request` (APPROVED, overlapping the date) — code `L` or `S`
4. `business_trip.business_trip_request` (APPROVED/COMPLETED, overlapping) — code `BT`
5. `permission.permission_request` (APPROVED, on the date) — code `P` (and flag if a worked day also has a permission overlap)

Idempotent: regenerating drops + re-creates the day rows. Manual corrections are written through `/days/{dayId}/correct` (reason required, audited) and then the per-month totals are recomputed.

Workflow `TIMESHEET_APPROVAL` (PRD 8.8.4 — HR Specialist → Manager → Payroll Specialist), approximated with `HR_SPECIALIST → HR_ADMIN` until the full role catalogue lands. After `APPROVED`, the timesheet is ready for Payroll to consume; `LOCKED` and `REOPENED` status values are reserved for that flow.

PRD 8.8.6 verified end-to-end on Aliya Mammadova's actual data:

```
May 2026 — 31 days, codes {A, H, W}, sum worked 40.26h + OT 1.75h, matches attendance ✓
June 2026 — 5 × L on her annual-leave week, leaveRequestId linked, totalLeaveDays = 5 ✓
July 2026 — 4 × BT on the Istanbul trip, btRequestId linked, totalBtDays = 4 ✓
```

### Milestone 8 — Payroll (PRD Section 8.9)

| Method | Path                                            | Notes                                          |
|--------|-------------------------------------------------|------------------------------------------------|
| GET    | `/api/payroll/runs`                             | list runs                                      |
| GET    | `/api/payroll/runs/{id}`                        | run header                                     |
| GET    | `/api/payroll/runs/{id}/results`                | per-employee payslip rows                      |
| POST   | `/api/payroll/runs`                             | create a DRAFT run for `(year, month, AZ)`     |
| POST   | `/api/payroll/runs/{id}/calculate`              | run the engine against APPROVED timesheets     |
| POST   | `/api/payroll/runs/{id}/bonuses`                | attach a bonus before recalc                   |
| POST   | `/api/payroll/runs/{id}/submit`                 | start `PAYROLL_APPROVAL` workflow              |
| POST   | `/api/payroll/runs/{id}/mark-paid`              | post-approval, stamps `paidAt`                 |
| POST   | `/api/payroll/runs/{id}/close`                  | from PAID → CLOSED                             |
| GET    | `/api/payroll/runs/{id}/bank-file`              | CSV bank-payment file (`text/csv` download)    |
| GET    | `/api/payroll/compensation?employeeId=`         | effective-dated salary history                 |
| POST   | `/api/payroll/compensation`                     | set/replace current salary                     |
| GET    | `/api/payroll/bank-accounts?employeeId=`        | bank account for an employee                   |
| POST   | `/api/payroll/bank-accounts`                    | upsert bank account                            |

**Lifecycle** (PRD 8.9.2):

```
DRAFT → CALCULATED → UNDER_REVIEW → APPROVED → PAID → CLOSED
                                                 ↑     ↓
                                              REOPENED (override)
```

**Statutory rules** are seeded as configurable JSON in `payroll.statutory_rule` (V16):

| Rule code         | Formula type           | AZ 2026 numbers                                                                                                  |
|-------------------|------------------------|------------------------------------------------------------------------------------------------------------------|
| `INCOME_TAX_AZ`   | `PROGRESSIVE_BRACKETS` | ≤ 2500 → 3% of (gross − 200 exemption); 2500–8000 → 75 + 10% × (gross − 2500); > 8000 → 625 + 14% × (gross − 8000)|
| `DSMF_AZ`         | `DSMF_AZ_2026`         | employee 3% × 200 + 10% × remainder (+10% above 8000); employer 22% × 200 + 15% × remainder (+11% above 8000)    |
| `MMI_AZ`          | `BANDED_PCT`           | ≤ 2500 → 2% each side; > 2500 → 2% on first 2500 + 0.5% on remainder, each side                                  |
| `UNEMPLOYMENT_AZ` | `FLAT_PCT`             | 0.5% employee + 0.5% employer                                                                                    |
| `OVERTIME_AZ`     | `OT_MULTIPLIERS`       | First 2 hours/day × 1.5, then × 2; baseline 160 expected monthly hours                                            |

**PRD 8.9.10 acceptance** verified end-to-end on Aliya, May 2026 (base 3000 AZN, 40.26h worked, 1.75h OT) — calculator output matches hand calculation to the cent:

```
hourly rate            = 3000 / 160         = 18.7500 AZN
overtime pay           = 1.75 × 1.5 × 18.75 =   49.22 AZN
gross                  = 3000 + 49.22       = 3049.22 AZN

income tax (2500–8000) = 75 + 10% × (gross − 2500)         =  129.92
DSMF employee          = 3% × 200 + 10% × 2849.22          =  290.92
DSMF employer          = 22% × 200 + 15% × 2849.22         =  471.38
MMI employee           = 2% × 2500 + 0.5% × 549.22         =   52.75
MMI employer           = (same)                            =   52.75
unempl employee        = 0.5% × 3049.22                    =   15.25
unempl employer        = (same)                            =   15.25
net                    = gross − employee deductions       = 2560.38 AZN  ✓
```

Every `payroll_result` row carries a step-by-step `calculation_details` JSON so an auditor can trace each number back to source data and the active rule version.

### Milestone 9 — Recruitment (PRD Section 8.10)

| Method | Path                                                       | Notes                                                |
|--------|------------------------------------------------------------|------------------------------------------------------|
| GET    | `/api/recruitment/vacancies?status=`                       | paginated vacancies list                             |
| GET    | `/api/recruitment/vacancies/{id}`                          | single vacancy                                       |
| POST   | `/api/recruitment/vacancies`                               | create (auto-numbered `VAC-00001`)                   |
| PUT    | `/api/recruitment/vacancies/{id}`                          | update                                               |
| POST   | `/api/recruitment/vacancies/{id}/status/{status}`          | OPEN / ON_HOLD / CLOSED / FILLED / CANCELLED          |
| GET    | `/api/recruitment/candidates?search=`                      | paginated candidates                                 |
| POST   | `/api/recruitment/candidates`                              | create (auto-numbered `CAND-00001`)                  |
| PUT    | `/api/recruitment/candidates/{id}`                         | update                                               |
| GET    | `/api/recruitment/applications?vacancyId=` (or `candidateId=`) | filter pipeline                                  |
| GET    | `/api/recruitment/applications/{id}/history`               | per-stage audit trail with ratings + recommendations |
| POST   | `/api/recruitment/applications`                            | apply: vacancy + candidate → starts at CV_SCREENING  |
| POST   | `/api/recruitment/applications/{id}/transition`            | move stage; rating + recommendation captured per move|
| GET    | `/api/recruitment/offers?applicationId=`                   | offer for an application (204 if none)               |
| PUT    | `/api/recruitment/offers?applicationId=`                   | create/update offer (DRAFT)                          |
| POST   | `/api/recruitment/offers/{id}/status/{status}`             | SENT / ACCEPTED / REJECTED / EXPIRED / RESCINDED     |

**Pipeline** (PRD 8.10.3 — default; per-vacancy override is a later seam):

```
CV_SCREENING → HR_INTERVIEW → TECHNICAL_INTERVIEW → FINAL_INTERVIEW → OFFER → HIRED
                                                                          ↘ REJECTED / WITHDRAWN (any time)
```

**HIRE flow** (PRD 8.10.8) — verified end-to-end on Nigar Hasanli against the existing Senior Platform Engineer position (POS-00001):

```
application HIRED
  → status APPROVED, createdEmployeeId set
  → Employee EMP-00003 created (status ON_PROBATION, positionId linked, hireDate from offer)
  → OnboardingWorkflow stub fires (will be replaced by the real workflow once Core HR onboarding tasks land)
  → position POS-00001: occupied 0 → 1, vacantHeadcount 3 → 2
  → vacancy switches to FILLED when all openings consumed
```

`StaffingService.adjustOccupancy(positionId, +1)` keeps the position's `vacancyState` in sync (`VACANT → PARTIALLY_OCCUPIED → OCCUPIED`) with the same precedence rules used by the manual update path.

### Milestone 10 — Termination + Contract Change (PRD 8.11, 8.12)

| Method | Path                                                       | Notes                                                |
|--------|------------------------------------------------------------|------------------------------------------------------|
| GET    | `/api/lifecycle/terminations?status=&employeeId=`          | paginated terminations list                          |
| GET    | `/api/lifecycle/terminations/{id}`                         | single termination + payout snapshot                 |
| POST   | `/api/lifecycle/terminations/submit`                       | submit (auto-numbered `TRM-00001`) — starts workflow |
| POST   | `/api/lifecycle/terminations/{id}/clearance`               | tick IT/HR/Finance/Assets clearances (after APPROVED) |
| POST   | `/api/lifecycle/terminations/{id}/exit-interview`          | record optional exit interview                       |
| POST   | `/api/lifecycle/terminations/{id}/process`                 | final processing — flips Employee → TERMINATED, releases the position, snapshots settlement |
| GET    | `/api/lifecycle/contract-changes?employeeId=&type=&status=`| paginated contract-change list                       |
| GET    | `/api/lifecycle/contract-changes/{id}`                     | single change with old/new value JSON                |
| POST   | `/api/lifecycle/contract-changes/submit`                   | submit (auto-numbered `CC-00001`) — starts workflow  |

**Termination flow** (PRD 8.11) — verified end-to-end on Nigar Hasanli (REDUNDANCY):

```
hrspec submits TRM-00001 → TERMINATION_APPROVAL workflow starts (Manager → HR/Legal → Executive)
admin approves 3 steps → listener flips termination to APPROVED
admin processes termination
  → Employee EMP-00003: ON_PROBATION → TERMINATED
  → Position POS-00001: occupied 1 → 0, vacancyState PARTIALLY_OCCUPIED → VACANT
  → Settlement snapshot: unused-leave payout (annual balance × daily rate) + severance (REDUNDANCY = 1 month, INVOLUNTARY_DISMISSAL = 10 days, else 0)
  → Calculation details (monthly base, daily rate, balance breakdown) stored as JSON for the auditor trace
```

Severance is a working default per reason code; the rule engine can replace it later (PRD 8.11.5). Unused-leave payout reads `leave_mgmt.leave_balance` for the ANNUAL type and multiplies the remaining days by (monthly base / 21.67).

**Contract Change flow** (PRD 8.12) — verified end-to-end on Aliya (SALARY 3000 → 3500 effective 2026-07-01):

```
hrspec submits CC-00001 (SALARY) → CONTRACT_CHANGE_APPROVAL workflow (Manager → HR → Finance/Executive)
admin approves 3 steps → listener flips change to APPROVED then auto-applies:
  → payroll.employee_compensation: new row (3500 AZN, effectiveFrom 2026-07-01)
  → previous row's effectiveTo set to 2026-06-30
  → Next payroll run picks up the new salary automatically (engine is effective-dated)
```

One service handles all 9 change types via JSONB payload + JSONB old-snapshot:

| Type           | Apply behaviour                                                   |
|----------------|-------------------------------------------------------------------|
| SALARY         | Adds an effective-dated row in `payroll.employee_compensation`    |
| POSITION       | Updates Employee.positionId + swaps staffing occupancy (old −1, new +1) |
| DEPARTMENT     | Updates Employee.orgUnitId / departmentName                       |
| MANAGER        | Updates Employee.managerId (rejects self-management)              |
| JOB_TITLE      | Updates Employee.positionTitle                                    |
| COST_CENTRE    | Updates Employee.costCentre                                       |
| GRADE / LOCATION / EMPLOYMENT_TYPE | Recorded as JSON only (live on the Position) |

### Milestone 11 — Performance Management System (PRD 8.13)

| Method | Path                                                       | Notes                                                |
|--------|------------------------------------------------------------|------------------------------------------------------|
| GET    | `/api/performance/cycles?status=`                          | review cycles                                        |
| POST   | `/api/performance/cycles`                                  | create cycle (DRAFT)                                  |
| PUT    | `/api/performance/cycles/{id}`                             | update                                               |
| POST   | `/api/performance/cycles/{id}/status/{status}`             | DRAFT → OPEN → CALIBRATING → CLOSED → COMPLETED       |
| GET    | `/api/performance/goals?cycleId=&employeeId=`              | goals for a cycle (and optionally employee)          |
| POST   | `/api/performance/goals`                                   | create SMART goal (auto-numbered `GOAL-00001`)        |
| POST   | `/api/performance/goals/{id}/progress`                     | progress + status update                              |
| POST   | `/api/performance/goals/{id}/rate`                         | final per-goal rating (0–5)                           |
| GET    | `/api/performance/reviews?cycleId=&employeeId=&status=`    | paginated reviews                                    |
| POST   | `/api/performance/reviews/start`                           | start review for (cycle, employee) — `REV-00001`      |
| POST   | `/api/performance/reviews/{id}/self`                       | self assessment                                       |
| POST   | `/api/performance/reviews/{id}/manager`                    | manager assessment + auto-compute weighted goal score |
| POST   | `/api/performance/reviews/{id}/submit`                     | starts PERFORMANCE_REVIEW_APPROVAL                    |
| POST   | `/api/performance/reviews/{id}/calibrate`                  | finalRating / band / recommendation / bonus%          |
| POST   | `/api/performance/reviews/{id}/close`                      | finalise → COMPLETED                                  |
| GET    | `/api/performance/feedback?cycleId=&subjectEmployeeId=`    | 180/360 feedback for an employee                     |
| POST   | `/api/performance/feedback`                                | submit feedback (NORMAL or ANONYMOUS visibility)      |

**Review lifecycle** (PRD 8.13.4):

```
DRAFT / SELF_IN_PROGRESS → SELF_SUBMITTED → MANAGER_SUBMITTED →
PENDING_APPROVAL (3-step workflow: Manager → HR/Calibration → Executive) →
APPROVED → CALIBRATING → COMPLETED
                         ↘ REJECTED / CANCELLED at any pre-approval step
```

**Goal score math** — weighted average of per-goal ratings:

```
goalScore = Σ(rating × weightPercent) / Σ(weightPercent)
```

If all weights are zero, falls back to a flat average; if no goals are rated yet, the score is null and the UI shows "— pending".

**End-to-end verified** on Aliya (ANNUAL-2026 cycle, seeded) — PRD 8.13 acceptance:

```
GOAL-00001  Platform stability — weight 60%, rated 4.0
GOAL-00002  Mentoring         — weight 40%, rated 3.5
   goal score = (4.0×60 + 3.5×40) / 100 = 3.8  ✓ matches hand calc
self review 4.0 → manager review 3.8 → submit → 3 admin approvals
   → review status APPROVED, goalScore 3.8
calibration finalRating=4.0, band="Exceeds Expectations",
   recommendation=BONUS_TIER_A, bonusPercent=15%
close → COMPLETED
+ anonymous PEER feedback from Rashad → Aliya:
   visibility=ANONYMOUS, author redacted, createdBy=anonymous ✓
```

The `recommendation` + `bonusPercent` fields are the input edge for the bonus matrix in Comp & Benefits (PRD 8.15) — the next module on the roadmap can read approved reviews and apply the matrix during payroll.

### Milestone 12 — Learning Management System (PRD 8.14)

| Method | Path                                                       | Notes                                                |
|--------|------------------------------------------------------------|------------------------------------------------------|
| GET    | `/api/learning/courses?status=&category=`                  | paginated course catalog                             |
| POST   | `/api/learning/courses`                                    | create course (DRAFT, `CRS-00001`)                    |
| PUT    | `/api/learning/courses/{id}`                               | update                                               |
| POST   | `/api/learning/courses/{id}/publish`                       | DRAFT → PUBLISHED (requires ≥ 1 question)             |
| POST   | `/api/learning/courses/{id}/archive`                       | → ARCHIVED                                            |
| GET    | `/api/learning/courses/{id}/questions?includeAnswers=`     | answer key hidden unless caller is HR / admin         |
| POST   | `/api/learning/courses/{id}/questions`                     | add MULTIPLE_CHOICE / MULTI_SELECT / TRUE_FALSE       |
| POST   | `/api/learning/courses/{id}/competencies`                  | map competency awarded on pass                       |
| GET    | `/api/learning/enrollments?courseId=&employeeId=&status=`  | paginated enrollments                                |
| POST   | `/api/learning/enrollments`                                | enroll (SELF_ENROLLED or ASSIGNED) → `ENR-00001`      |
| POST   | `/api/learning/enrollments/{id}/attempts`                  | start a new quiz attempt                              |
| POST   | `/api/learning/enrollments/attempts/{id}/submit`           | submit + auto-grade → PASSED / FAILED                 |
| GET    | `/api/learning/certificates?employeeId=`                   | issued certificates (`CERT-00001`)                    |
| GET    | `/api/learning/competencies`                               | competency catalogue                                  |
| POST   | `/api/learning/competencies`                               | create competency                                     |
| GET    | `/api/learning/competencies/employee/{employeeId}`         | awards for an employee                                |

**Grading rules** — set-equality on selected vs correct keys:

| Question type     | Pass condition                                          |
|-------------------|---------------------------------------------------------|
| MULTIPLE_CHOICE   | Exactly the one correct key selected                    |
| TRUE_FALSE        | Exactly the one correct key selected                    |
| MULTI_SELECT      | Set of selected keys equals set of correct keys (no partial credit) |

A passing attempt (score ≥ `passingScore`):
1. Flips the enrollment to **PASSED** + records `completedAt`.
2. Auto-issues a **Certificate** (`CERT-NNNNN`) with `validUntil = today + course.validForMonths` (NULL = no expiry).
3. **Awards each mapped competency** as an `EmployeeCompetency` row (`source=COURSE`, `source_ref = enrollment.id`, proficiency from the mapping, validUntil mirrors the certificate).

A failing attempt increments `attemptsUsed`; once it reaches `max_attempts`, the enrollment locks in **FAILED**.

**Seeded content** — `INFOSEC-101 — Information Security Essentials` (mandatory, COMPLIANCE, 1.5 h, 70% pass, 3 attempts, 12-month cert validity) with 4 questions (one of each type pattern) and 2 mapped competencies (`INFOSEC_AWARENESS` level 3, `GDPR_BASICS` level 2).

**End-to-end verified — PRD 8.14 acceptance:**

```
admin enrolls Aliya in INFOSEC-101  → ENR-00001 (ENROLLED, ASSIGNED)
employee starts attempt → IN_PROGRESS, attemptNo 1
employee submits all-correct answers (B, F, C, A+C+D) → 5/5 pts = 100% → PASSED
   → enrollment → PASSED, completedAt set, bestScorePercent=100
   → CERT-00001 issued, validUntil 2027-05-21 (12 months out)
   → INFOSEC_AWARENESS awarded at proficiency 3
   → GDPR_BASICS    awarded at proficiency 2
   (source=COURSE, source_ref=enrollment.id, validUntil mirrors cert)

Rashad enrolled, submits partial+wrong (1/5 = 20%) → FAILED for this attempt
   → enrollment status IN_PROGRESS (1/3 attempts used)
   → no certificate, no competencies awarded
```

The answer key is hidden from the employee view: `GET /courses/{id}/questions` returns `correctKeys: null` and `explanation: null` unless the caller has HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN.

### Milestone 13 — Compensation & Benefits (PRD 8.15)

| Method | Path                                                       | Notes                                                |
|--------|------------------------------------------------------------|------------------------------------------------------|
| GET    | `/api/compbenefits/matrix-rules?effectiveOn=`              | bonus matrix rules                                    |
| POST   | `/api/compbenefits/matrix-rules`                           | create rule                                           |
| PUT    | `/api/compbenefits/matrix-rules/{id}`                      | update                                                |
| GET    | `/api/compbenefits/allowance-types?activeOnly=`            | allowance catalogue                                   |
| POST   | `/api/compbenefits/allowance-types`                        | create                                                |
| GET    | `/api/compbenefits/allowances?employeeId=&effectiveOn=`    | per-employee allowances                               |
| POST   | `/api/compbenefits/allowances`                             | assign (`ALW-00001`) — closes prior open record       |
| POST   | `/api/compbenefits/allowances/{id}/end?endDate=`           | end an active allowance                               |
| DELETE | `/api/compbenefits/allowances/{id}?reason=`                | cancel                                                |
| GET    | `/api/compbenefits/bonus-runs?cycleId=&status=`            | paginated bonus runs                                  |
| POST   | `/api/compbenefits/bonus-runs/generate`                    | generate from a Performance cycle (`BR-00001`)        |
| GET    | `/api/compbenefits/bonus-runs/{id}/items`                  | per-employee items (`BRI-NNNNN`)                      |
| POST   | `/api/compbenefits/bonus-runs/{id}/push`                   | push items into a target payroll run                  |
| POST   | `/api/compbenefits/bonus-runs/{id}/cancel?reason=`         | cancel (only when not yet PUSHED)                     |

**Bonus matrix lookup** (PRD 8.15.2) — lower priority value wins; explicit recommendation match beats a generic rating band at the same priority:

```
Per-employee derivation order:
1. review.bonusPercent set during calibration → REVIEW_OVERRIDE
   amount = base × bonusPercent / 100
2. Otherwise matrix lookup by (recommendation, finalRating) on the run's anchor date
   → MATRIX_LOOKUP; amount = flat_amount, or base × bonus_percent / 100; cap at max_amount
3. No rule matches → amount = 0 with note "manual review required"
```

**Seeded rules** (priority 10 — recommendation match): `PROMOTION → 20%`, `BONUS_TIER_A → 15%`, `BONUS_TIER_B → 10%`, `BONUS_TIER_C → 5%`, `MERIT_INCREASE / PIP / NONE → 0%`. **Seeded rating-band fallbacks** (priority 100): `4.00–5.00 → 8%`, `3.00–3.99 → 3%`.

**Seeded allowance catalogue**: `TRANSPORT 100`, `MEAL 150`, `MOBILE 30`, `HOUSING 300`, `FUEL 100`, `FAMILY 50` (AZN).

**End-to-end verified — PRD 8.15 acceptance:**

```
admin assigns Aliya TRANSPORT 100 AZN effective 2026-08-01 → ALW-00001
admin generates BR-00001 from ANNUAL-2026 for 2026-08:
  Aliya (REV-00001, BONUS_TIER_A, finalRating 4.0, bonusPercent 15, base 3500 post CC-00001)
  → bonusAmount = 3500 × 15 / 100 = 525.00  ✓
  source = REVIEW_OVERRIDE (bonusPercent set during calibration)
push to PR-00002 (DRAFT, 2026-08):
  → payroll.payroll_bonus row created: bonus_type=PERFORMANCE, amount=525.00
  → note "Bonus run BR-00001 — REVIEW_OVERRIDE (BONUS_TIER_A)"
  → bonus_run_item.pushedPayrollBonusId set; bonus_run status → PUSHED

Matrix branch sanity-checked: clearing review.bonusPercent and regenerating
(BR-00002) hits the MATRIX_LOOKUP path with BONUS_TIER_A rule (15%) →
same 525.00 amount, source=MATRIX_LOOKUP, matrixRuleId populated.
```

The next payroll calculation on PR-00002 (once timesheets are approved) automatically picks the bonus into gross pay — no further changes needed in the payroll engine.

### Milestone 14 — Reporting & Analytics (PRD Section 10)

Read-only cross-module aggregations via `NamedParameterJdbcTemplate` — no new schema, since reports are pure SELECTs over existing module data. Eight endpoints, one frontend page with a tab per report.

| Method | Path                                              | Notes                                                |
|--------|---------------------------------------------------|------------------------------------------------------|
| GET    | `/api/reports/headcount`                          | total / active / probation / on leave / terminated YTD + attrition rate + by-status + by-department + 12-month hire/leaver trend |
| GET    | `/api/reports/attrition?year=`                    | terminations total, attrition rate, by reason, by month, total settlement payout |
| GET    | `/api/reports/payroll-summary?year=`              | YTD gross/net/tax/deductions, runs closed vs in progress, per-run table |
| GET    | `/api/reports/leave?year=`                        | usage by leave type, overdrawn balances, high-annual-leave-balance employees |
| GET    | `/api/reports/attendance?from=&to=`               | worked / late / early / overtime / absent days by employee for a date range |
| GET    | `/api/reports/training`                           | mandatory courses + per-course completion + overall compliance % + non-compliant employee list |
| GET    | `/api/reports/performance?cycleId=`               | per-cycle reviews total/completed, average final rating, half-point rating distribution, recommendation breakdown |
| GET    | `/api/reports/recruitment`                        | vacancies open/filled, candidate pool, applications by stage, avg time-to-hire, per-vacancy funnel |

**End-to-end verified — all 8 endpoints return correct totals against the seeded + test data:**

```
GET /reports/headcount     → 3 employees (1 ACTIVE Rashad, 1 ON_PROBATION Aliya, 1 TERMINATED Nigar)
                             attrition rate 33.33% (1 of 3)
GET /reports/attrition?year=2026 → 1 termination (Nigar / REDUNDANCY), payout 0 AZN
GET /reports/payroll-summary?year=2026 → 2 runs, gross YTD 3049.22 AZN, net YTD 2560.38 AZN
                                          (matches the verified May 2026 calculation)
GET /reports/leave?year=2026 → 8 leave types, none overdrawn
GET /reports/attendance?from=2026-05-01&to=2026-05-31
                            → 40.25 worked h, 15 late min, 70 early min, 105 OT min, 3 absent days
GET /reports/training       → 1 mandatory course (INFOSEC-101), 50% overall compliance
                              (Aliya PASSED, Rashad FAILED → 1 of 2 = 50%)
GET /reports/performance    → ANNUAL-2026 cycle, 1 review COMPLETED, avg final rating 4.00
GET /reports/recruitment    → 1 vacancy FILLED, 1 candidate pool, avg time-to-hire 11.15 days
```

Numbers cross-check with what the individual modules report. The frontend renders all 8 reports under a single tabbed **Reports & Analytics** page (top-level sider entry above Approvals) with inline mini-bar visualisations built from AntD `Progress` primitives — no chart library added to keep the bundle lean.

### Milestone 15 — Employee Self-Service (PRD Section 11)

The seam that turns the `employee/employee123` user from a smoke-test stub into a real first-class actor. Fixes the user↔employee mapping shortcut I was carrying since Leave (Milestone 5).

**V22 migration**: adds a unique `username` column to `core_hr.employee` and links the seeded `employee` user to EMP-00001 (Aliya). When Keycloak SSO lands later, this column maps to the `preferred_username` claim — the rest of the surface stays unchanged.

**Backend** — new `selfservice` package:
- `EmployeeContextService.currentEmployee()` resolves the Employee for the current `SecurityContext` user. Throws an actionable 400 when no mapping exists (`"User 'admin' is not linked to any employee record."`).
- `SelfController` exposes 17 endpoints under `/api/self/*`, all role-gated only by `isAuthenticated()` because the only data they can return is the caller's own.

| Method | Path                                  | Returns                                                |
|--------|---------------------------------------|--------------------------------------------------------|
| GET    | `/api/self/employee`                  | profile (also exposes username)                        |
| GET    | `/api/self/summary`                   | dashboard summary across modules                        |
| GET    | `/api/self/leave/balances`            | my balances for current year                            |
| GET    | `/api/self/leave/types`               | active leave types catalogue                            |
| GET    | `/api/self/leave/requests`            | my leave requests                                       |
| POST   | `/api/self/leave/submit`              | submit leave for myself — forces employeeId             |
| GET    | `/api/self/permission/balances`       | my hourly permission balances                           |
| GET    | `/api/self/permission/requests`       | my permission requests                                  |
| POST   | `/api/self/permission/submit`         | submit permission for myself                            |
| GET    | `/api/self/business-trips`            | my trips                                                |
| POST   | `/api/self/business-trips/submit`     | submit a trip for myself                                |
| GET    | `/api/self/timesheets`                | my timesheets (year-month sorted desc)                  |
| GET    | `/api/self/payslips`                  | my payslips                                             |
| GET    | `/api/self/learning/enrollments`      | my enrollments                                          |
| GET    | `/api/self/learning/certificates`     | my certificates                                          |
| GET    | `/api/self/learning/competencies`     | my awarded competencies                                  |
| GET    | `/api/self/performance/reviews`       | my reviews                                              |
| GET    | `/api/self/performance/goals`         | my goals                                                |
| GET    | `/api/self/allowances`                | my currently-active allowances                          |

**Self-submit endpoints** rebuild the underlying `SubmitRequest` with `employeeId` forced to the resolved current employee — even if a client sends a wrong/forged id in the body, the server overrides it. This lets the `EMPLOYEE` role submit leave/permission/BT on their own behalf without granting them `HR_ADMIN`.

**Frontend** — single `MyWorkspacePage` at `/my` with 8 tabs (Dashboard, Leave, Permission, Business trips, Timesheets, Payslips, Learning, Performance). Quick-action buttons jump to the existing form pages. Index redirect is now role-aware: HR/Admin land on `/employees` as before, everyone else lands on `/my`. New top-level **My Workspace** entry in the sider above the existing module groups.

**End-to-end verified — PRD Section 11 acceptance:**

```
admin logs in → /api/self/employee returns 400 "User 'admin' is not linked"  ✓
employee logs in → /api/self/employee returns Aliya (EMP-00001)              ✓
/api/self/summary aggregates across modules:
  annualLeaveRemaining=7.0, currentTimesheetStatus=APPROVED,
  lastPayslipNet=2560.38, certificatesHeld=1, activeReviewStatus=COMPLETED

employee POSTs to /api/self/leave/submit with employeeId=00000000-...-000000
  → backend overrides to Aliya's real id
  → LR-00003 created, status=PENDING, createdBy='employee'
  → workflow instance kicked off

After submit, /api/self/summary returns:
  annualLeaveRemaining=4.0  (was 7.0; reserved 3 days for the new request)  ✓
  leaveRequestsPending=1
HR-side /api/leave/requests sees the same LR-00003 row with the correct
employeeId — proves both views are pointing at the same record.
```

The audit trail records `createdBy='employee'` for self-submitted rows and `createdBy='hrspec'/'admin'` for HR-submitted ones, so accountability is intact.

### Milestone 16 — File Attachments via MinIO (PRD 14.6, 16.4)

Replaces the textarea-style `attachment_url(s)` fields sprinkled across modules with real file uploads. The blob lives in MinIO (S3-compatible), and `core_hr.attachment` is the auditable catalogue.

**Infrastructure** — new `hcm-minio` service in `docker-compose.yml` (ports 9000 S3 API, 9001 console; defaults `minio` / `minio12345`). The Spring Boot `MinioConfig` bean auto-creates the configured bucket on startup; if MinIO is unreachable the app keeps running and the upload endpoint returns 503 until storage is back.

**V23 migration** — `core_hr.attachment` with a soft pointer `(owner_module, owner_entity, owner_id)` into the owning record, plus `bucket`, `object_key`, `original_filename`, `content_type`, `size_bytes`, `uploaded_by`, `uploaded_at`, `deleted` (soft-delete keeps audit). Sequence `attachment_no_seq` drives the human-readable `ATT-NNNNN` id.

**Backend** — `AttachmentService` + 5 endpoints:

| Method | Path                                              | Notes                                                |
|--------|---------------------------------------------------|------------------------------------------------------|
| GET    | `/api/attachments?ownerModule=&ownerEntity=&ownerId=` | list non-deleted attachments for an entity          |
| GET    | `/api/attachments/{id}`                           | single attachment metadata                            |
| POST   | `/api/attachments` (multipart/form-data)          | upload — backend proxies to MinIO; key is `<module>/<entity>/<ownerId>/<uuid>-<filename>` |
| GET    | `/api/attachments/{id}/download`                  | streams the blob back with correct Content-Disposition |
| DELETE | `/api/attachments/{id}`                           | soft-delete metadata + remove from MinIO (HR/admin only) |

Filenames are sanitised server-side (path components stripped, non-alphanumerics replaced with `_`, max 200 chars). Each upload is audited. MinIO errors on delete are logged but don't fail the soft-delete — the orphan stays in the bucket and the metadata flips to deleted.

**Frontend** — reusable `AttachmentUploader` component that wraps AntD `Upload` for upload + `List` for browsing. Download uses axios (so the JWT header is applied) and triggers a save-as via blob URL. Delete is gated to HR roles. Integrated into `TerminationDetailPage` and `ContractChangeDetailPage` as the proof points; the same one-line component drops into any future detail page.

**End-to-end verified — PRD 14.6 / 16.4 acceptance:**

```
backend startup → MinIO bucket 'hcm-attachments' auto-created
employee POSTs /api/attachments with multipart file=wedding-invitation.txt (84 B)
  ownerModule=LEAVE, ownerEntity=LeaveRequest, ownerId=<LR-00003>
  → ATT-00001 created, object_key="leave/leaverequest/<LR>/<uuid>-wedding-invitation.txt"
employee GETs /api/attachments/<id>/download
  → HTTP 200, 84 bytes, Content-Disposition: attachment;filename="wedding-invitation.txt"
  → bytes match the uploaded file exactly
employee tries DELETE /api/attachments/<id>
  → HTTP 403 (RBAC blocks)
admin DELETE /api/attachments/<id>
  → soft-deleted record returned (deleted=true)
  → MinIO `mc ls` shows bucket is empty (object physically removed)
  → subsequent list returns []
```

### Milestone 17 — Scheduled reports + PDF/XLSX export (PRD 10.4 / 10.5)

Extends Milestone 14's synchronous report aggregations with downloadable PDF/XLSX, saved definitions, cron schedules, and a run history. Generated files reuse the MinIO attachment registry from Milestone 16 — the run-history download piggy-backs on the existing attachment download endpoint.

**Dependencies added**: `org.apache.poi:poi-ooxml 5.3.0` (XLSX) and `com.github.librepdf:openpdf 2.0.3` (PDF). Spring's `@EnableScheduling` lit up the cron walker.

**V24 migration** — new `reporting` schema with 3 tables + 3 sequences:
- `report_definition` — saved (name, type, default format, parameters JSON)
- `report_schedule` — Spring 6-field cron + recipients + `next_run_at` + `last_run_at` + `last_status`
- `report_run` — execution history with status RUNNING → SUCCESS|FAILED, linked to a `core_hr.attachment.id`, plus error message + trigger source (MANUAL / SCHEDULED)

**Backend** — render abstraction `ReportSection`/`ReportTable` so PDF and XLSX walk the same data; per-format renderers (`PdfReportRenderer`, `XlsxReportRenderer`) with brand-purple table headers + striped rows; `ReportSectionBuilder` shapes each of the 8 reports into sections; `ReportExportService` glues them together; `ReportRunService` runs synchronously, stores the bytes via `AttachmentService.uploadBytes(...)`, writes a `report_run` row with full audit; `ReportScheduleService` provides CRUD + a `@Scheduled(fixedDelay=60s, initialDelay=30s)` walker that fires due schedules and advances `next_run_at` via `org.springframework.scheduling.support.CronExpression`.

| Method | Path                                              | Notes                                                |
|--------|---------------------------------------------------|------------------------------------------------------|
| GET    | `/api/reports/export/{type}?format=PDF\|XLSX&...` | sync stream — no run history                          |
| GET    | `/api/reports/definitions[?type=&activeOnly=]`    | saved configs list                                    |
| POST   | `/api/reports/definitions`                        | create (`RDF-NNNNN`)                                  |
| PUT    | `/api/reports/definitions/{id}`                   | update                                                |
| POST   | `/api/reports/definitions/{id}/run`               | manual run → `RPT-NNNNN`, stored in MinIO             |
| GET    | `/api/reports/schedules`                          | schedules list                                        |
| POST   | `/api/reports/schedules`                          | create (`RSC-NNNNN`); computes `nextRunAt`            |
| PUT    | `/api/reports/schedules/{id}`                     | update; recomputes `nextRunAt` if cron changed        |
| POST   | `/api/reports/schedules/{id}/run-now`             | force-fire a schedule immediately                     |
| GET    | `/api/reports/runs?page=&size=`                   | paginated run history                                 |

**Frontend** — two pieces:
1. `<ExportButtons type=... params=... />` helper injected into every tab on `ReportsPage`. Each click downloads via the JWT-aware blob-save flow.
2. New `ReportSchedulesPage` at `/reports/schedules` with 3 tabs: **Definitions** (CRUD + Run-now), **Schedules** (CRUD + Run-now, with `next_run_at` / `last_run_at` / `last_status` columns), **Run history** (paginated, download button next to each SUCCESS row).

The sider's Reports & Analytics entry became a group with **Live reports** and **Schedules & history** children.

**End-to-end verified — PRD 10.4/10.5 acceptance:**

```
sync export:
  GET /api/reports/export/HEADCOUNT?format=PDF   → HTTP 200, 2384 B, valid PDF (2 pages)
  GET /api/reports/export/PAYROLL?format=XLSX    → HTTP 200, 4181 B, valid OOXML

saved definition + manual run:
  POST /api/reports/definitions {"name":"Daily headcount snapshot",
                                 "reportType":"HEADCOUNT","defaultFormat":"XLSX"}
    → RDF-00001 created
  POST /api/reports/definitions/<id>/run
    → RPT-00001 SUCCESS, attachment 1f1be293..., 4170 B file in MinIO
      at reporting/reportrun/<runId>/...

cron schedule fired automatically:
  POST /api/reports/schedules {cron: "0 55 10 * * *"} (~2 minutes ahead)
    → RSC-00001 created, nextRunAt set
  @Scheduled walker (fixedDelay 60s) wakes up after the due time
    → RPT-00002 SUCCESS with triggerSource=SCHEDULED, scheduleId=<RSC-00001>
    → schedule's lastRunAt/lastStatus updated, nextRunAt advanced to tomorrow

force-fire path:
  POST /api/reports/schedules/<id>/run-now
    → RPT-00003 SUCCESS with triggerSource=SCHEDULED

MinIO inspection: 3 XLSX files at 4.1 KiB each under reporting/reportrun/
```

### Milestone 18 — Security hardening (PRD 14.3 / 14.4 / 15.3)

Three concrete hardening items in one milestone. Keycloak OIDC swap is deferred — it's invasive enough to warrant its own dedicated milestone.

**Column-level AES-256-GCM encryption** for PII (PRD 14.3):
- `AesGcmEncryptor` wraps `javax.crypto.Cipher` with `AES/GCM/NoPadding`, 12-byte random IV per encryption, 128-bit auth tag. Wire format is `enc:v1:<base64(IV || ciphertext || tag)>`.
- `EncryptedStringConverter` (JPA `@AttributeConverter`) transparently encrypts on write and decrypts on read; falls through if the value doesn't carry the `enc:v1:` marker so legacy plaintext is still readable.
- Applied to `core_hr.employee.national_id`, `payroll.bank_account.iban`, `payroll.bank_account.account_number`. **V25** widens those columns to `VARCHAR(500)` so they fit ciphertext.
- Key loaded from `hcm.security.encryption.data-key` (base64-encoded 32 bytes). Key rotation is the next-tier follow-up — when it lands, the marker becomes `enc:v2:` and the encryptor will accept both during rotation.

**audit_log monthly partitioning** (PRD 15.3):
- **V26** renames the existing `audit.audit_log` to `_legacy`, recreates it as a declarative `PARTITION BY RANGE (created_at)` table with composite primary key `(id, created_at)`, creates 16 monthly partitions covering 2025-09 → 2026-12 + a `DEFAULT` catch-all, copies all existing rows into the partitioned shape, drops the legacy table.
- `AuditPartitionMaintenance` is a Spring `@Scheduled` bean: runs at 03:00 on the 25th of every month to `CREATE TABLE IF NOT EXISTS` the next month's partition, plus a startup hook + daily refresh that idempotently ensures current and next month exist.

**Login lockout + HTTP security headers** (PRD 14.4):
- `LoginAttemptService` keeps a per-username sliding window of failed-attempt timestamps in memory; 5 failures within 15 min → lock for 15 min (all three thresholds configurable). Multi-node HA should switch to Redis later; interface stays the same.
- `AuthController` now checks lock state before authenticating, records each failure, clears the window on success, and writes a `SECURITY/Login` audit row for every attempt outcome (`SUCCESS` / `FAILURE` / `LOCKED`).
- `AccountLockedException` → HTTP **429** with `Retry-After: <seconds>` header and a clear "try again in N seconds" message.
- `SecurityConfig` adds `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Strict-Transport-Security` (1 year + subdomains), and a strict `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` for the JSON API.

**End-to-end verified — PRD 14.3 / 14.4 / 15.3 acceptance:**

```
Encryption:
  PUT /api/employees/<aliya> { ..., "nationalId": "AZE-7E7E7E7E" }
  GET /api/employees/<aliya>                 → "nationalId": "AZE-7E7E7E7E"  (plaintext to caller)
  SELECT national_id FROM core_hr.employee   → "enc:v1:JSw8jJPnLYMKe7UbL..."  (ciphertext at rest)

Partitioning:
  Flyway V26 → 16 monthly partitions + DEFAULT created
  SELECT count(*) FROM audit.audit_log_2026_05   → 114    (auto-routed to current-month partition)
  SELECT count(*) FROM audit.audit_log_2026_06   → 0      (next month standing ready)

Lockout:
  5 × bad password for 'admin'           → each returns HTTP 400 "Invalid username or password"
  6th attempt                            → HTTP 429, Retry-After: 899, body:
                                            "Account is temporarily locked. Try again in 899 seconds."
  Correct password while locked          → still HTTP 429 (must wait out the lockout)
  Audit log SECURITY/Login rows          → 5 FAILURE + LOCKED rows, plus the SUCCESS that followed unlock

Security headers (response to login attempt):
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
  Strict-Transport-Security: max-age=31536000 ; includeSubDomains
```

### Milestone 19 — Keycloak OIDC swap (PRD 14.6)

Replaces the milestone-1 in-memory user store + locally-signed JWTs with
real OIDC against Keycloak. The `employee.username` ↔ `preferred_username`
seam that landed in milestone 15 means everything below the auth boundary
(EmployeeContextService, audit actor, RBAC) carries over unchanged.

**Keycloak service** (`docker-compose.yml`):
- `quay.io/keycloak/keycloak:24.0.5` in `start-dev` mode, port `8090`, H2-backed.
- Realm + client + roles + users provisioned from `keycloak/realm-millers-hcm.json` on first boot (`--import-realm`).
- Realm config: `sslRequired: none` (dev), `bruteForceProtected: true`, `failureFactor: 5`, `waitIncrementSeconds: 900` — the same 5-in-15 lockout shape we previously enforced in `LoginAttemptService`.
- Public client `hcm-web` with PKCE `S256`, standard flow, redirect URIs `http://localhost:5180/*` and `http://127.0.0.1:5180/*`.

**Backend** (Spring Security OAuth2 Resource Server):
- Dependency swap: out goes `io.jsonwebtoken:jjwt-*` + the in-memory `UserDetailsManager`, in comes `spring-boot-starter-oauth2-resource-server`.
- `application.yml` now points at `spring.security.oauth2.resourceserver.jwt.issuer-uri = http://localhost:8090/realms/millers-hcm`. Spring fetches the realm's RSA public keys from the JWKS endpoint on startup and validates every bearer against it.
- `SecurityConfig.jwtAuthenticationConverter()` maps `realm_access.roles` → `ROLE_*` authorities so existing `@PreAuthorize("hasRole('HR_ADMIN')")` annotations keep working, and pins `setPrincipalClaimName("preferred_username")` so `Authentication.getName()` returns "admin" / "hrspec" / "employee" (preserving the join to `core_hr.employee.username`).
- Retired files: `JwtService`, `JwtAuthFilter`, `JwtProperties`, `LoginAttemptService`, `AccountLockedException`, `LoginRequest`, `LoginResponse`. `AuthController.login` is gone; `AuthController.me` returns the username + roles + `email`/`name`/`issuedAt`/`expiresAt` extracted from the OIDC bearer.
- `TokenAcceptedAuditListener` (new) listens on Spring's `AuthenticationSuccessEvent`, dedupes by `jti`, and writes one `SECURITY/Login/TOKEN_ACCEPTED` audit row per JWT — so the API-side audit log keeps a per-session breadcrumb even though `LOGIN_SUCCESS`/`LOGIN_FAILURE` events themselves now live in Keycloak's event log.

**Frontend** (`web/`):
- New `keycloak-js@25` SDK + `src/auth/keycloak.ts` singleton (PKCE S256, `check-sso` onLoad mode, silent SSO via `public/silent-check-sso.html`).
- `AuthProvider` initialises Keycloak on boot, mirrors `keycloak.token` into `localStorage` so the existing axios interceptor keeps adding the `Bearer` header unchanged, and refreshes the token every 30 s with a 60 s leeway (`keycloak.updateToken(60)`).
- `RequireAuth` redirects unauthenticated users straight to `keycloak.login()` instead of an in-app form. The previous `/login` route + `LoginPage.tsx` are deleted.
- `logout()` calls `keycloak.logout({ redirectUri })` so the realm session is ended (not just the SPA's localStorage).
- Axios 401 interceptor now triggers `keycloak.login({ redirectUri: currentHref })` so expired tokens land users back on their exact route post-login.

**End-to-end verified — PRD 14.6 acceptance:**

```
Keycloak realm import:
  GET  http://localhost:8090/realms/millers-hcm/.well-known/openid-configuration → 200
  Realm settings:  bruteForceProtected=true  failureFactor=5
                   waitIncrementSeconds=900  sslRequired=none

Token issuance (Direct Access Grants, used here only for verification):
  POST /realms/millers-hcm/protocol/openid-connect/token
       grant_type=password client_id=hcm-web username=admin password=admin123
  → access_token (RS256, kid matches realm JWKS)
       preferred_username=admin
       realm_access.roles=[HR_ADMIN, SYSTEM_ADMIN]
       email=admin@millers.example  exp=now+3600s

API surface (backend at :8082, validating against Keycloak issuer):
  GET /api/auth/me  (admin token)   → {username:"admin",
                                       roles:["ROLE_HR_ADMIN","ROLE_SYSTEM_ADMIN"],
                                       email:"admin@millers.example", ...}
  GET /api/employees  (admin token)     → 200 totalElements=3
  GET /api/employees  (employee token)  → 403 (RBAC enforced — EMPLOYEE lacks ROLE_HR_*)
  GET /api/self/employee (employee token) → Aliya EMP-00001
        (preferred_username "employee" joined to core_hr.employee.username
         via the seam that landed in milestone 15)
  GET /api/auth/me  (bogus.bearer.token)  → 401

Brute-force protection (Keycloak realm):
  6 × wrong password for hrspec   → 6 × HTTP 401 "Invalid user credentials"
  Brute-force REST view for user  → numFailures=2, disabled=true
  Correct password mid-lockout    → HTTP 401 (account locked)
  Admin REST DELETE attack-detection/brute-force/users/{id} → 204
  Correct password post-unlock    → 200 + access_token

Audit row continuity:
  audit.audit_log (SECURITY/Login/TOKEN_ACCEPTED) rows for admin / hrspec /
  employee — one per JWT, dedup'd by jti, with {issuer, jti, expiresAt,
  roles} captured in new_value JSON. Joins cleanly to subsequent rows that
  the same user generates via their actions (employees CRUD, leave submit,
  etc.) because actor/preferred_username carry through.

SPA boot:
  http://localhost:5180/                                  → 200 (HTML)
  http://localhost:5180/src/auth/keycloak.ts              → 200 (module)
  http://localhost:5180/silent-check-sso.html             → 200 (helper)
  http://localhost:5180/api/auth/me  (no token)           → 401
       (Vite dev proxy → backend → OIDC reject)
```

### Milestone 20 — Email delivery of scheduled reports (PRD 10.5)

Lights up the `recipients` column on `report_schedule` that Milestone 17
captured but didn't wire — scheduled runs now also email the rendered
file to the configured recipients, with delivery state tracked per run
and a "Resend" action for failure recovery.

**Infra**:
- `mailhog/mailhog:latest` added to `docker-compose.yml` (SMTP `:1025`,
  web UI `:8025`). Captures everything we send so dev sessions never hit
  real inboxes. Production swap is one env-var: point `HCM_MAIL_HOST` at
  SES / SendGrid / Mailgun / Office365.
- `spring-boot-starter-mail` brings `JavaMailSender` + `MimeMessageHelper`.

**Backend**:
- New `email` package with `EmailService.sendReport(to, subject, htmlBody,
  attachmentName, bytes, contentType)` wrapping `JavaMailSender` —
  HTML body, file attachment, configurable From address (`hcm.mail.from`
  / `hcm.mail.from-name`), 5-second connect/read/write timeouts so SMTP
  outages fail fast instead of stalling a request thread.
- SMTP failures wrap as `EmailDeliveryException` (checked) so the caller
  records `email_status=FAILED` without unwinding the surrounding
  transaction.
- **V27** migration: adds `email_status`, `email_recipients`,
  `email_sent_at`, `email_error` to `reporting.report_run` + a CHECK
  constraint pinning the four allowed states (`NOT_REQUESTED`,
  `SKIPPED`, `SENT`, `FAILED`) and a partial index on
  `(email_status) WHERE status IN ('FAILED','SENT')` for run-history
  scans.
- `ReportRunService.runAdhoc` now, on a `TriggerSource.SCHEDULED` run,
  fetches the schedule's recipients, builds the email body with a small
  table of run metadata (run #, type, format, cron, finished_at, size),
  hands the in-memory payload to `EmailService.sendReport`, and writes
  the outcome onto the same `report_run` row. Manual `runDefinition`
  calls stay opt-out (no recipients → no email).
- New `ReportRunService.resendEmail(runId)` re-attempts delivery for a
  `SUCCESS` run by streaming the file back from MinIO via
  `AttachmentService.download(...)` and routing through the same code
  path. Exposed as `POST /api/reports/runs/{id}/resend-email`
  (HR_ADMIN / SYSTEM_ADMIN).
- Every email outcome writes an audit row:
  `REPORTING/ReportRun/EMAIL_SENT` (with recipients + subject),
  `EMAIL_SKIPPED` (with `reason=no_recipients`), or `EMAIL_FAILED`
  (with the underlying SMTP error).

**Frontend**:
- New `Email` column on the Run history tab. Coloured tag — Sent
  (green), Failed (red), Skipped (orange), `—` (default for non-scheduled
  runs). Tooltip carries the full recipients string, `email_sent_at`,
  or the SMTP error.
- New `Resend` button on rows where `status=SUCCESS && scheduleId &&
  emailStatus ∈ {FAILED, SENT}`. HR_ADMIN / SYSTEM_ADMIN only — guarded
  by `useAuth().hasRole(...)` so the EMPLOYEE view simply doesn't
  render the button.

**End-to-end verified — PRD 10.5 acceptance**:

```
RSC-00001 updated with recipients
  PUT /api/reports/schedules/<id>  recipients="hr-distribution@millers.example, ceo@millers.example"

Force-run via POST /api/reports/schedules/<id>/run-now
  → RPT-00004  status=SUCCESS  size=4170 bytes
  → email_status=SENT
  → email_recipients="hr-distribution@millers.example,ceo@millers.example"
  → MailHog inbox count=1
       From:    Millers HCM <no-reply@millers.example>
       To:      hr-distribution@millers.example, ceo@millers.example
       Subject: Test cron fire — HEADCOUNT
       Attachment: headcount-20260521-150331.xlsx  application/vnd.openxmlformats-...spreadsheetml.sheet

Byte-for-byte attachment check:
  SHA256 of MailHog attachment       = 562d67...c81642
  SHA256 of MinIO-stored attachment  = 562d67...c81642   ✓ identical (4170 B, both valid OOXML)

Skipped path (schedule with no recipients):
  → RPT-00005  email_status=SKIPPED  email_error="No recipients configured on schedule"

Failure path (MailHog stopped before run):
  → RPT-00006  status=SUCCESS  email_status=FAILED
     email_error="Mail server connection failed. ... Couldn't connect to host, port: localhost, 1025;
                  timeout 5000; ... Connection refused"
     (Run row still SUCCESS — the file is in MinIO, only delivery failed.)

Resend recovery (MailHog restarted, POST /api/reports/runs/<id>/resend-email):
  → RPT-00006 row now: email_status=SENT  email_sent_at=2026-05-21T15:04:57Z
  → MailHog inbox count=1
```

### Milestone 21 — Attachment uploader on Leave/Permission/BT/Course

Closes the gap left at the end of Milestone 16: the reusable
`<AttachmentUploader />` now drops into the four remaining surfaces.
The backend was already owner-blind so no API or schema change was
needed — only a UI rollout.

**Surface choices**:
- **Leave / Permission / Business Trip** — these modules have list +
  form pages but no dedicated detail page. Rather than scaffolding
  three fresh routes, each list row exposes a `Files` action button
  (with the `PaperClipOutlined` icon). Clicking it opens a right-side
  `<Drawer width={560}>` hosting the `<AttachmentUploader />`,
  scoped to `(MODULE, EntityName, rowId)`. If those modules later grow
  real detail pages, the Drawer's content moves over verbatim.
- **Course** — `CourseDetailPage` already exists, so the uploader
  lands inline as a `Course material` card between the markdown body
  and the quiz questions. Owner pair is `(LEARNING, Course, courseId)`.

**Owner identifiers** (mirror backend audit `MODULE` + JPA entity class):

| Module      | ownerModule    | ownerEntity            |
|-------------|----------------|------------------------|
| Leave       | `LEAVE`         | `LeaveRequest`         |
| Permission  | `PERMISSION`    | `PermissionRequest`    |
| Business Trip | `BUSINESS_TRIP` | `BusinessTripRequest` |
| Course      | `LEARNING`      | `Course`               |

**End-to-end verified** (using the existing `/api/attachments` endpoints
that the uploader calls):

```
employee uploads 36-byte receipt.txt for BT-00001
  POST /api/attachments  (multipart)
       ownerModule=BUSINESS_TRIP ownerEntity=BusinessTripRequest ownerId=<btId>
  → ATT-00008
       objectKey = business_trip/businesstriprequest/<btId>/<uuid>-receipt.txt
       uploadedBy = "employee"  (Keycloak preferred_username carrying through)

admin GET /api/attachments?ownerModule=BUSINESS_TRIP&ownerEntity=BusinessTripRequest&ownerId=<btId>
  → 1 row, the just-uploaded ATT-00008

admin GET /api/attachments/<ATT_ID>/download  → HTTP 200, 36 B (byte-perfect)
employee DELETE /api/attachments/<ATT_ID>     → HTTP 403  (RBAC)
admin DELETE /api/attachments/<ATT_ID>        → HTTP 200, soft-deleted +
                                                 physically removed from MinIO
follow-up GET                                  → []  (no remaining attachments)

audit.audit_log: ATTACHMENT/UPLOAD by 'employee' + ATTACHMENT/DELETE by 'admin'
both captured against the same attachment id.
```

### Milestone 22 — ABAC enforcement (PRD 14.9)

Closes the "ABAC not enforced" shortcut carried since Milestone 1. Until
now, `@PreAuthorize` annotations gated access purely by role —
`DEPARTMENT_MANAGER` was already wired through the role list but
treated identically to `HR_ADMIN` for read access. This milestone
introduces row-level scoping so a department manager sees only their
reporting chain.

**Primitive: `AccessScopeService`**
- Resolves the current user's effective scope to an
  `Optional<Set<UUID>>`: empty = unrestricted (HR_ADMIN / HR_SPECIALIST
  / SYSTEM_ADMIN / AUDITOR), present-with-set = scoped.
- Manager scope is the *transitive* set of their reporting chain plus
  themselves, computed via a recursive CTE on
  `core_hr.employee.manager_id` (`EmployeeRepository.descendantsIncluding`).
- Employee scope is just `{self.id}`.
- Wide roles win over `DEPARTMENT_MANAGER` — an HR-person who also
  happens to line-manage someone doesn't lose their HR view.
- Helpers: `isAccessible(id)`, `scopeOrNullForCurrentUser()`,
  `intersect(requestedIds)`.

**Wired into 5 list endpoints + 1 single-record path**:

| Endpoint                          | Scope filter applied via                              |
|-----------------------------------|--------------------------------------------------------|
| `GET /api/employees`              | `findByIdIn` / `findByIdInAndEmploymentStatus` / `findByIdInAndNameContaining` |
| `GET /api/employees/{id}`         | `accessScope.isAccessible(id)` → 404 if not in scope (avoids leaking row existence) |
| `GET /api/leave/requests`         | `findByEmployeeIdIn[AndStatus]OrderByStartDateDesc`    |
| `GET /api/permission/requests`    | `findByEmployeeIdIn[AndStatus]OrderByPermissionDateDesc` |
| `GET /api/business-trips`         | `findByEmployeeIdIn[AndStatus]OrderByStartDateDesc`    |
| `GET /api/timesheets`             | `findByPeriodYearAndPeriodMonthAndEmployeeIdInOrderByEmployeeIdAsc` |

The pattern in each service is the same three-branch decision:
- `scope == null` (unrestricted) → legacy code path
- `scope.isEmpty()` → return empty page (caller has no allowed ids)
- `scope.nonEmpty()` → use the `EmployeeIdIn(scope, ...)` repo method;
  if an explicit `employeeId` filter was supplied, refuse it when not
  in scope so a caller can't bypass via `?employeeId=<other-emp-id>`.

**Test data**:
- SQL: `UPDATE core_hr.employee SET manager_id = <Rashad.id> WHERE employee_no = 'EMP-00001'` and `SET username = 'manager' WHERE employee_no = 'EMP-00002'`. So Aliya now reports to Rashad, and Rashad's `username` maps to the new Keycloak `manager` account.
- Keycloak realm JSON adds a `manager / manager123` user with realm roles `DEPARTMENT_MANAGER` + `EMPLOYEE`.

**End-to-end verified — PRD 14.9 acceptance:**

```
Demo population:
  EMP-00001 Aliya Mammadova  (ON_PROBATION)   manager_id = EMP-00002.id
  EMP-00002 Rashad Aliyev    (ACTIVE)         username='manager'
  EMP-00003 Nigar Hasanli    (TERMINATED)     no manager — orphan w.r.t. Rashad

GET /api/employees:
  admin    → 3 rows (Rashad, Nigar, Aliya)
  manager  → 2 rows (Rashad himself + Aliya — Nigar correctly hidden)
  employee → HTTP 403  (EMPLOYEE has never been allowed on /api/employees;
                         the SPA's My Workspace uses /api/self/*)

GET /api/employees/{NIGAR_ID}:
  admin    → 200 OK
  manager  → 404 Not Found   (not in scope — surfaced as 404 not 403 so
                              we don't leak the row's existence)
GET /api/employees/{ALIYA_ID}:
  manager  → 200 OK          (direct report)

GET /api/leave/requests:
  admin    → 2 rows (both Aliya's)
  manager  → 2 rows (both Aliya's)        [Rashad has none; Aliya is in scope]

GET /api/permission/requests:                            ← clearest signal
  admin    → 2 rows  (1 Aliya's + 1 Nigar's)
  manager  → 1 row   (only Aliya's; Nigar's correctly filtered out)

GET /api/business-trips:
  admin    → 1 row (Aliya's)
  manager  → 1 row (Aliya's)

GET /api/timesheets?year=2026&month=5:
  admin    → 1 row (Aliya's)
  manager  → 1 row (Aliya's)

Bypass attempt — manager queries with explicit Nigar id:
  GET /api/leave/requests?employeeId=<NIGAR_ID>  with manager token
  → totalElements = 0   (scope filter forces empty when requested id is
                          outside the caller's allowed set)

Regression: /api/self/employee still resolves for EMPLOYEE role
  → EMP-00001 Aliya Mammadova  (username='employee')
```

### Milestone 23 — Keycloak hardening (PRD 14.6)

Closes three of the "Keycloak runs `start-dev` against H2…" shortcuts
that landed in milestone 19:

**Postgres backend**:
- New `hcm-keycloak-pg` container (`postgres:16-alpine`, port `5434`,
  dedicated `hcm-keycloak-pgdata` volume) holds all of Keycloak's auth
  state — realms, users, sessions, signing keys.
- Kept separate from `hcm-postgres` so the IdP and the HCM data plane
  have independent backup / retention / scaling decisions.
- On restart, the realm + users + brute-force counters all survive
  (in milestone 19's H2 setup, a `docker compose rm keycloak` wiped
  everything because the volume only mirrored `/opt/keycloak/data`).

**Production `start` mode** (was `start-dev`):
- `command: ["start", "--import-realm", "--http-port=8090"]`. The image
  auto-runs the Quarkus build phase when `KC_DB`, `KC_HOSTNAME_URL`,
  etc. change.
- `KC_HOSTNAME_URL=http://localhost:8090` ensures advertised OIDC
  endpoints carry the port — without this, Keycloak emits
  `http://localhost/realms/…` and the Spring resource server rejects
  every token for issuer mismatch.
- `KC_HEALTH_ENABLED=true` exposes `/health/ready` and `/health/live`
  on the HTTP port — the docker-compose healthcheck probes the ready
  endpoint instead of poking the discovery doc.

**Realm-level password policy**:
- `length(12) and digits(1) and upperCase(1) and lowerCase(1) and
  specialChars(1) and notUsername(undefined) and passwordHistory(3)`.
- Enforced on every password change — `kcadm.sh set-password
  --new-password weak` returns *"Invalid password: must contain at
  least 1 special characters"*.
- Demo user credentials updated to comply (see Demo accounts table
  above).

**TOTP / MFA path**:
- Realm OTP policy configured (`totp`, 6 digits, HMAC-SHA1, 30 s
  period, 1-step look-ahead window — Keycloak defaults).
- New `mfauser` account ships with `requiredActions:
  ["CONFIGURE_TOTP"]` — Direct Access Grants refuses to issue a token
  ("Account is not fully set up") until enrollment is completed via
  Keycloak's hosted login page. The enrollment flow shows a QR code +
  one-time secret for any compatible authenticator (Google Auth, 1Password,
  Authy, etc.).

**`sslRequired` is env-substitutable**:
- Realm JSON now reads `"sslRequired":
  "${env.KC_REALM_SSL_REQUIRED:none}"`. Dev keeps `none` (no TLS
  terminator on localhost); production flips
  `KC_REALM_SSL_REQUIRED=external` without editing the JSON.

**End-to-end verified — PRD 14.6 hardening acceptance:**

```
Persistence: realm survives docker compose restart keycloak
  → hcm-keycloak-pg contains 'realm' row (millers-hcm), 'client'
    (hcm-web), 'user_entity' × 5, 'credential' rows hashed.

Production start mode:
  GET /health/ready → HTTP 200            (only exposed in start mode)
  Discovery issuer = http://localhost:8090/realms/millers-hcm  ✓

Token issuance with new compliant passwords:
  admin     → HTTP 200, JWT issued
  hrspec    → HTTP 200, JWT issued
  employee  → HTTP 200, JWT issued
  manager   → HTTP 200, JWT issued

Password policy:
  kcadm.sh set-password ... --new-password weak
  → Invalid password: must contain at least 1 special characters.
                                    [invalidPasswordMinSpecialCharsMessage]

MFA gating:
  Direct grant for mfauser (correct password)
  → HTTP 400  "Account is not fully set up"
  (CONFIGURE_TOTP required action blocks token issuance — user must
   complete browser-flow enrollment first.)

Backend round-trip — Spring resource server validates against the new
Postgres-backed Keycloak:
  GET /api/auth/me  (admin token)
  → {username: "admin", roles: [ROLE_HR_ADMIN, ROLE_SYSTEM_ADMIN], …}

ABAC regression (Milestone 22):
  GET /api/permission/requests
  → admin   total=2  (Aliya's + Nigar's)
  → manager total=1  (only Aliya's — Nigar correctly filtered)
```

**Remaining for true production**: terminate TLS at a reverse proxy
(nginx / Traefik / AWS ALB), point `KC_HOSTNAME` at the public DNS
name, drop `KC_HTTP_ENABLED`, set `KC_REALM_SSL_REQUIRED=external`,
swap the master-realm `admin/admin` for a strong bootstrap credential
and rotate it, layer rate-limiting at the proxy, configure
`KC_LOG_LEVEL=warn`, externalise the Postgres backend to a managed
service.

### Milestone 24 — Extend ABAC (PRD 14.9)

Two follow-ups from Milestone 22's deferred list — closing the
single-record visibility hole on the request modules, and adding
scope-filtering to the workflow inbox so a department manager doesn't
see approval items routed to them for employees they don't own.

**Single-record scope checks** on the request modules:
- `LeaveRequestService.get(id)`, `PermissionRequestService.get(id)`,
  `BusinessTripService.get(id)`, `TimesheetService.get(id)` now resolve
  the row, then check `accessScope.isAccessible(row.employeeId)`.
- Out-of-scope rows surface as **HTTP 404** (not 403) — same pattern as
  Employee `/{id}` from M22, so an enumeration attempt can't even
  confirm the row exists.

**Workflow inbox scope filter**:
- New `security.scope.WorkflowSubjectResolver` registry resolves a
  `(subjectEntity, subjectId)` pair to the owning `employeeId` for the
  7 employee-scoped subjects: `LeaveRequest`, `PermissionRequest`,
  `BusinessTripRequest`, `Timesheet`, `TerminationRequest`,
  `ContractChange`, `PerformanceReview`. Org-wide / batch subjects
  (`OrgVersion`, `PayrollRun`) deliberately have no resolver — the
  role gate already restricts them to HR/admin.
- `AccessScopeService.isWorkflowSubjectAccessible(entity, subjectIdStr)`
  composes the resolver lookup with the existing scope rules:
  unrestricted callers pass; non-employee-scoped subjects pass; for
  employee-scoped subjects, the resolved `employeeId` must be in the
  caller's scope set.
- `WorkflowService.inboxFor(roles)` now post-filters candidate
  instances through `isWorkflowSubjectAccessible`. The
  role-membership query is unchanged, so the per-row cost is only
  paid for scoped callers (managers / employees).
- Subjects whose row can't be resolved (subject deleted, malformed
  id) are hidden — err on the side of less leakage.

**End-to-end verified — PRD 14.9 extended acceptance:**

```
Single-record scope checks:
  admin    GET /api/permission/requests/<aliya-id>   → 200
  admin    GET /api/permission/requests/<nigar-id>   → 200
  manager  GET /api/permission/requests/<aliya-id>   → 200   (direct report)
  manager  GET /api/permission/requests/<nigar-id>   → 404   (not in scope —
                                                              hidden, not 403)
  employee GET /api/permission/requests/<nigar-id>   → 404   (not self)

Workflow inbox scope filter:
  Pre-condition (test data): 2 pending workflow instances re-routed to
                             ROLE_DEPARTMENT_MANAGER as approver_role —
                             one over Aliya's request, one over Nigar's.

  /api/workflow/inbox  as admin    → count=0
       (admin doesn't have DEPARTMENT_MANAGER — role filter excludes;
        scope filter never runs.)
  /api/workflow/inbox  as manager  → count=1
       Only the instance over Aliya's request appears (subject_id matches
       Aliya, EMP-00001, who reports to Rashad).
       Nigar's instance is correctly filtered out by the scope check.

  /api/workflow/inbox  as hrspec   → count=1
       Sees the unrelated HR_SPECIALIST-routed LeaveRequest. Unrestricted
       caller — scope filter is a no-op.

Implication: before this milestone, any DEPARTMENT_MANAGER who got
listed as an approver via the workflow definition would have seen
approval items for every employee at that step — full information
leakage across departments. Now they see only the items for their
reporting chain.
```

### Milestone 25 — ABAC on reporting aggregations (PRD 14.9 / Section 10)

Closes the largest remaining ABAC gap: a `DEPARTMENT_MANAGER` calling
the 8 cross-module reports previously got full-org totals. Now each
aggregation is narrowed to the caller's accessible employee set.

**Approach**: `ReportService` gets `AccessScopeService` injected. Two
helpers — `scopeClause(scope, empColumn)` returning
`" AND <col> IN (:scopeIds) "` (or `""`) and `withScope(params, scope)`
binding the set — are woven into every aggregation query. The pattern
is the same for each report:

```java
Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
if (scope != null && scope.isEmpty()) return emptyReportDto();
// otherwise the SQL fragments + bound parameter include the scope predicate.
```

**Per-report scoping anchor column:**

| Report | Anchored on | Behaviour for `DEPARTMENT_MANAGER` |
|---|---|---|
| Headcount | `core_hr.employee.id`, `lifecycle.termination_request.employee_id` | Total counts + by-status + by-dept + 12-month trend all narrowed |
| Attrition | `lifecycle.termination_request.employee_id` (+ `core_hr.employee.id` for active count) | Terminations in scope only |
| Leave usage | `leave_mgmt.leave_balance.employee_id` (on the JOIN's ON clause for the per-type aggregate, on the WHERE for the at-risk lists) | Per-type sums + overdrawn + high-balance restricted to scope |
| Attendance | `attendance.daily_summary.employee_id` | Per-employee rows + roll-up totals narrowed |
| Training compliance | `learning.enrollment.employee_id` (on the LEFT JOIN ON for per-course; on the WHERE for non-compliant CROSS JOIN) | Active count + per-course completion + non-compliant list scoped |
| Performance distribution | `performance.performance_review.employee_id` | Cycle list stays org-wide (metadata); per-cycle stats / distribution / recommendations scoped |
| Payroll summary | n/a — `payroll_run` has no employee anchor | **Scoped callers see empty** (per-team view is a separate aggregation against `payroll_result` — deferred). Controller still role-gates `DEPARTMENT_MANAGER` out, so the service-level empty path is defence-in-depth. |
| Recruitment funnel | n/a — vacancies/candidates have no employee anchor | **Scoped callers see empty**, role-gated out at the controller |

**Controller role gates**: `DEPARTMENT_MANAGER` was added to
`@PreAuthorize` on `/attrition`, `/training`, `/performance` so the
scope filter actually has a chance to run. Already had it on
`/headcount`, `/leave`, `/attendance`. `/payroll-summary` and
`/recruitment` deliberately do *not* include it.

**End-to-end verified** — most visible on the two reports that surface
the terminated employee (Nigar) who's outside the manager's scope:

```
GET /api/reports/headcount
  admin    → total=3 active=1 probation=1 terminatedYtd=1 attr=33.33%
            byDept=[(Finance, 1), (Engineering, 1)]
  manager  → total=2 active=1 probation=1 terminatedYtd=0 attr=0.00%
            byDept=[(Finance, 1), (Engineering, 1)]
                                                ↑ Nigar (terminated) excluded

GET /api/reports/attrition
  admin    → REDUNDANCY:1, rate=33.33%, settlementPayout=0.0
  manager  → byReason=[], rate=0%, settlementPayout=0
                  ↑ Nigar's termination correctly filtered out

GET /api/reports/leave / training / performance
  admin    ≡  manager  (only data is for Aliya, who is in both scopes —
                        the filter applied but yields the same result)

GET /api/reports/payroll-summary
  admin    → grossYtd=3049.22, runs=2
  manager  → HTTP 403  (role gate; service-level scoped-empty is
                        defence-in-depth)

GET /api/reports/recruitment
  admin    → open=0, candidates=2, avgTtH=11.15 days
  manager  → HTTP 403  (same — role-gated)
```

**Security framing**: before this milestone, any
`DEPARTMENT_MANAGER` calling the 6 employee-anchored reports got
org-wide aggregates — they could see total headcount across the
company, all departments' attrition, every employee's leave-balance
risk, etc. Now they see only their reporting chain — matching the
list-endpoint + single-record + workflow-inbox guarantees from
milestones 22 and 24.

### Milestone 26 — Org-unit-based ABAC scoping (PRD 14.9)

Complements milestone 22's manager-chain scoping with an **org-unit
anchor** so HR specialists can be scoped to a region / division /
department. Same `AccessScopeService` resolves both shapes — no
separate mechanism.

**Schema (V28)**:
- New `core_hr.employee.scope_org_unit_id UUID NULL` with FK to
  `organization.org_unit(id)` and a partial index on non-null values.
- When set, the linked Keycloak user is scoped to the descendants of
  this org_unit instead of the wide HR_SPECIALIST default.

**Repositories**:
- `OrgUnitRepository.descendantUnitIds(root)` — recursive CTE on
  `organization.org_unit.parent_id`. Mirrors the
  `EmployeeRepository.descendantsIncluding` pattern.
- `EmployeeRepository.findIdsByOrgUnitIdIn(unitIds)` — materialises
  the employee set for the unit subtree.

**`AccessScopeService` refactor**:
- `HR_SPECIALIST` moved out of `WIDE_ROLES`. New branch (first match
  wins, after SYSTEM_ADMIN / HR_ADMIN / AUDITOR):
  - If caller has `HR_SPECIALIST` + is linked to an Employee + that
    employee has `scope_org_unit_id` set → org-unit scope.
  - Else `HR_SPECIALIST` falls through to unrestricted (back-compat —
    the seeded `hrspec` user without an employee link keeps its wide
    view of milestones 19-25 verifications).
- The two scoping dimensions can coexist on one user (e.g. an
  HR-specialist who's also a department manager), but the HR
  precedence wins, mirroring the M22 design.

**Demo org tree** (seeded for verification):

```
Millers 2.0           COMPANY
└── Operations        DIVISION
    ├── Finance       DEPARTMENT   (EMP-00002 Rashad)
    └── Engineering   DEPARTMENT   (EMP-00001 Aliya, EMP-00004 Sara HR)
```

- `EMP-00004 Sara HR` (new) — `username='hrspec'`,
  `org_unit_id=Engineering`, `scope_org_unit_id=Engineering`. So the
  Keycloak `hrspec` user is now scoped to Engineering's subtree.
- Existing `manager` user (Rashad) remains DEPARTMENT_MANAGER scoped
  via the M22 manager-chain rule.

**End-to-end verified — PRD 14.9 acceptance:**

```
GET /api/employees:
  admin   → 4 rows (Rashad/Finance, Sara/Eng, Nigar/—, Aliya/Eng)
  hrspec  → 2 rows (Sara, Aliya — both Engineering)        ← org-unit scope
                    Rashad (Finance) and Nigar (no unit) correctly hidden
  manager → 2 rows (Rashad self, Aliya report)             ← manager-chain scope

Single-record GET /api/employees/{id} as hrspec:
  Aliya  (Engineering)   → 200
  Rashad (Finance)       → 404   (out of scope — surfaced as 404 not 403)

Reports as hrspec — Headcount narrows to Engineering subtree:
  total=2 active=1 probation=1 terminatedYtd=0
  byDept=[(Engineering, 2)]
  (Finance and the terminated/unassigned Nigar both excluded)
```

The org-unit and manager-chain scopes compose cleanly with every
downstream surface added in M22 / M24 / M25 — list endpoints,
single-record fetches, the workflow inbox, and the 6 employee-anchored
reports all narrow correctly for either scope shape.

### Milestone 27 — Per-team payroll view (PRD 14.9 / Section 10)

Closes the largest remaining ABAC gap on reporting: the
`/api/reports/payroll-summary` endpoint used to return an empty DTO
for scoped callers (M25) because `payroll.payroll_run` is run-level
totals with no employee anchor. Now scoped callers get a real
per-team view re-aggregated from `payroll.payroll_result`.

**Service path**:
- Unrestricted callers (HR_ADMIN / SYSTEM_ADMIN / AUDITOR / FINANCE_USER
  / unscoped HR_SPECIALIST) → existing fast path that reads pre-computed
  `payroll_run` totals.
- Scoped callers (DEPARTMENT_MANAGER, org-unit-scoped HR_SPECIALIST) →
  new `payrollSummaryScoped` re-aggregates from `payroll_result` filtered
  by `employee_id IN (:scopeIds)`. SQL: `LEFT JOIN payroll_result pr ON pr.run_id = r.id AND pr.employee_id IN (:scopeIds) GROUP BY r.id HAVING COUNT(pr.id) > 0`.
  The `HAVING > 0` predicate hides runs where the scoped team had no
  payroll_result — otherwise the manager would see every org-wide run
  with all-zero totals, which is misleading.
- Empty scope (caller has a scope shape but no allowed employees) →
  returns the same empty DTO as M25.

**Controller role gate**: added `DEPARTMENT_MANAGER` to the
`@PreAuthorize` on `/payroll-summary`. EMPLOYEE deliberately stays
excluded — employees view their own payroll via `/api/self/payslips`,
not this org-level report.

**End-to-end verified — PRD 14.9 acceptance:**

```
Data: PR-00001 (May 2026, PAID, 1 payroll_result for Aliya — gross
3049.22 / net 2560.38 / tax 129.92 / deductions 358.92).
      PR-00002 (Aug 2026, DRAFT, no results yet).

GET /api/reports/payroll-summary?year=2026:
  admin     →  grossYtd=3049.22 netYtd=2560.38 runs=2
                PR-00001  PAID   gross=3049.22 net=2560.38 emps=1
                PR-00002  DRAFT  gross=0       net=0       emps=0
                (unrestricted — reads from payroll_run totals)

  hrspec    →  grossYtd=3049.22 netYtd=2560.38 runs=1
                PR-00001  PAID   gross=3049.22 net=2560.38 emps=1
                (org-unit scope = Engineering; Aliya is in Engineering;
                 PR-00002 hidden by HAVING > 0 — no scoped results yet)

  manager   →  grossYtd=3049.22 netYtd=2560.38 runs=1
                PR-00001  PAID   gross=3049.22 net=2560.38 emps=1
                (manager-chain scope = Rashad+Aliya; Aliya's payroll
                 visible. Same numbers as hrspec because Aliya is in
                 both scopes — different scope shapes, same membership)

  employee  →  HTTP 403
                (EMPLOYEE deliberately excluded; use /api/self/payslips)
```

The composition story is now complete: both ABAC shapes (manager-chain
from M22 and org-unit subtree from M26) feed through the same
`AccessScopeService.scopeOrNullForCurrentUser()`, and Payroll Summary
joins the other 5 employee-anchored reports in respecting them.

### Milestone 28 — TLS terminator in front of Keycloak (PRD 14.6)

Adds a real **nginx reverse proxy** that terminates TLS in front of
Keycloak, mirroring the production deployment pattern (nginx /
Traefik / cloud LB → Keycloak). For local dev a self-signed cert is
committed under `nginx/certs/` (with `localhost` + `127.0.0.1` SANs);
production would swap in a Let's Encrypt or corporate-CA cert.

**Stack additions**:
- `nginx/certs/localhost.{crt,key}` — 10-year self-signed cert with
  SAN `DNS:localhost, DNS:hcm-keycloak, IP:127.0.0.1, IP:::1`. Header
  string makes it impossible to miss: `O=Millers HCM (dev only — DO
  NOT USE IN PRODUCTION)`.
- `nginx/nginx.conf` — TLS 1.2/1.3, `HIGH:!aNULL:!MD5` ciphers,
  enlarged header buffers (Keycloak session cookies are chunky),
  `X-Forwarded-Proto: https`, `X-Forwarded-Host`, `X-Forwarded-Port:
  8443` set on every upstream request. The `KC_PROXY_HEADERS=xforwarded`
  env on Keycloak (set in M23) makes it honour these.
- `hcm-nginx` service in `docker-compose.yml` (`nginx:1.27-alpine`).
  Ports `8443:443` (HTTPS) and `8444:80` (plain HTTP → 301 to HTTPS).
  Healthcheck probes the discovery doc through the TLS path.

**What you can hit via TLS today**:
```
https://localhost:8443/realms/millers-hcm/.well-known/openid-configuration
https://localhost:8443/realms/millers-hcm/protocol/openid-connect/token
https://localhost:8443/realms/millers-hcm/protocol/openid-connect/auth
https://localhost:8443/admin/                       (Keycloak admin console)
```

The SPA can opt into the HTTPS path with one env var at build/dev time:
```bash
VITE_KEYCLOAK_URL=https://localhost:8443 npm run dev
```
(`keycloak-js` singleton from M19 already reads this env var.)

**Intentional dev-only choice — issuer stays HTTP**:
`KC_HOSTNAME_URL` is still `http://localhost:8090`, so the `iss` claim
in tokens stays HTTP. That keeps the **Spring backend's existing
HTTP path working without touching its Java truststore** — the
backend continues to fetch JWKS via `http://localhost:8090` and
validate tokens whose issuer is `http://localhost:8090/...`. A token
acquired over HTTPS (proxy) is byte-identical to one acquired over
HTTP (direct), because the proxy doesn't rewrite token contents.

**End-to-end verified — PRD 14.6 TLS acceptance:**

```
TLS handshake on :8443:
  Protocol  TLSv1.3
  Cipher    AEAD-AES256-GCM-SHA384
  Subject   O=Millers HCM (dev only — DO NOT USE IN PRODUCTION)/CN=localhost
  SAN       DNS:localhost, DNS:hcm-keycloak, IP:127.0.0.1, IP:::1

Discovery via HTTPS:
  GET https://localhost:8443/realms/millers-hcm/.well-known/openid-configuration
  → HTTP 200, 6046 bytes
    issuer            = http://localhost:8090/realms/millers-hcm   (intentional)
    token_endpoint    = http://localhost:8090/realms/...openid-connect/token
    jwks_uri          = http://localhost:8090/realms/...openid-connect/certs

Token issuance via HTTPS:
  POST https://localhost:8443/realms/millers-hcm/protocol/openid-connect/token
       grant_type=password username=admin password=Admin#Pass123!
  → access_token issued, iss claim = http://localhost:8090/realms/millers-hcm
                          preferred_username = admin

Token round-trip through backend (HTTP path unchanged):
  GET http://localhost:8082/api/auth/me  with that token
  → {username:"admin", roles:[ROLE_HR_ADMIN, ROLE_SYSTEM_ADMIN], …}

HTTP → HTTPS redirect on :8444:
  GET http://localhost:8444/realms/millers-hcm/.well-known/openid-configuration
  → HTTP 301
    Location: https://localhost:8443/realms/millers-hcm/.well-known/openid-configuration

Backend's HTTP loophole on :8090 still reachable (intentional for dev).
```

**Production hardening checklist** (documented, not enforced in dev):

1. **Replace the self-signed cert** with Let's Encrypt or your
   corporate CA. Update `nginx/nginx.conf` to point at the real
   cert files; restart `hcm-nginx`.
2. **Flip the realm via env override**:
   `KC_REALM_SSL_REQUIRED=external` on the `hcm-keycloak` service.
   The realm JSON already substitutes from this env var (M23 wiring).
3. **Close the HTTP loophole** — drop `KC_HTTP_ENABLED=true` and the
   `8090:8090` port mapping on the Keycloak service so only the
   nginx HTTPS listener is publicly reachable.
4. **Move the advertised hostname to HTTPS**:
   `KC_HOSTNAME_URL=https://auth.your-domain.example`. Tokens'
   `iss` claim then matches the HTTPS endpoint.
5. **Update the Spring backend's issuer-uri** to the HTTPS endpoint
   AND ensure the cert chain is in the Java truststore (Let's
   Encrypt root is usually pre-trusted by the JDK; private CAs need
   explicit import via `keytool -importcert` or
   `-Djavax.net.ssl.trustStore=...`).
6. **Add HSTS** (`Strict-Transport-Security max-age=63072000;
   includeSubDomains; preload`) and **OCSP stapling** in nginx.
7. **Pin a stricter cipher suite** (e.g. `TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384`) and disable TLSv1.2 if your clients can all do TLSv1.3.

### Milestone 29 — Slack / Teams delivery of scheduled reports (PRD 10.5)

Webhook transport for scheduled reports, mirroring the M20 email-delivery
pattern. The file itself stays in MinIO — Slack incoming-webhooks don't
accept uploads and Teams cards expect external URLs — so the notification
carries a summary and recipients click through to download from the
attachment registry.

**Schema (V29)**:
- `report_schedule` — adds `webhook_type` (`NONE` / `SLACK` / `TEAMS`,
  CHECK-constrained) + `webhook_url` (TEXT).
- `report_run` — adds `webhook_status`
  (`NOT_REQUESTED` / `SKIPPED` / `SENT` / `FAILED`,
  CHECK-constrained), `webhook_target` (snapshot of "{TYPE} url" at
  delivery time), `webhook_sent_at`, `webhook_error`. Mirrors the
  `email_*` columns from M20 so per-run delivery state surfaces the
  same way for both transports.

**Service** (`notifications.WebhookNotificationService`):
- Single entry point `sendReportNotification(type, url, run, schedule)`,
  POSTs JSON via Java's built-in `java.net.http.HttpClient` (no extra
  dependency). 5 s connect / 10 s request timeout so a stuck webhook
  doesn't block the run.
- **Slack** payload — Block Kit: `text` fallback + header block with
  📊 emoji + section block with mrkdwn fields (Run, Report, Format,
  Size, Finished, Schedule) + context block pointing users at the
  download flow.
- **Teams** payload — legacy O365 Connector MessageCard schema:
  `summary`, `themeColor` (`5B3FE5` — Millers purple), `title`,
  facts list mirroring the Slack fields. Newer Teams Workflow webhooks
  accept Adaptive Cards; MessageCard remains the safe default that
  works with both connector flavours and most channel-bridging tools.
- Failures (HTTP non-2xx, IO error, timeout, bad URL) surface as
  `WebhookDeliveryException` — caught by `ReportRunService` and
  recorded on the run row without rolling back the surrounding
  transaction.

**Wire-up** (`ReportRunService`):
- `runAdhoc` (scheduled trigger) now calls both `deliverEmail` and
  `deliverWebhook` after a successful run. Failures of either are
  independent — one channel can succeed while the other fails.
- New `POST /api/reports/runs/{id}/resend-webhook` mirrors
  `/resend-email`. The summary payload is rebuilt from the persisted
  run + schedule rows, so no MinIO download is needed for the resend.

**Frontend** (`ReportSchedulesPage`):
- Schedule create / edit form gains a webhook channel dropdown
  (`NONE` / `SLACK` / `TEAMS`) + webhook URL field.
- Run history gets a **Webhook** column with the same colour-coded
  tag pattern as the Email column (`Sent` / `Failed` / `Skipped`
  with tooltips carrying target + error / sent-at timestamp).
- Resend button split into two — `Resend email` and `Resend webhook` —
  both shown only for SUCCESS rows that have ever been requested
  (FAILED or SENT).

**End-to-end verified — PRD 10.5 acceptance:**

```
Setup: webhook capture listener (Python http.server on :9999).
Schedule RSC-00003 "Headcount → Slack webhook test":
   reportType=HEADCOUNT  format=PDF
   cron='0 0 6 * * *'   recipients=''  (so email is SKIPPED)
   webhookType=SLACK  webhookUrl=http://localhost:9999/slack

POST /api/reports/schedules/{id}/run-now
 → lastStatus=SUCCESS, run RPT-00007 generated
 → emailStatus=SKIPPED (no recipients)
 → webhookStatus=SENT  webhookTarget="SLACK http://localhost:9999/slack"
 → captured payload at /slack: Block Kit format with text fallback,
   📊 header, fields for Run/Report/Format/Size/Finished/Schedule
   ("text":"Scheduled report ready: Headcount → Slack webhook test
   (RPT-00007, XLSX)", "blocks":[{header}, {section: 6 fields}, {context}])

POST /api/reports/runs/{runId}/resend-webhook
 → webhookStatus=SENT, sentAt updated  (re-fire idempotent)

PUT  /api/reports/schedules/{id}  webhookType=TEAMS  webhookUrl=http://localhost:1/nope
POST /api/reports/runs/{runId}/resend-webhook
 → webhookStatus=FAILED  webhookError="I/O error posting TEAMS webhook: null"
   (connection refused — captured cleanly, run stays SUCCESS)

PUT  /api/reports/schedules/{id}  webhookUrl=http://localhost:9999/teams
POST /api/reports/runs/{runId}/resend-webhook
 → webhookStatus=SENT  webhookTarget="TEAMS http://localhost:9999/teams"
 → captured payload at /teams: MessageCard with title="📊 Headcount …",
   themeColor=5B3FE5, sections[0].facts = [Run, Report, Format, Size,
   Finished, Schedule]

Audit log: SECURITY/WEBHOOK_SENT, WEBHOOK_FAILED, WEBHOOK_SKIPPED
rows captured for each transition, each carrying {runNo, type, url} or
{runNo, error}.
```

**Production notes**:
- Slack incoming-webhook URLs and Teams connector URLs are bearer
  secrets — store them in your secret manager (Vault, AWS Secrets
  Manager, GCP Secret Manager) and inject via env override rather
  than committing them in `report_schedule.webhook_url`. The current
  schema stores them in plain text; a follow-up could encrypt them at
  rest using the same AES-GCM converter as `national_id`.
- For high-volume schedules consider replacing the per-run synchronous
  POST with a queue (Spring AMQP / SQS / Kafka) — today's `HttpClient`
  call happens on the scheduler thread, so a stuck channel slows the
  whole walker tick.
- Teams' Office 365 Connectors are deprecated in favour of Power
  Automate Workflow webhooks; MessageCard still works through both,
  but Adaptive Cards would be the natural follow-up.

### Milestone 30 — ABAC on workflow `/instances/{id}` + action endpoint (PRD 14.9)

Last gap in the workflow scope story. M24 added scope-filtering to the
inbox list endpoint; this closes the **single-record fetch**, the
**action POST**, and the **history endpoint** so a scoped caller can't
even enumerate the workflow instance ids they shouldn't see.

**Change**: scope check moved into `WorkflowService.get(id)`, which is
the single choke point shared by all three endpoints:
- `GET /api/workflow/instances/{id}` calls `service.get(id)` directly.
- `POST /api/workflow/instances/{id}/actions` calls `service.get(id)`
  as its very first step before any SoD / role logic — so an
  out-of-scope action POST returns 404 without leaking the existence
  of the row.
- `GET /api/workflow/instances/{id}/history` now calls `service.get(id)`
  too (return value ignored — just for the visibility side-effect).

Returns **HTTP 404** (not 403) when out of scope, matching the M22/M24
pattern (employees, leave / permission / BT / timesheet single-record).

**End-to-end verified — PRD 14.9 acceptance:**

Test instances:
- `f46fe723…` — workflow over Aliya's PermissionRequest (in manager's scope)
- `bc78bde5…` — workflow over Nigar's PermissionRequest (out of manager's scope)

| Caller | Aliya instance | Nigar instance |
|--------|----------------|----------------|
| admin | GET 200 / history 200 / action 200 | GET 200 / history 200 / action 200 |
| manager | GET 200 / history 200 / action 200 | **GET 404 / history 404 / action 404** |
| employee | GET 200 / history 200 / action 200 | **GET 404 / history 404 / action 404** |

Aliya is reachable for every caller (admin unrestricted; manager
because Aliya reports to Rashad; employee because Aliya = self). Nigar
is correctly hidden from both scoped callers across all three
surfaces.

The ABAC story across the workflow module is now complete:
**inbox list**, **single-record fetch**, **action POST**, and **history
log** all flow through `AccessScopeService.isWorkflowSubjectAccessible`
covering 7 employee-owned subjects (LeaveRequest, PermissionRequest,
BusinessTripRequest, Timesheet, TerminationRequest, ContractChange,
PerformanceReview). Org-wide / batch subjects (OrgVersion, PayrollRun)
deliberately have no resolver and pass through role-only gating.

### Milestone 31 — Webhook URL encryption at rest (PRD 14.3)

Closes a real "leaked DB dump → spammed Slack channel" risk by
extending the AES-256-GCM column encryption (M18) to the two columns
that now carry Slack/Teams incoming-webhook URLs.

**Change** — purely annotation-level; no schema migration needed
(`webhook_url` is `TEXT`, plenty of room for the ~33% base64 expansion
of the encrypted wire format):

- `ReportSchedule.webhookUrl` gains `@Convert(converter =
  EncryptedStringConverter.class)`.
- `ReportRun.webhookTarget` (the per-run snapshot "{TYPE} url") gains
  the same.
- `ReportRunService.deliverWebhook` now passes a redacted URL to the
  `audit.audit_log` payloads instead of the full token — bearer-token
  tail is replaced with `…`, leaving scheme + host visible so an
  operator reading the audit log can still tell which channel was hit.

**Helper** — `ReportRunService.maskUrl(String)`:

```
https://hooks.slack.com/services/T0/B0/abcd1234   →   https://hooks.slack.com/…
https://millers.webhook.office.com/webhookb2/uid  →   https://millers.webhook.office.com/…
malformed-url                                     →   malformed-url… (first 20 chars + …)
```

**End-to-end verified — PRD 14.3 acceptance:**

```
Configure schedule RSC-00004 with a Slack-shaped URL:
  http://localhost:9999/services/T0FAKE/B0FAKE/abcd1234SECRETtoken5678

API round-trip:
  GET /api/reports/schedules/{id}     → webhookUrl matches exactly (plaintext)
  GET /api/reports/runs/{id}          → webhookTarget = "SLACK <full url>"
                                         (transparent decrypt on read)

DB at rest:
  SELECT webhook_url   FROM reporting.report_schedule WHERE id = …
    → "enc:v1:zEReX6A/G4RAi2W0aF3CPoTltex6U8MrGGDhcWkEUJGkvS0x4Q8tTP26FrcRuP/Hdu61vsnHo…"
  SELECT webhook_target FROM reporting.report_run    WHERE id = …
    → "enc:v1:2k1KlXJo7nTnNVPtCA0eFEoGz+gegp31ryt22+nphAmfuhHyrVze/uOvHQCXPrZks/uWscpvT…"

Audit log row for the run (audit.audit_log.new_value JSONB):
  { "url": "http://localhost/…", "type": "SLACK", "runNo": "RPT-00008" }
  (The "SECRET" portion of the URL is nowhere in the audit JSON.)

Delivery still works:
  Cron-driven scheduler reads the ciphertext + decrypts + POSTs successfully.
  webhookStatus = SENT every fire.
```

Same `enc:v1:` marker as the existing `national_id` / `iban` /
`account_number` columns from M18, so the future `enc:v2:` key
rotation lands uniformly across all encrypted columns.

### Milestone 32 — Inline image previews in the AttachmentUploader

UX polish: the right-side Drawer that lists attachments now renders a
**48×48 thumbnail** next to each image-type entry, with **click-to-zoom
lightbox** via AntD's `<Image>` component. Non-image entries get a
neutral file glyph in the same 48px gutter so the column stays
aligned.

**Why a fetch dance instead of plain `<img src="/api/.../download">`** —
the attachment download endpoint requires a JWT bearer header, which
the browser can't set on a normal `<img>` GET. The component now:

1. Calls a new `attachmentsApi.previewBlob(id)` that hits
   `/api/attachments/{id}/download` via axios (JWT applied), gets the
   bytes back as a `Blob`, and returns a `URL.createObjectURL(...)`
   string usable in `<img src>`.
2. Caches the blob URL per attachment id in component state.
3. Re-renders rows whose attachment has a cached URL with an AntD
   `<Image>` (built-in preview / zoom on click).
4. Revokes any cached URLs whose underlying attachment is no longer in
   scope (item removed, owner switched) so the browser doesn't hold
   the bytes in memory forever — and revokes everything on unmount.

**Heuristic** — `isPreviewable(contentType)`:
- `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `image/svg+xml`,
  `image/avif`, `image/bmp` — preview.
- `image/tiff` — skipped (browsers don't render natively).
- Anything non-`image/*` — file glyph.

**End-to-end verified:**

```
PNG upload (admin token):
  POST /api/attachments  multipart=test-pixel.png  type=image/png
  → ATT-00011  bucket=hcm-attachments  contentType=image/png  size=70 B

previewBlob round-trip (the call the component makes):
  GET  /api/attachments/{ATT-00011}/download
  → HTTP 200, 70 bytes, content-type=image/png
  → diff vs upload: byte-for-byte identical
  → URL.createObjectURL produces a blob URL the <img> can render

Vite-served updated component module (HMR’d into the browser):
  contains `isPreviewable`, `previewBlob`, `previewUrls`, `FileOutlined`
```

The same component now renders thumbnails on all 6 surfaces it's
already integrated into — Termination + Contract Change (inline
cards), Leave / Permission / Business Trip (Files Drawer), Course
material (inline card).

### Milestone 33 — End-to-end TLS to Keycloak (PRD 14.6)

Closes the last item from the M28 production-hardening checklist:
the backend now talks to Keycloak over HTTPS, the realm enforces
`sslRequired=external`, and the `:8090` HTTP loophole on the host is
gone. The custom truststore lets the JDK trust our self-signed cert
without polluting the rest of the system trust store.

**Truststore** (`nginx/certs/truststore.p12`):
- PKCS12 keystore generated via `keytool -importcert -storetype PKCS12
  -alias hcm-keycloak-dev -file localhost.crt`. Password `changeit` —
  the standard JDK default. Contains exactly one entry: our
  self-signed `localhost.crt`. ~1.4 KB.
- Production swap: replace with `JDK_default_cacerts` (which already
  trusts Let's Encrypt / DigiCert / etc.) by leaving
  `hcm.security.keycloak.trust-store-path` empty — the
  `KeycloakJwtConfig` bean falls back to Spring Boot's default
  autoconfig.

**Custom `JwtDecoder`** (`config.KeycloakJwtConfig`):
- When `hcm.security.keycloak.trust-store-path` is set, builds a
  `RestTemplate` whose `SimpleClientHttpRequestFactory.prepareConnection`
  swaps in an `SSLSocketFactory` backed by a `TrustManagerFactory` that
  loaded only the configured truststore.
- `NimbusJwtDecoder.withIssuerLocation(issuerUri).restOperations(rt).build()`
  wires that RestTemplate into the OIDC JWKS fetch.
- Critically, **only** the JWT decoder's HTTPS calls go through this
  trust; MinIO / MailHog / future external HTTPS still use the JDK
  defaults. No "everything's pinned to one cert" trap.

**docker-compose changes** (`hcm-keycloak`):
- `KC_HOSTNAME_URL` + `KC_HOSTNAME_ADMIN_URL` both flip to
  `https://localhost:8443`. Token `iss` claim now matches the SPA's
  client URL and the backend's `issuer-uri`.
- `KC_REALM_SSL_REQUIRED=external` (formerly `none`) — the env-time
  realm substitution + an admin-API `update realms/millers-hcm` for
  the already-imported realm.
- Host port mapping `8090:8090` removed. Only `expose: ["8090"]` so
  nginx (same Compose network) can still reach it. The host literally
  can't talk plain HTTP to Keycloak.

**application.yml**:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${HCM_OIDC_ISSUER:https://localhost:8443/realms/millers-hcm}
hcm:
  security:
    keycloak:
      trust-store-path:     ${HCM_KEYCLOAK_TRUSTSTORE:./nginx/certs/truststore.p12}
      trust-store-password: ${HCM_KEYCLOAK_TRUSTSTORE_PASSWORD:changeit}
```

**SPA** (`auth/keycloak.ts`): default `url` flipped from
`http://localhost:8090` to `https://localhost:8443`. The
`VITE_KEYCLOAK_URL` env override still wins.

**End-to-end verified — PRD 14.6 TLS finish-line acceptance:**

```
1) Host :8090 plain-HTTP : REFUSED      (port mapping gone — loophole closed)
2) Discovery doc         : HTTP 200, issuer = https://localhost:8443/realms/millers-hcm
3) Token issuance        : iss = https://localhost:8443/realms/millers-hcm
4) Backend /api/auth/me  : HTTP 200, user=admin, roles=[ROLE_HR_ADMIN, ROLE_SYSTEM_ADMIN]
                            (backend's NimbusJwtDecoder fetched JWKS over HTTPS
                             using the custom truststore — validation succeeded)
5) ABAC regression       : manager → total=1 permission req, Aliya only
                            (Nigar correctly hidden — M22/M24 chain intact)

Backend log:
  KeycloakJwtConfig — loaded PKCS12 truststore from ./nginx/certs/truststore.p12 (1 entries)

Realm settings post-flip:
  realm           : millers-hcm
  sslRequired     : external
  passwordPolicy  : length(12) and digits(1) and upperCase(1) and lowerCase(1) and
                    specialChars(1) and notUsername(undefined) and passwordHistory(3)
  bruteForceProtected: true
```

**Production swap from here** (documented for completeness):
1. Replace `nginx/certs/localhost.{crt,key}` with a Let's Encrypt or
   corporate-CA cert + key for your public DNS name.
2. Update `KC_HOSTNAME_URL` + `KC_HOSTNAME_ADMIN_URL` to the public
   DNS name.
3. Set `HCM_KEYCLOAK_TRUSTSTORE=""` (or unset it) — the JDK's
   `cacerts` already trusts the major CAs, no custom truststore
   needed.
4. Move the Postgres backing store to a managed instance
   (`hcm-keycloak-pg` → AWS RDS / Cloud SQL / managed Postgres). The
   realm import file stays the same.

### Milestone 34 — Monthly leave accrual cron (PRD 8.5.2)

Closes the long-standing "monthly accrual / pro-ration / seniority bumps"
seam carried since milestone 5's Leave module. Each accrual-bearing
leave type now grants a configurable per-month bump (default `default /
12`) on the 1st of every month, walking every employee whose contract
is in force. The walker is idempotent — re-runs for the same period
short-circuit via an audit-log marker.

**Schema (`V30__leave_monthly_accrual.sql`):**

```sql
ALTER TABLE leave_mgmt.leave_type
    ADD COLUMN accrues_monthly      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN monthly_accrual_days NUMERIC(6,2);

UPDATE leave_mgmt.leave_type
   SET accrues_monthly = TRUE, monthly_accrual_days = 1.75
 WHERE code = 'ANNUAL';                       -- 21 / 12 ≈ 1.75
```

- `accrues_monthly` — gate flag; FALSE keeps the legacy one-shot grant
  (entitlement = `default_annual_entitlement_days` on first touch).
- `monthly_accrual_days` — explicit per-month bump; NULL falls back to
  `default / 12`.
- The seeded ANNUAL type is flipped to monthly; SICK / MARRIAGE / etc.
  stay one-shot until HR opts them in via the standard LeaveType edit.

**Service (`leave.service.LeaveAccrualService`):**

- `accrueForMonth(year, month, dryRun)` walks **every employee in
  `ACCRUING_STATUSES`** (`ACTIVE`, `ON_PROBATION`, `ON_LEAVE`,
  `ON_BUSINESS_TRIP`, `CONTRACTOR`, `INTERN` — excludes terminal
  `TERMINATED` / `RETIRED` and `SUSPENDED`, since AZ Labour Code §132
  freezes the accrual clock during suspension) × every
  `findByActiveTrueAndAccruesMonthlyTrueOrderByCodeAsc()` leave type.
- Each (employee, type, year) materialises a `leave_balance` row at
  zero (NOT the default annual — that's what monthly accrual is
  replacing) and the bump (`monthly_accrual_days` else `default / 12`)
  is added to `entitlement_days`. Existing `leave_balance` rows
  (e.g. created lazily by the old one-shot path) are bumped on top.
- Every bump writes a dedicated audit row
  (`LEAVE / LeaveBalance / MONTHLY_ACCRUAL`) carrying
  `{period, employeeId, leaveTypeId, leaveTypeCode, year, deltaDays,
  entitlementBefore, entitlementAfter}` so post-hoc reconciliation is
  trivial.
- **Idempotency** is enforced by checking the audit history for a
  `"period":"YYYY-MM"` marker on the balance before bumping. The
  regex tolerates the whitespace PostgreSQL JSONB inserts on
  round-trip (Jackson writes compact, JSONB normalises to
  `"key": "value"`). Re-runs for the same period are no-ops.

**Scheduler:**

```java
@Scheduled(cron = "${hcm.leave.accrual.cron:0 0 2 1 * *}")
```

Fires at 02:00 on the 1st of every month, off-peak after audit's own
03:00 partition walker. Cron overridable via env for tighter testing.

**Admin endpoint (`POST /api/leave/accruals/run-now`):**

```
POST /api/leave/accruals/run-now?year=2026&month=5&dryRun=true   # HR_ADMIN / SYSTEM_ADMIN
```

- All params optional (default: now-year, now-month, dryRun=false).
- Returns an `AccrualResult` envelope:
  `{year, month, dryRun, activeEmployees, eligibleTypes,
   balancesCredited, balancesSkipped, totalDaysCredited}`.
- Used for off-cycle correction (e.g. backfill a missed month) and as
  the verification probe below.

**End-to-end verified:**

```
1) Admin HTTPS token acquired.
2) Aliya Mammadova employee resolved (ON_PROBATION — included).
3) ANNUAL type: accruesMonthly=true, monthlyAccrualDays=1.75
4) Aliya 2026 ANNUAL BEFORE   : no row yet
5) Dry-run 2026-05            : 3 employees × 1 type → 3 credited, 5.25 days
                                 (no DB writes — verification confirmed by re-fetch)
6) Live    2026-05            : 3 credited, 5.25 days
7) Aliya 2026 ANNUAL AFTER    : 1.75 (0 + 1.75 ✓)
8) Re-run  2026-05            : 0 credited, 3 SKIPPED (idempotency ✓)
9) Live    2026-06            : 3 credited, 5.25 days   (different period bumps)
10) Aliya 2026 ANNUAL after June: 3.50 (1.75 + 1.75 ✓)
```

**Production swap from here:**

1. Configure per-type `monthly_accrual_days` for SICK / EDUCATIONAL /
   etc. if HR wants them on monthly cadence instead of one-shot.
2. Seniority-bump rule (PRD 8.5.2) — extend `LeaveType` with a
   `seniority_brackets_json` column and have the accrual service read
   `employment_start_date` to pick the correct bracket.
3. Public-holiday exclusion when computing leave-day spend (already a
   policy flag on `leave_type`; payroll-side calculation still TODO).
4. Manager-chain anniversary bumps — fire the bump on the employee's
   service anniversary instead of the calendar 1st, by deriving
   `hire_date_anniversary == today.dayOfMonth`.

### Milestone 35 — Real MANAGER step role in workflows (PRD 9 / 14.9)

Closes the long-standing "Manager step is approximated with
`ROLE_HR_SPECIALIST` until manager hierarchy ABAC lands" stand-in
documented in V10's seed comment. Now `workflow_step` rows can declare
they resolve to the subject's direct manager (via
`core_hr.employee.manager_id` — the chain landed in M22), and the
workflow inbox + action endpoints both narrow accordingly. After this
milestone, **only the actual direct manager** — not every user with
`ROLE_DEPARTMENT_MANAGER` in the org — sees a given pending row.

**Schema (`V31__workflow_manager_step.sql`):**

```sql
ALTER TABLE workflow.workflow_step
    ADD COLUMN resolves_to_manager BOOLEAN NOT NULL DEFAULT FALSE;

-- Flip the 7 employee-owned "Manager review" steps in one go.
UPDATE workflow.workflow_step
SET approver_role       = 'ROLE_DEPARTMENT_MANAGER',
    resolves_to_manager = TRUE
WHERE step_order = 1
  AND name = 'Manager review'
  AND definition_id IN (
        '333…','444…','555…','666…','888…','999…','aaa…'
        -- LEAVE / BUSINESS_TRIP / PERMISSION / TIMESHEET /
        -- TERMINATION / CONTRACT_CHANGE / PERFORMANCE_REVIEW
  );
```

Payroll's `PAYROLL_APPROVAL` keeps `ROLE_HR_SPECIALIST` — payroll runs
are org-wide, no employee dimension to resolve.

**Entity** — `WorkflowStep.resolvesToManager` boolean field, mapped
via Lombok `@Getter`/`@Setter`.

**Service logic** (`workflow.service.WorkflowService`):

- New `WorkflowSubjectResolver` + `EmployeeRepository` injected.
- Helper `resolveSubjectManagerId(instance)` — pipes the
  `(entity, subjectId)` through M24's `WorkflowSubjectResolver`,
  loads the resulting `Employee`, returns its `manager_id`.
- Helper `currentEmployeeIdOrNull()` — maps `currentRequest.username()`
  → `Employee.id` via `findByUsername`. Stays internal to avoid
  pulling in the whole self-service module.
- `inboxFor(roles)` now does a two-tier filter:
  1. Existing M24/M26 ABAC scope check on the subject.
  2. New M35 check — if current step `resolvesToManager`, the caller
     must be that subject's manager. SYSTEM_ADMIN bypasses.
- `act()` `APPROVE` / `REJECT` / `RETURN` paths call new
  `requireResolvedApprover(i, step, isAdmin, msg)` immediately after
  the role gate. Two failure modes that throw `BadRequestException`:
  - Manager-resolved step but the subject has no `manager_id` →
    refuses (prevents the pooled-inbox regression on mis-seeded data).
  - Manager-resolved step + caller isn't the resolved manager → 400.

**End-to-end verified** (against a freshly submitted Aliya → Rashad
leave request, post-V31):

```
1) Aliya  POST /api/self/leave/submit       → LR-00005 created
2) Workflow instance current step           → ROLE_DEPARTMENT_MANAGER, idx=1
3) Inbox visibility:
   admin     inbox total=0  (no ROLE_DEPARTMENT_MANAGER → SQL excludes)
   manager   inbox total=2  contains-this-wf=YES  ✓ (Rashad sees Aliya's row)
   hrspec    inbox total=0  contains-this-wf=no   ✓ (NO LONGER the stand-in)
   employee  inbox total=0  (the initiator, not an approver)
4) hrspec POST APPROVE                      → HTTP 400 (role gate rejects)
5) manager POST APPROVE                     → HTTP 200, advances to ROLE_HR_ADMIN
6) Post-approval inbox:
   admin     YES (has HR_ADMIN now that step rerouted)
   manager   no  (his manager-resolved step is done)
   hrspec    no  (still no HR_ADMIN role)
7) admin   POST APPROVE                     → HTTP 200, status=APPROVED
```

The headline regression: pre-M35, **hrspec** was the M5 stand-in
manager — anyone with `ROLE_HR_SPECIALIST` saw every employee's
"Manager review" row in their inbox. Now hrspec sees zero of those
rows and only Aliya's actual direct manager (Rashad) can act on them.

**Behaviour on in-flight instances**: V31 only updates the
`workflow_step` policy — it does NOT rewrite `workflow_instance.current_step_role`
on already-PENDING rows. Those continue routing under their original
role until they complete. A production deployment that wants in-flight
rows to immediately re-route to the new policy can run:

```sql
UPDATE workflow.workflow_instance wi
SET current_step_role = 'ROLE_DEPARTMENT_MANAGER'
WHERE wi.status = 'PENDING'
  AND wi.current_step_role = 'ROLE_HR_SPECIALIST'
  AND wi.subject_entity IN ('LeaveRequest','PermissionRequest','BusinessTripRequest',
                            'Timesheet','TerminationRequest','ContractChange',
                            'PerformanceReview');
```

Kept out of V31 deliberately — letting in-flight requests finish under
the rules they started under is the safer default.

**Production swap from here:**

1. The 7 employee-owned workflows now require every employee to have
   a `manager_id`. Top-of-org employees (CEO, founder) won't be able
   to submit leave / BT / permission through these workflows until a
   manager is set or the workflow definition is forked with a
   non-manager-resolved step 1.
2. Self-approval guard already in place (`require(!isInitiator)`),
   so a manager who is somehow their own manager (data quirk) still
   can't self-approve.
3. Substitute / acting-manager support — when a manager is on leave,
   add a `delegate_manager_id` column on Employee + extend the
   resolver to walk the delegate chain on the date of approval.

### Milestone 36 — Server-side thumbnail generation (PRD 14.6 / 16.4)

Closes a UX/bandwidth gap from M32. The inline image previews in the
`AttachmentUploader` Drawer were rendering the full upload — fetching a
4 MB photo to draw 48 pixels. M36 pre-bakes a 256-px JPEG sibling
object on upload so the inline tile fetches ~10 KB; the click-to-zoom
lightbox lazily loads the full image only when opened.

**Schema (`V32__attachment_thumbnail.sql`):**

```sql
ALTER TABLE core_hr.attachment
    ADD COLUMN thumb_object_key VARCHAR(500);
```

Nullable on purpose — non-image uploads have no thumb, pre-M36 rows
have no thumb until backfilled, and generation failures (corrupt JPG,
OOM on huge TIFF) leave it null too. The `/thumbnail` endpoint
falls back to the original blob in every "null" case, so previews
keep working uniformly.

**`ThumbnailService`** (`attachment.service.ThumbnailService`):

- JDK-only — no extra deps. Decodes with `javax.imageio`, resizes via
  `Graphics2D` with `VALUE_INTERPOLATION_BILINEAR` +
  `VALUE_RENDER_QUALITY` + antialiasing, encodes JPEG at q=0.85 via
  `ImageWriter`/`MemoryCacheImageOutputStream`. Configurable knobs:
  `hcm.attachment.thumbnail.max-edge-px:256`,
  `hcm.attachment.thumbnail.jpeg-quality:0.85`.
- **Static allowlist** of content types (`image/png`, `jpeg`, `gif`,
  `bmp`, `webp`). Deliberately excludes SVG (vector + text) + TIFF
  (huge, slow). Strips `; charset=…` suffix + lowercases before lookup.
- **Fit-inside-square** with aspect preserved; never upscales (a 96×96
  favicon stays 96×96, doesn't get blown up to 256).
- **White background** painted under the image — JPEG can't represent
  transparency, so a transparent PNG's alpha channel would otherwise
  render as black.
- **Every failure mode returns `null`** so the upload still succeeds.
  Failure cases: unsupported content type, ImageIO returns null on
  decode, OOM on a giant input, JPEG encoder hiccup. Logged at `WARN`.

**Upload wiring** (`AttachmentService`):

- New `maybeStoreThumbnail(originalKey, payload, contentType)` runs
  after the main `putObject` succeeds. Stores the thumb at
  `<originalKey>.thumb.jpg` in the same MinIO bucket — namespaced
  under the same `<module>/<entity>/<ownerId>/` prefix so a
  `mc ls --recursive` reads naturally.
- Wired into both `upload(MultipartFile)` (refactored to read the
  payload once into `byte[]` — the existing 20 MB `maxBytes` guard
  makes the in-memory copy safe) and `uploadBytes(...)` (server-
  generated payloads, e.g. report exports — but those never declare
  an image content type, so generation is a no-op there).
- `delete(id)` now removes both the original + the `.thumb.jpg`
  sibling via new `removeQuietly(key)` helper. MinIO failures still
  log + carry on, matching the original M16 behaviour.

**New endpoint** (`GET /api/attachments/{id}/thumbnail`):

- Returns the pre-baked JPEG when `thumb_object_key` is populated.
- Falls back to the original blob otherwise — pre-M36 rows, non-image
  content, generation failures, or "thumb object went missing from
  MinIO somehow" all share this path. The endpoint never 500s the
  preview UX.
- `X-Thumb-Source` response header: `generated` vs.
  `original-fallback` — useful for debugging + future cache-hit
  metrics on the client.
- `Content-Disposition: inline` (not `attachment`) since this is for
  in-page rendering, not save-as.

**AttachmentResponse DTO** gains `hasThumbnail: boolean` so the SPA
could branch on it if needed — though the fallback path means the
client doesn't strictly have to.

**Frontend** (`AttachmentUploader.tsx`):

- New `attachmentsApi.thumbnailBlob(id)` mirrors `previewBlob` but
  hits `/thumbnail`.
- Renamed component state `previewUrls` → `thumbUrls` (48×48 inline
  tile); new `fullUrls` cache lazily populated **only when the
  click-to-zoom lightbox opens** (`onVisibleChange` hook). Until then
  the lightbox upscales the thumb, which is a much better
  perceived-perf curve than blank-while-loading.
- Cleanup paths revoke both URL caches on unmount + on stale-row
  eviction. AntD `<Image>`'s `preview.src` is set to the full URL
  when present, falling back to the thumb otherwise.

**End-to-end verified:**

```
0) Tokens acquired ✓
1) Image upload — PNG payload = 312,118 bytes
   POST /api/attachments       : HTTP 201 → ATT-00012 hasThumbnail=true
   GET  /thumbnail             : HTTP 200, 30,814 bytes,
                                  X-Thumb-Source=generated,
                                  Content-Type=image/jpeg
   → thumbnail is 9.9% of original size ✓
2) Non-image upload — TXT payload = 760 bytes
   POST /api/attachments       : HTTP 201 → ATT-00013 hasThumbnail=false
   GET  /thumbnail (fallback)  : HTTP 200, 760 bytes,
                                  X-Thumb-Source=original-fallback,
                                  Content-Type=text/plain
                                  (byte-for-byte identical to upload)
3) Pre-M36 row simulation — NULL thumb_object_key on the image row:
   GET  /thumbnail (fallback)  : HTTP 200, 312,118 bytes,
                                  X-Thumb-Source=original-fallback,
                                  Content-Type=image/png
                                  → backward-compat path serves original ✓

MinIO listing:
   c1519626-...-m36.png            305 KiB   (original)
   c1519626-...-m36.png.thumb.jpg   30 KiB   (M36 thumbnail sibling)
```

**Bandwidth win**: a Drawer with 5 photo attachments goes from
~20 MB of preview traffic to ~50 KB on first render (250× cheaper).
Click-to-zoom on one of them pays the full image fetch once.

**Production swap from here:**

1. Backfill job for pre-M36 image rows — walk every `attachment`
   with `thumb_object_key=NULL AND content_type LIKE 'image/%'`,
   download + thumbnail + put. Kept out of M36 since the fallback
   path makes pre-M36 rows work without it; backfill is a perf
   optimisation, not a correctness step.
2. Replace the JDK `ImageIO` path with `imgscalr` or `thumbnailator`
   if PNG/JPEG quality starts to matter for design-tool exports —
   the AWT renderer is fine for photo thumbnails but has weaker
   chroma subsampling than libjpeg-turbo would give.
3. Per-content-type max-edge tuning (e.g. 512 for raster-heavy
   detail-page galleries vs. 96 for sidebar avatars) — currently a
   single global config knob.

### Milestone 37 — Acting / delegate manager support (PRD 9 / 14.9)

Closes the "manager on vacation can't approve anything" hole that M35
left wide open. The 7 employee-owned workflows route to the subject's
direct `manager_id` — and that worked fine until the manager went on
leave, at which point every request to them piled up in an inbox
nobody read. M37 adds a time-bound delegation column on `Employee`:
when a manager sets one, the workflow engine routes their approval
tasks to the delegate during the configured window.

**Schema (`V33__employee_delegation.sql`):**

```sql
ALTER TABLE core_hr.employee
    ADD COLUMN delegate_manager_id UUID REFERENCES core_hr.employee (id),
    ADD COLUMN delegate_from       DATE,
    ADD COLUMN delegate_to         DATE,

    -- All-three-null or all-three-set with valid window.
    ADD CONSTRAINT chk_employee_delegation_window CHECK (
        (delegate_manager_id IS NULL AND delegate_from IS NULL AND delegate_to IS NULL)
        OR (delegate_manager_id IS NOT NULL AND delegate_from IS NOT NULL
            AND delegate_to IS NOT NULL AND delegate_from <= delegate_to)
    ),
    -- No self-delegation (noop cycle).
    ADD CONSTRAINT chk_employee_delegation_not_self CHECK (
        delegate_manager_id IS NULL OR delegate_manager_id <> id
    );

CREATE INDEX idx_employee_delegate_manager
    ON core_hr.employee (delegate_manager_id)
    WHERE delegate_manager_id IS NOT NULL;
```

Three columns on Employee, no separate `delegation` table — most
employees never set this. The partial index keeps the sparse-column
cost down. SQL check constraints enforce the invariants on every
write path (Hibernate, raw SQL, kcadm scripts, future bulk imports).

**Entity + DTOs**:
- `Employee.delegateManagerId / delegateFrom / delegateTo` (LocalDate).
- `EmployeeRequest` + `EmployeeResponse` gain matching fields.
- `EmployeeService.validateDelegation(selfId, request)` runs a 4-step
  defense-in-depth check that yields friendlier 400s than raw constraint
  errors: (a) all-three-or-none, (b) not self, (c) delegate exists,
  (d) from ≤ to. Wired into both `create()` and `update()`.

**Service logic** (`WorkflowService.resolveSubjectManagerId`):

Two-step resolution:

```java
Optional<UUID> subjectMgr = subjectResolver
        .resolveEmployeeId(i.getSubjectEntity(), i.getSubjectId())
        .flatMap(employees::findById)
        .map(Employee::getManagerId);
if (subjectMgr.isEmpty()) return null;
return employees.findById(subjectMgr.get())
        .map(this::effectiveApprover)
        .orElse(subjectMgr.get());
```

`effectiveApprover(Employee manager)`:
- If `delegateManagerId == null` → return manager id (existing behaviour).
- If today() ∈ `[delegateFrom, delegateTo]` → return the delegate's id +
  `log.info("Workflow approval routed to delegate …")` so an operator
  reading the backend log can correlate "why did Sara just see this
  row" with HR's earlier delegation decision.
- Otherwise (window expired or hasn't started) → return manager id.

**Single hop only — no chain walk**. If a delegate is themselves on
leave, the original manager has to pick a different delegate; the
engine doesn't recursively re-delegate. Two reasons: (a) the SQL
self-delegation check refuses cycles by construction, but multi-hop
fan-out makes routing harder for HR to reason about, and (b) one hop
is enough for every realistic "manager on vacation" scenario.

**End-to-end verified:**

```
0) Tokens for admin / employee / manager acquired
1) kcadm: grant Sara (hrspec user) ROLE_DEPARTMENT_MANAGER so she can
   appear in the inbox-SQL filter. Set Rashad's delegation:
     delegate_manager_id = Sara.id
     delegate_from       = 2026-05-01
     delegate_to         = 2026-05-31      (covers today: 2026-05-21)
2) Aliya submits SOCIAL leave LR-00006:
     Sara   inbox  contains-this-wf = YES     ← acting as Rashad's delegate
     Rashad inbox  contains-this-wf = no      ← original manager bypassed during window
3) Sara POST APPROVE → HTTP 200, advances to ROLE_HR_ADMIN step.
4) Backdate window to 2024-01-01 .. 2024-12-31, Aliya submits LR-00007:
     Rashad inbox  contains-new-wf = YES      ← own inbox back, window lapsed
     Sara   inbox  contains-new-wf = no       ← stops seeing new rows
5) PUT employees/{rashad} with delegateManagerId=rashad.id:
     HTTP 400 "An employee cannot delegate to themselves"
6) Cleanup — cleared delegation on Rashad.
```

**Production swap from here:**

1. UI for HR to set delegation — currently uses the generic
   `PUT /api/employees/{id}` payload. A dedicated
   `POST /api/employees/{id}/delegation` with a smaller body would be
   friendlier in the SPA, but the field round-trip on the existing
   endpoint already works end-to-end for headless workflows.
2. Auto-clear delegation on the day after `delegate_to` so stale rows
   don't linger if HR forgets to clear it. Cheap nightly walker.
3. Multi-hop chain walk if HR wants "the deputy's deputy" semantics —
   feasible (the SQL cycle guard prevents infinite recursion), but
   currently a deliberate non-feature for routing simplicity.
4. Self-service delegation for managers who want to set their own
   deputy without an HR ticket — gate to `ROLE_DEPARTMENT_MANAGER`
   acting on their own employee row.

### Milestone 38 — Holiday calendar (PRD 8.4 / 8.5 / 8.9)

Closes three module-level TODOs in one milestone — Timesheet's
weekend-only `H` code (since M9), Leave's `exclude_holidays` policy
flag (since M27 / M34), and the seam for payroll OT holiday-rate
multipliers (future M39+ work). Single `core_hr.holiday` table feeds
all three.

**Schema (`V34__holiday_calendar.sql`):**

```sql
CREATE TABLE core_hr.holiday (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    jurisdiction      VARCHAR(8)   NOT NULL DEFAULT 'AZ',
    holiday_date      DATE         NOT NULL,
    name              VARCHAR(200) NOT NULL,
    holiday_type      VARCHAR(32)  NOT NULL DEFAULT 'PUBLIC',
    recurring         BOOLEAN      NOT NULL DEFAULT TRUE,
    notes             TEXT,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    -- + audit fields + CHECK on holiday_type + UNIQUE(jurisdiction, date)
);
CREATE INDEX idx_holiday_jurisd_date
    ON core_hr.holiday (jurisdiction, holiday_date)
    WHERE active = TRUE;
```

- `jurisdiction` lives at the row level so multi-country tenants can
  coexist. Default `AZ`. Configurable via
  `hcm.holiday.default-jurisdiction`.
- `holiday_type` ∈ {`PUBLIC`, `RELIGIOUS`, `MOURNING`, `COMPANY`} —
  CHECK-constrained. Slices the calendar so reports can break out
  "Labour Code days" vs. "company closures".
- `recurring` is a hint for future-year backfill jobs — true `PUBLIC`
  holidays like Republic Day repeat annually, but RELIGIOUS feasts
  move on the Hijri calendar and need explicit per-year seeding.
- Partial index `WHERE active = TRUE` keeps the hot-path lookup
  cheap on the `(jurisdiction, date)` covering key.

**AZ 2026 seed (21 rows):** New Year (1-2 Jan), Day of Mourning
(20 Jan, Article 110), Women's Day (8 Mar), Novruz (20, 23, 24 Mar) +
Ramazan Bayramı overlap (21-22 Mar), Day of Victory over Fascism
(9 May), Republic Day (28 May) + Qurban Bayramı (27, 29 May), National
Salvation (15 Jun), Armed Forces Day (26 Jun), Restoration of
Independence (18 Oct), Victory Day (8 Nov), State Flag Day (9 Nov),
Constitution Day (12 Nov), National Revival (17 Nov), International
Solidarity Day (31 Dec). Per Labour Code Article 105 + State
Committee for Religious Affairs.

**`HolidayService`** (`corehr.service.HolidayService`):
- `isHoliday(date)` / `isHoliday(date, jurisdiction)` — single-row
  lookup against the default or explicit jurisdiction.
- `holidayDatesIn(YearMonth)` / `holidayDatesBetween(start, end)` —
  materialises the set in one query. **Hot-path optimisation**: the
  timesheet engine calls this once per month and does per-day
  contains-checks in O(1), instead of N+1 `isHoliday` round trips for
  28-31 dates per employee.
- CRUD via `HolidayController` — GET any-auth, POST/PUT/DELETE
  HR_ADMIN / SYSTEM_ADMIN. Friendly 400s on duplicate `(jurisdiction,
  date)` collisions before the DB UNIQUE fires.

**Downstream integration:**

- **TimesheetGenerator** — new priority tier between "permission" and
  "absent":
  ```java
  } else if (permission != null) {
      row.setPrimaryCode("P");
  } else if (isHoliday) {       // M38: was weekend-only
      row.setPrimaryCode("H");
  } else if (workingDay) {
      row.setPrimaryCode("A");
  } else {
      row.setPrimaryCode("H");  // legacy: weekend per schedule
  }
  ```
  Also adds a `WORKED_ON_HOLIDAY` anomaly when attendance shows work
  on a holiday — the payroll OT engine consumes that flag (future
  hook).

- **LeaveRequestService.computeDays** — extended with a
  `Set<LocalDate> holidayDates` parameter. Caller pre-fetches once
  via `holidays.holidayDatesBetween(start, end)` when the leave type
  sets `exclude_holidays`; the helper drops those dates from the
  count. Pre-M38 callers pass `Set.of()` for back-compat.

**End-to-end verified:**

```
1) GET /api/holidays?year=2026               → 21 rows
   GET /api/holidays?year=2026&month=3       → 6 rows
                                                 (Women's Day + 5-day
                                                 Novruz/Ramazan cluster)
   GET /api/holidays?year=2026&month=5       → 4 rows
                                                 (Victory + Republic Day
                                                 + Qurban x2)

2) Leave spend math + exclude_holidays:
   SOCIAL leave 2026-05-25..2026-05-31, excludeHolidays=false:
     LR-00017 totalDays = 7   (no exclusion)
   Flip SOCIAL → excludeHolidays=true; resubmit 2026-06-23..2026-06-29:
     LR-00018 totalDays = 6   (7 cal − 1 holiday on 26 Jun)
   2026-11-07..2026-11-13 (3 holidays in 7 days):
     LR-00019 totalDays = 4   (7 − 3 = Victory + State Flag + Constitution)

3) Timesheet H-code on holiday-weekdays (Jan 2026 for Aliya):
   2026-01-01 (Thu New Year)         → H ✓
   2026-01-02 (Fri New Year #2)      → H ✓
   2026-01-20 (Tue Day of Mourning)  → H ✓
   Pre-M38 these would be 'A' (absent) when a schedule was assigned.
```

**Production swap from here:**

1. Wire `WORKED_ON_HOLIDAY` into the payroll OT engine — apply the
   holiday-rate multiplier configured in `payroll.rule_config` when a
   timesheet day carries that anomaly. Schema already in place
   (`rule_json`); just needs the engine to read it.
2. Compensatory day-off math — Labour Code Article 105: a holiday
   landing on a Saturday/Sunday gives the following Monday off. A
   nightly walker could materialise the lieu days into the calendar
   for the upcoming year so the timesheet engine sees them
   automatically.
3. Hijri-feast backfill — Ramazan / Qurban Bayramı move ~11 days
   earlier each Gregorian year. Pin the State Committee's
   announcement into a recurring import task.
4. Per-employee holiday calendar override (e.g. interns who don't
   observe certain religious holidays, expat workers under a
   different jurisdiction's leave policy) — would need a
   `holiday_calendar_id` foreign key on Employee + a `holiday`-to-
   calendar mapping table. Out of scope here; jurisdiction at the row
   level already covers the multi-country case.

### Milestone 39 — Payroll holiday-OT multiplier (PRD 8.9.4)

Closes the M38 follow-on hook. The Timesheet engine was already
flagging `WORKED_ON_HOLIDAY` on any day with worked minutes + a
holiday date, but the payroll OT calculator ignored it — every OT
hour was paid at the standard Article 165 ladder (1.5× / 2.0×).
After M39 those same hours are paid at the Article 167 holiday
premium (default 2.0×, configurable per jurisdiction).

**Schema (`V35__overtime_holiday_multiplier.sql`):**

```sql
UPDATE payroll.statutory_rule
SET rule_json = jsonb_set(rule_json, '{holidayMultiplier}', '2.0'::jsonb, TRUE)
WHERE code = 'OVERTIME_AZ' AND jurisdiction = 'AZ'
  AND effective_from = DATE '2026-01-01';
```

JSONB merge — idempotent, no new column. Labour Code Article 167:
work on weekly days off, public holidays, and days of mourning is
compensated at "not less than" double the standard hourly rate. 2.0
is the legal floor; collective agreements can raise it without
schema changes.

**`StatutoryCalculator` refactor:**

- New `DailyOt(BigDecimal hours, boolean holiday)` record replaces
  the previous `List<BigDecimal>` parameter on `overtimePay()`.
- Holiday-flagged days take a separate code path: **all** OT hours
  that day are multiplied by `holidayMultiplier` — the first-N/rest
  split does NOT apply because the holiday premium already
  compensates the extra burden (and Article 167 doesn't carve out a
  "first N hours" exemption).
- Standard (non-holiday) days keep the existing
  `firstHoursPerDay → firstMultiplier`, rest at `afterMultiplier`
  logic — pre-M39 behaviour, unchanged.
- `OvertimePay` record extended with `holidayHours` + `holidayPay`
  so the trace JSON can break out the two streams.
- Back-compat: `holidayMultiplier` defaults to `afterMultiplier` if
  `rule_json` omits the key — fresh deployments that haven't run V35
  still calculate correctly (no NPE, just no premium).

**`PayrollEngine` wiring:**

```java
List<StatutoryCalculator.DailyOt> dailyOt = days.stream()
        .map(d -> new StatutoryCalculator.DailyOt(
                d.getOvertimeHours(),
                d.getAnomalies() != null
                        && d.getAnomalies().contains("WORKED_ON_HOLIDAY")))
        .toList();
```

The anomaly string is the same one M38's `TimesheetGenerator` emits.
No new repository methods, no new TimesheetDay columns — one source
of truth for "was today a holiday I worked on".

**`buildTrace` split** — the payroll-result `calculation_details`
JSONB now carries:

```json
"overtime": {
  "totalHours":    "4.00",
  "totalPay":      "131.25",
  "hourlyRate":    "18.7500",
  "expectedMonthlyHours": "160",
  "standardHours": "2.00",
  "standardPay":   "56.25",
  "holidayHours":  "2.00",
  "holidayPay":    "75.00"
}
```

HR can audit exactly what was paid under each Article without
re-running the engine. Fields are additive — pre-M39 callers
reading `totalHours / totalPay / hourlyRate / expectedMonthlyHours`
still work.

**End-to-end verified** (Aliya Jan 2026, base salary 3000 AZN):

```
1) Seed attendance.daily_summary:
     2026-01-01 (Thu — New Year holiday): worked 600 min, OT 120 min
     2026-01-05 (Mon — regular weekday):  worked 600 min, OT 120 min
2) Regenerate Jan 2026 timesheet:
     2026-01-01 primaryCode=W, overtimeHours=2.0,
                anomalies="WORKED_ON_HOLIDAY" ✓
     2026-01-05 primaryCode=W, overtimeHours=2.0, no anomaly ✓
3) Approve timesheet + create payroll run PR-00003.
4) POST /api/payroll/runs/{id}/calculate → totalGross=3131.25
5) Trace JSON:
     hourlyRate    = 3000 / 160        = 18.75
     standardHours = 2.00  standardPay = 56.25   (2 × 1.5 × 18.75)
     holidayHours  = 2.00  holidayPay  = 75.00   (2 × 2.0 × 18.75)
     totalHours    = 4.00  totalPay    = 131.25
     gross         = 3000 + 131.25     = 3131.25 ✓
```

**Production swap from here:**

1. Weekend OT (Labour Code Article 167 also covers weekly rest days)
   — currently only the holiday flag fires. Future work: emit a
   `WORKED_ON_WEEKEND` anomaly from `TimesheetGenerator` for
   attendance on a non-working scheduled day, plus a
   `weekendMultiplier` rule field. Same wiring shape as M39.
2. Per-employee OT cap (Article 99: max 4 OT hours / day) — emit a
   timesheet anomaly when daily OT exceeds the cap and surface it on
   the payroll trace as an audit flag.
3. Holiday-OT premium tied to bonus matrix (M61) — for executive
   grades where the LC floor doesn't apply, override
   `holidayMultiplier` per-employee. Currently global per
   jurisdiction; per-grade lookup would need a new key on the
   `compensation_grade` row.

## What's wired vs. stubbed

| Concern                 | This slice                                    | Later milestone            |
|-------------------------|-----------------------------------------------|----------------------------|
| Employee master data    | Full CRUD, status transitions, audit          | Effective-dated history    |
| Org Structure           | Versioned tree, draft/approve/activate, rollback, audit | Visual chart export to PDF/PNG/Excel; historical-date view |
| Staffing Table          | Position CRUD with derived vacancy state, audit, RBAC | Budget vs actual reports; multi-step headcount change workflow |
| Audit log               | Per-write entries on every module; monthly `PARTITION BY RANGE (created_at)` partitioning (PRD 15.3) with auto-create scheduler for next month and startup hook for current+next | Long-term archival / cold storage of partitions older than retention window, partition pruning verification via `EXPLAIN` in `/audit` UI |
| Workflow                | Sequential engine with definitions, inbox, role + SoD checks; org module wired via events; **real MANAGER step resolution** (M35) — `workflow_step.resolves_to_manager` flag flips the 7 employee-owned workflows (Leave / BT / Permission / Timesheet / Termination / Contract Change / Performance Review) so step 1 narrows to the subject's actual `manager_id` instead of pooling to every `ROLE_HR_SPECIALIST` (the M5 stand-in). Inbox visibility + APPROVE/REJECT/RETURN action gate both enforce — only the resolved manager (or SYSTEM_ADMIN) can act; refuses when the subject has no `manager_id` set. **Time-bound delegation** (M37) — `employee.delegate_manager_id` + `delegate_from` + `delegate_to` (single-hop, SQL-checked no-self-delegation + all-three-or-none + window invariants); when today() ∈ window, `WorkflowService.resolveSubjectManagerId` routes the original manager's approval tasks to the delegate instead. Defense-in-depth service-layer validation yields friendly 400s on top of the SQL constraint checks. Logged at INFO when a substitution fires so operators can correlate "why did the delegate see this row". | Parallel/conditional steps, SLA-based escalation, definition admin UI, automatic in-flight instance rewrites on policy changes (currently in-flight rows finish under the rules they started under — manual SQL rewrite documented in M35), auto-clear delegation past `delegate_to` (currently stays in place until HR clears), multi-hop delegation chain walk (deliberately single-hop now), self-service delegation UI for managers (currently goes through HR via the generic employee PUT) |
| Attendance              | Fixed weekly schedules, REST + CSV ingest, daily-summary engine with manual correction; `WORKED_ON_HOLIDAY` anomaly emitted by the Timesheet generator (M38) feeds the payroll holiday-OT multiplier (M39) | Shift / rotational / summarized accounting, SOAP / SFTP / DB-view connectors, weekend-OT anomaly (currently weekday/holiday split only) |
| Leave                   | Configurable types, lazy balances, submit → workflow → commit/release math; **monthly accrual cron** (`@Scheduled` at 02:00 on the 1st) walking every accrual-bearing type × every employee whose contract is in force; per-type `monthly_accrual_days` override (else `default / 12`); idempotent re-runs via audit-log marker (`LEAVE / LeaveBalance / MONTHLY_ACCRUAL` row carrying period + delta); admin `POST /api/leave/accruals/run-now?year=&month=&dryRun=` for off-cycle / verification (M34, PRD 8.5.2); **`exclude_holidays` spend math** (M38) — when set, `computeDays` pre-fetches the date-range's holidays from M38's `HolidayService` and drops them from the total, so a 7-day leave over a 3-holiday week counts as 4 days against the bank | Pro-ration on hire (currently bumps the full per-month amount regardless of hire date), seniority bumps (Labour Code tenure-based annual entitlement table), maternity DSMF integration, anniversary-bump rules (anniversary-of-hire instead of calendar 1st) |
| Business Trip           | Trip request, advance ledger (requested/approved/paid/actual), 3-step workflow, post-trip reconciliation | MinIO attachment uploads, auto-timesheet `BT` code (lands with Timesheet 8.8), dedicated Finance role, multi-currency conversion, per-destination allowance tables |
| Permission              | Hourly balance per (employee, type, year), single-step approval workflow, PRD 8.7.4 math | Mobile self-service, public-holiday exclusion, manager-hierarchy ABAC |
| Timesheet               | Monthly per-employee grid consolidating Attendance + Leave + BT + Permission with one primary code per day (W/L/S/BT/P/H/A), per-day correction, 2-step workflow; **holiday-aware `H` code** (M38) — pre-M38 H was weekend-only, now also fires on dates in `core_hr.holiday` (AZ 2026 seeded, jurisdiction at the row level for multi-country tenants); `WORKED_ON_HOLIDAY` anomaly recorded when attendance shows work on a holiday (feeds future payroll OT multipliers) | `LOCKED` state driven by Payroll, Excel/PDF/CSV export, department-level rollup views, lieu-day rollover for holidays landing on weekends (Article 105) |
| Payroll                 | AZ 2026 income tax (with the 200 AZN exemption notch), DSMF, MMI, unemployment, OT multipliers; configurable `rule_json` with effective dates; CSV bank-file export; full lifecycle (DRAFT → CALCULATED → APPROVED → PAID → CLOSED) via the workflow chassis; **holiday-OT premium** (M39, PRD 8.9.4) — `OVERTIME_AZ.holidayMultiplier` (default 2.0, Article 167 floor) replaces the standard 1.5×/2.0× ladder for OT hours whose timesheet day carries `WORKED_ON_HOLIDAY` (emitted by M38's TimesheetGenerator on attendance + holiday); `payroll_result.calculation_details` JSONB breaks out `standardHours / standardPay / holidayHours / holidayPay` for audit | Per-bank file templates (ABB / Kapital / PASHA / Respublika), advance/loan deductions, garnishments, final-settlement on termination, pro-ration on mid-month hire/leave, PDF payslip, ERP journal-entry export, weekend-OT premium (Article 167 also covers weekly rest days — currently only the holiday flag fires), per-grade override of holiday multiplier |
| Recruitment             | Vacancies linked to staffing positions, candidate pool with search, 6-stage default pipeline with rating + recommendation per move, offer creation + DRAFT/SENT/ACCEPTED/REJECTED transitions, HIRED creates the Employee and adjusts position occupancy (PRD 8.10.8) | Configurable pipeline per vacancy, MinIO CV / offer-letter uploads, structured onboarding task lists beyond the existing stub, candidate-portal portal (apply-online), source-effectiveness + time-to-hire reports, AI candidate matching (explicitly deferred per PRD 5.2) |
| Lifecycle: Termination  | Workflow-driven exit flow (3-step TERMINATION_APPROVAL), clearance checklist (IT/HR/Finance/Assets), optional exit interview, processing flips Employee → TERMINATED, releases the position, snapshots unused-leave + severance settlement with audit-traceable JSON | Configurable severance rule (CBA bands by tenure), MinIO attachment uploads, automatic last-payroll generation, payroll-result hook for the settlement payout, document generation (release letter, work-book entry), revocation of system accounts, integration with public-holiday calendar for the last working date |
| Lifecycle: Contract change | One service for SALARY / POSITION / DEPARTMENT / MANAGER / GRADE / LOCATION / JOB_TITLE / EMPLOYMENT_TYPE / COST_CENTRE; old/new value snapshot as JSON; SALARY adds an effective-dated payroll row, POSITION swaps staffing occupancy, others update Employee in place (PRD 8.12) | Multi-change bundling (promotion = job-title + salary + manager + grade in one approval), automatic acting-allowance handling, retroactive effective dates, contract-amendment document generation |
| Performance             | Review cycles (annual / quarterly / mid-year / probation / project / ad-hoc) with calibration phase; SMART goals with weight + progress + per-goal rating + OKR-style cascading via parent_goal_id; per-employee review with self → manager → 3-step workflow → calibration → completed; weighted goal score auto-computed; 180/360 feedback with NORMAL or ANONYMOUS visibility; calibration captures finalRating / band / recommendation / bonusPercent (PRD 8.13) | Competency framework + structured competency ratings, per-cycle custom rating scales applied to UI, bonus-matrix engine that consumes recommendation/bonusPercent during payroll (PRD 8.15), public goal alignment trees (visual OKR cascade), AI-assisted feedback summarisation, calibration committee UI with ratings distribution, PIP tracking workflow |
| Learning                | Course catalog (DRAFT/PUBLISHED/ARCHIVED) with markdown body + MULTIPLE_CHOICE / MULTI_SELECT / TRUE_FALSE quiz questions; enrollments (SELF_ENROLLED or ASSIGNED); attempts with set-equality auto-grading and `max_attempts` cap; on PASSED → certificate auto-issued (`CERT-NNNNN`, valid for `course.validForMonths` months) and mapped competencies awarded to employee at the configured proficiency level (PRD 8.14); answer keys hidden from non-admin question fetches | SCORM / xAPI content packages, video tracking with watch-percentage progress, instructor-led / blended sessions with attendance roster, MinIO uploads for course material + cover images, learning paths (sequenced courses), recommended-for-you engine, partial credit on MULTI_SELECT, drag-and-drop / fill-in-the-blank question types, integration with Performance "development" goals so completing a course auto-rates the linked goal |
| Comp & Benefits         | Effective-dated bonus matrix keyed by Performance recommendation or final-rating band, with priority + flat amount or % of base + cap; allowance catalogue (TRANSPORT / MEAL / MOBILE / HOUSING / FUEL / FAMILY / ...) with taxable + recurring flags + default amount; effective-dated employee allowance assignments (closes prior open row); bonus runs that read APPROVED/CALIBRATING/COMPLETED reviews from a Performance cycle, derive per-employee bonus (REVIEW_OVERRIDE > MATRIX_LOOKUP fallback), then push as payroll_bonus rows (bonus_type=PERFORMANCE) into a target payroll run; full audit trail (PRD 8.15) | Multi-currency bonus runs with FX conversion, equity grants / vesting schedules, total-rewards statements (annual letter PDF), salary-band master with mid/min/max + compa-ratio, benefits enrollment (insurance choices) with open-enrollment windows, allowance proration on partial-month assignments and automatic push into payroll alongside bonuses, market-data benchmarks integration, bonus accruals on the balance sheet ahead of payment |
| Reporting & Analytics   | 8 cross-module reports via native SQL aggregations (no new schema): Headcount, Attrition, Payroll YTD, Leave Usage, Attendance, Training Compliance, Performance Distribution, Recruitment Funnel. Single tabbed Reports & Analytics page on the frontend with inline mini-bar charts built from AntD primitives. **Scheduled reports**: saved definitions + Spring 6-field cron schedules + `@Scheduled` walker that fires due jobs every 60s; per-format renderers (PDF via OpenPDF, XLSX via Apache POI) walking a shared `ReportSection`/`ReportTable` model; generated files stored in MinIO via the existing attachment registry; full run history with status/duration/download. **Email delivery** of scheduled runs via `spring-boot-starter-mail` (MailHog in dev, swap-by-env to any SMTP provider). **Slack / Teams webhook delivery** via Java `HttpClient` — Block Kit payload for Slack, MessageCard for Teams, file stays in MinIO (notification carries a summary + click-through). `report_run.email_status` + `webhook_status` track `NOT_REQUESTED` / `SKIPPED` / `SENT` / `FAILED` per run independently, snapshots of recipients / webhook target at delivery time, and the SMTP / HTTP error captured on failure. `POST /api/reports/runs/{id}/resend-email` and `/resend-webhook` retry each transport independently; HR-only Resend buttons on the Run history UI. **Webhook URLs encrypted at rest** via AES-256-GCM on `report_schedule.webhook_url` + `report_run.webhook_target`; audit-log payloads redact the bearer-token tail (PRD Section 10, PRD 10.5, PRD 14.3) | CSV export, ad-hoc report builder UI, drill-through links from KPI tiles to the underlying module page, time-series charting library, trend comparisons across years, AI-assisted insights, per-recipient delivery preferences, schedule timezone selector, in-line HTML report embedding (vs attachment-only), DKIM / SPF signing for outgoing mail, async queue-backed delivery for high-volume schedules, Adaptive Cards for Teams Workflow webhooks |
| Self-service            | User↔Employee mapping via `core_hr.employee.username`; `EmployeeContextService` resolves current Employee from auth; 17 `/api/self/*` endpoints surface profile, leave, permission, business trips, timesheets, payslips, learning, performance, allowances + summary; self-submit shortcuts force `employeeId` to the current user so EMPLOYEE role can request leave/permission/BT without HR roles; tabbed `MyWorkspacePage` with role-aware index redirect (PRD Section 11) | Approval-chain visibility for the employee on their own pending requests, in-app notifications (badge counts), public-profile / org-chart "who's who" view, contact directory search, document repository per employee (contracts, payslip PDFs once generated), team-view for managers showing their direct reports' leave/timesheets/reviews |
| Attachments             | Real file uploads to MinIO (S3-compatible); soft-delete via `core_hr.attachment` registry indexed by `(owner_module, owner_entity, owner_id)`; backend proxies multipart upload + streams download with the JWT applied; filename sanitised server-side; reusable `AttachmentUploader` component (one-line drop-in) integrated across **6 surfaces**: Termination + Contract Change detail pages (inline cards), Leave / Permission / Business Trip list rows (Files button → right Drawer), and Course detail (Course material card); **48×48 inline image thumbnails** (M32) with click-to-zoom lightbox via AntD `<Image>` for png/jpeg/gif/webp/svg/avif/bmp attachments (fetched via JWT-authed axios + `URL.createObjectURL` since `<img src>` can't carry the bearer); **server-side thumbnail generation** (M36) — JDK-only `ThumbnailService` (javax.imageio + Graphics2D, no extra deps) bakes a 256-px JPEG sibling at `<key>.thumb.jpg` in MinIO on upload for image content types (PNG/JPEG/GIF/BMP/WEBP allowlist; SVG/TIFF deliberately skipped); `/api/attachments/{id}/thumbnail` serves the small blob (~10 KB for a 300 KB photo → 9.9× cheaper inline), falls back to the original blob for pre-M36 rows / non-image / generation failures with `X-Thumb-Source: original-fallback`; click-to-zoom lazily fetches the full image only when the lightbox opens; graceful degrade to HTTP 503 when MinIO is offline (PRD 14.6, 16.4) | Per-employee document vault distinct from per-entity attachments, virus scanning (ClamAV/S3 lambda), client-side multipart upload for large files (currently 20 MB cap, all proxied), versioning of replaced files, retention policy on soft-deleted blobs, MinIO replication / object lock for compliance, **backfill thumbnails for pre-M36 image rows** (currently they use the fallback path; backfill is a perf opt, not a correctness step), per-content-type max-edge tuning (currently one global config knob), PDF / video inline preview, image gallery view |
| Auth                    | Keycloak 24 OIDC (Authorization Code + PKCE) via `spring-boot-starter-oauth2-resource-server`; **production `start` mode** against a dedicated Postgres backend (`hcm-keycloak-pg`); realm `millers-hcm` auto-imported on first boot; **end-to-end TLS** — nginx terminator on `:8443`, `KC_HOSTNAME_URL=https://localhost:8443`, realm `sslRequired=external`, `:8090` HTTP loophole **closed**; backend's `NimbusJwtDecoder` trusts the self-signed cert via a custom PKCS12 truststore (`nginx/certs/truststore.p12`) wired through a `KeycloakJwtConfig` bean — only the JWKS fetch uses the custom trust, the rest of the JDK trust is untouched; PKCE on the SPA via `keycloak-js`; `realm_access.roles` mapped to Spring `ROLE_*` authorities; `preferred_username` used as principal name; brute-force protection (5 attempts → 15 min lock); password policy `length(12) + digits/upper/lower/specialChars/notUsername/history(3)`; TOTP/MFA path via `CONFIGURE_TOTP` required action (demo: `mfauser`); `SECURITY/Login/TOKEN_ACCEPTED` audit rows dedup'd by `jti` (PRD 14.6) | Production cert (Let's Encrypt / corporate CA — flip `HCM_KEYCLOAK_TRUSTSTORE=""` to fall back to JDK default trust), MFA enforcement on the demo HR/admin accounts (currently only `mfauser` is TOTP-gated), SAML / LDAP federation, social IdPs (Google / Microsoft), token-introspection caching, master-admin bootstrap rotation, signed-URL handoff for MinIO downloads, per-tenant realm, managed-Postgres backing store for `hcm-keycloak-pg` |
| ABAC                    | `AccessScopeService` resolves the caller to either unrestricted (SYSTEM_ADMIN / HR_ADMIN / AUDITOR; HR_SPECIALIST when no org-unit anchor) or scoped. **Two scope shapes** compose: (1) **manager-chain** for `DEPARTMENT_MANAGER` — transitive reports via recursive CTE on `core_hr.employee.manager_id`; (2) **org-unit subtree** for `HR_SPECIALIST` when their linked employee has `scope_org_unit_id` set — recursive CTE on `organization.org_unit.parent_id` → employees by `org_unit_id`. EMPLOYEE → self. Applied to: 5 list endpoints, 5 single-record fetches (404 not 403 when out of scope), **all 4 workflow surfaces** (inbox list + single-record GET + action POST + history — via `WorkflowSubjectResolver` covering 7 subjects), and **all 7 employee-anchored reports** (Headcount / Attrition / Leave / Attendance / Training / Performance + per-team **Payroll Summary** re-aggregated from `payroll_result`). Recruitment funnel stays empty for scoped callers (no employee anchor). Bypass attempt via explicit `?employeeId=` is collapsed to empty (PRD 14.9) | Composable HR_SPECIALIST scoping across multiple org units (currently one anchor per employee), org-unit scoping resolution against historical (non-active) org versions, per-team recruitment view (vacancies have no natural employee anchor — would need a different model), scope on the workflow `/instances?subject=` list-by-subject endpoint (currently relies on caller already having authorized the subject elsewhere) |
| PII encryption          | AES-256-GCM column encryption on `employee.national_id`, `bank_account.iban`, `bank_account.account_number`, and (M31) `report_schedule.webhook_url` + `report_run.webhook_target` via JPA `@AttributeConverter` with versioned `enc:v1:` wire format; plaintext fallback for legacy rows; audit-log payloads redact the URL tail so Slack/Teams bearer tokens don't leak via `audit.audit_log.new_value` (PRD 14.3) | Key rotation (`enc:v2:`) with dual-read window, KMS-backed key storage (currently from env var), encryption of attachment payloads at rest in MinIO, searchable encryption on the encrypted columns |
| HTTP security headers   | XFO DENY, XCTO nosniff, HSTS 1y + subdomains, Referrer-Policy strict-origin-when-cross-origin, strict CSP for the JSON API (PRD 14.4) | CSRF tokens (only relevant once cookie-based session is added), per-endpoint CORS allowlist, signed-URL handoff for MinIO direct-download |
| Mobile                  | Not in milestone 1                            | Flutter app (PRD Section 11) |

## Next sensible step

All **24 PRD core modules** + **Reporting & Analytics (Section 10)** +
**Employee Self-Service (Section 11 web)** + **MinIO attachments
(14.6 / 16.4) on all 6 surfaces with image-preview thumbnails** +
**Scheduled reports with PDF/XLSX export** + **Security hardening
(14.3 / 14.4 / 15.3)** + **Keycloak OIDC + Postgres backend +
password policy + TOTP/MFA path + end-to-end TLS via nginx with
custom truststore on the backend (14.6)** + **Email + Slack/Teams
webhook delivery of scheduled reports with URL encryption at rest
(10.5 / 14.3)** + **ABAC with both manager-chain and org-unit scoping
across list endpoints, single-record fetches, all 4 workflow surfaces,
and all 7 employee-anchored reports including per-team Payroll Summary
(14.9)** are now in place. Remaining roadmap:

- **Mobile** (Flutter, Section 11 native) — same self-service surface,
  native iOS/Android app with attendance check-in.
- **Seniority + anniversary leave bumps** — extends M34's monthly
  accrual walker with Labour Code tenure-bracket lookups +
  hire-anniversary firing in place of the calendar 1st (PRD 8.5.2).
- **Self-service delegation UI** — extends M37 with a manager-facing
  page so DEPARTMENT_MANAGER can set their own deputy without an HR
  ticket. Currently goes through the generic employee PUT endpoint.
- **Backfill thumbnails for pre-M36 image rows** — walk every
  `attachment` with `thumb_object_key=NULL AND content_type LIKE
  'image/%'`, download → thumbnail → put. Perf optimisation only;
  M36's fallback path already handles these rows correctly.
- **Key rotation (`enc:v2:`)** — dual-read window so existing
  `enc:v1:` ciphertext rolls forward to a new key without downtime.
- **Weekend-OT premium** — extends M39 with a `WORKED_ON_WEEKEND`
  anomaly + `weekendMultiplier` rule field so Labour Code Article
  167 applies uniformly to rest-day work (currently only holidays
  fire the premium).

Plus the cross-cutting tasks tracked in the "What's wired vs. stubbed"
table above (AES key rotation, lieu-day rollover for weekend holidays, …).
