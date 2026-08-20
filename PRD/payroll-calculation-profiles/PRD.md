---
feature: payroll-calculation-profiles
module: payroll
payroll_impact: true
status: building
depends_on: [timesheet-daily-capture, timesheet-approval-control, timesheet-payroll-inputs]
---

# Payroll Calculation Profiles — the company's four pay bases

**Payroll-affecting. Requires human sign-off before it drives a real payroll
run** (CLAUDE.md autonomy rule 3). Six questions in `BLOCKERS.md` are
un-inventable and must be answered before the engine is trusted with money.

## 0. Why this PRD exists, and what it settles

`timesheet-payroll-inputs` (slice 3) priced the January 2026 workbook and
stopped on three questions. The July 2026 material — the WhatsApp discussion
with Emil plus three spreadsheets (`Onshore contract with Random offshore
trip.xlsx`, `Offshore contract with rotation.xlsx`, `Offshore contracts with
random onshore hours.xlsx`) — **answers the most expensive one of them.**

> **Row 9 was not an error.** The January workbook paid one employee
> `baseSalary × 1.75` with no hours and no rate — 5,222 AZN where the hourly
> rule gives 1,210 AZN, a 4,012 AZN gap that blocked sign-off. That employee is
> on an **offshore rotation contract**, and rotation staff are paid
> `Base × 1.75` monthly regardless of how many offshore days they worked. It is
> a contractual pay basis, not a data-entry mistake.

The structural consequence is the point of this PRD:

> **There is no universal salary formula.** An employee is assigned a
> **calculation profile** derived from their contract and work regime, and
> payroll dispatches to the matching engine. Everything else — multipliers,
> allowance rates, balancing periods — is configuration, not code.

Slice 3's conclusion that gross is the sum of priced categories and that base
salary is not itself paid holds for the three **hourly** profiles. Rotation is
the exception: there, base salary *is* the pay basis.

## 1. The four calculation profiles

| Code | Contract / regime | Offshore pay basis | Excess settlement |
|---|---|---|---|
| `ONSHORE_FIXED` | Onshore, 5/2, 40 h/week | n/a | monthly |
| `ONSHORE_RANDOM_OFFSHORE` | Onshore contract, ad-hoc offshore trips | hourly, actual offshore hours × 1.75 | monthly, same month as the trip |
| `OFFSHORE_ROTATION` | Offshore rotation, 1 day ON / 1 day OFF, 12 h days | **monthly**, `base × 1.75` | accumulated, settled Apr / Aug / Dec |
| `OFFSHORE_RANDOM_ONSHORE` | Offshore contract, occasional onshore work | hourly on **derived** offshore hours × 1.75 | *(see BLOCKERS Q6)* |

Stored on the employee's contract/assignment as effective-dated configuration —
never inferred each month from the hours that happen to be present. Two
employees with identical timesheets and identical base salaries are paid
different amounts if their profiles differ, and that is correct.

### 1.1 The distinction that must not be collapsed

```
ONSHORE_RANDOM_OFFSHORE:  offshoreHours × hourlyRate × 1.75    ← hour-driven
OFFSHORE_ROTATION:        baseSalary × 1.75                     ← month-driven
```

Do not build one generic "offshore calculation". A rotation employee with one
offshore hour in the month receives the full `base × 1.75`; an onshore-contract
employee with one offshore hour receives `1 × rate × 1.75`.

## 2. Common inputs

Every profile consumes **approved and locked** attendance only. Payroll never
reads raw timesheets (see §7).

| Input | Source | Category code (existing) |
|---|---|---|
| Norm working hours for the period | `payroll.period_norm_hours` | — |
| Offshore hours | timesheet | `OFFSHORE_HOURS` |
| Onshore working hours | timesheet | `ONSHORE_HOURS` |
| Onshore overtime hours | timesheet | `ONSHORE_OVERTIME_HOURS` |
| Quayside hours | timesheet | `QUAYSIDE_HOURS` |
| Offshore nightshift hours | shift schedule | `OFFSHORE_NIGHT_HOURS` |
| Quayside nightshift hours | shift schedule | `QUAYSIDE_NIGHT_HOURS` |
| Offshore public-holiday hours | holiday calendar | `OFFSHORE_HOLIDAY_HOURS` |
| Quayside public-holiday hours | holiday calendar | `QUAYSIDE_HOLIDAY_HOURS` |
| Hotel/quarantine hours | timesheet | `HOTEL_QUARANTINE_HOURS` |
| Meal allowance days | timesheet | `MEAL_ALLOWANCE_DAYS` |
| Transport allowance days | timesheet | `TRANSPORT_ALLOWANCE_DAYS` |
| Vacation hours / amount | leave + averaging | `VACATION_HOURS` |
| Sick leave hours / amount | leave | `SICK_LEAVE_HOURS` |
| **Excess hours** | **calculated — never entered** | `EXCESS_HOURS` (read-only) |

