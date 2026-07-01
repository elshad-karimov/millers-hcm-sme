# Compensation Management (HCM_10) — Analysis & Reconciled Build Plan

**Module:** compensation · **Jurisdiction:** AZ · **Currency:** AZN primary (currency field modelled; **no FX / no multi-country statutory** — explicit scope reduction, per the payroll-module decision) · **payroll_impact:** true (approved comp changes feed payroll — delivery hard-stops for human sign-off).

Gap-check verdict: **PASS** (no un-inventable gaps). All §36 formulas are pinned; thresholds/matrices are tenant-config with defaults (below).

---

## Core principle (the module's reason to exist)
> Compensation **defines, plans, validates, approves, and governs** compensation changes. **Payroll only processes** compensation that has been approved, effective-dated, audited, and transferred through controlled integration. Historical salary records are never overwritten.

---

## What already exists → REUSE / EXTEND (do NOT rebuild)

| Capability | Anchor (reuse) |
|-----------|----------------|
| Grades / job family / payroll group | `staffing.grade` (min/max/currency), `Grade`, `GradeService` — **EXTEND** (add midpoint / pay-band) |
| Canonical salary record (effective-dated, never-overwrite) | `payroll.employee_compensation`, `CompensationService.upsert()` — **write all approved salary changes through this** |
| Compa-ratio / range penetration | `CompRatioService` (M102) — reuse the math |
| Merit / review cycle + worksheet + planning | `CompCycle` + `CompProposal` (M118), `CompPlanningService`, approval → `CompensationService.upsert()` — **EXTEND** |
| Allowances (payroll-wired) | `EmployeeAllowance` + `payroll.payroll_allowance` |
| Bonus (matrix, run, simulator, payroll push) | `BonusRunService`, `BonusMatrix`, `payroll.payroll_bonus` — reuse |
| Approval workflow | `WorkflowService` — seed a `workflow_definition` + steps, start on the record, `@EventListener` on completion → apply |
| Comp letters | `LetterRequestService` (seed `letter_template`, submit with custom fields) |
| GL / ERP export | `ErpExportService`, GL journal (V198) — comp flows to GL via the next payroll run |
| Performance ratings feed | `PerformanceReview.finalRating` |
| Position ↔ grade | `staffing.position.grade_id` |

**Genuinely NEW:** pay bands w/ stored midpoint+quartiles, salary-change request + governance, compensation exceptions, merit matrix table, compensation budget vs actual, incentive plans, commission plans, market survey data, total-compensation statement, explicit comp→payroll transfer record, comp dashboard/analytics, comp documents/notifications, comp security/audit hardening.

## Convention locks (from audit)
- `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'` (String, NOT UUID). Next migration = **V200**.
- `AuditService.record(module, entityName, entityId, action, oldValue, newValue)` (6-param).
- `BadRequestException` + `ResourceNotFoundException` in `az.millers.hcm.common`.
- Roles: `az.millers.hcm.security.SecurityRoles` (READ_PAYROLL / WRITE_PAYROLL / WRITE_HR_ADMIN_ONLY; R_* name constants). No comp-specific role yet — add `READ_COMPENSATION` / `WRITE_COMPENSATION` if separation is wanted; otherwise reuse payroll/HR roles.
- `AccessScopeService.scopeForCurrentUser()` for manager hierarchy scoping.
- Schema prefix for new tables: `compensation.`

---

## Pinned formulas (§36 — non-negotiable)
- **Compa-ratio** = employee_salary / range_midpoint × 100
- **Range penetration** = (salary − min) / (max − min) × 100
- **Merit amount** = current_salary × merit_pct
- **New salary** = current + merit + promotion_adj + market_adj
- **Bonus** = eligible_salary × target_bonus_pct × performance_multiplier
- **Total compensation** = base + allowances + bonus + incentives + employer_benefits + employer_contributions

