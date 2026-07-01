# Payroll Multi-Tenant Module — Analysis (M349–M358)

## Module Scope

**Module:** payroll  
**Jurisdiction:** Azerbaijan (AZ) only  
**Currency:** AZN only  
**Payroll Impact:** YES (8 of 10 milestones directly affect payroll calculations)

The PRD extends the existing payroll infrastructure with:
1. Configurable salary components (earnings/deductions) with per-employee assignments
2. Salary advance management with approval and payroll recovery
3. Payroll loan management with automatic installment deductions
4. Off-cycle payroll runs for ad-hoc payouts
5. Payroll variance reporting (period-over-period comparison)
6. Year-end processing and AZ annual tax certificates (Form 154)
7. Cost center allocation with GL journal generation
8. PDF payslip generation with MinIO storage and email delivery
9. Payroll control board (pre-flight checks, dashboard, employee holds)
10. Extended payroll reports suite (7 reports)

## Milestone Table

| Milestone | Feature | Flyway | Payroll Impact | Dependencies |
|-----------|---------|--------|----------------|--------------|
| M349 | Salary component catalog + per-employee assignments + PayrollEngine integration | V193 | **YES** | PayrollEngine, StatutoryCalculator |
| M350 | Employee payroll hold + off-cycle run type | V194 | **YES** | PayrollRun, PayrollEngine |
| M351 | Salary advance requests + payroll deduction recovery | V195 | **YES** | PayrollDeduction, AuditService |
| M352 | Payroll loan + installment auto-deduction | V196 | **YES** | PayrollDeduction, PayrollEngine |
| M353 | Payroll variance report + YTD summary | — | No | PayrollResultRepository |
| M354 | Year-end processing + AZ annual tax certificate (Form 154) | V197 | **YES** | PayrollResult, ReportExportService |
| M355 | Cost center allocation + GL journal generation | V198 | **YES** | ErpExportService, PayrollResult |
| M356 | PDF payslip generation + MinIO storage + email delivery | — | No | AttachmentService, EmailService |
| M357 | Payroll control board (pre-flight checks + dashboard) | — | No | PayrollRunService |
| M358 | Payroll reports suite (7 reports) | — | No | PayrollResultRepository |

## Actor Roles

| Role | Access Level |
|------|--------------|
| ROLE_HR_ADMIN | Full read/write on all payroll data; approve advances/loans; trigger PDF generation; generate annual certificates |
| ROLE_PAYROLL_SPECIALIST | Full read/write on all payroll data (same as HR_ADMIN for payroll module) |
| ROLE_HR_SPECIALIST | Read-only on payroll runs/results; salary fields masked; cannot approve or modify |
| ROLE_SYSTEM_ADMIN | Final approval step in PAYROLL_APPROVAL workflow only; no edit rights |
| ROLE_EMPLOYEE | View own payslips and payroll results only; download own PDF payslip |
| ROLE_MANAGER / ROLE_DEPARTMENT_MANAGER | View team payroll summary (gross/net only, no deduction breakdown); scoped by AccessScopeService hierarchy |

## Employee-Lifecycle Steps Touched

1. **Compensation Setup** (hire/transfer/promotion): Component assignments added/modified
2. **Active Employment**: Monthly payroll runs include all active component assignments; advances/loans accumulate
3. **Leave Events**: Unpaid leave deductions (M343) reduce net; leave encashment adds bonus (M344) — no change
4. **Attendance Events**: Late/early/absence deductions wired via AttendanceDeductionBridge (M327) — no change
5. **Performance Bonuses**: PayrollBonus rows added to runs — existing; extended with component catalog
6. **Termination**: FinalSettlementService handles terminal payout; outstanding loans written off or final-deducted
7. **Year-End**: Annual payroll summary and AZ tax certificate generated per employee

## Workflows and Approval Chains

