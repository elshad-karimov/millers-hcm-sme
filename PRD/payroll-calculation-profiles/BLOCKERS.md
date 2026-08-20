# BLOCKERS — payroll-calculation-profiles

**Status: built and validated. HARD STOP before it drives a live payroll run**
(CLAUDE.md autonomy rules 1–3).

Everything the July 2026 material pins down is implemented and passing —
profiles, the four pricing engines, the balancing accumulator, the MEWA split,
and 45 tests against the company's own figures. `PayrollEngine` is untouched and
the only way in is a read-only preview.

The six questions below still cannot be inferred from the spreadsheets or the
chat, and each changes what a real person is paid. Three of them make the engine
**refuse and say so** rather than produce a number:

| Question | What refuses | What it costs to guess |
|---|---|---|
| Q1 | monthly excess, whenever night hours are present | ~800 AZN/employee/month |
| Q2 | every rotation settlement | ~856 AZN/employee/settlement |
| Q6 | derived offshore when onshore + sick exceeds the norm | the whole offshore line |
| Q6.1 | accumulator posting when no categories are configured | how many hours reach a settlement |

Autonomy rule 3 applies regardless: this touches salary, so it needs human
sign-off before any live run.

Every worked example in `fixtures/july-2026-worked-examples.json` was
recomputed independently, matches the company spreadsheets to the cent, and is
asserted by a test. See `PRD.md` §15 for what was built and where.

---

## Resolved by this material — carried back to slice 3

**`timesheet-payroll-inputs` BLOCKERS Q1 (row 9) is answered.** The employee
paid `baseSalary × 1.75` with no hours is on an **offshore rotation contract**.
Rotation staff receive base + 75% monthly regardless of offshore days worked.
The 4,012 AZN gap was a real contractual pay basis, not an error, and
`MONTHLY_SALARY_MULTIPLE` is promoted from per-employee override to the defining
basis of the `OFFSHORE_ROTATION` profile.

Rows 8 and 12 of the January workbook remain unexplained — their offshore and
quayside lines use hour counts that contradict the declared quantities
(136 vs 132; 80/168 vs 121/154). Nothing in the July material addresses them.

---

## Q1 — Do night hours enter the monthly excess sum? *(highest risk)*

The spreadsheet's excess formula is
`offshoreHours + onshoreHours + offshoreNightHours − norm`.

But in this system night hours are a **re-classification of hours already
counted** in `offshoreHours` — that is how they are captured (V317, shift
schedule) and why the night line pays only a 0.20 top-up rather than a full
rate. Adding them again to the excess sum **double-counts every night hour**.

Both spreadsheet examples are consistent with either reading: the first has zero
night hours, the second (`12 + 160 + 24 − 184 = 12`) can't distinguish "night
added" from "night already inside the 12 offshore hours" without the underlying
timesheet.

**Question:** in the excess comparison, are night hours a separate addend, or
are they already inside the offshore hours figure?

At 24 night hours and a 19 AZN rate, getting this wrong is ~800 AZN per employee
per month, in the direction of overpayment.

This governs two things, and they are handled differently because the evidence
differs:

* **Earnings** — offshore and quayside fall back to treating night as a subset
  (not added to the 1.75 / 1.60 base) and warn that they did. That is not a
  guess: it is slice 3's behaviour, pinned to the cent against January workbook
  rows 13 and 14.
* **Excess** — refuses outright whenever night hours are present. There is no
  validated precedent (slice 3 never derived excess from hours), so there is
  nothing to fall back to.

Answering Q1 sets `calculation_profile.night_hours_separate_from_base` and both
paths follow it. A test already pins the answered case: with the flag set to
true, the spreadsheet's `12 + 160 + 24 − 184 = 12` reproduces exactly.

## Q2 — Rotation excess multiplier: 3.50× or 2.75×?

"2 qat və 75% əlavə" reads either way:

| Reading | Multiplier | 60 excess hours at 19.02 AZN |
|---|---|---|
| A — 2 × 1.75 | 3.50 | 3,994.57 AZN |
| B — 2 + 0.75 | 2.75 | 3,138.59 AZN |

A 856 AZN difference per employee per settlement. The July spreadsheets cannot
resolve it — July is not a settlement month, so no rotation excess appears in
the data.