## Pinned validation rules (§43 — acceptance criteria)
Grade/band code unique per tenant · min ≤ mid ≤ max · salary ≥ 0 · currency required · effective_from ≤ effective_to · one active comp record per (employee, period) · **salary change outside band → exception approval** · **increase > threshold → approval** · **budget exceeded → policy (WARNING/HARD_STOP/EXCEPTION_APPROVAL)** · worksheet can't submit with missing required employees · **comp change can't go to payroll before approval** · no payroll transfer for terminated (unless final-settlement) · **employee can't approve own change** · manager can't see outside hierarchy · tenant isolation · **historical salary never overwritten** · transfer idempotent.

## Pinned tenant-config defaults (to seed)
| Key | Default |
|-----|---------|
| `max_increase_pct_without_approval` | 15 |
| `budget_exceeded_policy` | WARNING (or HARD_STOP / EXCEPTION_APPROVAL) |
| `default_currency` | AZN |
| Merit matrix | §11 example, 4 ratings × 3 range-positions (Excellent 8/6/4, Good 5/4/2, Meets 3/2/1, Below 0/0/0) |
| Incentive curve | threshold 80% (no payout), target 100%, cap 120% |

---

## Reconciled milestone plan (reuse-first — M359 → M372)

### Phase A — Foundation (M359–M360)
- **M359** Compensation schema + tenant comp config + **pay bands** (grade min/mid/max/quartiles, effective-dated, band-code unique, min≤mid≤max) + Compensation Structure admin page. Seed config defaults + a starter band set.
- **M360** Employee **Compensation Profile** (current salary, grade, band, **compa-ratio + range penetration** via reused CompRatioService, pending changes, history) + **change-reason** catalog. Profile tab on employee page + a Compensation module landing.

### Phase B — Salary-change governance (the core new value) (M361–M362)
- **M361** **Salary change request + approval workflow**: request (current/proposed, reason, effective date, band+threshold checks) → seed `SALARY_CHANGE_APPROVAL` workflow → on APPROVED, write via `CompensationService.upsert()` (effective-dated, never overwrite) + audit. Self-approval blocked; >15% or out-of-band ⇒ exception path.
- **M362** **Compensation exceptions** (BELOW_BAND / ABOVE_BAND / OVER_BUDGET / ABOVE_THRESHOLD) register + dashboard, tied to M361 + budgets.

### Phase C — Cycles, merit matrix, budgets (extend M118) (M363–M364)
- **M363** **Merit matrix** table (seed §11 example) wired into CompCycle/CompProposal planning to auto-suggest merit % from performance rating × range penetration; out-of-guideline ⇒ exception.
- **M364** **Compensation budget control**: budget by cycle/dept/manager/grade, consumed vs remaining, over-budget policy enforcement.

### Phase D — Variable pay NEW (M365–M366)
- **M365** **Incentive plans + payouts** (target, threshold/target/cap curve, floor/cap) → one-time payout to payroll.
- **M366** **Commission plans + payouts** (rate / tiered; sales-input stub) → one-time payout to payroll.

### Phase E — Market data, total-comp, transfer (M367–M369)
- **M367** **Market salary survey + data** (percentiles P25/50/75/90) + market-ratio comparison on the profile.
- **M368** **Total Compensation Statement** (base + allowances + bonus + incentives + employer benefits + contributions) → PDF (reuse PayslipPdfService pattern) + employee self-service.
- **M369** **Compensation → Payroll transfer** record (idempotent, effective-dated, APPROVED-only, not terminated) + status tracking; one-time bonus/incentive/commission → `payroll_bonus`.

### Phase F — Dashboard, documents, security/audit (M370–M372)
- **M370** **Compensation dashboard + analytics + reports** (compa-ratio distribution, out-of-band, budget utilisation, pending approvals).
- **M371** **Comp documents** (salary-increase / promotion / bonus letters via letter engine) + **notifications**.
- **M372** **Security + audit hardening** (field-level salary masking, export restriction, comp roles, SoD) + final quality gate.

Payroll-impact hard-stop applies to M369 (transfer to payroll) — surfaced for human sign-off at delivery (AUTONOMY: gated).

## Testing deliverable
As each phase completes, its front-end UAT scenarios are appended to
`Compensation_UAT_Test_Script.xlsx` (Instructions · Test Cases w/ Pass/Fail dropdown · Sign-off),
same format as the payroll UAT file, so a tester can execute and record results per feature.