| Workflow | Steps | Approval Authority |
|----------|-------|-------------------|
| **Salary Advance Request** | PENDING → APPROVED / REJECTED → DEDUCTED / CANCELLED | Single-step: HR_ADMIN approves directly (no workflow engine) |
| **Payroll Loan** | PENDING → ACTIVE → FULLY_REPAID / WRITTEN_OFF / CANCELLED | Single-step: HR_ADMIN approves directly |
| **Payroll Run (REGULAR & OFF_CYCLE)** | DRAFT → CALCULATED → UNDER_REVIEW → APPROVED → PAID → CLOSED | Existing PAYROLL_APPROVAL workflow (3-step); SYSTEM_ADMIN final |
| **Employee Hold** | Flag/unflag ON_HOLD per run | HR_ADMIN sets; cleared on recalculation after removal |

## What-Exists vs What-Is-New

| Component | What Exists | What Is New |
|-----------|-------------|-------------|
| `payroll.employee_compensation` | Effective-dated monthly base salary | — |
| `payroll.bank_account` | One active per employee; IBAN encrypted | — |
| `payroll.statutory_rule` | JSON-based AZ 2026 tax rules (income tax, DSMF, MMI, unemployment, OT) | — |
| `payroll.payroll_run` | DRAFT→CALCULATED→UNDER_REVIEW→APPROVED→PAID→CLOSED; REGULAR run | Add OFF_CYCLE run_type; description; employee_ids |
| `payroll.payroll_result` | Per-employee gross/net/tax/contributions + JSONB calculation_details | Add `payroll_result_component` table for component breakdown |
| `payroll.payroll_bonus` | PERFORMANCE, ONE_TIME, ADVANCE types | — |
| `payroll.payroll_deduction` | UNPAID_LEAVE type; source_leave_request_id | Add LOAN_INSTALLMENT, SALARY_ADVANCE types; source FK columns |
| `PayrollEngine` | Base salary + OT + allowances + statutory deductions + attendance deductions | Load salary_component_assignment; snap to payroll_result_component |
| `StatutoryCalculator` | PROGRESSIVE_BRACKETS, DSMF_AZ_2026, BANDED_PCT, FLAT_PCT, OT_MULTIPLIERS | — |
| `AttendanceDeductionBridge` | Late/early/absence deductions wired | — |
| `BankFileService` | CSV bank-file export | — |
| `FinalSettlementService` | Terminal-employee payout | — |
| **Salary Component Catalog** | Does not exist | **NEW**: `payroll.salary_component` (code, name, kind, calculation_method, percentage, is_taxable, is_statutory) |
| **Component Assignment** | Does not exist | **NEW**: `payroll.salary_component_assignment` (employee_id, component_id, amount_override, effective_from/to) |
| **Result Component Snapshot** | Does not exist | **NEW**: `payroll.payroll_result_component` (result_id, component_code, kind, amount, is_taxable) |
| **Salary Advance** | Does not exist | **NEW**: `payroll.salary_advance` |
| **Payroll Loan** | Does not exist | **NEW**: `payroll.payroll_loan` |
| **Employee Payroll Hold** | Does not exist | **NEW**: `payroll.payroll_run_hold` |
| **Cost Center Allocation** | Does not exist | **NEW**: `payroll.cost_center_allocation` + `payroll_result_cost_split` |
| **GL Account Mapping** | Does not exist | **NEW**: `payroll.gl_account_mapping` |
| **GL Journal** | Does not exist | **NEW**: `payroll.gl_journal` + `gl_journal_line` |
| **Annual Tax Certificate** | Does not exist | **NEW**: `payroll.annual_tax_certificate` |
| **PDF Payslip** | Payslip view in self-service (HTML) | **NEW**: PDF generation + MinIO storage + email delivery |
| **Payroll Control Board** | Basic run status | **NEW**: Pre-flight checks, dashboard with MoM variance, hold management |

## Key Validations Per Section

