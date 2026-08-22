# Running payroll from timesheets and absence

How a month goes from an employee's hours to a payslip, written after running it
end to end on the live SME system (October 2026, EMP-00001, 2 000 AZN base).

---

## Before the month starts — configuration

These are set once, or once a year. Skip any of them and the month cannot be
priced.

| What | Where | Note |
|---|---|---|
| Departments, positions, locations | Master Data | the employee record picks from these |
| Legal entity | Master Data → Legal Entities | payroll resolves one; without it screens 500 |
| Leave types | Master Data → Leave Types | before anyone can request absence |
| Timesheet projects | Master Data → Timesheet Projects | the cost code a day books to |
| Salary components | Master Data → Salary Components | earnings and deductions catalogue |
| **Norm working hours for the month** | **no screen — SQL only** | **see the warning below** |

> **Norm hours block everything.** Every hourly rate is `base salary ÷ norm hours`
> for that month. If the row is missing, pricing refuses outright:
>
> > *Norm working hours are not configured for 2026-10. Every hourly rate divides
> > by it, so nothing can be priced until it is set.*
>
> Only 2026-01 (151 h) and 2026-07 (184 h) were seeded. There is **no UI and no
> API** to add one — a month has to be inserted directly into
> `payroll.period_norm_hours`. This is the first thing to fix if payroll is to
> run every month.

---

## The monthly cycle

### 1. The employee records the month
**Self-Service → My Timesheet** (`/my/timesheet?period=YYYY-MM`)

Pick the work type for a day first; the form then asks only for the hours that
apply to it. What was entered in the test run:

| Day | Work type | Entered |
|---|---|---|
| 1, 2 Oct | Onshore | 8 h, meal ✓, transport ✓ |
| 5 Oct | Onshore | 8 h, 90 min overtime, meal ✓, transport ✓ |
| 6, 7 Oct | Offshore | 12 h, of which 4 h night |
| 8 Oct | Leave | 8 h unpaid |

Overtime is keyed in **minutes** and rounded to the configured 30-minute step —
90 minutes became **1.5 h** automatically, shown as a derived value.

### 2. The employee submits
**Submit for approval** on the same screen. Status goes `DRAFT → SUBMITTED`.

### 3. The line manager approves
**Manager Self-Service → Timesheet Approvals** → open the month → **Approve**.

The review screen shows each day: what was entered, what was calculated from it,
where it was worked and against which project, plus the variance against any
attendance record. Individual days can be sent back with a reason instead of
rejecting the whole month.

Status goes `SUBMITTED → PENDING_HR`.

### 4. HR verifies
Same screen, as an HR administrator. Status goes `PENDING_HR → APPROVED`.

Nobody can approve their own month — the system refuses it, and it refused it
during this test.

### 5. Payroll runs the month
**Payroll → Payroll Runs → new run** for the period, then **Calculate**.

Check **Payroll → Control Board** first, and **Variance Report** afterwards —
comparing against last month is the cheapest way to catch a wrong run before
anybody is paid.

### 6. Payslips go out
**Payroll → Payroll Runs → open the run → Send payslips.**
Employees then see them under **My Workspace → Payroll**.

---

## What the test run produced

Same employee, same approved month, 2 000 AZN base, 176 h norm.

**Payroll → Time & Attendance Inputs** priced the timesheet like this:

| Line | Qty | Rate | Amount |
|---|---|---|---|
| Offshore hours | 24 h | 19.89 (11.36 × 1.75) | 477.27 |
| Onshore hours | 24 h | 11.36 | 272.73 |
| Overtime | 1.5 h | 22.73 (2×) | 34.09 |
| Meal allowance | 3 days | 12.00 | 36.00 |
| Transport allowance | 3 days | 10.00 | 30.00 |
| Offshore night top-up | 8 h | 2.27 (20 %) | 18.18 |
| **Gross** | | | **868.27** |
| **Net** | | | **729.88** |

5 AZN per meal day (15.00) is held exempt from every contribution base, exactly
as the workbook describes.

**The payroll run for the same month produced something else:**

| | Time & Attendance Inputs | Payroll run |
|---|---|---|
| Gross | 868.27 | 2 028.13 |
| Net | 729.88 | 1 727.78 |
| Offshore, night, meal, transport | priced | **0.00** |
| Unpaid leave day | not paid | **ignored** (pro-ration 1.0) |
| Hourly divisor | 176 h (the configured norm) | 160 h (fixed) |

---

## The gap, stated plainly

**The payroll run does not read the timesheet pricing.** It pays the full monthly
salary plus overtime, and ignores the offshore multiplier, the night top-up, the
meal and transport allowances, and the unpaid day.

The rules themselves are correct and configured — `payroll.time_pay_rule` holds
the workbook's own numbers (offshore 1.75×, quayside 1.60×, night 20 %, meal
12 AZN/day, transport 10 AZN/day) and the preview applies them faithfully. The
two sides are simply not connected: `TimesheetPayCalculator` is called by the
preview and by the statutory calculator, and **never by `PayrollEngine`**.

Until that is joined up, the run's figures are wrong for an hours-based
workforce, and **Time & Attendance Inputs is the number to trust**.

This is a payroll calculation change, so it needs a decision rather than a quiet
fix — see the notes at the end of the session for the options.
