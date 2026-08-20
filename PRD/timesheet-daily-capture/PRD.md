---
feature: timesheet-daily-capture
module: attendance
payroll_impact: false
status: backlog
depends_on: []
---

# Timesheet — Daily Capture (slice 1 of 3)

Employees record what they actually did, day by day, in operational terms
(hours by work type, allowance eligibility). The system aggregates those days
into the monthly quantities payroll will later price. **No monetary value is
entered, stored, displayed or computed anywhere in this slice.**

Slice 2 (`timesheet-approval-control`) adds manager/HR approval and period
control. Slice 3 (`timesheet-payroll-inputs`, payroll-impacting) turns the
approved quantities into money.

## 1. Why

Today a timesheet is *generated from attendance by HR* (`POST /api/timesheets/generate`,
HR-only) and a day carries one primary code plus worked/overtime hours. That
cannot express the pay-relevant reality of this workforce:

* the same employee works **offshore, onshore and quayside** in one month, and
  each is priced differently;
* **offshore staff have no biometric device**, so attendance cannot be the
  source of truth for them — it is at best corroborating evidence;
* night hours, public-holiday rota hours, meal/transport allowance days,
  quarantine and excess hours are all separate payroll quantities today
  maintained by hand in an Excel workbook.

Hand-maintained monthly Excel rows are the failure mode this replaces: no
validation, no approval trail, no link back to the day it happened on.

## 2. Scope

**In:** work-type dimension on a timesheet day; a configurable catalog of time
categories; employee daily entry with draft/submit; auto-derivation of holiday,
night and leave quantities; validation split into blocking errors and warnings;
attendance-vs-timesheet variance display; monthly aggregation into per-category
totals; employee-facing Calendar / Grid / Summary views.

**Out (later slices):** manager approval, return-for-correction, HR control
board, period lock, retro corrections (slice 2). Rates, amounts, gross, tax,
deductions, net (slice 3). Any change to `PayrollEngine`.

## 3. Roles and what each may do

| Action | Employee | Manager | HR | Payroll |
|---|---|---|---|---|
| Enter own daily quantities | ✅ | — | ✅ (on behalf, audited) | — |
| See own timesheet | ✅ | — | ✅ | — |
| See a report's timesheet | — | ✅ (slice 2) | ✅ | — |
| Submit own month | ✅ | — | ✅ | — |
| Edit after submit | ❌ (recall first) | — | ❌ | — |
| See any monetary value | ❌ | ❌ | ❌ | ✅ (slice 3) |

An employee may only ever read or write their **own** timesheet. Enforced in
the service against `EmployeeContextService.currentEmployee()`, not by the
route — matching how `/api/self/**` already works.

## 4. Data model

Catalog-driven rather than 14 fixed columns, so a new pay-relevant quantity is
a row, not a migration — and so slice 3 can bind a salary component to a
category code instead of hardcoding this client's workbook.

### 4.1 `timesheet.time_category` (new, tenant-scoped, configurable)

| Column | Notes |
|---|---|
| `code` | stable key, e.g. `OFFSHORE_HOURS` — referenced by payroll in slice 3 |
| `name` | display label shown to the employee |
| `unit` | `HOURS` or `DAYS` |
| `applies_to` | CSV of work types this category can be entered against |
| `derived` | `true` = system-computed (holiday, night, leave), employee cannot type it |
| `source` | `EMPLOYEE`, `HOLIDAY_CALENDAR`, `SHIFT_SCHEDULE`, `LEAVE` |
| `max_per_day` | validation ceiling (e.g. 1 for allowance days, 24 for hours) |
| `display_order`, `active` | |

Seeded with the 14 quantities the current workbook needs:
`OFFSHORE_HOURS`, `ONSHORE_HOURS`, `ONSHORE_OVERTIME_HOURS`, `QUAYSIDE_HOURS`,
`EXCESS_HOURS`, `MEAL_ALLOWANCE_DAYS`, `TRANSPORT_ALLOWANCE_DAYS`,
`HOTEL_QUARANTINE_HOURS`, `OFFSHORE_NIGHT_HOURS`, `QUAYSIDE_NIGHT_HOURS`,
`OFFSHORE_HOLIDAY_HOURS`, `QUAYSIDE_HOLIDAY_HOURS`, `VACATION_HOURS`,
`SICK_LEAVE_HOURS`.

### 4.2 `timesheet.day_quantity` (new)

One row per (day, category) with a non-zero quantity. Carries `derived_from`
so a system-computed value is visibly not employee-typed.