July 2026 norm = **184 hours**. January 2026 was 151. It is dated data, already
modelled by `payroll.period_norm_hours`.

## 3. Rates

```
hourlyRate   = baseSalary / normHours          full precision, no early rounding
overtimeRate = hourlyRate × 2
```

Worked: `3500 / 184 = 19.0217391304…`, `overtimeRate = 38.0434782609…`.

Rounding rule (unchanged from slice 3, and it is load-bearing): full precision
throughout, 2 dp HALF_UP on final figures only. Rounding per line drifts by
cents against the company workbooks.

## 4. Earning components

All multipliers are **absolute, not premiums**: 1.75 pays 1.75× the hourly rate
in total. Night at 0.20 is the exception — it is a genuine top-up on hours
already paid through the offshore or quayside line, because night hours
re-classify hours already counted rather than adding new ones.

| Earning code | Formula | Profiles |
|---|---|---|
| `ONSHORE_REGULAR` | `onshoreHours × hourlyRate` | all |
| `ONSHORE_OT` | `onshoreOtHours × hourlyRate × 2` | all |
| `OFFSHORE_75` | `(offshoreHours + offshoreNightHours) × hourlyRate × 1.75` | `ONSHORE_RANDOM_OFFSHORE` |
| `OFFSHORE_ROTA_SALARY` | `baseSalary × 1.75` | `OFFSHORE_ROTATION` |
| `OFFSHORE_DERIVED` | `hourlyRate × (norm − onshoreHours − sickHours) × 1.75` | `OFFSHORE_RANDOM_ONSHORE` |
| `QUAYSIDE` | `(quaysideHours + quaysideNightHours) × hourlyRate × 1.60` | all |
| `OFFSHORE_NIGHT` | `offshoreNightHours × hourlyRate × 0.20` | all |
| `QUAYSIDE_NIGHT` | `quaysideNightHours × hourlyRate × 0.20` | all |
| `OFFSHORE_HOLIDAY` | `holidayHours × hourlyRate × 1.75` | all |
| `QUAYSIDE_HOLIDAY` | `holidayHours × hourlyRate × 1.60` | all |
| `HOTEL_QUARANTINE` | `quarantineHours × hourlyRate × 1.75` | all |
| `EXCESS` | see §5 | profile-dependent |
| `MEWA` | `onshoreAmount × mewaRate` (per employee) | where eligible |
| `MEAL` | `mealDays × 12 AZN`, 5 AZN/day exempt from every contribution base | all |
| `TRANSPORT` | `transportDays × 10 AZN` | all |
| `VACATION` | from average-earnings calculation, not from this month's hours | all |
| `SICK_PAY` | from leave | all |
| `EXTRA` | manual, reason required | all |

```
gross = Σ payable earning lines
```

Note `OFFSHORE_75` includes night hours in the 1.75 base **and** pays the 0.20
night top-up separately. That is what the spreadsheet does; it is deliberate,
not double-counting, because night hours are a subset re-classification and the
1.75 line is what pays them at all.

Every rate above is a row in `payroll.time_pay_rule` today. **No multiplier,
allowance rate or threshold goes in Java source.** `12`, `10`, `1.75`, `1.60`,
`0.20`, `2.0` are all data.

## 5. Excess hours — two different things that must never share a column

`overtimeHours ≠ excessHours`.

* **Onshore overtime** is a recognised overtime event, paid in the month at 2×.
* **Excess** is hours above the applicable norm, discovered by comparison.

Excess is **always system-calculated and read-only to employees and managers.**
Employees record where and when they worked; the system decides what exceeded a
norm. (The January workbook's `Excess hours` input column was zero in every row
while excess amounts were still paid — exactly the failure mode this prevents.)

### 5.1 Monthly settlement — `ONSHORE_RANDOM_OFFSHORE`

```
excessHours  = MAX(0, offshoreHours + onshoreHours + offshoreNightHours − normHours)
excessAmount = excessHours × hourlyRate × 1.75
```

