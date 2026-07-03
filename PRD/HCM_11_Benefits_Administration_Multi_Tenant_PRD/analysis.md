# HCM_11 — Benefits Administration — Analysis & Lean Build Plan

**Module:** benefits · **payroll_impact:** true (M378 employee-contribution deduction bridge + M383 GL posting)
**Depends on:** HCM_09 Payroll (done), HCM_10 Compensation (done).
**Scope guard:** AZ / AZN only. Currency modelled but **no FX / no multi-country statutory logic** (same reduction as payroll & compensation).

## Phase 1 outcome
- **gap-checker: PASS** — no hard stops. All cost/contribution/eligibility/proration rules are tenant-configured (inventable with seeded defaults). Seed defaults adopted: proration = calendar-days / 30-day month; default split employer-paid 100/0; 8 AZ categories (Health, Life, Pension, Meal, Transport, Housing, Mobile, Wellness); dependent child eligible to 18 (25 if student); contribution cap = monthly; OE window default 30 days; claim limit = annual.
- **Existing-code audit** → the decisive finding: **M108 already ships a real (shallow) benefits module** (`compbenefits` schema/package, `BenefitsService`, `BenefitsController`, `BenefitsPage.tsx`, nav `/compbenefits/benefits`). **Verdict: EXTEND M108 in place** — same schema/package/nav/SPA page (new tabs). Do NOT fork a parallel module (no-spaghetti rule).

## Reuse map (audit-confirmed)
| Capability | Verdict | Reuse target |
|---|---|---|
| Plan catalog + enrol/waive/terminate lifecycle | **EXTEND** | `compbenefits.BenefitsService` (M108) |
| Allowances (meal/transport/housing) | REUSE as reference only | `comp_benefits.employee_allowance` + `PayrollEngine` — do **not** re-implement |
| Dependents (+ `benefit_eligible`/`insurance_eligible`, temporal eligibility) | REUSE | `core_hr.employee_dependent` (M71/M135); only NEW = enrollment↔dependent junction |
| Employee-contribution → payroll | REUSE | `payroll.payroll_deduction` RECURRING (`PayrollDeductionService.create/cancel`, engine already picks up) |
| Approval workflow | REUSE | `workflow` engine — seed definition, `WorkflowService.start`, `@TransactionalEventListener(AFTER_COMMIT)`+REQUIRES_NEW finalize (compensation SALARY_CHANGE pattern) |
| Confirmation letters | REUSE | `hr_letters` letter engine (`LetterRequestService.submit`, seed template) |
| GL / ERP posting | **EXTEND** | `payroll.GLJournalService` — add BENEFIT_EMPLOYER_COST/PAYABLE lines |
| Total-comp `employer_benefits_total` = ZERO placeholder | **EXTEND** (fill seam) | `compensation.TotalCompStatementService` (~line 118) |
| Self-service | REUSE | `MyWorkspacePage` Tabs registry + `SelfController` + `EmployeeContextService`; M108 already exposes `/me` |
| Provider/vendor master, insurance/pension structure, coverage tiers, claims, provider-file import/reconcile | **NEW** | (claims mirror `businesstrip.ExpenseClaim` state machine; import mirrors `EmployeeImportService`) |
| Roles | NEW constants | add `READ_BENEFITS`/`WRITE_BENEFITS` + `BENEFITS_MANAGER` to `SecurityRoles` (M108 currently borrows HR roles) |

## Conventions (locked)
- Schema `compbenefits` (extend); package `az.millers.hcm.compbenefits`; nav `/compbenefits/benefits` (add tabs).
- New tables: `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'` mapped `private String tenantId = "default";`. Backfill `tenant_id` onto the pre-multitenant M108 tables (`benefit_plan`, `benefit_enrollment`) and `payroll.payroll_deduction`.
- Next Flyway = **V206** (V193/V198 have benign uncommitted FK-drop fixes from the payroll build — leave intact). `AuditService.record` 6-param. Exceptions in `az.millers.hcm.common`. `AccessScopeService` for scoping.

## Lean milestone plan — M373–M387 (15 milestones, 6 phases)
Consolidated from the analyzer's 31 (M373–M403) by folding overlap into EXTEND/REUSE. Each milestone = migration + backend + SPA, boot-verified, committed; UAT Excel grown per phase.

