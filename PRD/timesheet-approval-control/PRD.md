---
feature: timesheet-approval-control
module: attendance
payroll_impact: false
status: backlog
depends_on: [timesheet-daily-capture]
---

# Timesheet — Approval & Period Control (slice 2 of 3)

Managers act on what employees submitted; HR sees the whole period and decides
when it is closed. Still no money: this slice controls *whether* quantities are
trustworthy, not what they are worth.

## 1. Why

Slice 1 lets an employee submit a month. Nothing can act on it — a submitted
timesheet just sits there. Without this slice the quantities never become
trustworthy, and payroll must not consume untrusted quantities.

The rule this enforces: **payroll consumes approved, locked periods — never raw
employee entries.**

## 2. Scope

**In:** single-step manager approval with a dedicated queue and bulk-approve for
clean months; a review screen comparing declared hours against attendance;
approve / return-for-correction / reject; returning *specific days* rather than
a whole month; HR period control with payroll readiness; period lock;
correction requests after approval or lock.

**Out:** rates, amounts, gross, tax, net — slice 3. No change to
`PayrollEngine` in this slice either. Multi-step approval chains — see §6.

## 3. State machine

```
DRAFT ──submit──> SUBMITTED ──approve──> APPROVED ──lock──> LOCKED
  ^                   │                                        │
  │                   ├──return──> RETURNED ──resubmit──> SUBMITTED
  │                   └──reject───> DRAFT                      │
  └────────────────── correction approved <────────────────────┘
                             (reopens named days only)
```

`RETURNED` is distinct from `REJECTED` on purpose. Returned means "fix these
specific days and send it back" and preserves everything the employee entered;
rejected means "this submission should not proceed at all". Collapsing them —
the pre-slice-2 behaviour, where a workflow RETURN silently became DRAFT —
loses the manager's instruction and the employee's place.

## 4. Per-day return

A month can be 31 days; returning all of it because one day is wrong makes the
employee re-check 30 correct days. So a return names days:

* each named day goes to `RETURNED` with the manager's reason;
* every other day stays `APPROVED` and is not re-opened for editing;
* the **month cannot become APPROVED until no day is left in RETURNED**.

## 5. Roles

| Action | Employee | Manager | HR | Payroll |
|---|---|---|---|---|
| Submit own month | ✅ | — | — | — |
| See a report's timesheet | — | ✅ hierarchy only | ✅ | ✅ read-only |
| Approve / return / reject | ❌ own | ✅ hierarchy only | ✅ | — |
| Lock / unlock a period | — | — | ✅ | — |
| Request a correction after lock | ✅ | ✅ | ✅ | — |
| Approve a correction | — | ✅ | ✅ | — |

A manager sees only employees within their hierarchy — enforced through the
existing `AccessScopeService`, the same scoping the rest of the product uses.
Nobody may approve their own timesheet, including a manager who reports to
themselves in a broken hierarchy.

## 6. Approval chain — single step for now

**As built, the chain is one step: the employee's manager decides.** Decisions
are made directly in the dedicated queue below, not through the generic
workflow inbox, and an employee submission does not create a workflow instance.

Multi-step routing (§20 of the original design — offshore crews running
Employee → Offshore Supervisor → Department Manager while office staff run
Employee → Manager) is **deferred**. The workflow engine and its seeded
`TIMESHEET_APPROVAL` definition already exist and remain the intended home for
it, but wiring them means the month's status can only be driven by the
workflow-completion event: a manager approving step 1 of 3 must leave the month
`SUBMITTED`, which the direct path here cannot express.

Until that lands, do not promise a tenant a two-stage timesheet approval.
The `RETURNED` handling in `TimesheetWorkflowListener` is already correct for
when it does.

## 7. Period control

`timesheet.period_control` holds one row per (tenant, year, month):

* `OPEN` — employees may submit, managers may approve;
* `LOCKED` — no submission, no approval, no edit; payroll may consume.

Locking is HR's act and is refused while any timesheet in the period is still
`SUBMITTED` or `RETURNED`, because locking an unapproved month is precisely how
unapproved hours reach payroll.

## 8. Corrections after approval or lock

A locked month is never silently edited. A correction request names the day,
the current value, the requested value and a reason; on approval it reopens
**only that day**. Once slice 3 exists and a period has been paid, the same
request becomes a retro adjustment rather than a rewrite of history.

## 9. API

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/manager/timesheets?year&month&status` | manager/HR, hierarchy-scoped |
| `GET` | `/api/manager/timesheets/{id}` | review detail incl. attendance variance |
| `POST` | `/api/manager/timesheets/{id}/approve` | |
| `POST` | `/api/manager/timesheets/{id}/return` | body names days + reason |
| `POST` | `/api/manager/timesheets/{id}/reject` | |
| `POST` | `/api/manager/timesheets/bulk-approve` | clean months only |
| `GET` | `/api/timesheets/control/{year}/{month}` | HR control board |
| `POST` | `/api/timesheets/control/{year}/{month}/lock` | HR |
| `POST` | `/api/timesheets/control/{year}/{month}/unlock` | HR |
| `POST` | `/api/self/timesheets/{y}/{m}/corrections` | employee |
| `POST` | `/api/manager/timesheets/corrections/{id}/approve` | manager/HR |

## 10. Acceptance criteria

1. A manager sees submitted timesheets for their reports only; an employee
   outside the hierarchy is absent, not forbidden.
2. Bulk approve accepts only months with no blocking findings and no returned day.
3. Returning two named days leaves the other 29 approved and re-opens only
   those two for the employee.
4. A month with a day still in `RETURNED` cannot be approved.
5. Nobody can approve their own timesheet.
6. Locking a period is refused while any timesheet is `SUBMITTED` or `RETURNED`.
7. A locked period refuses employee edits and manager approvals.
8. An approved correction reopens exactly the named day.
9. Every approve / return / reject / lock / correction is audit-logged with
   actor, timestamp, before and after.
10. No endpoint in this slice returns a monetary amount.