Worked: `96 + 136 + 0 − 184 = 48`; `12 + 160 + 24 − 184 = 12`.
`56 × 19.0217… × 1.75 = 1,864.13` — matches the spreadsheet to the cent.

Never negative: a below-norm month pays no excess, and does not create a debt.

> **Open — BLOCKERS Q1:** whether `offshoreNightHours` belongs in that sum is
> contradicted by the capture model, where night hours are a re-classification
> of hours already counted in `offshoreHours`. Adding them double-counts. Both
> spreadsheet examples are consistent with either reading (one has zero night
> hours). **Un-inventable; materially changes pay.**

Note this uses **1.75**, not the 2× overtime rate. Excess in this profile is
priced as offshore work, not as overtime. Configurable, not assumed.

### 5.2 Balancing-period settlement — `OFFSHORE_ROTATION`

Summarised working-time accounting. Overtime is not decided monthly.

```
periodActual = Σ monthly eligible hours over the balancing period
periodNorm   = Σ monthly norm hours over the balancing period
excessHours  = MAX(0, periodActual − periodNorm)
```

Fixed company-wide periods and settlement payrolls:

| Period | Window | Settled in |
|---|---|---|
| 1 | 01 Jan – 30 Apr | April payroll |
| 2 | 01 May – 31 Aug | August payroll |
| 3 | 01 Sep – 31 Dec | December payroll |

Fixed calendar dates, **not** "every four months from the employee's start
date". Months 1–3 of a period accumulate only; nothing is paid.

### 5.3 The accumulator ledger

A rotation employee needs a visible running balance, one row per month, never
recomputed silently:

```
Employee EMP001 — period 2026-MAY-AUG
month   actual   norm   delta   running
May       180     160     +20      +20
Jun       140     168     −28       −8
Jul       220     184     +36      +28
Aug       200     168     +32      +60
                                  ────
settled at 31 Aug: 60 hours
```

Negative months reduce the balance (that is the point of summarised
accounting). The running balance may go negative mid-period; only the **final**
figure is floored at zero. Closing a period writes an immutable settlement row
and opens the next — payroll data is never physically deleted (global rule 12).

### 5.4 Excess price for rotation

> **Open — BLOCKERS Q2.** "2 qat və 75% əlavə" is either `× 2 × 1.75 = 3.50`
> or `× (2 + 0.75) = 2.75`. The July spreadsheets cannot resolve it: July is not
> a settlement month, so no rotation excess is present in the data. Ships as a
> mandatory configuration parameter with **no default** — an unconfigured
> rotation settlement refuses to calculate rather than guessing between two
> answers that differ by 27%.

## 6. MEWA

An employee-specific earning, observed at 30% and 60% of the onshore amount.
There is no global MEWA formula and one must not be invented.

```
mewaAmount = onshoreAmount × mewaRate      // per employee, effective-dated
```

Configured on the employee's contract/assignment: eligible yes/no, basis
(onshore earning), rate, effective dates. **MEWA is not excess.** The existing
`payroll.employee_excess_rule` currently conflates them — its
`PERCENT_OF_ONSHORE` method is MEWA and its `UNITS_AT_RATE` method is a fixed
excess quantity. §9 of this PRD splits them.

## 7. Attendance → payroll boundary

Payroll must not consume raw time. Already the shape of slices 1–2; restated
because the calculation engine depends on it.

```
employee enters timesheet (date, location, hours, offshore/onshore/quayside)
   → classification (night, holiday, quayside from schedule + calendar)
   → manager approval
   → attendance engine produces classified monthly quantities
   → excess / balancing calculation
   → period approved and LOCKED
   → payroll reads the locked summary
```

Payroll reads a locked attendance summary or it does not run. Post-lock
attendance changes require a controlled adjustment (global rule 14), never an
in-place edit of a paid period.

## 8. Calculation order

1. Resolve the employee's **calculation profile** (effective-dated). No
   profile ⇒ hard failure, not a default.
2. Load **norm hours** for the period. Missing ⇒ hard failure (it is the
   divisor behind every rate).
