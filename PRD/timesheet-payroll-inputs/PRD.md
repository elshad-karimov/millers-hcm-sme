---
feature: timesheet-payroll-inputs
module: payroll
payroll_impact: true
status: backlog
depends_on: [timesheet-daily-capture, timesheet-approval-control]
---

# Timesheet → Payroll Inputs (slice 3 of 3)

Turns the approved, locked quantities from slices 1–2 into money, replacing the
January 2026 workbook. **Payroll-affecting: requires human sign-off before any
of it drives a real payroll run** (CLAUDE.md autonomy rule 3).

Source of truth for every rule below: `Copy of Payroll calculation 2026
January 2.xlsm`, sheet `For JX`. Fixtures with the workbook's own expected
values are in `fixtures/january-2026.json`.

## 1. The calculation, as the workbook does it

Norm working hours for the period: **151** (cell `F3`).

```
hourlyRate    = baseSalary / normHours
overtimeRate  = hourlyRate × 2
```

Base salary is **not itself paid**. It exists only to derive the hourly rate;
gross is the sum of the category amounts. An employee with no recorded hours
earns nothing. This answers the question that blocked this slice: offshore and
quayside are **not** an apportionment of base salary and **not** premiums on
top of it — they are the whole of pay, priced per hour.

| # | Amount | Formula | Multiplier |
|---|---|---|---|
| T | Offshore Salary | `offshoreHours × hourlyRate × 1.75` | 1.75 |
| U | Quayside Salary | `quaysideHours × hourlyRate × 1.6` | 1.6 |
| V | Onshore work | `onshoreHours × hourlyRate` | 1.0 |
| W | Onshore overtime | `overtimeHours × overtimeRate` | 2.0 |
| Y | Excess / MEWA | **not derivable — see §3** | — |
| Z | Meal Allowance | `mealDays × 12 AZN` | flat |
| AA | Transport Allowance | `transportDays × 10 AZN` | flat |
| AB | Hotel Quarantine | `quarantineHours × hourlyRate × 1.75` | 1.75 |
| AC | Offshore Nightshift | `offshoreNightHours × hourlyRate × 0.2` | 0.2 |
| AD | Quayside Nightshift | `quaysideNightHours × hourlyRate × 0.2` | 0.2 |
| AE | Offshore Rota Public Holiday | `phHours × hourlyRate × 1.75` | 1.75 |
| AF | Quayside Rota Public Holiday | `phHours × hourlyRate × 1.6` | 1.6 |
| AG | Extra amount | manual entry | — |
| AH / AI | Vacation / Sick Leave AZN | manual entry | — |

```
gross = T + U + V + W + Y + Z + AA + AB + AC + AD + AE + AF + AG + AH + AI
```

Note the multipliers are **absolute, not premiums**: offshore pays 1.75× the
hourly rate in total, not the rate plus 75%. Nightshift at 0.2 is therefore a
top-up on hours already paid through the offshore or quayside line — consistent
with slice 1, where night hours re-classify hours already counted.

Column **X ("Quayside amount")** is empty in every row and duplicates U. It is
treated as dead and not implemented.

## 2. Statutory deductions

Let `mealExempt = mealDays × 5` — 5 AZN of each 12 AZN meal allowance is exempt
from every contribution base (though all 12 are paid).

```
incomeTax = 0                                                    if gross < 200
          = (gross − lifeIns − mealExempt − 200)  × 0.14         if (gross − lifeIns) ≤ 2500
          = (gross − lifeIns − mealExempt − 2500) × 0.25 + 350   otherwise

spf                 = (gross − sickLeave − lifeIns − mealExempt) × 3%
unemploymentFund    = (gross − mealExempt − sickLeave) × 0.5%
compulsoryInsurance = (gross − mealExempt − sickLeave) × 2%                       if gross ≤ 8000
                    = (gross − 8000 − mealExempt − sickLeave) × 0.5% + 160        otherwise

totalDeductions = incomeTax + spf + unemploymentFund + compulsoryInsurance
                + lifeInsurance + azercell + advance
netPay          = gross − totalDeductions
```

Two asymmetries are reproduced from the workbook rather than tidied, because
"tidying" a statutory base silently changes people's pay:

