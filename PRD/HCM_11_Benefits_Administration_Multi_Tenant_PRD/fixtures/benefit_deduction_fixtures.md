# HCM_11 M378 — Benefit → payroll deduction fixtures

Worked examples for `payroll-validation`. The benefit→payroll bridge creates a **RECURRING**
`payroll.payroll_deduction` from an active enrollment's **employee-contribution snapshot**.
The `PayrollEngine` subtracts RECURRING deductions from **taxable gross** (PayrollEngine.java
~line 384), so benefit employee-contributions are **pre-tax**.

## Deterministic assertions (independent of statutory rates)

### F1 — activation creates the deduction
- **Given** a plan with employee contribution **30.00 AZN/mo**, and an employee enrolled ACTIVE
  with `startDate = 2026-07-01`.
- **Then** exactly one `payroll.payroll_deduction` row exists with:
  - `deduction_type = 'RECURRING'`
  - `amount_per_period = 30.00`
  - `status = 'ACTIVE'`
  - `start_period_year = 2026`, `start_period_month = 7`
  - `end_period_year IS NULL` (open-ended)
  - `source_benefit_enrollment_id = <enrollment id>`
  - `description = 'Benefit contribution: <plan name>'`

### F2 — tiered contribution snapshot
- **Given** a plan with a **FAMILY** tier employee contribution **90.00**, enrolled on that tier.
- **Then** the deduction `amount_per_period = 90.00` (the tier snapshot, not the plan flat rate).

### F3 — zero employee contribution creates no deduction
- **Given** an employer-paid plan (employee contribution **0.00**), enrolled ACTIVE.
- **Then** **no** `payroll_deduction` row is created for that enrollment.

### F4 — idempotency
- **Given** F1's enrollment, and the activation hook runs again (e.g. resume, or re-approval).
- **Then** still exactly **one** ACTIVE deduction for that enrollment (no duplicate).

### F5 — suspend / terminate cancels
- **Given** F1's ACTIVE deduction, when the enrollment is **suspended** or **terminated**.
- **Then** that deduction row becomes `status = 'CANCELLED'` (never physically deleted).
- Resuming a suspended enrollment creates a **new** ACTIVE deduction (F1 shape).

## Net-pay invariant (engine-derived)

For a payroll run covering the deduction's period, with employee monthly base **B** and benefit
employee-contribution **D**:

- `taxable_gross` decreases by **D** (vs. the same employee with no benefit deduction).
- Because the deduction is pre-tax, `net` decreases by **D − Δstatutory(D)**, where
  `Δstatutory(D)` is the marginal income-tax + DSMF + MMI + unemployment on the reduced base.
- **Invariant to check:** run payroll for the sample employee twice — once with the enrollment
  ACTIVE and once after terminating it — and confirm the ACTIVE run's `deduction_amount` is
  exactly **D** higher and `net_pay` is lower by `D − Δstatutory(D)`.

> Sample to run live: employee base **2000.00 AZN**, benefit employee-contribution **30.00**.
> Expected `deduction_amount` contribution from benefits = **30.00**; net decreases by 30 minus
> the statutory marginal on 30. The exact net is produced by the AZ 2026 statutory calculator —
> `payroll-validation` should assert the **30.00 deduction row** and the **directional net delta**
> against a real run rather than a hard-coded net (rates are tenant/jurisdiction config).