3. Load **approved, locked** attendance quantities. Unlocked ⇒ refuse.
4. Hours are already classified by the attendance engine.
5. `hourlyRate = base / norm`; `overtimeRate = hourlyRate × 2`.
6. Price earning components **per profile** (§4).
7. Excess — monthly or balancing-period per profile (§5).
8. Allowances: meal, transport, MEWA.
9. Vacation and sick pay (from leave, not from this month's hours).
10. `gross = Σ earnings`.
11. Statutory employee deductions (§10).
12. Other deductions: advance, alimony, loan installments, life insurance.
13. `net = gross − totalEmployeeDeductions`.
14. Employer contributions (§11).
15. Employer payroll cost.
16. **Client markup — outside payroll** (§12).

## 9. Data model

### Already built by slice 3 — reuse, do not duplicate

| Table | Role |
|---|---|
| `payroll.time_pay_rule` | multiplier catalog per category (1.75, 1.60, 0.20, meal 12/exempt 5, transport 10) |
| `payroll.time_pay_rule_override` | dated per-employee exception, mandatory reason |
| `payroll.period_norm_hours` | dated norm hours (151 Jan, 184 Jul) |
| `payroll.employee_excess_rule` | currently MEWA **and** excess conflated — split below |
| `TimesheetPayCalculator` | pure arithmetic, fixture-pinned |

`MONTHLY_SALARY_MULTIPLE` already exists as a pay basis on `time_pay_rule` —
it was added to reproduce workbook row 9. This PRD reclassifies it from
"per-employee override for an anomaly" to **the defining basis of the
`OFFSHORE_ROTATION` profile**.

### New

| Table | Role |
|---|---|
| `payroll.calculation_profile` | the four profiles: offshore salary mode, multipliers, excess method, balancing scheme, night/quayside factors |
| `payroll.employee_calculation_profile` | dated assignment of a profile to an employee, `tenant_id` + `employee_id` |
| `payroll.balancing_scheme` | period windows + settlement months (`OFFSHORE_4_MONTH`: Jan–Apr, May–Aug, Sep–Dec) |
| `payroll.excess_accumulator` | one row per employee × balancing period, status `OPEN` / `SETTLED` |
| `payroll.excess_accumulator_month` | actual, norm, delta, running balance — the audit trail behind a settlement |
| `payroll.employee_mewa_rule` | MEWA eligibility, basis, rate, effective dates (split out of `employee_excess_rule`) |

Every table carries `tenant_id`; every query filters on it. Accumulator and
settlement rows are append-only.

## 10. Employee deductions

Unchanged from slice 3 and reproduced as the workbook computes them, including
the asymmetries — tidying a statutory base silently changes people's pay.

```
mealExempt = mealDays × 5

incomeTax = 0                                                  if gross < 200
          = (gross − lifeIns − mealExempt − 200)  × 0.14       if (gross − lifeIns) ≤ 2500
          = (gross − lifeIns − mealExempt − 2500) × 0.25 + 350 otherwise

spf                 = (gross − sick − lifeIns − mealExempt) × 3%
unemployment        = (gross − mealExempt − sick) × 0.5%
compulsoryInsurance = (gross − mealExempt − sick) × 2%                    if gross ≤ 8000
                    = (gross − 8000 − mealExempt − sick) × 0.5% + 160     otherwise
```

SPF subtracts life insurance; unemployment and compulsory insurance do not.

> **Open — BLOCKERS Q5.** These are the 14/25 oil-and-gas rates. The main
> `StatutoryCalculator` in this repo implements 3/10/14 as 2026 private-sector
> (V306). Still unresolved from slice 3, and **no percentage here is verified
> against 2026 Azerbaijani legislation** — the workbook is evidence of current
> company practice, not of statutory correctness.

## 11. Employer contributions

Separate from employee deductions; never netted against pay.

```
employerSpf                 = applicableBase × 22%
employerUnemployment        = applicableBase × 0.5%
employerCompulsoryInsurance = same tier logic as the employee side
employerLifeInsuranceCost   = 22% linked to the employee's life-insurance value

employerTotalCost = gross + employerSpf + employerUnemployment
                  + employerCompulsoryInsurance + employerLifeInsuranceCost
```

The main engine already models employee/employer pairs via
`StatutoryCalculator`'s rule JSON; the profile engine feeds it, it does not
reimplement it.

## 12. Client markup is not payroll

```
markup           = employerTotalCost × 8%
grossCostCharged = employerTotalCost × 1.08
```

The company spreadsheets compute this in the same sheet as salary. **The system
must not.** It is contract/project costing and belongs with
`CostingCalculator` / `LaborRate`, downstream of a finalised payroll result.
Keeping it inside payroll would put a billing parameter inside the calculation
that determines someone's take-home pay.

## 13. Worked examples (fixtures)

`fixtures/july-2026-worked-examples.json`, all recomputed independently and
matching the spreadsheets to the cent. Norm = 184 throughout.

| Profile | Base | Input | Expected |
|---|---|---|---|
| rate | 3,500 | — | hourly 19.0217391304, OT 38.04 |
| `ONSHORE_FIXED` | 3,000 | 184 h | 3,000.00 |
| `ONSHORE_RANDOM_OFFSHORE` | 3,500 | 136 onshore h | 2,586.96 |
| `ONSHORE_RANDOM_OFFSHORE` | 3,500 | 96 offshore h | 3,195.65 |
| `ONSHORE_RANDOM_OFFSHORE` | 3,500 | 56 excess h | 1,864.13 |
| `ONSHORE_RANDOM_OFFSHORE` | 3,500 | 32 night h | 121.74 |
| onshore OT | 1,333 | 51.5 h | 746.19 |
| `OFFSHORE_ROTATION` | 2,210 | any offshore | 3,867.50 |
| `OFFSHORE_ROTATION` | 2,984 | any offshore | 5,222.00 |
| `OFFSHORE_RANDOM_ONSHORE` | 2,428 | 8 onshore h, 0 sick | offshore 4,064.26 + onshore 105.57 |
| meal / transport | — | 17 days | 204.00 / 170.00 |
| accumulator | — | +50 −30 +12 +32 | 64 h at 30 Apr |
| accumulator | — | +20 −28 +36 +32 | 60 h at 31 Aug |

## 14. Validations and edge cases

1. No calculation profile for the period ⇒ refuse to calculate. Never default.
2. No norm hours for the period ⇒ refuse.
3. Attendance period not approved and locked ⇒ refuse.
4. Excess hours floored at zero; a below-norm period pays nothing and creates
   no debt.
5. Rotation excess multiplier unconfigured ⇒ refuse the settlement (Q2).
6. A recorded quantity with no pay rule ⇒ warn loudly; never silently unpaid.
7. No MEWA rule ⇒ no MEWA paid, stated explicitly, not approximated.
8. Profile change mid-balancing-period ⇒ **BLOCKERS Q4** — the accumulator's
   ownership across a profile switch is undefined.
9. Termination mid-period ⇒ **BLOCKERS Q4** — settle the partial accumulator or
   forfeit it.
10. Retroactive attendance correction after a settled period ⇒ adjustment
    against the next open period, never a rewrite of a settled one.
11. `hourlyRate` uses full precision; only final figures round 2 dp HALF_UP.
12. Rotation employees with a full month of vacation ⇒ **BLOCKERS Q3**.

## 15. What is built

Built and passing as of 2026-08-19. **Nothing here runs, finalises, posts or
reverses a payroll**; `PayrollEngine` is untouched.

| Piece | Where |
|---|---|
| Schema, seeds, MEWA split-out backfill | `db/migration/V327__payroll_calculation_profiles.sql` |
| Accumulator wiring, configurable inputs, traceability columns | `db/migration/V328__payroll_profile_accumulator_wiring.sql` |
| Profiles, assignment, MEWA, balancing, accumulator entities + repos | `payroll/profile/` |
| The four pricing engines | `ProfilePayCalculator.java` |
| Balancing ledger, settlement, corrections | `ExcessAccumulatorService.java` |
| Posting on attendance-period lock | `ExcessAccumulatorPostingService.java` |
| Configuration writes, effective dating, audit | `CalculationProfileAdminService.java` |
| Read-only preview | `ProfilePayPreviewService/Controller.java` |
| Configuration API | `api/CalculationProfileAdminController.java` |
| Statutory deductions, shared with slice 3 | `payroll/timepay/StatutoryDeductionCalculator.java` |
| 84 tests | `src/test/java/az/millers/hcm/payroll/profile/` |

Verification: **833 tests pass**, including slice 3's 17 unchanged — the
statutory extraction moved no number. All 324 migrations apply to an empty
database, the seed script in `testing/` runs clean, and its teardown is
surgical.

### The accumulator has a production caller

`TimesheetPeriodService.lock()` publishes `TimesheetPeriodLockedEvent`;
`ExcessAccumulatorPostingService` listens **after commit** and posts every
employee on a balancing profile. Three properties make that safe:

* **The lock cannot fail because of payroll.** The listener runs after the
  attendance transaction commits and swallows its own errors — telling HR a lock
  failed when it succeeded would be worse than a missing ledger row.
* **One employee's gap does not hide the rest.** Missing norm hours or
  unconfigured categories are collected per employee and returned; everyone else
  still posts.
* **Re-posting is the correction path.** It replaces the month and recomputes
  every later running balance, and refuses on a settled period.

Which categories the accumulator sums is configuration
(`calculation_profile.accumulator_categories`), seeded to mirror the monthly
excess sum. Every posted month records the categories it was built from, so
changing the configuration never silently rewrites a past month.

### The endpoints

Read-only preview — payroll, compensation, HR admin, system admin:

```
GET  /api/payroll/calculation-profiles
GET  /api/payroll/calculation-profiles/preview/{year}/{month}
GET  /api/payroll/calculation-profiles/preview/{year}/{month}/{employeeId}
GET  /api/payroll/calculation-profiles/excess-ledger/{employeeId}
```

Configuration — deliberately narrower, **no HR admin**, because these change
what people are paid:

```
PUT    /api/payroll/calculation-profiles/admin/profiles/{code}
DELETE /api/payroll/calculation-profiles/admin/profiles/{code}/settings/{setting}
POST   /api/payroll/calculation-profiles/admin/assignments
GET    /api/payroll/calculation-profiles/admin/assignments/{employeeId}
POST   /api/payroll/calculation-profiles/admin/mewa
GET    /api/payroll/calculation-profiles/admin/mewa/{employeeId}
POST   /api/payroll/calculation-profiles/admin/norm-hours
POST   /api/payroll/calculation-profiles/admin/accumulator/post/{year}/{month}
POST   /api/payroll/calculation-profiles/admin/accumulator/settle
```

Every write requires a stated reason, is effective-dated where it applies to a
person, and is audit logged with its old and new value. Overlapping assignments
are closed or rejected — two profiles covering one month would make pay depend
on row order.

**Answering Q1, Q2 and Q6 is a `PUT`, not a deploy.** That is the point of the
whole design: the open questions live in configuration, so Emil's answer is a
request body with a reason attached and an audit entry behind it.

### Where it refuses

Three configuration values are unresolved and the engine will not invent them.
Each produces a blocker and a zero, never a plausible number.

| Refusal | Trigger | Question |
|---|---|---|
| Monthly excess | any offshore night hours recorded | Q1 |
| Rotation settlement | a period closes with payable hours | Q2 |
| Derived offshore | onshore + sick exceeds the norm | Q6 |
| Accumulator posting | no categories configured | Q6.1 |

Two more fail loudly rather than defaulting: an employee with no calculation
profile, and a period with no norm hours.

Offshore and quayside **earnings** do not refuse when Q1 is unanswered. They
fall back to treating night hours as a subset of the offshore figure and warn
that they did — that reading is slice 3's, pinned to the cent against the
January workbook, so it is the validated status quo rather than a guess. Excess
has no such precedent, which is why it refuses instead.

## 16. Scope

**In:** calculation profiles and their assignment; the four pricing engines;
the balancing accumulator and its ledger; MEWA split from excess; profile-aware
preview validated against `fixtures/`; employer-cost output.

**Out until sign-off:** `PayrollEngine` is not modified. Nothing here runs,
finalises, posts or reverses a payroll, and no payslip or GL posting is
produced. The accumulator records hours owed and a settlement records an amount
due, but no money moves until the engine is wired — which is the sign-off gate,
not an oversight. Client markup stays in costing, not payroll.

## 17. Acceptance criteria

1. Every fixture row in §13 reproduces to the cent.
2. Two employees with identical timesheets and identical base salaries but
   different profiles produce correctly different pay.
3. A rotation employee with one offshore hour is paid `base × 1.75` in full.
4. An onshore-contract employee with one offshore hour is paid `1 × rate × 1.75`.
5. The accumulator ledger shows every month's actual, norm, delta and running
   balance, and a settlement is traceable to those rows.
6. Months 1–3 of a balancing period pay no excess.
7. An unconfigured rotation excess multiplier refuses the settlement.
8. `EXCESS_HOURS` is read-only on every employee- and manager-facing surface.
9. No employee- or manager-facing endpoint exposes another person's amounts;
   salary and payroll figures stay permission-gated (global rules 6–9).
10. Markup appears in no payroll result, payslip or GL posting.