**Section 3 (Salary Components)**
- Component code unique per tenant
- Only one active assignment per (employee, component) — new closes prior open
- EARNING adds, DEDUCTION subtracts; signed accordingly in engine
- Net pay floor = 0 (total deductions cannot exceed total earnings)

**Section 4 (Salary Advance)**
- requested_amount ≤ 50% of monthly_base_salary
- One PENDING or APPROVED advance per employee at a time
- Idempotent deduction: no duplicate row for (advance_id, period)
- Reversible before DEDUCTED status

**Section 5 (Payroll Loan)**
- monthly_installment > 0 and ≤ outstanding_balance
- Idempotent: no duplicate LOAN_INSTALLMENT for (loan_id, period_year, period_month)
- Auto-flip to FULLY_REPAID when outstanding_balance = 0

**Section 6 (Off-Cycle Runs)**
- OFF_CYCLE requires explicit non-empty employee_ids list
- One REGULAR run per (year, month, jurisdiction) — UNIQUE constraint
- Same PAYROLL_APPROVAL 3-step workflow

**Section 7 (Variance)**
- Only REGULAR runs compared by default
- >10% gross variance flagged

**Section 8 (Year-End)**
- Only for employees with ≥ 1 PAID REGULAR run in the year
- Calendar year: Jan 1 – Dec 31
- Re-generation replaces existing record (idempotent)

**Section 9 (Cost Center)**
- Sum of active allocations per employee = 100.00%
- Effective-dated; default = OrgUnit cost center or 'DEFAULT'

**Section 10 (GL Journal)**
- Generated on APPROVED status (AZ practice)
- Total debits = total credits (balanced)
- Re-generation replaces existing lines

**Section 11 (PDF Payslip)**
- Generated after run reaches PAID
- Stored in MinIO under payroll/payslips/{run_id}/{employee_id}/payslip.pdf

**Section 12 (Control Board)**
- Pre-flight warnings: no compensation, no approved timesheet, ON_HOLD, pending advances, retroactive salary change
- ON_HOLD employees skipped during calculation

## Acceptance Criteria per Milestone

**M349** — Salary Component Catalog + PayrollEngine Integration
1. HR_ADMIN creates/updates/deactivates components with code, name, kind, calculation_method, is_taxable, is_statutory
2. HR_ADMIN assigns components to employees with effective dates and optional amount override
3. Only one active assignment per (employee, component) — new closes prior
4. PayrollEngine: EARNING is_taxable=true → taxable gross; EARNING is_taxable=false → net after tax; DEDUCTION → net minus
5. Each component snapshotted into payroll_result_component
6. calculation_details JSONB includes component breakdown
7. Audit trail for all changes

**M350** — Employee Payroll Hold + Off-Cycle Run Type
1. HR_ADMIN flags employee ON_HOLD for specific run with reason
2. PayrollEngine skips ON_HOLD employees; logs warning in audit trace
3. Hold cleared when run recalculated after removal
4. Off-cycle run: run_type=OFF_CYCLE, description, explicit employee_ids list
5. Off-cycle runs: same DRAFT→CALCULATED→APPROVED→PAID→CLOSED lifecycle
6. Off-cycle totals separate from regular month totals
7. Payroll Runs list shows run_type column

**M351** — Salary Advance Requests
1. Employee request: amount ≤ 50% of base salary; rejected if PENDING/APPROVED exists
2. HR_ADMIN approves with partial amount option
3. On approval: PayrollDeduction SALARY_ADVANCE created for repayment period
4. Advance disbursement path (PayrollBonus ADVANCE or manual mark)
5. Deduction applied in repayment period; status → DEDUCTED
6. Reversible before DEDUCTED: cancels associated deduction
7. Every status change audit-logged

**M352** — Payroll Loan + Installment Auto-Deduction
1. HR_ADMIN creates loan with principal, monthly_installment, start_deduction_month
2. term_months derived as ceil(principal / monthly_installment)
3. PayrollEngine auto-creates LOAN_INSTALLMENT deduction (idempotent per loan+period)
4. amount = min(monthly_installment, outstanding_balance)
5. On run PAID: amount_repaid updated; FULLY_REPAID when balance = 0
6. Loan status audit-logged

