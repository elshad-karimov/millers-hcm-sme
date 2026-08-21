# Employee Management — Test Case Pack

**Module:** `core-hr` · **Payroll impact:** indirect (position/FTE/status feed payroll and leave)
**System under test:** `az.millers.hcm.corehr` (employee master, profile tabs, lifecycle, import, self-service change) plus the `web/` HR admin screens.
**Derived from:** `EmployeeController`, `EmployeeService`, `EmployeeRequest/Response`, `AccessScopeService`, `SecurityRoles`, `EmployeeImportService`, `RehireService`, `PersonalInfoChangeService`, `PersonalInfoFieldValidator`, `EmployeeStatusOverlayService`, `NoteVisibility`, `PublicEmployeeBadgeController`, `TenantResolutionFilter`.

---

## 1. Test environment, fixtures and conventions

### 1.1 Tenants

| Key | Tenant id | Purpose |
| --- | --- | --- |
| T1 | `default` | Primary tenant under test (also the `TenantContext.DEFAULT` fallback) |
| T2 | `acme` | Second tenant, used only to prove isolation |

### 1.2 Org hierarchy fixture (tenant T1)

```
OU-ROOT  Millers Holding
 ├─ OU-ENG   Engineering            manager: E-MGR-A
 │   ├─ OU-ENG-BE  Backend          manager: E-MGR-B  (reports to E-MGR-A)
 │   └─ OU-ENG-FE  Frontend         manager: E-MGR-C  (reports to E-MGR-A)
 └─ OU-FIN   Finance                manager: E-MGR-D
```

| Fixture | Employee no | Role linkage | Notes |
| --- | --- | --- | --- |
| E-MGR-A | EMP-00001 | `DEPARTMENT_MANAGER` | Top of the Engineering chain |
| E-MGR-B | EMP-00002 | `DEPARTMENT_MANAGER` | Reports to E-MGR-A |
| E-MGR-C | EMP-00003 | `DEPARTMENT_MANAGER` | Reports to E-MGR-A |
| E-MGR-D | EMP-00004 | `DEPARTMENT_MANAGER` | Finance — outside the Engineering chain |
| E-STAFF-1 | EMP-00010 | `EMPLOYEE` | Reports to E-MGR-B, status `ACTIVE` |
| E-STAFF-2 | EMP-00011 | `EMPLOYEE` | Reports to E-MGR-C, status `ON_PROBATION` |
| E-STAFF-3 | EMP-00012 | `EMPLOYEE` | Reports to E-MGR-D (Finance) |
| E-TERM-1 | EMP-00020 | — | `TERMINATED`, `rehireEligible = true` |
| E-TERM-2 | EMP-00021 | — | `TERMINATED`, `rehireEligible = false` |
| E-RET-1 | EMP-00022 | — | `RETIRED`, `rehireEligible = true` |
| E-ORPHAN | EMP-00030 | — | No manager, no position, no org unit |