**Question:** one real April, August or December payroll line for a rotation
employee with settled excess would settle it definitively.

Ships as a **mandatory parameter with no default**; an unconfigured rotation
settlement refuses to calculate.

## Q3 — Vacation, sick leave, and the rotation qualification

Three linked unknowns:

1. Do vacation and sick-leave hours count as **actual** hours in the
   balancing-period comparison, or do they **reduce the norm** for that period?
   These give different excess totals.
2. Emil states a rotation employee gets `base × 1.75` if they were offshore for
   **even one hour** in the month. Does that hold for a month spent entirely on
   vacation or sick leave — i.e. zero offshore hours? What is the pay basis
   then: plain base salary, or nothing from this line?
3. Vacation amounts in the spreadsheets come from separate history and cannot be
   reconstructed from the month's hours. Confirm vacation pay is average-earnings
   based, and over which reference period.

## Q4 — Accumulator lifecycle: profile change, termination, transfer

Undefined in the source material, and unavoidable in production:

* An employee moves from `OFFSHORE_ROTATION` to `ONSHORE_FIXED` in month 2 of a
  balancing period — is the partial accumulator settled at the switch, carried,
  or void?
* An employee is terminated in month 3 — is the partial balance paid out at
  final settlement, or forfeited?
* Retroactive attendance correction lands against an already-settled period —
  confirm it becomes an adjustment on the next open period and never rewrites a
  settled one (this is the design assumption; global rules 12–14).

## Q5 — Income tax regime *(still open from slice 3)*

| | Rates | Exemption |
|---|---|---|
| Company workbooks | 14% to 2,500, then 25% + 350 | 200 AZN |
| `StatutoryCalculator` (V306) | 3% / 10% / 14% | none |

Different regimes, materially different tax on the same gross.

**Question:** which applies to this legal entity? If both apply to different
entities, tax must become entity-scoped — its own slice of work.

Separately: **no statutory percentage in this PRD is verified against 2026
Azerbaijani legislation.** SPF 3%/22%, unemployment 0.5%, compulsory insurance
2%/0.5% above 8,000, and the tax brackets are all transcribed from the company's
current spreadsheets — evidence of company practice, not of legal correctness.
This needs legal verification before production go-live, independently of the
question above.

## Q6 — Which hours enter the accumulator, and is the derived-offshore formula universal?

1. For rotation employees, which categories count as "actual eligible hours"?
   Offshore only, or offshore + onshore + quayside + night + holiday?

   **Now configuration, not code.** `calculation_profile.accumulator_categories`
   is seeded to `OFFSHORE_HOURS,ONSHORE_HOURS` so the balancing sum mirrors the
   monthly excess sum, with night added only when Q1 says night hours are extra.
   Every posted month records the categories it was built from, so correcting
   this later never silently rewrites a past month — but the seeded set is a
   reasoned default, **not a confirmed answer**, and it decides how many hours
   reach a settlement.
2. `OFFSHORE_RANDOM_ONSHORE` prices offshore on **derived** hours:
   `hourlyRate × (norm − onshoreHours − sickHours) × 1.75`. This ignores the
   employee's **actual recorded offshore hours** entirely and imputes them from
   the norm. Confirm that is intended for every employee on this profile, and
   what happens when `onshore + sick > norm` (the term goes negative — floor at
   zero, or is it an error?).

## Q7 — MEWA rates, per employee

Observed at 30% and 60% of the onshore amount. No global rule exists and none
will be invented. **Provide the eligibility flag, basis and rate for every
employee who receives MEWA**, with effective dates. An employee with no rule is
paid no MEWA — deliberately visible rather than silently approximated.

## Q8 — Confirm as company policy (configurable, lower risk)

Not blocking the build — each is a configuration row — but should be confirmed
before the first live run:

* Quayside factor **1.60** and night top-up **0.20**.
* Public-holiday work priced at the same 1.75 / 1.60 factors as ordinary
  offshore / quayside work. The spreadsheets show only current practice; whether
  that satisfies statutory holiday-pay requirements is unverified.
* Hotel/quarantine hours priced at the offshore 1.75 factor.
* Meal 12 AZN/day (5 exempt) and transport 10 AZN/day — current rates,
  effective-dated so a change is a row, not a deploy.
* The 8% markup is billing, not payroll, and must not appear in any payslip,
  payroll result or GL posting.