**Phase A — Catalog foundations**
- **M373** (V206) Benefit **categories** master (seed 8 AZ) + **roles** (`READ/WRITE_BENEFITS`, `BENEFITS_MANAGER`) + `tenant_id` backfill on `benefit_plan`/`benefit_enrollment`. Categories tab.
- **M374** (V207) Benefit **provider/vendor master** (contract no + dates + contact); link `benefit_plan.provider_id` (legacy free-text kept). Providers tab.
- **M375** (V208) Plan enrichment: **coverage tiers** (`benefit_plan_tier`: EMPLOYEE_ONLY/…/FAMILY, per-tier employer/employee split + coverage amount) + **structured eligibility rules** (`benefit_eligibility_rule`, AND-ed filters à la `ChecklistTemplateRule`) + category/provider link + plan_year. Plan editor extended.

**Phase B — Enrollment & contribution → payroll**
- **M376** (V209) Enrollment overhaul: coverage-tier selection + **dependent-level junction** (`benefit_enrollment_dependent`) with eligibility validation (reuse dependent flags) + full status machine (DRAFT→SUBMITTED→PENDING_APPROVAL→APPROVED→ACTIVE→SUSPENDED→TERMINATED/EXPIRED/CANCELLED/REJECTED) + contribution snapshot from tier.
- **M377** (V210) **Enrollment approval workflow** (seed `BENEFIT_ENROLLMENT_APPROVAL`, mgr+HR steps); submit→start; AFTER_COMMIT+REQUIRES_NEW activates on APPROVED; self-approval blocked.
- **M378** (V211) **Payroll deduction bridge** (payroll_impact core): `source_benefit_enrollment_id` + `tenant_id` on `payroll.payroll_deduction`; on ACTIVE→idempotent RECURRING `PayrollDeduction(employeeContribution)`, on terminate/suspend→cancel. Fixtures + payroll-validation.

**Phase C — Open enrollment & life events**
- **M379** (V212) **Open-enrollment windows** + plan year; enrollment/change gated to an open window or a life-event window.
- **M380** (V213) **Qualifying life events** (marriage/birth/…) open a per-employee special enrollment window; approval + audit.

**Phase D — Claims**
- **M381** (V214) **Benefit claims** + items (mirror `ExpenseClaim` DRAFT→SUBMITTED→APPROVED/REJECTED→PAID) against an active enrollment.
- **M382** Claim **approval + payout tracking** (role-gated approve/reject/markPaid; reimbursement = tracking only — NOT auto-pushed to payroll, mirroring `ExpenseClaim.markPaid`; documented seam to avoid the `payroll_bonus.run_id` NOT-NULL trap).

**Phase E — Finance & reconciliation**
- **M383** (V215) **GL posting** for employer benefit cost (extend `GLJournalService` + `gl_account_mapping`) + **fill the Total-Comp `employer_benefits_total` seam** (`calculateEmployerBenefitCosts`).
- **M384** (V216) **Provider-file import + reconciliation** (mirror `EmployeeImportService` validate-and-report; discrepancy report enrolled vs provider roster).

**Phase F — Self-service, dashboard, reports, notifications**
- **M385** Employee **My Benefits** tab in `MyWorkspacePage` (reuse `/me`) — enrollments, contributions, dependents covered, submit claim / life event.
- **M386** Benefits **dashboard + reports** (AccessScope-scoped): enrolment by category/plan, employer spend, participation rate, claims summary, upcoming renewals.
- **M387** **Confirmation letters** (seed `BENEFIT_CONFIRMATION_LETTER` en+az) + **notifications** on enrol/approve/claim/life-event (reuse `NotificationService`; `NotificationCategory.BENEFIT_ENROLLMENT` already exists).

## Payroll hard-stop / sign-off (CLAUDE.md rule 3)
M378 (deduction bridge) & M383 (GL) are payroll-affecting. Build autonomously (deductions are *standing instructions*, not a run finalize/reverse → contained risk), seed fixtures, run payroll-validation, then **present at the gate for human sign-off before merge** (AUTONOMY=gated). Claims payout is tracking-only (no money movement) by design.

## Testing deliverable
`Benefits_UAT_Test_Script.xlsx` (front-end, click-by-click, Pass/Fail) regenerated from `gen_benefits_uat.py` and grown each phase — same shape as the compensation UAT (Instructions + Test Cases w/ status dropdown + Sign-off).