Tenant T2 holds one employee, **E-ACME-1** (`EMP-00001` in T2 — same employee number as T1's E-MGR-A, deliberately).

### 1.3 Test users

| User | Roles | Linked employee |
| --- | --- | --- |
| `sysadmin` | `SYSTEM_ADMIN` | — |
| `hradmin` | `HR_ADMIN` | — |
| `hrspec` | `HR_SPECIALIST` | — (no `scope_org_unit_id` → unrestricted) |
| `hrspec-eng` | `HR_SPECIALIST` | employee with `scope_org_unit_id = OU-ENG` |
| `auditor` | `AUDITOR` | — |
| `mgr-a` | `DEPARTMENT_MANAGER` | E-MGR-A |
| `mgr-b` | `DEPARTMENT_MANAGER` | E-MGR-B |
| `mgr-d` | `DEPARTMENT_MANAGER` | E-MGR-D |
| `staff-1` | `EMPLOYEE` | E-STAFF-1 |
| `staff-3` | `EMPLOYEE` | E-STAFF-3 |
| `payroll` | `PAYROLL_SPECIALIST` | — |
| `nolink` | `EMPLOYEE` | **no** linked employee row |
| `anon` | unauthenticated | — |

### 1.4 Conventions

* **Result codes:** `200/201` success, `400` `BadRequestException`, `401` unauthenticated, `403` role denied, `404` not found **or deliberately hidden by scope**.
* Every test asserting a write must also assert the audit row unless stated otherwise (GLOBAL RULE 10).
* Where a case says "hidden as 404", asserting `403` is a **failure** — the code returns 404 on purpose so the response cannot confirm the row exists.
* Priority: **P1** blocking (privacy, hierarchy, tenant, audit, data integrity), **P2** core functional, **P3** edge/cosmetic.

---

## 2. Create employee — `POST /api/employees`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-CRE-001 | Create with the minimum payload | `hradmin` authenticated | POST with `firstName`, `lastName`, `hireDate` only | `201`; body has generated `employeeNo`; `employmentStatus = ON_PROBATION`; `employmentType = PERMANENT`; `ftePercent = 100.00`; `rehireEligible = true` | P2 |
| EM-CRE-002 | Create with the full payload | `hradmin` | POST every field in `EmployeeRequest` with valid values | `201`; every supplied field echoed back unchanged in `EmployeeResponse` | P2 |
| EM-CRE-003 | Status is not caller-controlled | `hradmin` | POST a body that also carries `employmentStatus: ACTIVE` (unknown property) | `201` and stored status is `ON_PROBATION` — the request DTO has no status field, so the value is ignored, never applied | P1 |
| EM-CRE-004 | Employee number is generated, not supplied | `hradmin` | POST with `employeeNo: "EMP-99999"` in the body | `201`; stored `employeeNo` is the next sequence value, not `EMP-99999` | P2 |
| EM-CRE-005 | Onboarding workflow fires on create | `hradmin` | POST a valid new hire | `201`; an onboarding workflow instance exists for the new employee | P2 |
| EM-CRE-006 | Initial history slices are opened at hire date | `hradmin` | POST with `hireDate = 2026-03-01`; then `GET /api/employees/{id}/change-history` | Both an employment slice and a status slice exist, effective `2026-03-01`, reason `Hired` — never an empty history | P1 |
| EM-CRE-007 | CREATE audit entry is written | `hradmin` | POST, then `GET /api/employees/{id}/audit` as `hradmin` | One entry: module `CORE_HR`, entity `Employee`, action `CREATE`, `before = null`, `after` = the created snapshot, actor `hradmin` | P1 |
| EM-CRE-008 | Position occupancy is incremented | Position `POS-1` with `budgetedHeadcount = 2`, `occupied = 0` | POST with `positionId = POS-1` | `201`; `POS-1.occupiedHeadcount = 1`; a PRIMARY `position_occupancy` row is open from the hire date | P2 |
| EM-CRE-009 | Create without a position skips occupancy | `hradmin` | POST with `positionId = null` | `201`; no occupancy row is written; no seat counter changes | P3 |
| EM-CRE-010 | Position at full budget is rejected | `POS-FULL` budget 1, occupied 1 | POST with `positionId = POS-FULL` | `400`; no employee row created; occupancy unchanged | P1 |
| EM-CRE-011 | Plan headcount cap is enforced | Tenant plan caps active headcount at N; N employees exist | POST one more employee | `400` from the plan-limit gate; no row created | P2 |
| EM-CRE-012 | Unknown manager is rejected | `hradmin` | POST with `managerId` = a random UUID | `400` `"Manager not found: <uuid>"` | P2 |
| EM-CRE-013 | Cross-tenant manager reads as unknown | `hradmin` in T1 | POST with `managerId` = E-ACME-1's id (tenant T2) | `400` `"Manager not found"` — the tenant filter makes the row invisible; no cross-tenant link is created | P1 |
| EM-CRE-014 | Duplicate email is rejected, case-insensitively | E-STAFF-1 has `ayten.m@millers.az` | POST with `email = "AYTEN.M@MILLERS.AZ"` | `400` `"An employee with this email already exists"` | P2 |
| EM-CRE-015 | Duplicate national ID is rejected | E-STAFF-1 has `nationalId = AZE1234567` | POST with the same national ID (any case) | `400` `"An employee with this national ID already exists"` — proves the application-side scan works despite per-row AES-GCM IVs | P1 |
| EM-CRE-016 | Duplicate external HR ID is rejected | E-STAFF-1 has `externalHrId = GHRS-88` | POST with `externalHrId = " ghrs-88 "` | `400` `"An employee with external HR ID ghrs-88 already exists"` — trimmed and case-insensitive | P1 |
| EM-CRE-017 | Blank external HR ID stores NULL | `hradmin` | POST with `externalHrId = "   "` | `201`; stored value is `null`, so a second blank create also succeeds (the partial unique index ignores NULLs) | P2 |
| EM-CRE-018 | Delegation trio must be all-or-nothing | `hradmin` | POST with only `delegateManagerId` set | `400` `"Delegation fields must be set together…"` | P2 |
| EM-CRE-019 | Delegation window must be ordered | `hradmin` | POST with `delegateFrom = 2026-06-30`, `delegateTo = 2026-06-01`, valid delegate | `400` `"delegateFrom must be on or before delegateTo"` | P2 |
| EM-CRE-020 | Unknown delegate is rejected | `hradmin` | POST with all three delegation fields, delegate = random UUID | `400` `"Delegate not found: <uuid>"` | P2 |
| EM-CRE-021 | Delegation trio accepted when complete and valid | `hradmin` | POST with delegate = E-MGR-B, from `2026-06-01`, to `2026-06-30` | `201`; all three values persisted | P2 |
| EM-CRE-022 | Future seniority date is rejected | `hradmin` | POST with `seniorityDate` = tomorrow | `400` `"seniorityDate cannot be in the future"` (not a raw DB constraint violation) | P2 |
| EM-CRE-023 | Seniority date equal to today is accepted | `hradmin` | POST with `seniorityDate` = today | `201` | P3 |
| EM-CRE-024 | Approver reference must exist | `hradmin` | POST with `timesheetApproverId` = random UUID | `400` `"Employee not found for timesheet approver: <uuid>"` | P1 |
| EM-CRE-025 | Cross-tenant approver reads as unknown | `hradmin` in T1 | POST with `expenseApproverId` = E-ACME-1's id | `400` `"Employee not found for expense approver"` | P1 |
| EM-CRE-026 | All three approver references validated independently | `hradmin` | POST with a valid timesheet approver but an invalid HR verifier | `400` naming the **HR timesheet verifier**; no row created | P2 |
| EM-CRE-027 | Create is atomic on validation failure | `hradmin` | POST a payload that passes the plan gate but fails approver validation | No employee row, no occupancy bump, no audit entry, no history slice, and the employee-number counter is not consumed permanently | P1 |
| EM-CRE-028 | Hire date in the past is accepted | `hradmin` | POST with `hireDate = 1999-01-01` | `201` — back-dated hires are legitimate during data take-on | P3 |
| EM-CRE-029 | Hire date in the future is accepted | `hradmin` | POST with `hireDate` = +30 days | `201`; the employee is `ON_PROBATION` with a future-dated history slice | P3 |
| EM-CRE-030 | Employee with no email is accepted | `hradmin` | POST omitting `email` | `201`; the duplicate-email check short-circuits on blank | P2 |

## 3. Field validation matrix — create and update

Applies to `POST /api/employees` and `PUT /api/employees/{id}`. All expect `400` with a field-level violation message unless stated.

| ID | Field | Input | Expected | Pri |
| --- | --- | --- | --- | --- |
| EM-VAL-001 | `firstName` | missing / `""` / `"   "` | `400` `@NotBlank` | P2 |
| EM-VAL-002 | `firstName` | 101 characters | `400` `@Size(max=100)` | P3 |
| EM-VAL-003 | `firstName` | exactly 100 characters | accepted | P3 |
| EM-VAL-004 | `lastName` | missing / blank | `400` `@NotBlank` | P2 |
| EM-VAL-005 | `middleName` | 101 characters | `400` | P3 |
| EM-VAL-006 | `hireDate` | missing / `null` | `400` `@NotNull` | P2 |
| EM-VAL-007 | `hireDate` | `"01-03-2026"` (non-ISO) | `400` malformed-payload error, not a 500 | P2 |
| EM-VAL-008 | `birthDate` | today | `400` `@Past` | P3 |
| EM-VAL-009 | `birthDate` | future date | `400` `@Past` | P3 |
| EM-VAL-010 | `birthDate` | `1970-01-01` | accepted | P3 |
| EM-VAL-011 | `nationality` | `"az"` (lowercase) | `400` `"nationality must be an ISO 3166-1 alpha-2 code"` | P3 |
| EM-VAL-012 | `nationality` | `"AZE"` | `400` | P3 |
| EM-VAL-013 | `nationality` | `"AZ"` | accepted | P3 |
| EM-VAL-014 | `nativeLanguage` | `"EN"` (uppercase) | `400` `"nativeLanguage must be ISO 639-1 alpha-2 (lowercase)"` | P3 |
| EM-VAL-015 | `nativeLanguage` | `"az"` | accepted | P3 |
| EM-VAL-016 | `bloodGroup` | `"AB+"`, `"O-"` | accepted | P3 |
| EM-VAL-017 | `bloodGroup` | `"C+"`, `"AB"`, `"o+"` | `400` blood-group pattern message | P3 |
| EM-VAL-018 | `ftePercent` | `0`, `0.00`, `-1` | `400` `"ftePercent must be > 0"` | P2 |
| EM-VAL-019 | `ftePercent` | `100.01`, `150` | `400` `"ftePercent must be ≤ 100"` | P2 |
| EM-VAL-020 | `ftePercent` | `0.01`, `50.00`, `100.00` | accepted | P2 |
| EM-VAL-021 | `ftePercent` | omitted | defaults to `100.00` | P2 |
| EM-VAL-022 | `employmentType` | omitted | defaults to `PERMANENT` | P2 |
| EM-VAL-023 | `employmentType` | `"FREELANCE"` (not in enum) | `400` malformed-enum error, not `500` | P2 |
| EM-VAL-024 | `professionalExperienceYears` | `-0.5` | `400` `"… must be ≥ 0"` | P3 |
| EM-VAL-025 | `professionalExperienceYears` | `70.5` | `400` `"… must be ≤ 70"` | P3 |
| EM-VAL-026 | `professionalExperienceYears` | `0`, `70` | accepted (boundaries inclusive) | P3 |
| EM-VAL-027 | `email` | `"not-an-email"` | `400` `@Email` | P2 |
| EM-VAL-028 | `workEmail` | `"a@b"` vs `"a@b.com"` | validated by `@Email` consistently with `email` | P3 |
| EM-VAL-029 | `email` | 161 characters | `400` `@Size(max=160)` | P3 |
| EM-VAL-030 | `phone` | 33 characters | `400` `@Size(max=32)` | P3 |
| EM-VAL-031 | `extension` | 11 characters | `400` `@Size(max=10)` | P3 |
| EM-VAL-032 | `nationalId` / `taxId` / `socialInsuranceId` | 65 characters | `400` `@Size(max=64)` | P3 |
| EM-VAL-033 | `fullNameLocal` | 301 characters | `400` `@Size(max=300)` | P3 |
| EM-VAL-034 | `externalHrId` | 41 characters | `400` `@Size(max=40)` | P3 |
| EM-VAL-035 | `maritalStatus` | `"COMPLICATED"` | `400` enum-parse error | P3 |
| EM-VAL-036 | `workType` | invalid enum literal | `400` enum-parse error | P3 |
| EM-VAL-037 | `positionId` | `"not-a-uuid"` | `400` type-mismatch error, not `500` | P2 |
| EM-VAL-038 | Multiple violations | blank `firstName` **and** bad `nationality` | `400` listing **both** field errors | P3 |
| EM-VAL-039 | Unicode names | `firstName = "Ayşən"`, `fullNameLocal = "MƏMMƏDOVA Ayşən Elşad qızı"` | accepted and round-tripped byte-identical | P2 |
| EM-VAL-040 | Injection-shaped text | `lastName = "O'Brien'); DROP TABLE core_hr.employee;--"` | Stored literally; the table still exists; the value is HTML-escaped when rendered in the web UI | P1 |
| EM-VAL-041 | Empty JSON body | `POST {}` | `400` naming `firstName`, `lastName`, `hireDate` | P2 |
| EM-VAL-042 | Malformed JSON | truncated body | `400`, not `500` | P3 |

## 4. Employee number allocation

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-NUM-001 | Format is `EMP-` + 5 zero-padded digits | counter at 0 | Create one employee | `employeeNo = "EMP-00001"` | P2 |
| EM-NUM-002 | Numbers increment | 3 sequential creates | Compare | `EMP-00001`, `EMP-00002`, `EMP-00003` | P2 |
| EM-NUM-003 | Counter behind seeded data self-heals | Seed `EMP-00001..EMP-00005` with the tenant counter left at 0 | Create a new employee | Succeeds with `EMP-00006`; a WARN is logged for each skipped number; no unique-constraint failure and no permanent wedge | P1 |
| EM-NUM-004 | Allocation is bounded | Counter behind by more than 100 taken numbers | Create | `IllegalStateException` surfaced as a clean `500`, logged — fails loudly rather than spinning | P3 |
| EM-NUM-005 | Numbers are never reused after a rollback | Create fails on approver validation, then a valid create follows | The failed attempt does not permanently burn a number, and the successful create takes the next free one | P2 |
| EM-NUM-006 | Numbering is per tenant | T1 has `EMP-00001` | Create the first employee in T2 | T2 also gets `EMP-00001`; the two rows coexist | P1 |
| EM-NUM-007 | Concurrent creates never collide | 10 parallel `POST /api/employees` | 10 distinct employee numbers, no duplicate, no failure | P1 |

## 5. Read and search — `GET /api/employees`, `GET /api/employees/{id}`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-SRCH-001 | Default paging | ≥ 25 employees | `GET /api/employees` | `200`; `size = 20`, `page = 0`; sorted by `lastName` ascending | P2 |
| EM-SRCH-002 | Explicit paging | 25 employees | `GET ?page=1&size=10` | Rows 11–20 by last name; `totalElements = 25` | P2 |
| EM-SRCH-003 | Page beyond the end | 5 employees | `GET ?page=99` | `200` with an empty content array, correct `totalElements` — not an error | P3 |
| EM-SRCH-004 | Free-text search | E-STAFF-1 is "Ayten Mammadova", EMP-00010 | `GET ?search=mammad` / `?search=EMP-00010` | Matching rows returned; search is case-insensitive | P2 |
| EM-SRCH-005 | Search with no match | — | `GET ?search=zzzzzz` | `200`, empty content | P3 |
| EM-SRCH-006 | Single status filter (back-compat) | mixed statuses | `GET ?status=ACTIVE` | Only `ACTIVE` rows | P2 |
| EM-SRCH-007 | Multi-valued status filter | mixed statuses | `GET ?status=ACTIVE&status=ON_PROBATION` | Union of both statuses, no others | P2 |
| EM-SRCH-008 | Invalid status literal | — | `GET ?status=BOGUS` | `400`, not `500` | P3 |
| EM-SRCH-009 | `employmentType` filter | mixed types | `GET ?employmentType=CONTRACT` | Only contract employees | P3 |
| EM-SRCH-010 | `departmentOrgUnitId` filter | — | `GET ?departmentOrgUnitId=OU-ENG-BE` | Only employees in that org unit | P2 |
| EM-SRCH-011 | `departmentName` filter | — | `GET ?departmentName=Backend` | Only matching free-text departments | P3 |
| EM-SRCH-012 | `positionId` filter | — | `GET ?positionId=POS-1` | Only holders of that position | P3 |
| EM-SRCH-013 | `managerId` filter | E-MGR-B has 3 reports | `GET ?managerId=<E-MGR-B>` | Exactly the 3 **direct** reports (not the transitive chain) | P2 |
| EM-SRCH-014 | `leaveGroupId` filter | — | `GET ?leaveGroupId=<id>` | Only members of that leave group | P3 |
| EM-SRCH-015 | `costCentre` filter | — | `GET ?costCentre=CC-100` | Only that cost centre | P3 |
| EM-SRCH-016 | `nationality` / `maritalStatus` filters | — | Filter on each | Correct subsets | P3 |
| EM-SRCH-017 | Hire-date range, both bounds | hires spread across 2025–2026 | `GET ?hireDateFrom=2026-01-01&hireDateTo=2026-06-30` | Only hires inside the window; both bounds inclusive | P2 |
| EM-SRCH-018 | Hire-date open-ended range | — | `GET ?hireDateFrom=2026-01-01` alone, then `?hireDateTo=…` alone | Each bound works independently | P3 |
| EM-SRCH-019 | Inverted hire-date range | — | `GET ?hireDateFrom=2026-06-01&hireDateTo=2026-01-01` | `200` with an empty result (or an explicit `400`) — never a `500` | P3 |
| EM-SRCH-020 | Filters AND together | — | `GET ?status=ACTIVE&departmentOrgUnitId=OU-ENG-BE&employmentType=PERMANENT` | Only rows satisfying **all three** | P2 |
| EM-SRCH-021 | Filters compose with ABAC scope | `mgr-b` authenticated | `GET ?status=ACTIVE` | Only ACTIVE employees **within E-MGR-B's chain** — the scope predicate is ANDed, never replaced | P1 |
| EM-SRCH-022 | Get by id | `hradmin` | `GET /api/employees/{E-STAFF-1}` | `200`, full `EmployeeResponse` | P2 |
| EM-SRCH-023 | Get unknown id | `hradmin` | `GET /api/employees/{random-uuid}` | `404` `"Employee not found: <uuid>"` | P2 |
| EM-SRCH-024 | Get malformed id | `hradmin` | `GET /api/employees/abc` | `400`, not `500` | P3 |
| EM-SRCH-025 | Single query, no N+1 | 200 employees | Enable SQL logging; `GET ?page=0&size=50` with 4 filters | One `SELECT` for the page plus one count query; scope and filters land in a single `WHERE` clause | P2 |

## 6. Update employee — `PUT /api/employees/{id}`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-UPD-001 | Update a scalar field | E-STAFF-1 exists | PUT with a changed `phone` | `200`; new value stored; `updatedBy = hradmin` | P2 |
| EM-UPD-002 | UPDATE audit records before and after | — | PUT a changed `departmentName`, then read the audit | One `UPDATE` entry whose `before` holds the old value and `after` the new one | P1 |
| EM-UPD-003 | Nullable field cleared by null | E-STAFF-1 has `preferredName = "Aytu"` | PUT with `preferredName = null` | Stored value becomes `null` (null means clear for the M132/M133 field blocks) | P2 |
| EM-UPD-004 | `rehireEligible` is preserved when omitted | E-TERM-2 has `rehireEligible = false` | PUT without `rehireEligible` | Value stays `false` — `null` means "don't touch" for this field only | P1 |
| EM-UPD-005 | `rehireEligible` can be set explicitly | E-TERM-2 | PUT with `rehireEligible = true` | Value becomes `true`; audited | P2 |
| EM-UPD-006 | Status cannot be changed via PUT | E-STAFF-1 is `ACTIVE` | PUT a full payload | Status remains `ACTIVE` — only `POST /{id}/status` moves it | P1 |
| EM-UPD-007 | Employee number is immutable | — | PUT with a different `employeeNo` field in the body | `employeeNo` unchanged | P2 |
| EM-UPD-008 | Self-manager rejected | E-STAFF-1 | PUT with `managerId = <E-STAFF-1>` | `400` `"An employee cannot be their own manager"` | P1 |
| EM-UPD-009 | Self matrix manager rejected | E-STAFF-1 | PUT with `matrixManagerId = <E-STAFF-1>` | `400` `"An employee cannot be their own matrix manager"` | P2 |
| EM-UPD-010 | Self functional manager rejected | E-STAFF-1 | PUT with `functionalManagerId = <E-STAFF-1>` | `400` `"An employee cannot be their own functional manager"` | P2 |
| EM-UPD-011 | Self timesheet approver rejected | E-STAFF-1 | PUT with `timesheetApproverId = <E-STAFF-1>` | `400` `"An employee cannot be their own timesheet approver"` — a self-approved timesheet would hollow out the approval audit trail | P1 |
| EM-UPD-012 | Self expense approver rejected | E-STAFF-1 | PUT with `expenseApproverId = <E-STAFF-1>` | `400` naming the expense approver | P1 |
| EM-UPD-013 | Self HR verifier rejected | E-STAFF-1 | PUT with `hrTimesheetVerifierId = <E-STAFF-1>` | `400` naming the HR timesheet verifier | P1 |
| EM-UPD-014 | Self-delegation rejected | E-STAFF-1 | PUT with all three delegation fields, delegate = self | `400` `"An employee cannot delegate to themselves"` | P1 |
| EM-UPD-015 | Delegation cleared by nulling all three | E-STAFF-1 has an active delegation | PUT with all three delegation fields null | `200`; delegation cleared (`setCount == 0` is a legal state) | P2 |
| EM-UPD-016 | Own email/national ID not flagged as duplicate | E-STAFF-1 has `nationalId = AZE1234567` | PUT resubmitting the same national ID | `200` — the self row is excluded from the uniqueness scan | P1 |
| EM-UPD-017 | Another employee's national ID is rejected | E-STAFF-2 holds `AZE7654321` | PUT E-STAFF-1 with `AZE7654321` | `400` `"An employee with this national ID already exists"` | P1 |
| EM-UPD-018 | Own external HR ID not flagged | E-STAFF-1 has `GHRS-88` | PUT resubmitting `GHRS-88` | `200` | P2 |
| EM-UPD-019 | Position change moves the seat counters | E-STAFF-1 on `POS-1`; `POS-2` has budget | PUT with `positionId = POS-2` | `POS-1.occupied` −1, `POS-2.occupied` +1; the old PRIMARY occupancy closes today; a new one opens today | P1 |
| EM-UPD-020 | Position change into a full seat is rejected | `POS-FULL` at budget | PUT E-STAFF-1 with `positionId = POS-FULL` | `400`; **no** counters move and the old occupancy stays open | P1 |
| EM-UPD-021 | Same-position update is a no-op at the gate | E-STAFF-1 on `POS-1` | PUT with the same `positionId` | `200`; occupancy counters unchanged; no new occupancy row | P2 |
| EM-UPD-022 | Position cleared to null | E-STAFF-1 on `POS-1` | PUT with `positionId = null` | `POS-1.occupied` −1; occupancy closed; no new row opened | P2 |
| EM-UPD-023 | Position assignment auto-grants required items | `POS-2` has mandatory profile items | PUT moving E-STAFF-1 to `POS-2` | The new position's mandatory items appear in HR's PENDING queue | P2 |
| EM-UPD-024 | Update out-of-scope employee is hidden | `mgr-b` authenticated | PUT E-STAFF-3 (Finance) | `403` from the role gate (`DEPARTMENT_MANAGER` cannot write) — and if the role were granted, `update()` calls `get()` so the row would surface as `404`, never as a successful write | P1 |
| EM-UPD-025 | Update unknown id | `hradmin` | PUT a random UUID | `404` | P2 |
| EM-UPD-026 | Concurrent updates do not lose data | Two clients read E-STAFF-1 | Both PUT different fields simultaneously | No lost-update corruption: either optimistic-lock failure or last-write-wins with both audit rows present — document actual behaviour | P2 |
| EM-UPD-027 | Manager loop is not created | E-MGR-B reports to E-MGR-A | PUT E-MGR-A with `managerId = <E-MGR-B>` | **Known gap to confirm:** the service only blocks direct self-reference. Verify whether a 2-node cycle is created, and that `descendantsIncluding` does not infinite-loop if it is | P1 |

## 7. Status transitions — `POST /api/employees/{id}/status`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-STA-001 | Probation → active | E-STAFF-2 is `ON_PROBATION` | POST `{ "newStatus": "ACTIVE", "reason": "Probation passed" }` | `200`; status `ACTIVE` | P2 |
| EM-STA-002 | Status slice recorded | as above | `GET /api/employees/{id}/change-history` | A status slice effective **today** carrying the supplied reason | P1 |
| EM-STA-003 | STATUS_CHANGE audit written | as above | Read the audit | Action `STATUS_CHANGE`, `before = {ON_PROBATION, null}`, `after = {ACTIVE, "Probation passed"}` | P1 |
| EM-STA-004 | Same-status transition rejected | E-STAFF-1 is `ACTIVE` | POST `newStatus = ACTIVE` | `400` `"Employee is already in status ACTIVE"`; no slice, no audit row | P2 |
| EM-STA-005 | Transition to every enum value | E-STAFF-1 | Walk `ACTIVE → SUSPENDED → ON_LEAVE → ACTIVE → TERMINATED` | Each accepted; each writes a slice and an audit row in order | P2 |
| EM-STA-006 | Reason is optional but recorded when given | — | POST with and without `reason` | Both accepted; the audit `after` reflects the reason or `null` | P3 |
| EM-STA-007 | Terminated employee reactivation | E-TERM-1 is `TERMINATED` | POST `newStatus = ACTIVE` | Document actual behaviour. Reactivating a terminated row bypasses the rehire flow (no `previous_employee_id`, no plan-seat check, no fresh probation) — flag as a finding if permitted | P1 |
| EM-STA-008 | Invalid status literal | — | POST `newStatus = "FIRED"` | `400`, not `500` | P3 |
| EM-STA-009 | Missing `newStatus` | — | POST `{}` | `400` `@NotNull` | P3 |
| EM-STA-010 | Status change on unknown id | — | POST to a random UUID | `404` | P2 |
| EM-STA-011 | Status change does not touch payroll history retroactively | E-STAFF-1 has a finalised payroll period | POST `newStatus = NON_ACTIVE` | Prior payroll results are unchanged; only forward periods are affected | P1 |
| EM-STA-012 | `isAvailableForRostering` honoured downstream | — | Set each of `TERMINATED`, `RETIRED`, `SUSPENDED`, `GARDEN_LEAVE`, `NON_ACTIVE` | Employee disappears from roster/shift candidate lists; `ACTIVE`, `ON_LEAVE`, `MATERNITY_LEAVE` remain rosterable | P2 |

## 8. Status overlays — `/api/employees/{id}/status-overlays`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-OVL-001 | Apply an overlay | E-STAFF-1 is `ACTIVE` | POST `{ status: ON_BUSINESS_TRIP, effectiveFrom: today }` | `201`; primary status stays `ACTIVE`; the overlay is listed as open | P2 |
| EM-OVL-002 | Two different overlays coexist | E-STAFF-1 has an open business-trip overlay | Apply `MATERNITY_LEAVE` | Both overlays open simultaneously | P2 |
| EM-OVL-003 | Primary-only status rejected as an overlay | — | POST `status = TERMINATED` (also `ACTIVE`, `ON_PROBATION`, `RETIRED`, `CONTRACTOR`, `INTERN`) | `400` `"<STATUS> is a primary status and cannot be used as an overlay"` | P1 |
| EM-OVL-004 | Re-applying the same overlay on a later date closes the prior one | Open `ON_LEAVE` from `2026-03-01` | Apply `ON_LEAVE` from `2026-04-01` | The prior overlay is closed on `2026-04-01`; exactly one open overlay of that status remains | P1 |
| EM-OVL-005 | Re-applying on the identical date replaces the prior row | Open `ON_LEAVE` from `2026-03-01` | Apply `ON_LEAVE` from `2026-03-01` | The prior row is deleted and replaced — no duplicate open overlay, no partial-unique-index violation | P2 |
| EM-OVL-006 | Closing an overlay | Open overlay | POST `/{overlayId}/close` with a close date | `200`; overlay shows closed with that date | P2 |
| EM-OVL-007 | Closing twice is rejected | Closed overlay | POST close again | `400` `"Overlay is already closed"` | P2 |
| EM-OVL-008 | Close date before `effectiveFrom` rejected | Overlay from `2026-04-01` | Close on `2026-03-01` | `400` | P2 |
| EM-OVL-009 | Overlay on an unknown employee | — | POST to a random employee UUID | `400` `"Employee not found: <uuid>"` | P2 |
| EM-OVL-010 | Overlay respects ABAC scope | `mgr-d` (Finance) | Read/apply an overlay on E-STAFF-1 (Engineering) | Blocked — the row is not accessible | P1 |
| EM-OVL-011 | Overlay delete is audited | Open overlay | DELETE `/{overlayId}` | Removal is recorded in the audit trail with the actor | P1 |
| EM-OVL-012 | Overlay affects payroll/leave as designed | `MATERNITY_LEAVE` overlay open across a payroll period | Run payroll | The overlay is reflected per the payroll rules (paid/unpaid), and the result is reproducible | P1 |

## 9. Hierarchy, scoping and privacy (ABAC)

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-SCOPE-001 | HR admin is unrestricted | `hradmin` | `GET /api/employees?size=200` | Every employee in T1 is returned | P1 |
| EM-SCOPE-002 | System admin is unrestricted | `sysadmin` | Same | Every employee returned | P1 |
| EM-SCOPE-003 | Auditor is unrestricted read-only | `auditor` | `GET` list and `GET /{id}` | `200` for both; every write endpoint returns `403` | P1 |
| EM-SCOPE-004 | Manager sees the transitive chain plus self | `mgr-a` | `GET /api/employees?size=200` | E-MGR-A, E-MGR-B, E-MGR-C and all their reports — **and nothing from Finance** | P1 |
| EM-SCOPE-005 | Manager cannot see a peer's team | `mgr-d` (Finance) | `GET /api/employees` | Only the Finance chain; E-STAFF-1/2 absent | P1 |
| EM-SCOPE-006 | Direct fetch outside the chain is hidden as 404 | `mgr-d` | `GET /api/employees/{E-STAFF-1}` | `404` — **not** `403`, so the response does not confirm the row exists | P1 |
| EM-SCOPE-007 | Sub-manager sees only their own subtree | `mgr-b` | `GET /api/employees` | E-MGR-B plus their reports; E-MGR-A and E-MGR-C's team absent | P1 |
| EM-SCOPE-008 | Manager can read themselves | `mgr-b` | `GET /api/employees/{E-MGR-B}` | `200` — self is included in the chain | P2 |
| EM-SCOPE-009 | Org-unit-scoped HR specialist | `hrspec-eng` (anchor `OU-ENG`) | `GET /api/employees?size=200` | Only employees in `OU-ENG` and its descendants (`OU-ENG-BE`, `OU-ENG-FE`); Finance absent | P1 |
| EM-SCOPE-010 | Org-unit scope follows the tree recursively | Add `OU-ENG-BE-QA` under `OU-ENG-BE` with one employee | `hrspec-eng` lists employees | The new grandchild's employee is included | P1 |
| EM-SCOPE-011 | Unanchored HR specialist stays unrestricted | `hrspec` (no `scope_org_unit_id`) | `GET /api/employees` | Every employee — documented back-compat behaviour; confirm it is intentional for this deployment | P1 |
| EM-SCOPE-012 | Empty org-unit scope sees nothing | HR specialist anchored to an org unit with no employees and no descendants | `GET /api/employees` | `200` with an empty page (the scope resolves to a disjunction) — never a full unfiltered list | P1 |
| EM-SCOPE-013 | Unlinked user sees nothing | `nolink` | `GET /api/employees` | `403` (role gate) — and, if the role were widened, an empty page, never all rows | P1 |
| EM-SCOPE-014 | Manager with no reports sees only self | Manager whose reports were all terminated and reassigned | `GET /api/employees` | Exactly one row: themselves | P2 |
| EM-SCOPE-015 | Scope recomputes after a reorg | `mgr-b` lists their team; E-STAFF-1 is then moved under E-MGR-C | `mgr-b` lists again | E-STAFF-1 disappears from `mgr-b`'s scope immediately (scope is stateless, recomputed per call) | P1 |
| EM-SCOPE-016 | Scope recomputes after a promotion | E-STAFF-1 is made the manager of two new hires | `staff-1` lists employees | Still scoped to `EMPLOYEE` (self only) until the `DEPARTMENT_MANAGER` role is granted; then the chain appears | P1 |
| EM-SCOPE-017 | HR-admin who also line-manages keeps the wide view | A user holding both `HR_ADMIN` and `DEPARTMENT_MANAGER` | `GET /api/employees` | Unrestricted — wide roles win | P2 |
| EM-SCOPE-018 | Employee role cannot browse the directory | `staff-1` | `GET /api/employees` | `403` — `EMPLOYEE` is not in `READ_HR_PLUS_MANAGERS` | P1 |
| EM-SCOPE-019 | Employee cannot fetch their own record via the HR endpoint | `staff-1` | `GET /api/employees/{E-STAFF-1}` | `403` — self-service must go through the ESS endpoints, not the HR directory | P1 |
| EM-SCOPE-020 | Employee cannot fetch a colleague's record | `staff-1` | `GET /api/employees/{E-STAFF-2}` | `403` | P1 |
| EM-SCOPE-021 | Scope is not bypassable through the filter params | `mgr-d` | `GET /api/employees?managerId=<E-MGR-B>` | Empty page — the caller-supplied filter is ANDed with the scope, never used to widen it | P1 |
| EM-SCOPE-022 | Scope applies to every sub-resource | `mgr-d` | Call each of `/documents`, `/notes`, `/assets`, `/dependents`, `/education`, `/certifications`, `/health`, `/addresses`, `/identifications`, `/emergency-contacts`, `/assignments`, `/approval-limits`, `/change-history` for E-STAFF-1 | Every one is blocked (`403` by role or `404`/`400` by scope) — no sub-resource leaks a profile the parent record hides | P1 |
| EM-SCOPE-023 | Change-history endpoint respects scope | `mgr-d` | `GET /api/employees/{E-STAFF-1}/change-history` | Blocked; no salary, position or status history leaks | P1 |
| EM-SCOPE-024 | `/api/me/change-history` returns only self | `staff-1` | `GET /api/me/change-history` | Only E-STAFF-1's own history | P1 |

## 10. Role permission matrix (RBAC)

Run each row for every listed role. `✓` = allowed, `✗` = `403`.

| ID | Endpoint | SYSTEM_ADMIN | HR_ADMIN | HR_SPECIALIST | DEPT_MANAGER | AUDITOR | EMPLOYEE | PAYROLL_SPEC | anon | Pri |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EM-RBAC-001 | `GET /api/employees` | ✓ | ✓ | ✓ | ✓ (scoped) | ✓ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-002 | `GET /api/employees/{id}` | ✓ | ✓ | ✓ | ✓ (scoped) | ✓ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-003 | `POST /api/employees` | **✗** | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-004 | `PUT /api/employees/{id}` | **✗** | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-005 | `POST /api/employees/{id}/status` | **✗** | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-006 | `GET /api/employees/{id}/audit` | ✓ | ✓ | **✗** | ✗ | ✓ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-007 | `GET /api/employees/{id}/qr` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | `401` | P2 |
| EM-RBAC-008 | `POST /api/employees/import` | per `ROLES` const | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-009 | `POST /api/employees/rehire` | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-010 | `GET/POST /api/employees/{id}/documents` | ✓ | ✓ | ✓ | per controller `READ/WRITE_ROLES` | read only | ✗ | ✗ | `401` | P1 |
| EM-RBAC-011 | `POST /api/employees/{id}/approval-limits` | ✓ | ✓ | **✗** | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-012 | `DELETE /api/employees/{id}/approval-limits/{lid}` | ✓ | ✓ | **✗** | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-013 | `POST /api/employees/{id}/required-documents` | ✓ | ✓ | **✗** | ✗ | ✗ | ✗ | ✗ | `401` | P1 |
| EM-RBAC-014 | `GET/POST /api/employees/{id}/certifications` | per `CERT_*` const | ✓ | ✓ | verify | ✗ | ✗ | ✗ | `401` | P2 |
| EM-RBAC-015 | `GET/PUT /api/employees/{id}/health` | per `HEALTH_ROLES` (incl. `OCCUPATIONAL_HEALTH`) | ✓ | verify | verify | verify | ✗ | ✗ | `401` | P1 |
| EM-RBAC-016 | `GET /api/public/employees/{id}/badge` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **✓ (anonymous)** | P1 |

**EM-RBAC-017 (P1) — SYSTEM_ADMIN cannot write employees.** `EmployeeController` gates create/update/status on the inline `hasAnyRole('HR_ADMIN','HR_SPECIALIST')` rather than `SecurityRoles.WRITE_HR`, so `SYSTEM_ADMIN` is excluded from exactly these three endpoints while it is included in every other HR write in the codebase. Confirm this is intended; if it is, it belongs in the role documentation, and if not it is an inconsistency the shared constants exist to prevent.

**EM-RBAC-018 (P1) — HR_SPECIALIST cannot read the employee audit log.** Deliberate per `SecurityRoles.READ_AUDIT`: an HR specialist must not be able to browse their own log entries. Verify the `403` and that the UI hides the audit tab for that role rather than showing an error.

**EM-RBAC-019 (P2) — Role removal takes effect on the next token.** Revoke `DEPARTMENT_MANAGER` from `mgr-b` in Keycloak; with the old token still valid the scope may persist until expiry. Document the window and confirm it matches the token TTL policy.

**EM-RBAC-020 (P1) — No endpoint accepts a caller-supplied tenant or role hint.** Replay each write with `X-Tenant-Id`, `X-Roles` and a `tenantId` body field; all are ignored — tenant comes from the JWT issuer only.

## 11. Tenant isolation

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-TEN-001 | List is tenant-scoped | T1 and T2 both populated | `hradmin@T1` lists employees | Only T1 rows; E-ACME-1 absent | P1 |
| EM-TEN-002 | Direct fetch across tenants | — | `hradmin@T1` `GET /api/employees/{E-ACME-1}` | `404` | P1 |
| EM-TEN-003 | Update across tenants | — | `hradmin@T1` PUTs E-ACME-1 | `404`; T2's row is unchanged | P1 |
| EM-TEN-004 | Status change across tenants | — | `hradmin@T1` POSTs a status change on E-ACME-1 | `404`; no audit row in either tenant | P1 |
| EM-TEN-005 | Duplicate email across tenants is allowed | E-STAFF-1 in T1 has `a@b.com` | Create an employee in T2 with `a@b.com` | `201` — uniqueness is per tenant | P1 |
| EM-TEN-006 | Duplicate national ID across tenants is allowed | — | Same national ID in T1 and T2 | Both accepted; neither scan sees the other tenant's rows | P1 |
| EM-TEN-007 | Duplicate external HR ID across tenants is allowed | — | Same `externalHrId` in both tenants | Both accepted | P1 |
| EM-TEN-008 | Cross-tenant foreign keys are impossible | — | Try `managerId`, `positionId`, `orgUnitId`, `leaveGroupId`, `payrollGroupId`, all three approver ids pointing at T2 rows | Every one rejected with "not found" | P1 |
| EM-TEN-009 | Import is tenant-scoped | — | `hradmin@T2` imports a file | Rows land in T2 only; the T1 count is unchanged | P1 |
| EM-TEN-010 | Audit history is tenant-scoped | — | `hradmin@T1` reads the audit for a T2 employee id | Empty or `404` — never T2 audit rows | P1 |
| EM-TEN-011 | Search filters cannot cross tenants | — | `hradmin@T1` filters by a T2 org unit / position id | Empty page | P1 |
| EM-TEN-012 | Tenant context is cleared between requests | Pooled threads | Alternate T1 and T2 requests on the same connection under load | No bleed: each response contains only its own tenant's rows | P1 |
| EM-TEN-013 | Unknown JWT issuer does not fall back to the default tenant | Token from an unregistered issuer | Any employee call | Rejected at authentication. Confirm it never reaches the controller with the context bound to `DEFAULT` — that would expose the default tenant's directory | P1 |

## 12. Public badge and QR endpoints

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-QR-001 | QR renders a PNG | `staff-1` authenticated | `GET /api/employees/{id}/qr` | `200`, `Content-Type: image/png`, valid PNG bytes, `Cache-Control: private, max-age=300` | P2 |
| EM-QR-002 | Default size | — | No `size` param | 300 px image | P3 |
| EM-QR-003 | Size clamped low | — | `?size=10` | Rendered at 50 px, not an error | P3 |
| EM-QR-004 | Size clamped high | — | `?size=99999` | Rendered at 1024 px | P3 |
| EM-QR-005 | Non-numeric size | — | `?size=abc` | `400`, not `500` | P3 |
| EM-QR-006 | QR payload contains no PII | — | Decode the PNG | Exactly `MILLERS-HCM\|<uuid>\|<employeeNo>` — no name, no salary, no national ID | P1 |
| EM-QR-007 | QR is available to any authenticated user | `payroll`, `staff-1` | `GET .../qr` for another employee | `200` — deliberately open; confirm no PII is exposed by the image itself | P2 |
| EM-QR-008 | QR requires authentication | `anon` | `GET .../qr` | `401` | P1 |
| EM-QR-009 | Public verify accepts a valid payload | — | `GET /api/public/employees/verify?payload=MILLERS-HCM\|<uuid>\|EMP-00010` | `200`; employee number, full name, position title, department, status | P2 |
| EM-QR-010 | Public verify rejects a foreign prefix | — | `payload=OTHER\|<uuid>\|X` | `400` `"Unrecognised QR payload format"` | P2 |
| EM-QR-011 | Public verify rejects a malformed UUID | — | `payload=MILLERS-HCM\|not-a-uuid\|X` | `400` `"QR payload contains an invalid employee ID"` | P2 |
| EM-QR-012 | Public verify on an unknown id | — | Valid format, random UUID | `404` | P2 |
| EM-QR-013 | Public badge returns no sensitive fields | `anon` | `GET /api/public/employees/{id}/badge` | Response contains **only** employeeNo, full name, position, department, status — never salary, national ID, email, phone, birth date or manager | P1 |
| EM-QR-014 | Public badge resolves against the DEFAULT tenant only | E-ACME-1 exists in T2 | `anon` `GET /api/public/employees/{E-ACME-1}/badge` | `404` — anonymous requests leave the tenant context unbound and fall back to `TenantContext.DEFAULT`, so non-default tenants are unreachable. **Confirm this is the intended product behaviour**: badge scanning is effectively single-tenant, and conversely any `default`-tenant employee's name, position, department and employment status are readable by anyone who knows or guesses the UUID | P1 |
| EM-QR-015 | Public endpoint enumeration resistance | `anon` | Issue 1 000 badge requests with random UUIDs | All `404`; rate limiting or throttling applies; no timing side channel distinguishes "exists in another tenant" from "does not exist" | P1 |
| EM-QR-016 | Terminated employee badge | E-TERM-1 | `anon` badge lookup | Returns `TERMINATED` status. Confirm the product wants a terminated person's name and department exposed anonymously; if not, the endpoint should 404 for terminal statuses | P1 |

## 13. Employee documents and required documents

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-DOC-001 | Upload a document | `hradmin`, E-STAFF-1 | `POST /api/employees/{id}/documents` | `201`; document listed with its category and metadata | P2 |
| EM-DOC-002 | List documents | Two documents exist | `GET .../documents` | Both returned, newest first | P2 |
| EM-DOC-003 | Fetch a single document | — | `GET .../documents/{docId}` | `200` with the correct payload | P2 |
| EM-DOC-004 | Document for an unknown employee | — | `POST` to a random employee UUID | `400` `"Employee not found: <uuid>"` | P2 |
| EM-DOC-005 | Document id from another employee | Doc D1 belongs to E-STAFF-1 | `GET /api/employees/{E-STAFF-2}/documents/{D1}` | `404` — the document must be re-checked against the path employee, not returned by id alone | P1 |
| EM-DOC-006 | Document access respects ABAC scope | `mgr-d` | `GET /api/employees/{E-STAFF-1}/documents` | Blocked | P1 |
| EM-DOC-007 | Confidential documents hidden from unauthorised roles | A document flagged confidential/HR-only | List as a department manager | The confidential row is absent from the list and direct fetch returns `404` | P1 |
| EM-DOC-008 | Document delete is soft/traceable | — | `DELETE .../documents/{docId}` | Removal is recorded with actor and timestamp; verify whether the blob is retained per the retention policy | P1 |
| EM-DOC-009 | Oversized upload rejected | File over the configured limit | POST | `413`/`400` with a clear message, not a `500` | P2 |
| EM-DOC-010 | Disallowed file type rejected | `.exe` upload | POST | Rejected | P2 |
| EM-DOC-011 | Filename traversal is neutralised | Filename `../../etc/passwd` | POST | Stored under a sanitised name; nothing is written outside the storage root | P1 |
| EM-DOC-012 | Required document created | `hradmin` | `POST .../required-documents` with a label | `201`; status `PENDING` | P2 |
| EM-DOC-013 | Required document without a label | — | POST with `label` omitted | `400` `"label is required"` | P3 |
| EM-DOC-014 | Satisfy a requirement | Pending requirement | `POST .../required-documents/{id}/satisfy` | Status becomes satisfied; who and when are recorded | P2 |
| EM-DOC-015 | Waive a requirement | Pending requirement | `POST .../required-documents/{id}/waive` with a reason | Status becomes waived, reason and actor stored, entry audited | P1 |
| EM-DOC-016 | Only HR_ADMIN may satisfy or waive | `hrspec` | POST satisfy and waive | `403` for both (`WRITE_HR_ADMIN_ONLY`) | P1 |
| EM-DOC-017 | Document expiry alerts | A document expiring in 7 days | Run the expiry job | The employee and HR are notified once; a second run does not re-notify | P2 |

## 14. Profile sub-tabs

Run each block for E-STAFF-1 as `hradmin` unless noted.

| ID | Area | Case | Expected | Pri |
| --- | --- | --- | --- | --- |
| EM-PROF-001 | Identifications | Create passport with number, issue and expiry | `201`; the number is stored encrypted at rest | P1 |
| EM-PROF-002 | Identifications | Verify an identification (`/verify`) | Status becomes verified; verifier and timestamp recorded | P2 |
| EM-PROF-003 | Identifications | Expiry before issue date | `400` | P2 |
| EM-PROF-004 | Identifications | Duplicate document number for the same type | Rejected or explicitly allowed — document the rule | P2 |
| EM-PROF-005 | Identifications | Delete | Removed and audited | P2 |
| EM-PROF-006 | Addresses | Create HOME and WORK addresses | Both persist and are typed correctly | P2 |
| EM-PROF-007 | Addresses | Two HOME addresses | Either the prior one closes or the second is rejected — document; ESS address changes must resolve to exactly one current HOME slice | P1 |
| EM-PROF-008 | Addresses | Invalid country code (`"AZE"`) | `400` | P3 |
| EM-PROF-009 | Emergency contacts | Create, list, update, delete | Full CRUD works; the primary contact is identifiable | P2 |
| EM-PROF-010 | Emergency contacts | Delete the only contact | Allowed or blocked per policy — document; the ESS `emergencyContactName/Phone` change path must still resolve | P2 |
| EM-PROF-011 | Dependents | Add a dependent with relationship and birth date | `201` | P2 |
| EM-PROF-012 | Dependents | Dependent birth date in the future | `400` | P3 |
| EM-PROF-013 | Dependents | Eligibility end reason recorded on removal | Reason persisted, not a silent delete — benefits eligibility depends on it | P1 |
| EM-PROF-014 | Education | Add a record with level and institution | `201` | P2 |
| EM-PROF-015 | Education | Verify (`/verify`) | Verification status, verifier and date recorded | P2 |
| EM-PROF-016 | Education | Graduation year before birth year | `400` or flagged | P3 |
| EM-PROF-017 | Work experience | Add with from/to dates | `201` | P2 |
| EM-PROF-018 | Work experience | `to` before `from` | `400` | P2 |
| EM-PROF-019 | Work experience | Overlapping employments | Allowed (legitimate) — confirm no crash in the tenure calculation | P3 |
| EM-PROF-020 | Certifications | Add with an expiry date | `201`; feeds the expiry-alert source | P2 |
| EM-PROF-021 | Certifications | Expiry alert fires | Alert raised at the configured lead time, exactly once | P2 |
| EM-PROF-022 | Certifications | Verify and delete | Both work and are audited | P2 |
| EM-PROF-023 | Health | Read health record as `OCCUPATIONAL_HEALTH` | `200` | P1 |
| EM-PROF-024 | Health | Read health record as `DEPARTMENT_MANAGER` | Verify against `HEALTH_ROLES` — a line manager must not read medical data unless the policy explicitly allows it | P1 |
| EM-PROF-025 | Health | Read health record as `AUDITOR` | Confirm intended: audit access to medical data is a privacy decision, not a default | P1 |
| EM-PROF-026 | Vaccinations | CRUD | Works; gated identically to health | P2 |
| EM-PROF-027 | Assets | Issue an asset, then close it | Both states recorded with an event history | P2 |
| EM-PROF-028 | Assets | Reissue a closed asset | New event appended; the prior history is preserved | P2 |
| EM-PROF-029 | Notes — `HR_ONLY` | List notes as `DEPARTMENT_MANAGER` | The `HR_ONLY` note is silently dropped from the list | P1 |
| EM-PROF-030 | Notes — `MANAGER_ONLY` | List notes as `HR_SPECIALIST` | The note is dropped — `MANAGER_ONLY` deliberately excludes HR specialists | P1 |
| EM-PROF-031 | Notes — `SYSTEM_ADMIN_ONLY` | List as `HR_ADMIN` | The note is dropped | P1 |
| EM-PROF-032 | Notes — `ALL_HR` | List as `HR_SPECIALIST`, `HR_ADMIN`, `DEPARTMENT_MANAGER`, `SYSTEM_ADMIN` | Visible to all four | P2 |
| EM-PROF-033 | Notes — `AUDITOR` | List any visibility level as `auditor` | No note is visible (`AUDITOR` matches no branch) — confirm intended | P1 |
| EM-PROF-034 | Notes — direct fetch bypass | Note N1 is `HR_ONLY` | `GET` N1 by id as a department manager | Blocked — filtering must not be list-only | P1 |
| EM-PROF-035 | Rewards | CRUD | Works and is audited | P2 |
| EM-PROF-036 | Approval limits | Create a limit, then delete it | Both audited; limits are enforced by the workflow engine | P1 |
| EM-PROF-037 | Approval limits | Overlapping limits of the same type | Documented behaviour — a stale or duplicated limit would let an approval exceed its ceiling | P1 |
| EM-PROF-038 | Assignments | Create with `effectiveFrom` and `assignmentType` | `201` | P2 |
| EM-PROF-039 | Assignments | Missing `effectiveFrom` | `400` `"effectiveFrom is required"` | P2 |
| EM-PROF-040 | Assignments | Missing `assignmentType` | `400` `"assignmentType is required"` | P2 |
| EM-PROF-041 | Assignments | Attempt to change `employeeId` on update | `400` `"employeeId is immutable"` | P1 |
| EM-PROF-042 | Assignments | Unknown position | `400` `"Position not found: <uuid>"` | P2 |
| EM-PROF-043 | Assignments | Close an already-closed assignment | `400` `"Assignment is already closed"` | P2 |
| EM-PROF-044 | Assignments | Close date before `effectiveFrom` | `400` | P2 |
| EM-PROF-045 | All sub-tabs | Every create/update/delete writes an audit row naming the actor | Verified for each of the 12 sub-resources | P1 |

## 15. Bulk import — `POST /api/employees/import`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-IMP-001 | Valid import commits | `.xlsx` with 10 valid rows | POST with `dryRun=false` | 10 employees created; job records 10 successes, 0 errors | P2 |
| EM-IMP-002 | Dry run commits nothing | Same file | POST with `dryRun=true` | `200` preview with per-row validation; **zero** employees created; a job row still persists for audit | P1 |
| EM-IMP-003 | Missing required header | File without `hireDate` | POST | Rejected with a clear message naming the missing column | P2 |
| EM-IMP-004 | Header matching is case- and space-tolerant | Headers `"  FirstName "`, `"LASTNAME"` | POST | Parsed correctly | P3 |
| EM-IMP-005 | Unrecognised columns are ignored | File with a `salary` column | POST | Column silently ignored; no field is written from it — confirm this is acceptable and that HR is warned, since a silently dropped column reads as a successful import | P1 |
| EM-IMP-006 | Partial success | 10 rows, 3 invalid | POST with `dryRun=false` | 7 created, 3 in the error report with row numbers and reasons; the job is not rolled back wholesale | P2 |
| EM-IMP-007 | Row-level validation matches the API | Row with `ftePercent = 150` | POST | That row errors with the same message the REST API gives | P2 |
| EM-IMP-008 | Duplicate email inside the file | Two rows share an email | POST | The second row errors; the first is created | P1 |
| EM-IMP-009 | Duplicate against existing data | Row's national ID already exists | POST | That row errors `"An employee with this national ID already exists"` | P1 |
| EM-IMP-010 | Every imported row is audited | 10-row import | Read audit | 10 `CREATE` entries plus the import-job entry, all attributed to the importing user | P1 |
| EM-IMP-011 | Import honours the plan cap | Plan allows 5 more; file has 10 rows | POST | The first 5 succeed, the rest error on the plan gate — the cap is not bypassed by the bulk path | P1 |
| EM-IMP-012 | Import honours position budgets | Rows target a full position | POST | Those rows error; occupancy never exceeds budget | P1 |
| EM-IMP-013 | Date cells in Excel date format | `hireDate` as a real Excel date cell | POST | Parsed correctly (not as a serial number) | P2 |
| EM-IMP-014 | Date cells as text | `hireDate` as `"2026-03-01"` text | POST | Parsed correctly | P2 |
| EM-IMP-015 | Ambiguous date text | `"03/01/2026"` | POST | Either parsed by a documented rule or rejected — never silently interpreted as the wrong month | P1 |
| EM-IMP-016 | Empty sheet | Header row only | POST | `200` with 0 processed; not a crash | P3 |
| EM-IMP-017 | Non-spreadsheet upload | `.pdf` renamed to `.xlsx` | POST | `400` with a clear message, not a `500` | P2 |
| EM-IMP-018 | Large file | 5 000 rows | POST | Completes within the agreed SLA; memory stays bounded; progress/status is observable via `/history` | P2 |
| EM-IMP-019 | Import history | Several jobs run | `GET /api/employees/import/history` | Jobs listed newest first with status, counts and the actor | P2 |
| EM-IMP-020 | Import history is tenant-scoped | Jobs in both tenants | `GET /history` as `hradmin@T1` | Only T1 jobs | P1 |
| EM-IMP-021 | Formula cells | A cell containing `=1+1` | POST | Evaluated or rejected deterministically; no formula injection into stored text | P2 |
| EM-IMP-022 | Leading-quote CSV injection | `firstName = "=cmd|' /C calc'!A0"` | Import, then export | The value is neutralised on export so a spreadsheet does not execute it | P1 |

## 16. Rehire — `POST /api/employees/rehire`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-RHR-001 | Rehire a terminated employee | E-TERM-1 (`TERMINATED`, eligible) | POST with `newHireDate` after the prior hire date | `201`; a **new** employee row with a new employee number, `previousEmployeeId` set, status `ON_PROBATION` | P2 |
| EM-RHR-002 | Rehire a retired employee | E-RET-1 (`RETIRED`) | POST | `201` — both `TERMINATED` and `RETIRED` are valid sources | P2 |
| EM-RHR-003 | Rehire from an active employee is rejected | E-STAFF-1 (`ACTIVE`) | POST | `400` `"Can only rehire from a TERMINATED or RETIRED employee; EMP-00010 is ACTIVE"` | P1 |
| EM-RHR-004 | Ineligible employee is rejected | E-TERM-2 (`rehireEligible = false`) | POST | `400` `"… is flagged not rehire-eligible"` | P1 |
| EM-RHR-005 | Hire date before the prior hire date is rejected | E-TERM-1 hired `2020-01-01` | POST `newHireDate = 2019-01-01` | `400` `"newHireDate must be on or after the prior hire date"` | P2 |
| EM-RHR-006 | Missing hire date | — | POST without `newHireDate` | `400` (`@NotNull`) | P2 |
| EM-RHR-007 | Unknown previous employee | — | POST with a random UUID | `404` `"Previous employee not found: <uuid>"` | P2 |
| EM-RHR-008 | Cross-tenant rehire | `hradmin@T1`, previous = a T2 employee | POST | `404` | P1 |
| EM-RHR-009 | Rehire consumes a plan seat | Plan at capacity | POST | `400` from the plan gate — the terminated row never counted, so the rehire must | P1 |
| EM-RHR-010 | Rehire respects the position budget | Prior position now full | POST without a position override | `400`; occupancy unchanged | P1 |
| EM-RHR-011 | Position override honoured | — | POST with `positionId` set | The new row takes the override, not the prior position | P2 |
| EM-RHR-012 | Manager / department overrides honoured | — | POST with `managerId`, `departmentName`, `positionTitle`, `orgUnitId` | Overrides applied; unspecified fields inherit from the prior row | P2 |
| EM-RHR-013 | Prior record is preserved intact | E-TERM-1 | POST, then read E-TERM-1 | Still `TERMINATED`; its history, documents and payroll records are untouched — a rehire never mutates or deletes the prior row | P1 |
| EM-RHR-014 | Rehire is audited on both rows | — | Read the audit | A `CREATE` for the new row carrying `previousEmployeeId` and the reason; the link is traceable in both directions | P1 |
| EM-RHR-015 | Double rehire from the same prior row | Rehire E-TERM-1 twice | Second POST | Prior row is still `TERMINATED`, so it passes the status gate — confirm whether a second concurrent active rehire is intended, and flag it if two live employees now point at one prior record | P1 |
| EM-RHR-016 | Rehire starts a fresh leave-balance and seniority baseline | — | Inspect leave balances after rehire | Balances start fresh for the new row; the prior row's balances are not carried over unless `seniorityDate` is explicitly set | P1 |
| EM-RHR-017 | Reason is capped | `reason` of 4 001 characters | POST | `400` `@Size(max=4000)` | P3 |

## 17. Self-service personal-info change — `/api/personal-info-changes`

| ID | Title | Preconditions | Steps | Expected | Pri |
| --- | --- | --- | --- | --- | --- |
| EM-PIC-001 | Submit a phone change | `staff-1` | POST `{ employeeId, fieldKey: "phone", newValue: "+994551234567", reason }` | `201`; request no `PIC-000001`; status pending; `oldValue` captured | P2 |
| EM-PIC-002 | Whitelist enforced | `staff-1` | POST `fieldKey = "salary"` (also try `hireDate`, `employmentStatus`, `managerId`, `nationalId`, `positionId`) | `400` `"Field not editable via self-service: <key>"` for every one — the whitelist is the privacy boundary | P1 |
| EM-PIC-003 | All whitelisted fields accepted | `staff-1` | Submit each of `email`, `phone`, `addressLine1/2`, `city`, `district`, `postalCode`, `country`, `maritalStatus`, `emergencyContactName`, `emergencyContactPhone` | Each `201` | P2 |
| EM-PIC-004 | Invalid email rejected | — | `fieldKey = email`, `newValue = "nope"` | `400` `"email is not a valid address"` | P2 |
| EM-PIC-005 | Invalid phone rejected | — | `newValue = "abc"` | `400` `"phone must be a valid phone number"` | P2 |
| EM-PIC-006 | Invalid postal code rejected | — | `newValue = "!!"` | `400` `"postalCode is not a valid format"` | P3 |
| EM-PIC-007 | Invalid country rejected | — | `newValue = "Azerbaijan"` | `400` ISO alpha-2 message | P3 |
| EM-PIC-008 | Invalid marital status rejected | — | `newValue = "COMPLICATED"` | `400` listing valid values | P3 |
| EM-PIC-009 | Over-long free text rejected | — | `addressLine1` of 201 characters | `400` `"addressLine1 exceeds 200 characters"` | P3 |
| EM-PIC-010 | Null clears a field | — | `newValue = null` on a nullable field | Accepted — null legitimately clears | P3 |
| EM-PIC-011 | No-op change rejected | E-STAFF-1's phone is already `X` | Submit `phone = X` | `400` `"New value is identical to the current value — nothing to approve"` | P2 |
| EM-PIC-012 | Unknown employee | — | POST with a random `employeeId` | `400` `"Employee not found: <uuid>"` | P2 |
| EM-PIC-013 | Employee cannot submit on someone else's behalf | `staff-1` | POST with `employeeId = <E-STAFF-2>` | **Expected to FAIL as built.** `PersonalInfoChangeController.submit` is gated only by `isAuthenticated()`, the subject arrives in the request body, and `PersonalInfoChangeService.submit` loads the employee by id without calling `scope.isAccessible(...)` — so any authenticated user can open a change request against any colleague's record, and the captured `oldValue` leaks that colleague's current email, phone or marital status back to the submitter. Correct behaviour: `403`/`404` unless the caller is the subject or holds an HR write role | P1 |
| EM-PIC-014 | Approval applies the change | Pending request | Approve through the workflow | Status `APPROVED` then `APPLIED`; the employee field is mutated; `updatedBy` is the approver | P1 |
| EM-PIC-015 | Address changes land on the HOME slice | Pending `city` change | Approve | The employee's HOME address slice is updated (the submit path stores `null` as `oldValue` for address fields — confirm the apply path resolves the correct slice) | P1 |
| EM-PIC-016 | Emergency-contact changes land on the primary contact | Pending `emergencyContactName` change | Approve | The primary emergency contact is updated, not a new duplicate row | P2 |
| EM-PIC-017 | Rejection changes nothing | Pending request | Reject | Status `REJECTED`; the employee record is untouched; decider and comment recorded | P1 |
| EM-PIC-018 | Cancellation is HR-only | `staff-1` | `POST /{id}/cancel` | `403` (`WRITE_HR`) | P2 |
| EM-PIC-019 | HR can cancel | `hradmin` | `POST /{id}/cancel` | Status `CANCELLED`, audited | P2 |
| EM-PIC-020 | Every transition is audited | One request through submit → approve → apply | Read audit | An entry per transition naming the actor — the approval audit trail is mandatory | P1 |
| EM-PIC-021 | `/mine` returns only the caller's requests | `staff-1` | `GET /api/personal-info-changes/mine` | Only E-STAFF-1's requests | P1 |
| EM-PIC-022 | `GET /{id}` respects scope | `staff-3` | Fetch E-STAFF-1's request id | `404` (hidden, not `403`) | P1 |
| EM-PIC-023 | Queue list respects scope | `mgr-b` | `GET /api/personal-info-changes` | Only requests from E-MGR-B's chain | P1 |
| EM-PIC-024 | Request numbers are sequential and per tenant | Submit several | Numbers `PIC-000001`, `PIC-000002`, …; T2 numbering is independent | P2 |
| EM-PIC-025 | Duplicate pending request on the same field | One pending `phone` change | Submit another `phone` change | Documented behaviour — two pending requests on one field can apply out of order and silently overwrite each other | P1 |

## 18. Audit trail

| ID | Title | Steps | Expected | Pri |
| --- | --- | --- | --- | --- |
| EM-AUD-001 | Audit endpoint returns full history | Create, update twice, change status; `GET /{id}/audit` as `hradmin` | Four entries in chronological order with actor, timestamp, action and before/after | P1 |
| EM-AUD-002 | Audit is read-only | Attempt any mutation of the audit rows through the API | No endpoint exists; direct DB writes are outside the application surface | P1 |
| EM-AUD-003 | Failed writes leave no audit entry | Trigger a validation failure on update | No `UPDATE` entry is recorded | P2 |
| EM-AUD-004 | Actor is the authenticated user, not "system" | Update as `hrspec` | Audit actor is `hrspec` | P1 |
| EM-AUD-005 | Audit survives employee status changes | Terminate an employee | The full audit history is still retrievable | P1 |
| EM-AUD-006 | Audit entries are idempotent under retry | Replay the same write (see `AuditIdempotencyTest`) | No duplicated audit rows | P2 |
| EM-AUD-007 | Before/after snapshots exclude nothing material | Update `nationalId` | The audit shows the change occurred; confirm whether the encrypted value is stored in plaintext in the audit payload — if so, that is a privacy leak into a table with a wider read audience | P1 |
| EM-AUD-008 | Audit access is itself restricted | `hrspec` calls `/{id}/audit` | `403` — an actor cannot inspect the log of their own actions | P1 |

## 19. PII, encryption and masking

| ID | Title | Steps | Expected | Pri |
| --- | --- | --- | --- | --- |
| EM-PII-001 | National ID is encrypted at rest | Create an employee with `nationalId`, then read the raw DB column | Ciphertext, not the plaintext value | P1 |
| EM-PII-002 | Tax ID and social-insurance ID encrypted at rest | Same for `taxId`, `socialInsuranceId` | Ciphertext | P1 |
| EM-PII-003 | Each row uses a distinct IV | Two employees with the **same** national ID in different tenants | The stored ciphertexts differ | P1 |
| EM-PII-004 | Encrypted values round-trip | Read the employee back through the API | Plaintext matches what was written | P1 |
| EM-PII-005 | Email masking | `PiiMasking.maskEmail("ayten.m@example.com")` and the masked API surface | `a***m@example.com` shape; short local parts handled (`ab@x.io` → `*b@x.io`) | P2 |
| EM-PII-006 | Phone masking keeps the last four | `+994 55 123 4567` | `•••• •••• 4567` | P2 |
| EM-PII-007 | Document-id masking keeps head and tail | `AZE12345678` | `AZ•••78`; a 4-character input masks entirely | P2 |
| EM-PII-008 | Masked fields for non-privileged readers | Read an employee as a role outside `PiiAccessRoles` | National ID, tax ID and social-insurance ID are masked or absent — never full plaintext | P1 |
| EM-PII-009 | Full PII for privileged readers only | Read as `HR_ADMIN` | Full values returned | P1 |
| EM-PII-010 | Salary is never on the employee payload | Inspect `EmployeeResponse` for every role | No salary, pay grade or bank detail appears on any employee-management response | P1 |
| EM-PII-011 | Masking applies to the list endpoint too | List employees as a department manager | Sensitive fields are masked in **every** row, not only on the detail view | P1 |
| EM-PII-012 | PII is not written to application logs | Create and update with PII at `DEBUG` log level | No national ID, tax ID, email or phone appears in the logs | P1 |
| EM-PII-013 | PII is not echoed in error messages | Trigger the duplicate-national-ID error | The message says "An employee with this national ID already exists" and does **not** include the value or the conflicting employee's identity | P1 |
| EM-PII-014 | Export/report surfaces respect masking | Export from `EmployeeManagementReportsPage` as a manager | The exported file carries the same masking as the UI | P1 |

## 20. Web UI (HR admin and manager screens)

| ID | Screen | Case | Expected | Pri |
| --- | --- | --- | --- | --- |
| EM-UI-001 | `EmployeesPage` | Load as `hradmin` | Paged table, working search box, filter controls, column sort by last name | P2 |
| EM-UI-002 | `EmployeesPage` | Load as `mgr-b` | Only the manager's chain is listed; no "create employee" button | P1 |
| EM-UI-003 | `EmployeesPage` | Load as `staff-1` | The screen is not reachable from navigation, and a direct URL shows a permission message rather than a raw 403 | P1 |
| EM-UI-004 | `EmployeesPage` | Filters combine and are reflected in the URL | Filters survive a page refresh and a back-navigation | P3 |
| EM-UI-005 | `EmployeesPage` | Empty result | Friendly empty state, not a blank table | P3 |
| EM-UI-006 | `EmployeeFormPage` | Create with the minimum fields | Success toast; redirect to the new employee's detail page | P2 |
| EM-UI-007 | `EmployeeFormPage` | Server-side validation errors | Each error is bound to its field, not shown as one generic banner | P2 |
| EM-UI-008 | `EmployeeFormPage` | Duplicate email | The inline message matches the API text | P2 |
| EM-UI-009 | `EmployeeFormPage` | Unsaved-changes guard | Navigating away warns before discarding | P3 |
| EM-UI-010 | `EmployeeFormPage` | Double-submit | The button disables during the request; only one employee is created | P1 |
| EM-UI-011 | `EmployeeDetailPage` | All tabs render | Profile, documents, assignments, history, notes, assets — each loads without console errors | P2 |
| EM-UI-012 | `EmployeeDetailPage` | Tabs hidden by role | The audit tab is absent for `HR_SPECIALIST`; note tabs respect `NoteVisibility` | P1 |
| EM-UI-013 | `EmployeeDetailPage` | Status change dialog | Reason is captured and shown in the history timeline afterwards | P2 |
| EM-UI-014 | `EmployeePicker` | Type-ahead | Results are scoped to the caller's ABAC scope — a manager cannot pick an employee outside their chain | P1 |
| EM-UI-015 | `EmployeeManagementReportsPage` | Run each report | Data matches the API; tenant and permission filters are applied | P1 |
| EM-UI-016 | `BulkReorgPage` | Reassign managers in bulk | Each move is audited individually; occupancy and scope update afterwards | P1 |
| EM-UI-017 | All screens | XSS | An employee named `<img src=x onerror=alert(1)>` renders as text everywhere it appears | P1 |
| EM-UI-018 | All screens | Session expiry mid-edit | The user is prompted to re-authenticate; form input is not silently lost | P2 |
| EM-UI-019 | All screens | Localisation | Local-script names (`fullNameLocal`, `positionTitleLocal`) render correctly in the UI and in printed output | P2 |
| EM-UI-020 | Mobile (`mobile/lib/models/employee.dart`) | Profile view and update | The mobile client honours the same scoping and masking as the web client | P1 |

## 21. Performance and resilience

| ID | Title | Steps | Expected | Pri |
| --- | --- | --- | --- | --- |
| EM-PERF-001 | Directory listing at scale | 50 000 employees; `GET ?page=0&size=20` | p95 under the agreed budget; the query uses an index on `(tenant_id, last_name)` | P2 |
| EM-PERF-002 | Deep pagination | `?page=2000` | No full-table scan blow-up; response time stays acceptable | P2 |
| EM-PERF-003 | Filtered search at scale | Four filters combined over 50 000 rows | Single indexed query; no in-memory filtering | P2 |
| EM-PERF-004 | Manager scope at depth | A 6-level, 5 000-person reporting chain | The recursive CTE returns within budget; no stack or recursion limit is hit | P2 |
| EM-PERF-005 | Org-unit scope at breadth | HR specialist anchored at the root of a 300-unit tree | Scope resolution stays within budget | P2 |
| EM-PERF-006 | Import throughput | 5 000-row import | Completes within the SLA; memory bounded; no request timeout | P2 |
| EM-PERF-007 | Concurrent writes | 50 parallel updates across distinct employees | No deadlock; all writes and audit rows land | P2 |
| EM-PERF-008 | Scope caching correctness | Repeated calls in one request | Scope is recomputed per call as documented; no stale scope after a mid-session reorg | P1 |

## 22. Regression and cross-module edges

| ID | Title | Steps | Expected | Pri |
| --- | --- | --- | --- | --- |
| EM-REG-001 | Employee changes do not rewrite finalised payroll | Change position, FTE and status on an employee with a finalised payroll period | Prior payroll results are byte-identical afterwards; only future periods are affected | P1 |
| EM-REG-002 | FTE change and leave accrual | Change `ftePercent` from 100 to 50 mid-year | Accrual recalculates forward only; already-accrued balance is not silently rewritten | P1 |
| EM-REG-003 | Manager change reroutes pending approvals | An employee has a pending leave request; change their manager | Document the behaviour: the pending approval either follows the new manager or stays with the old one — either way it must be deterministic and audited, never orphaned | P1 |
| EM-REG-004 | Delegation window drives approval routing | Set a delegation window covering today | Approvals route to the delegate inside the window and back to the manager outside it | P1 |
| EM-REG-005 | Approver overrides beat the line manager | Set `timesheetApproverId` on an employee | Their timesheet routes to that approver, not the line manager | P1 |
| EM-REG-006 | Approver cleared falls back to the line manager | Clear `timesheetApproverId` | Routing falls back to the manager; no orphaned task | P1 |
| EM-REG-007 | Termination leaves records intact | Terminate an employee | Payroll history, payslips, documents and audit are all preserved — nothing is physically deleted | P1 |
| EM-REG-008 | Terminated employee disappears from active surfaces | Terminate | Excluded from active headcount, roster candidates and the default directory filter, but still findable with an explicit status filter | P2 |
| EM-REG-009 | Employee deletion is not exposed | Search the API surface for `DELETE /api/employees/{id}` | No such endpoint exists — employees are only status-changed | P1 |
| EM-REG-010 | Org-unit deletion with employees attached | Delete an org unit that still has employees | Blocked or reparented deterministically; no employee is orphaned into an invisible scope | P1 |
| EM-REG-011 | Position deletion with holders | Delete a position with occupants | Blocked; occupancy history is preserved | P1 |
| EM-REG-012 | Employee-to-user linkage break | Remove the Keycloak user behind a linked employee | The employee record survives; scope resolution degrades to "see nothing" rather than "see everything" | P1 |
| EM-REG-013 | Re-linking a user to a different employee | Repoint `username` to another employee row | Scope follows the new linkage immediately; the old scope is gone | P1 |
| EM-REG-014 | Data-migration take-on | Import a legacy register with explicit employee numbers, then create a new hire | Creation succeeds (counter self-heals, EM-NUM-003); no number collision | P1 |
| EM-REG-015 | Backup and restore | Restore a tenant from backup | Employee, history, audit and document references are all consistent afterwards | P2 |

---

## 23. Coverage summary and open questions

**Counts:** 30 create · 42 validation · 7 numbering · 25 search · 27 update · 12 status · 12 overlay · 24 scoping · 20 RBAC · 13 tenant · 16 badge/QR · 17 documents · 45 profile sub-tabs · 22 import · 17 rehire · 25 self-service · 8 audit · 14 PII · 20 UI · 8 performance · 15 regression = **419 test cases**.

**Automated today:** `EmployeeChangeHistoryServiceTest`, `EmployeeWorkforceFieldsTest` (EM-CRE-016/024/025/026, EM-UPD-011/012/013), `EmployeeQrCodeServiceTest`, `EmployeeHistorySliceOrderTest`, `PersonalInfoFieldValidatorTest` (EM-PIC-002/004–009), `PiiMaskingTest` (EM-PII-005/006/007), `EmploymentTypeTest`.

**Not automatable with the current harness:** there is no `@SpringBootTest`, MockMvc or Testcontainers setup in `src/test` — every existing test is a Mockito unit test. All RBAC, scoping, tenant-isolation and endpoint-contract cases above therefore need either a new integration harness or execution against a running instance.

**Questions the pack could not resolve from the code — these need a product answer before the affected cases get a pass/fail expectation:**

1. **EM-RBAC-017** — is `SYSTEM_ADMIN` deliberately excluded from employee create/update/status, when it holds every other HR write?
2. **EM-QR-014/016** — is anonymous badge lookup meant to be default-tenant-only, and should a terminated employee's name and department stay publicly readable?
3. **EM-PIC-013 — confirmed gap, not a question.** Personal-info-change submission performs no ownership or scope check on the body's `employeeId`. This is a self-service scoping break (GLOBAL RULE 8) and leaks the subject's current field value as `oldValue`. Needs a fix, then the test case flips to a pass expectation.
4. **EM-UPD-027** — should a manager cycle (A→B→A) be rejected? Only direct self-reference is blocked today.
5. **EM-STA-007** — should `TERMINATED → ACTIVE` be reachable through the status endpoint, bypassing the rehire flow's eligibility, seat and probation rules?
6. **EM-PROF-024/025/033** — which roles may read medical data, and should `AUDITOR` see employee notes at all?
7. **EM-IMP-005** — should an unrecognised import column warn rather than be silently dropped?
