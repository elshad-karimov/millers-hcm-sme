# BLOCKERS — timesheet-payroll-inputs

**Status: defaults selected, built and validated. Stopped before payroll integration.**

## Defaults selected (2026-08-14) — change the data, not the code

You asked to pick one of each and iterate. These are the choices; all three are
configuration, so revising them is a row, not a deploy.

| # | Question | Selected | How to change it |
|---|---|---|---|
| 1 | Deviating rows | **canonical hourly rule** is the default; deviations are dated per-employee overrides with a mandatory reason | insert into `payroll.time_pay_rule_override` |
| 2 | Excess / MEWA | **`PERCENT_OF_ONSHORE`**; no rule ⇒ no excess paid | insert into `payroll.employee_excess_rule` |
| 3 | Income tax | **`AZ_OIL_GAS_2026`** (the workbook's 14/25) | tenant setting `payroll.timepay.tax-regime` |

Row 9's `salary × 1.75` basis is now a supported pay basis
(`MONTHLY_SALARY_MULTIPLE`) rather than a special case, and with that override
in place **the engine reproduces row 9 to the cent** — proving the deviation is
something the system can hold as configuration.

Rows 8 and 12 remain deviations by design: their workbook formulas use hour
counts that contradict the declared quantities (136 vs 132, and 80/168 vs
121/154). Those are not a rate rule, so they belong in an adjustment line, not
an engine. The questions below still stand.

---

**Original analysis follows.**

Per CLAUDE.md autonomy rule 3, a payroll-affecting feature always needs human
sign-off, and rules 1–2 forbid inventing a payroll formula. Three questions
below are un-inventable. `PayrollEngine` is **untouched**; nothing here can move
money until they are answered.

Everything unambiguous in the workbook **is** built and passes: 15 calculator
tests, 706 in the suite, rows 13 and 14 reproduced to the cent, rows 10 and 11
reproduced once their excess rule is configured.

---

## 1. Three employees are paid by formulas that contradict the stated rules

| Row | Employee | Workbook formula | Canonical rule | Gap |
|---|---|---|---|---|
| 8 | HVAC Technician | offshore uses **136** hours | declared offshore hours = **132** | 102.45 AZN |
| 9 | Vessel Field Engineer | `baseSalary × 1.75` — no hours, no rate | 35 hrs × rate × 1.75 | **≈ 4,012 AZN** |
| 12 | Senior Electrician Offshore | offshore **80** hrs, quayside **168** hrs | declared 121 and 154 | material |

**Row 9 is the serious one.** It pays offshore as a monthly multiple rather than
per hour — 5,222 AZN where the hourly rule gives 1,210 AZN. That is either a
different contractual pay basis for rota staff, or an error that has been paid.

**Question:** for each of these three, is the workbook's figure the correct pay,
or is the canonical hourly rule correct? If some staff really are paid
`salary × 1.75` regardless of hours, that is a fourth pay basis and needs to be
stated as a rule, not typed per person each month.

Until answered: the engine computes the canonical rule and reports rows 8, 9 and
12 as deviating. It does not reproduce the overrides.

## 2. Excess / MEWA has no single rule

Four employees, four different formulas — two as a percentage of onshore pay
(30%, 60%), two as a hardcoded unit count at 1.6× rate (33, 85). The declared
`Excess hours` input is **zero in every row**, so the quantity an employee
records has never driven this amount.

**Question:** confirm the method and value for every employee who receives
excess. An employee with no configured rule earns no excess — deliberately, so
a missing configuration is visible rather than silently approximated.

## 3. The income-tax regime conflicts with what this system already implements

| | Rate structure | Exemption |
|---|---|---|
| **Your workbook** | 14% up to 2,500 AZN, then 25% + 350 | 200 AZN |
| **`StatutoryCalculator` in this repo** | 3% / 10% / 14% | none (V306) |

These are different regimes — the workbook's is the oil-and-gas / public-sector
one, the repo's is documented as 2026 private-sector. They produce materially
different tax on the same gross.

**Question:** which regime applies to this legal entity? If both apply to
different entities, tax must become entity-scoped, which is a change to the
existing engine and its own slice of work.

Until answered: the new calculator implements the workbook's regime in
isolation. **`StatutoryCalculator` is unchanged**, so no existing payroll
behaviour moved.

---

## Smaller things, decided and worth confirming

* **Base salary is not paid.** Gross is the sum of the category amounts; an
  employee with no hours earns nothing. Derived from the workbook, where
  `GROSS = SUM(T:AI)` never includes column Q.
* **Meal allowance**: 12 AZN/day paid, 5 AZN/day exempt from every contribution
  base. Implemented as `exempt_per_unit`.
* **SPF subtracts life insurance; unemployment and compulsory insurance do
  not.** Reproduced as-is — tidying a statutory base silently changes pay.
* **Column AR "Other"** is a display subtotal of life insurance + Azercell +
  advance, outside `SUM(AK:AQ)`. Not implemented as a deduction.
* **Column X "Quayside amount"** is empty everywhere and duplicates U. Treated
  as dead.
* **Rounding**: full precision throughout, 2dp HALF_UP only on the final
  figures — matching the workbook. Rounding per line drifts by cents.

## What is deliberately not built

* No change to `PayrollEngine`, no payroll run, no payslip, no GL posting.
* No preview UI. Showing HR a calculated net that embeds three unresolved
  questions would invite it to be trusted before it has been signed off.

---

## Update 2026-08-19 — question 1 is answered by the July 2026 material

The WhatsApp discussion with Emil plus the three July 2026 spreadsheets identify
**row 9 as an offshore rotation contract**, not an error. Rotation staff are paid
`baseSalary × 1.75` monthly regardless of how many offshore days they worked;
the rotation spreadsheet shows `2,984 × 1.75 = 5,222.00` — the same base salary
and the same figure as row 9.

So the 4,012 AZN gap was a real contractual pay basis. `MONTHLY_SALARY_MULTIPLE`
stops being a per-employee override for an anomaly and becomes the defining
basis of a named calculation profile.

* **Rows 8 and 12 remain unexplained.** Nothing in the July material addresses
  the hour counts that contradict the declared quantities.
* **Question 2 (excess / MEWA) is reframed, not closed.** MEWA and excess are
  two different earnings that `payroll.employee_excess_rule` currently conflates.
* **Question 3 (income-tax regime) is still open**, carried forward unchanged.

Continues in `prd/payroll-calculation-profiles/` — see its `PRD.md` and
`BLOCKERS.md`.