### 4.3 `timesheet.timesheet_month_total` (new)

The monthly aggregate per category — recomputed on every day change. **This is
the payroll input contract**: slice 3 reads only this table, never the days.

### 4.4 `timesheet.timesheet_day` (extended)

`work_type` (`ONSHORE|OFFSHORE|QUAYSIDE|BUSINESS_TRIP|REMOTE|LEAVE|SICK|NON_WORKING`),
`entry_source` (`EMPLOYEE|ATTENDANCE|LEAVE|HOLIDAY|HR`), `employee_note`,
`attendance_variance_hours`, `variance_explanation`.

Existing columns keep their meaning; nothing is dropped, so the current
attendance-driven generation and `PayrollEngine` continue to work unchanged.

## 5. Derivation rules (employee never types a payroll field name)

| Employee enters | System derives |
|---|---|
| Work type `OFFSHORE`, 12 h, on a date flagged in the holiday calendar | `OFFSHORE_HOLIDAY_HOURS = 12` |
| Work type `QUAYSIDE`, 12 h, on a public holiday | `QUAYSIDE_HOLIDAY_HOURS = 12` |
| Work type `OFFSHORE`, shift crossing the configured night window | `OFFSHORE_NIGHT_HOURS = <night portion>` |
| Approved leave request covering the day | `VACATION_HOURS` / `SICK_LEAVE_HOURS`, day is **read-only** with a link to the request |
| Meal / transport eligibility ticked | `MEAL_ALLOWANCE_DAYS = 1`, `TRANSPORT_ALLOWANCE_DAYS = 1` |

Derived categories are recomputed from source on every save — an employee can
never desync them. Where a rule requires an employee override, the override is
allowed but demands a justification and raises a warning for the approver.

## 6. Validation

**Blocking (submission refused)**
* negative quantity;
* total hours on one calendar day > configured daily maximum;
* a required working day with no entry;
* leave and full-day work on the same day;
* a category entered against a work type it does not apply to;
* public-holiday category on a non-holiday date;
* night hours greater than the worked hours they belong to;
* allowance days > `max_per_day`;
* editing a month that is not `DRAFT`/`REOPENED`.

**Warning (submission allowed, surfaced to the approver)**
* timesheet total differs from attendance beyond tolerance;
* overtime above the monthly threshold;
* weekend hours entered;
* no attendance record exists for a worked day.

## 7. API (all under `/api/self/timesheets`, `hasRole('EMPLOYEE')`)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/{year}/{month}` | own month: header, days, totals, validation state |
| `GET` | `/{year}/{month}/categories` | categories enterable, by work type |
| `PUT` | `/{year}/{month}/days/{date}` | upsert one day |
| `POST` | `/{year}/{month}/days/{date}/copy-previous` | copy the previous day |
| `POST` | `/{year}/{month}/submit` | validate + submit |
| `POST` | `/{year}/{month}/recall` | pull back a submission not yet approved |

A month is auto-created in `DRAFT` on first read, so an employee never has to
ask HR to "generate" it.

## 8. UI

`My Time` self-service area (same card design as the other areas), leading to a
monthly timesheet with **Calendar / Grid / Summary / History** tabs. The daily
entry form shows only the fields the selected work type allows. The Summary tab
is the employee-readable version of the workbook's quantity columns — quantities
only, no money.

## 9. Acceptance criteria

1. An employee opens `My Time`, sees the current month in `DRAFT`, and can enter
   a day without HR generating anything first.
2. Choosing `OFFSHORE` shows offshore fields only; choosing `ONSHORE` shows
   onshore, overtime and allowance fields only.
3. Entering 12 h offshore on a configured public holiday produces
   `OFFSHORE_HOLIDAY_HOURS = 12` without the employee naming that category.
4. A day covered by an approved leave request is read-only, shows the request
   number, and contributes `VACATION_HOURS`.
5. Submitting with a missing required working day is refused and names the day;
   submitting with an attendance variance succeeds and records a warning.
6. Monthly totals equal the sum of the day quantities for every category.
7. An employee cannot read or write another employee's timesheet (404, not 403).
8. Every quantity change is audit-logged with before/after.
9. No endpoint in this slice returns a monetary amount.

## 10. Open items — do NOT invent (deferred to slice 3)

Rate basis per category; whether base salary is apportioned across work types or
premiums are additive; night/holiday multipliers; excess/MEWA formula; meal and
transport daily amounts; quarantine rate; vacation/sick pay basis; rounding.
These are payroll rules and are out of scope here by design — this slice is
correct without them.
