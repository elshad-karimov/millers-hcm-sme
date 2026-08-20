# Timesheet & Approval — user guide

Millers HCM **SME edition**. Covers recording a month, getting it approved, and
the settings that change how both behave.

Local URLs (dev): app **http://localhost:5181** · Keycloak admin
**http://localhost:8190** · API **http://localhost:8083**

| Login | Password | Sees |
|---|---|---|
| `employee` | `Employee#123!` | own timesheet only |
| `admin` | `Admin#Pass123!` | HR admin + system admin; also the manager of `employee` |

---

## 1. Recording a month (employee)

**My Timesheet** → month picker → **Detailed grid**.

The grid opens **read-only**. Press **Edit timesheet** to change anything; press
**Done editing** to go back. A month that is submitted, approved or locked has no
Edit button at all — it says so instead of showing dead controls.

### The columns

Six value columns, the same six as the paper form. They are *generic* — the row's
**Work Type** decides which pay category each number is actually filed against:

| Column | On an Offshore day | On an Onshore day | On a Quayside day |
|---|---|---|---|
| Regular Hours | Offshore hours | Onshore hours | Quayside hours |
| Night Hours | Offshore nightshift | — | Quayside nightshift |
| Public Holiday Hours | Offshore rota on PH | — | Quayside rota on PH |

Leave and Sick work the same way: pick that work type and Regular Hours becomes
vacation or sick hours.

- **Hours are HH:MM.** `8:30`, `08:15`, `11:00`. It also accepts `8`, `8.5`,
  `8,5` and `0830`. Anything it cannot read turns the field red rather than
  guessing.
- **OT Minutes (Actual)** is what you actually worked, in whole minutes — `45`.
- **OT Hours (Rounded)** is greyed out on purpose. The server rounds actual
  minutes by the company rule (currently to the nearest 30 min) and that figure
  is what gets paid. It cannot be typed.
- **Public Holiday Hours** only accepts a value on a date the calendar marks
  **PH**; otherwise it is greyed.
- **Meal** and **Transport** are tick boxes — one day's entitlement each.
- **Excess** and **Hotel Quarantine** hours have no column; enter them per day
  under **⋯**. Editing a row in the grid keeps them intact.

### Fast entry — fill a rotation in three actions

A hitch is one row, not eight:

1. Fill one row completely (work type, location, hours, allowances).
2. Tick the days that match, using the checkboxes or **Select Weekdays** /
   **Select All**.
3. Press **Copy → Selected** on the row you filled.

Dates are never overwritten. Everything else on the row is copied, and because
the columns are generic, copying onto a day with a different work type still
files the hours correctly.

Nothing is written until you press **Save N days** — the whole month goes in one
request, so it either all lands or none of it does. **Discard** throws away
unsaved edits. The row's **Status** tells you where each day stands: Empty,
Edited (unsaved), Saved, Check (warning) or Attention (blocking).

### Validation

**Blocking** — cannot submit:

- more than **12 hours** in a day
- onshore overtime over **4 hours across any two consecutive days**
- holiday hours on a non-holiday, leave and work on the same day, negative or
  over-max values

**Warnings** — travel to the approver, do not block: weekend work, hours that
disagree with the attendance device, and a nightshift/holiday figure that
disagrees with the roster or calendar.

---

## 2. Getting it approved

Press **Submit for approval**, tick the confirmation, submit.

```
employee submits ──► Manager review ──► HR sign-off ──► Approved
     SUBMITTED         PENDING_HR                        APPROVED
   "With your manager"    "With HR"
```

- The **manager** is resolved dynamically to the employee's own direct manager.
- **Recall** is possible only while the month is still with the manager. Once
  the manager approves, it is gone — someone has put their name to the numbers.
- Individual days are marked approved only at **final** sign-off. Before that
  the month is still in flight and payroll must not treat it as settled.

**Approver's view:** *Manager → Team Timesheets* (`/manager/timesheets`). The
queue shows only what is waiting on **you** — managers see months awaiting
manager review, HR sees months awaiting HR. Someone who is both sees both. From
there you can approve, return named days for correction, or reject.

Every step is audit-logged: `EMPLOYEE_SUBMIT`, then one `APPROVAL_DECISION` per
step, with the actor, the comment and the workflow step number.

---

## 3. Changing the approval route (no deploy)

The chain is **configuration, not code**. *Workflow & Approvals → Workflow
Definitions* (`/workflow/definitions`), definition **`TIMESHEET_APPROVAL`**:

| Step | Name | Approver |
|---|---|---|
| 1 | Manager review | `ROLE_DEPARTMENT_MANAGER`, resolved to the employee's manager |
| 2 | Payroll sign-off | `ROLE_HR_ADMIN` |

Each step supports: a role **or** a named approval group, resolve-to-manager,
resolve-to-HRBP, an SLA in hours with an escalation action, parallel voting, and
a condition expression. Add a third step, swap an approver, or put a 48-hour SLA
on manager review — it takes effect on the next submission.

> Months submitted **before** routing existed have no workflow attached and will
> refuse approval, asking the employee to recall and resubmit. Only affects
> timesheets submitted before this feature shipped.

---

## 4. Settings

*Platform & Admin → Tenant Settings* (`/admin/settings`).

| Setting | Default | Effect |
|---|---|---|
| `timesheet.work-locations` | `BDWJF,SCV,Aberdeen` | The Work Location dropdown. **Leave empty and the field becomes free text.** |
| `timesheet.overtime.rounding-minutes` | `30` | Rounding applied to actual OT minutes. `0` pays the exact minutes. This moves money — change it deliberately. |
| `timesheet.validation.require-project` | `false` | Turn on to make Project / Cost Code mandatory on any day with hours. **Load projects first** (*Timesheet Projects*) or every timesheet becomes unsubmittable. |
| `timesheet.validation.max-daily-hours` | `12` | Blocking daily ceiling. |
| `timesheet.validation.max-overtime-two-consecutive-days` | `4` | Blocking two-day OT ceiling. |
| `timesheet.night.window.start` / `.end` | `22:00` / `06:00` | Window used to cross-check typed nightshift hours against the roster. |

**Time categories** (`timesheet.time_category`) are per-tenant rows — the column
set can be extended without code.

---

## 5. Plan / edition

The tenant is on a plan: **LITE**, **STANDARD** or **ENTERPRISE**. The badge in
the header shows which. Modules outside the plan are hidden from navigation,
shown locked in *Tenant Settings → Modules*, and refuse their API with a 403 —
hiding a tile is not the enforcement.

LITE includes self-service, manager self-service, employee management,
organization, employee lifecycle, time & attendance, leave & absence, payroll,
benefits, workflow & approvals, reports, and platform admin.

Changing a tenant's plan is a control-plane action:
`PUT /api/admin/tenants/{tenantId}/plan` with `{"plan":"STANDARD"}`, SYSTEM_ADMIN
only, audit-logged, and it takes effect immediately with no restart. A downgrade
never deletes data — modules simply stop answering.

---

## Known gaps

- **Nobody is notified.** Approval is a pull model: the approver finds the month
  in their queue. No email, no inbox push.
- **The 12-hour rest rule is not enforced.** It needs shift start/end times; the
  timesheet stores daily totals only.
- **No Bulk Entry dialog.** Copy → Selected covers the same ground.
- **No paste from Excel.**
- Business-trip days record onshore hours and carry **no** meal or transport
  entitlement, matching the reference sheet.