**M353** — Payroll Variance Report + YTD Summary
1. Variance: per-employee prior/current gross/net/tax, delta, delta_pct, flags
2. Flags: SALARY_CHANGE, BONUS_ADDED, NEW_EMPLOYEE, EMPLOYEE_ABSENT, DEDUCTION_ADDED, COMPONENT_CHANGE
3. Summary: total change, count with >10% gross variance, new/exited
4. YTD: per-employee aggregation across PAID REGULAR runs in calendar year

**M354** — Year-End + AZ Annual Tax Certificate
1. Annual payroll summary per employee for calendar year
2. AZ Form 154 equivalent: name, VÖEN, employer VÖEN, annual gross, exempt, taxable, tax withheld
3. Certificate stored in payroll.annual_tax_certificate; status=GENERATED
4. Only for employees with ≥ 1 PAID REGULAR run in year
5. Re-generation replaces existing record

**M355** — Cost Center Allocation + GL Journal
1. Cost center allocations: effective-dated, sum = 100%
2. Engine splits payroll cost by allocation pct; snapshots in payroll_result_cost_split
3. GL mapping: debit/credit accounts per component_kind + optional component_code
4. Journal generated on APPROVED; balanced (debits = credits)
5. Re-generation replaces existing lines

**M356** — PDF Payslip
1. PDF: header, earnings, OT, bonuses, gross, deductions, statutory, net, employer contrib (info), masked IBAN
2. Stored in MinIO; employee downloads from portal
3. HR_ADMIN triggers bulk generation
4. Optional email delivery after PAID

**M357** — Payroll Control Board
1. Pre-flight checklist: no compensation, no timesheet, ON_HOLD, pending advances, retroactive salary
2. Dashboard: open run status, headcount, gross/net/tax, MoM variance, loans, pending advances
3. Hold management UI

**M358** — Payroll Reports Suite
1. Period payroll summary, variance (enhanced), YTD summary, employer cost, loan/advance status, bank file reconciliation, annual certificate list

## Integration Points

| Integration | Direction | Status |
|-------------|-----------|--------|
| Attendance (M327/M331) | AttendanceDeductionBridge → PayrollEngine | Existing — no change |
| Leave (M343/M344) | UNPAID_LEAVE PayrollDeduction rows → PayrollEngine | Existing — no change |
| Comp & Benefits allowances | EmployeeAllowance → payroll_allowance snapshot | Existing — augmented by salary_component_assignment |
| ERP export | ErpExportService extended for GL journal | Existing — extended |
| MinIO | AttachmentService reused for PDF storage | Existing — reused |
| Email | EmailService reused for payslip delivery | Existing — reused |
| Workflow Engine | PAYROLL_APPROVAL reused for off-cycle runs | Existing — reused |

## Pinned Business Rules (Section 18)

| Rule | Pinned Value |
|------|--------------|
| Advance ceiling | 50% of monthly_base_salary |
| Advance repayment | Next payroll period (default); HR can override |
| Advance one-at-a-time | Prior must be DEDUCTED or CANCELLED |
| Loan installment | Fixed; min = 1 AZN; max = outstanding_balance |
| Off-cycle run approval | Same PAYROLL_APPROVAL 3-step workflow |
| Annual tax cert year | Calendar year Jan 1 – Dec 31 |
| Cost center default | OrgUnit.cost_center_code → fallback = 'DEFAULT' |
| GL journal trigger | On APPROVED status (AZ practice) |
| Payslip PDF stored | After run reaches PAID |
| Component taxability | EARNING is_taxable=true → taxable gross; is_taxable=false → net after statutory |
| Deduction component | Subtracts from net after all taxes |
| Pro-ration | Applies to base salary only, not fixed-amount components |