* **SPF** subtracts life insurance; unemployment and compulsory insurance do not.
* Column **AR "Other"** is `lifeIns + azercell + advance` — a display subtotal,
  not an independent deduction. It sits outside `SUM(AK:AQ)` so it is not
  double-counted. Not implemented as a deduction.

## 3. HARD STOP — what the workbook does not pin down

These are un-inventable under CLAUDE.md autonomy rules 1 and 2. They are
implemented as **explicit configuration or manual input**, never guessed.

### 3.1 Excess / MEWA has four different formulas

| Row | Role | Formula | Declared excess hours (G) |
|---|---|---|---|
| 8 | HVAC Technician | `hourlyRate × 1.6 × 33` | 0 |
| 10 | Senior Accountant | `onshoreAmount × 30%` | 0 |
| 11 | Transport Officer | `onshoreAmount × 60%` | 0 |
| 12 | Senior Electrician Offshore | `hourlyRate × 1.6 × 85` | 0 |
| 9, 13, 14 | — | none | 0 |

The `Excess hours` input column is **zero in every row**, so the declared
quantity does not drive the amount at all. Two incompatible methods are in use
(a percentage of onshore pay, and a hardcoded unit count at 1.6× rate), and the
percentages and unit counts differ per person.

**Resolved as:** a per-employee, effective-dated excess rule with two supported
methods — `PERCENT_OF_ONSHORE` and `UNITS_AT_RATE` — that must be configured.
An employee with no rule gets no excess amount. **Confirm the intended rule for
each affected employee before the first live run.**

### 3.2 Three rows contradict the stated formulas

| Row | Column | Formula used | Canonical would be | Difference |
|---|---|---|---|---|
| 8 | T | `base/151 × **136** × 1.75` | `132 × rate × 1.75` (C8 = 132) | 4 hours |
| 9 | T | `base × 1.75` — no hours, no rate | `35 × rate × 1.75` (C9 = 35) | 5222.00 vs 1210.40 |
| 12 | T | `base/151 × **80** × 1.75` | `121 × rate × 1.75` (C12 = 121) | 41 hours |
| 12 | U | `**168** × rate × 1.6` | `154 × rate × 1.6` (F12 = 154) | 14 hours |

Row 9 is not a rounding slip: it pays offshore as `monthlySalary × 1.75`,
ignoring hours entirely — a different pay basis, worth **4,011 AZN more** than
the hourly rule for that employee.

**Not implemented.** The engine computes the canonical formula. Each deviation
is either (a) a data-entry error in the workbook, or (b) a real per-employee pay
basis that must be stated as a rule. **This needs your answer before sign-off** —
it is the difference between paying someone 1,210 AZN and 5,222 AZN.

### 3.3 Income tax regime conflicts with what this system already implements

The workbook uses **14% / 25%** with a 2,500 AZN threshold and a 200 AZN
exemption — the Azerbaijani oil-and-gas / public-sector regime.

`StatutoryCalculator` in this repository implements **3% / 10% / 14%** with no
200 exemption, documented in V306 as the 2026 private-sector rates.

These are different regimes and produce materially different tax. **Neither is
changed by this slice.** The workbook's regime is implemented as a separate,
explicitly named ruleset (`AZ_OIL_GAS_2026`) used only by this calculator;
the existing engine is untouched. **Confirm which regime applies to this legal
entity before the first live run.**

## 4. Scope of this slice

**In:** the pay-rule catalog and per-employee excess rules; a calculator that
prices approved month totals; a per-employee calculation screen mirroring the
workbook's column order; validation of every fixture row to the cent.

**Out until sign-off:** `PayrollEngine` is **not** modified. Nothing here runs,
finalizes or posts a payroll. The calculator is reachable only through a
read-only preview so the numbers can be compared against the workbook before
any money moves.

## 5. Acceptance criteria

1. Rows 13 and 14 (fully canonical) reproduce gross and net to the cent.
2. Rows 10 and 11 reproduce to the cent once their excess rule is configured.
3. Rows 8, 9 and 12 are reported as **deviating**, with the difference stated —
   never silently matched.
4. Employees with no configured excess rule get no excess amount, not a guess.
5. The preview is read-only: no payroll run, result or payslip is created.
6. No employee- or manager-facing endpoint exposes any amount from this slice.
