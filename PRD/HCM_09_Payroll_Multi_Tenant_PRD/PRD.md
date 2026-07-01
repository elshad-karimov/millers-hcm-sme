---
feature: payroll-multi-tenant
module: payroll
payroll_impact: true
status: backlog
depends_on: [leave-request-management, time-and-attendance]
---

# 9. Payroll Management Module — Full Enterprise Features (Azerbaijan, Multi-Tenant)

The Payroll Management module is the financial backbone of the HCM platform. It must accurately compute, approve, and post employee pay for every period, with complete audit trails, statutory compliance for Azerbaijan, and clean integration points into the rest of the HCM (attendance, leave, performance bonuses, advances/loans, and GL/ERP).

**Jurisdiction: Republic of Azerbaijan only** (multi-country is out of scope per project decision).  
**Currency: AZN only** for all calculations.

---

## 1. Purpose

- Compute accurate gross-to-net pay for every employee every period, incorporating all earnings and deductions.
- Enforce AZ statutory rules (income tax, DSMF, MMI, unemployment) with effective-dated rules, so new rates take effect automatically.
- Provide a structured approval workflow before payroll is finalised.
- Generate a bank file, GL journal, and PDF payslip as the three downstream outputs.
- Eliminate manual spreadsheets: salary components, advances, loans, and deductions are all managed inside the system.

---

## 2. What Already Exists (do not re-implement)

The following payroll infrastructure was built in earlier milestones and must be extended — not replaced:

| Component | Key behaviour |
|---|---|
| `payroll.employee_compensation` | Effective-dated monthly base salary; `CompensationService.upsert()` closes the prior open row |
| `payroll.bank_account` | One active bank account per employee; IBAN encrypted |
| `payroll.statutory_rule` | Configurable JSON-based tax rules with effective dates; `StatutoryCalculator` reads them |
| `payroll.payroll_run` | Monthly run per (year, month, jurisdiction='AZ'); lifecycle DRAFT→CALCULATED→UNDER_REVIEW→APPROVED→PAID→CLOSED |
| `payroll.payroll_result` | Per-employee gross/net/tax/contributions + JSONB `calculation_details` audit trace |
| `payroll.payroll_bonus` | Bonuses attached to a run (PERFORMANCE, ONE_TIME, etc.) |
| `payroll.payroll_deduction` | Recurring/one-off deductions; UNPAID_LEAVE type from M343; `source_leave_request_id` idempotency column |
| `payroll.payroll_allowance` | Snapshot of allowances included in a run; taxable vs non-taxable |
| `payroll.erp_export` / `erp_export_line` | ERP/GL export rows by account code |
| `PayrollEngine` | Calculates run from APPROVED timesheets; pro-ration, OT, allowances, statutory deductions, attendance deductions |
| `StatutoryCalculator` | PROGRESSIVE_BRACKETS, DSMF_AZ_2026, BANDED_PCT, FLAT_PCT, OT_MULTIPLIERS |
| `AttendanceDeductionBridge` | Late/early/absence deductions wired from attendance into PayrollEngine |
| `BankFileService` | CSV bank-file export |
| `FinalSettlementService` | Terminal-employee payout |
| `PayrollRunService.addBonus()` | Used by leave encashment (M344) |
| `PayrollRunWorkflowListener` | PAYROLL_APPROVAL workflow |

---

## 3. Salary Component Catalog (§3)

### 3.1 Component types

A **salary component** is a named, configurable line item that can appear as an earning or a deduction on a payslip.

| Field | Type | Notes |
|---|---|---|
| `code` | VARCHAR(64) UNIQUE | e.g. `TRANSPORT`, `HOUSING`, `MEAL`, `THIRTEENTH_MONTH`, `LOAN_REPAYMENT`, `GARNISHMENT` |
| `name` | VARCHAR(200) | Display name on payslip |
| `kind` | ENUM(`EARNING`, `DEDUCTION`) | Whether it adds to or reduces gross or net |
| `calculation_method` | ENUM(`FIXED_AMOUNT`, `PERCENTAGE_OF_BASE`, `FORMULA`) | How the value is derived |
| `percentage` | NUMERIC(6,4) | Used when `calculation_method = PERCENTAGE_OF_BASE`; e.g. 0.10 = 10% of base salary |
| `is_taxable` | BOOLEAN | If true, included in taxable gross for **income tax** calculation; if false, exempt from income tax (added to net after tax) |
| `contribution_exempt` | BOOLEAN | If true, exempt from DSMF/MMI/unemployment contribution base as well. Most non-taxable allowances are still subject to DSMF — only special statutory social payments are contribution-exempt. Default false. |
| `is_statutory` | BOOLEAN | System-managed (cannot be deleted; e.g. INCOME_TAX, DSMF, UNPAID_LEAVE) |
| `active` | BOOLEAN | Inactive components are not assignable but existing assignments finish their periods |

### 3.2 Per-employee component assignments

An employee can have one or more component assignments beyond base salary:

| Field | Notes |
|---|---|
| `employee_id` | FK to employee |
| `component_id` | FK to salary_component |
| `amount_override` | Override the component's default amount/percentage for this employee |
| `effective_from` / `effective_to` | Effective-dated, nullable effective_to for open-ended |
| `reason` | e.g. "Role-specific transport allowance" |

**Business rules:**
- Multiple assignments of different components per employee are allowed.
- Only one active assignment per (employee, component) at a time — creating a new one closes the prior open one (same pattern as `employee_compensation`).
- EARNING components with `is_taxable=true` increase the taxable gross before statutory (income tax + DSMF + MMI + unemployment) calculations.
- EARNING components with `is_taxable=false` and `contribution_exempt=false` (default): exempt from income tax but still included in the DSMF/MMI/unemployment contribution base. Amount added to net pay after statutory deductions.
- EARNING components with `is_taxable=false` and `contribution_exempt=true`: exempt from both income tax and DSMF/MMI/unemployment (rare — only for statutory social payments). Amount added to net pay after statutory deductions.
- DEDUCTION components reduce **net pay after all statutory taxes** — they do NOT reduce the DSMF/MMI contribution base (voluntary deductions such as loan repayments and salary advance recovery should not affect statutory obligations).
- All active component assignments for an employee are included in every payroll run automatically.

### 3.3 Payroll Engine changes

`PayrollEngine.calculate()` must be extended to:
1. Load all active `salary_component_assignment` rows for each employee.
2. For `EARNING` / `is_taxable=true`: add to taxable gross.
3. For `EARNING` / `is_taxable=false`: add to net after tax.
4. For `DEDUCTION`: subtract from net.
5. Snapshot each component line into `payroll_result_component` table (component_code, kind, amount, is_taxable) for payslip display.
6. Include the component breakdown in `calculation_details` JSONB trace.

---

## 4. Salary Advance Management (§4)

Employees can request an advance against their upcoming salary. All advances are recovered through payroll.

### 4.1 Salary advance request

| Field | Notes |
|---|---|
| `employee_id` | |
| `requested_amount` | Must be ≤ 50% of employee's current monthly_base_salary |
| `requested_for_month` | Year + month the advance will be paid out |
| `repayment_month` | Year + month the deduction is applied (next or same payroll period) |
| `reason` | Optional |
| `status` | PENDING → APPROVED / REJECTED → DEDUCTED / CANCELLED |
| `approved_amount` | HR may partially approve |
| `approved_by` / `approved_at` | |
| `payroll_bonus_id` | Set when the advance is added as a negative bonus or separate payroll component to the run |

**Business rules:**
- Maximum advance: 50% of monthly base salary (employer configurable — store in a config table or as a parameter).
- An employee can have at most one PENDING or APPROVED advance at a time.
- On approval: create a `payroll_deduction` row of type `SALARY_ADVANCE` for the repayment period, linking back to the advance id.
- The advance itself is disbursed by adding a `payroll_bonus` of type `ADVANCE` (negative effect on employer cash, positive for employee) to the target payroll run, or simply marking the advance as disbursed manually.
- Advances are reversible before DEDUCTED status: cancel the associated deduction row.
- Audit: every status change is logged to the audit trail.

### 4.2 Approval workflow

Single-step: HR_ADMIN approves. No workflow engine required (straight-through, no escalation) — use a direct service call + audit log.

---

## 5. Payroll Loan Management (§5)

An employee loan is repaid via fixed monthly installments deducted from payroll over a defined term.

### 5.1 Loan record

| Field | Notes |
|---|---|
| `employee_id` | |
| `principal_amount` | Total loan amount disbursed |
| `monthly_installment` | Fixed amount deducted each period; must be > 0 |
| `term_months` | Derived: `ceil(principal / monthly_installment)` |
| `disbursement_date` | Date the loan was physically paid out |
| `start_deduction_month` | Year + month of first deduction |
| `status` | ACTIVE → FULLY_REPAID / WRITTEN_OFF / CANCELLED |
| `amount_repaid` | Running total, updated each time a deduction is applied |
| `outstanding_balance` | `principal - amount_repaid` |
| `approved_by` / `approved_at` | |

### 5.2 Automatic installment deduction

- Every time `PayrollEngine.calculate()` runs, it checks for ACTIVE loans with `start_deduction_month ≤ current period` and `outstanding_balance > 0`.
- Creates a `payroll_deduction` row of type `LOAN_INSTALLMENT` linking to the loan id, amount = min(monthly_installment, outstanding_balance).
- On run PAID event: update `amount_repaid += installment`, flip status to FULLY_REPAID when `outstanding_balance = 0`.
- Idempotency: do not create a duplicate deduction if one already exists for (loan_id, period_year, period_month).

### 5.3 Approval

HR_ADMIN directly approves via service call + audit log (no workflow).

---

## 6. Off-Cycle Payroll Runs (§6)

An off-cycle run is an extra payroll run within a period for a subset of employees (e.g. a mid-month bonus payout, a termination settlement, or a correction).

### 6.1 Off-cycle run fields

| Field | Notes |
|---|---|
| `run_type` | REGULAR (default) or OFF_CYCLE |
| `description` | e.g. "Q2 spot bonus payout", "Termination settlement — Nigar" |
| `employee_ids` | Explicit list of employee UUIDs included in this run (nullable = all, i.e. regular run) |

**Business rules:**
- Off-cycle runs use the same payroll lifecycle (DRAFT → CALCULATED → APPROVED → PAID → CLOSED).
- Off-cycle results are NOT included in regular month totals on the run header (they have their own totals).
- Off-cycle runs are excluded from payroll variance comparisons unless explicitly included.
- The same approval workflow (PAYROLL_APPROVAL) applies.
- Off-cycle runs show as a separate type on the Payroll Runs list page.

---

## 7. Payroll Variance Report (§7)

Period-over-period comparison between two payroll runs to flag unexpected changes.

### 7.1 Variance row per employee

| Field | Notes |
|---|---|
| `employee_id` / `employee_name` | |
| `prior_gross` / `current_gross` | |
| `gross_delta` / `gross_delta_pct` | |
| `prior_net` / `current_net` | |
| `net_delta` / `net_delta_pct` | |
| `prior_tax` / `current_tax` | |
| `variance_flags` | List of reasons: `SALARY_CHANGE`, `BONUS_ADDED`, `NEW_EMPLOYEE`, `EMPLOYEE_ABSENT`, `DEDUCTION_ADDED`, `COMPONENT_CHANGE` |

### 7.2 Summary

- Total payroll change, count of employees with >10% gross variance, new/exited employees.

### 7.3 Business rules

- Only REGULAR runs are compared (off-cycle excluded unless both chosen runs are off-cycle).
- A new employee (not in prior run) shows with prior = 0 and flag `NEW_EMPLOYEE`.
- An employee absent (not in current run) shows with current = 0 and flag `EMPLOYEE_ABSENT`.

---

## 8. Year-End Payroll Processing (§8)

At calendar year-end (January run or on demand), generate annual payroll summaries and AZ tax certificates.

### 8.1 Annual payroll summary per employee

Aggregate across all PAID REGULAR runs in a calendar year:
- Total gross, total income tax, total DSMF (employee), total MMI (employee), total unemployment (employee)
- Total bonuses, total allowances
- Total net paid

### 8.2 AZ Annual Income Tax Certificate (Form 154 equivalent)

Per-employee document showing:
- Employee name, VÖEN (tax ID = national_id), employer VÖEN
- Annual gross income, exempt amount, taxable income, income tax withheld
- Period: 01 Jan – 31 Dec of the tax year

Generated as a JSON record that can be rendered to PDF (reuse `ReportExportService`). One record per (employee, year). Stored in `payroll.annual_tax_certificate`.

**Business rules:**
- Only generated for employees with ≥ 1 PAID REGULAR run in the year.
- Generated by HR_ADMIN; once generated, status = GENERATED; re-generation replaces the existing record.

---

## 9. Cost Center Allocation (§9)

Distribute an employee's payroll cost across multiple cost centers (for GL/management accounting).

### 9.1 Cost center split

| Field | Notes |
|---|---|
| `employee_id` | |
| `cost_center_code` | Matches GL cost center codes from ERP |
| `cost_center_name` | Display label |
| `allocation_pct` | Percentage (e.g. 60.00); sum of all active allocations for an employee must = 100.00 |
| `effective_from` / `effective_to` | Effective-dated |

**Business rules:**
- If no allocation defined for an employee, 100% defaults to their department's default cost center (nullable fallback = a global default).
- The engine computes cost per component (base, OT, allowances, bonuses) and splits by percentage.
- Snapshots stored in `payroll_result_cost_split` for audit.

---

## 10. GL Journal Generation (§10)

Map payroll components to chart-of-accounts GL codes and generate a posting journal.

### 10.1 GL account mapping

| Field | Notes |
|---|---|
| `component_kind` | EARNING / DEDUCTION / TAX / DSMF_EE / DSMF_ER / MMI_EE / MMI_ER / UNEMPL_EE / UNEMPL_ER / NET_PAY |
| `component_code` | Optional specific component; null = applies to all of `component_kind` |
| `debit_account` | GL account code to debit |
| `credit_account` | GL account code to credit |

### 10.2 Journal output

For each payroll run, generate a journal with:
- One debit line per (cost_center, GL account) for salary expense
- Credit to payable accounts (net pay payable, tax payable, DSMF payable, etc.)
- Export as CSV or JSON (same pattern as ERP export)

**Business rules:**
- Journal can only be generated for a run in APPROVED or PAID status.
- Re-generation replaces existing journal lines for that run.

---

## 11. PDF Payslip & Self-Service Delivery (§11)

Replace the current basic payslip view with a full-detail PDF.

### 11.1 Payslip content (per employee per run)

- Header: company name, pay period, payslip number, employee name/ID/position/department
- Earnings section: base salary + each EARNING component with amount (taxable vs non-taxable labelled)
- Overtime: hours and amount
- Bonuses: each bonus with type and amount
- Gross pay total
- Deductions before statutory: each DEDUCTION component
- Statutory deductions: income tax, DSMF (employee), MMI (employee), unemployment (employee)
- Net pay
- Employer contributions (informational): DSMF employer, MMI employer, unemployment employer
- Bank account (last 4 digits of IBAN masked)
- QR code (optional, link to portal)

### 11.2 Delivery

- PDF stored in MinIO (reuse `AttachmentService`) under `payroll/payslips/{run_id}/{employee_id}/payslip.pdf`.
- Employee can view their own payslip in My Workspace → Payslips tab (already exists — the PDF download link is the new addition).
- HR_ADMIN can trigger bulk PDF generation for all employees in a run.
- Optional email delivery: after PAID, email each employee their payslip PDF (reuse `EmailService`).

---

## 12. Payroll Control Board (§12)

HR workspace for managing and monitoring payroll status.

### 12.1 Pre-calculation checklist

Before running `calculate()`, display a pre-flight check:
- Employees with no active compensation (no salary on record)
- Employees with no approved timesheet for the period
- Employees flagged as ON_HOLD for this run
- Employees with pending advance requests not yet processed
- Employees with a salary change effective this period (retroactive warning)

### 12.2 Payroll summary dashboard

- Current open run status (not started / draft / calculated / under review / approved / paid)
- Headcount vs paid headcount
- Total gross, total net, total tax
- Month-over-month gross variance
- Unpaid loans count and outstanding balance
- Pending advance requests count

### 12.3 Employee payroll hold

- HR can flag an employee as ON_HOLD for a specific run (run_id + employee_id + reason).
- The engine skips ON_HOLD employees during calculation, logging a warning in the run's audit trace.
- The hold is cleared automatically when the run is recalculated after removal.

---

## 13. Payroll Reports Suite (§13)

| Report | Description |
|---|---|
| Period payroll summary | Gross/net/tax totals and per-employee breakdown for a run |
| Variance report | Period-over-period comparison (§7) |
| YTD payroll summary | Per-employee cumulative totals for the year |
| Employer cost report | Gross + employer DSMF + employer MMI + employer unemployment |
| Loan & advance status | Outstanding balances, repayment schedule, upcoming deductions |
| Bank file reconciliation | Sum of bank file vs sum of net pay in the run (should match) |
| Annual tax certificate list | All certificates generated for a year |

---

## 14. Security & Access Control

- `ROLE_HR_ADMIN` and `ROLE_PAYROLL_SPECIALIST`: full read/write on all payroll data.
- `ROLE_HR_SPECIALIST`: read-only on payroll runs and results; cannot approve or modify salary.
- `ROLE_SYSTEM_ADMIN`: final approval step in PAYROLL_APPROVAL workflow only; no payroll edit rights.
- `ROLE_EMPLOYEE`: can view their own payslips and payroll results only; cannot see any other employee's data.
- `ROLE_MANAGER` / `ROLE_DEPARTMENT_MANAGER`: can view their team's payroll summary (gross/net only, no detailed deduction breakdown) — scoped by AccessScopeService hierarchy.
- Salary amount fields are **masked** in HR_SPECIALIST read responses (show `****` or `null`).
- All payroll mutations are audit-logged.
- Bank account numbers are encrypted at rest (existing `EncryptedStringConverter`).

---

## 15. Audit Trail

All payroll mutations — run creation, calculation, approval, payslip generation, advance/loan changes, component changes — must produce an `audit_log` entry via `AuditService.record(module, entityName, entityId, action, oldValue, newValue)`.

---

## 16. Validation Rules

| Rule | Detail |
|---|---|
| Advance max | ≤ 50% of current monthly_base_salary |
| Advance one at a time | No second PENDING/APPROVED advance if one exists |
| Loan installment | > 0, ≤ outstanding_balance |
| Cost center allocation | Sum of active allocations per employee = 100.00% |
| Component kind | EARNING adds, DEDUCTION subtracts; signed accordingly |
| Payroll run uniqueness | One REGULAR run per (year, month, jurisdiction) |
| Off-cycle run | Requires explicit employee_ids |
| Bank file | Can only be exported after run status = PAID |
| Annual certificate | Can only be generated for PAID runs in the target year |

---

## 17. Milestone Plan

| Milestone | Feature | Flyway | Payroll Impact |
|---|---|---|---|
| M349 | Salary component catalog + per-employee assignments + PayrollEngine integration | V193 | **YES** |
| M350 | Employee payroll hold + off-cycle run type | V194 | **YES** |
| M351 | Salary advance requests + payroll deduction recovery | V195 | **YES** |
| M352 | Payroll loan + installment auto-deduction | V196 | **YES** |
| M353 | Payroll variance report + YTD summary | — | No |
| M354 | Year-end processing + AZ annual tax certificate (Form 154) | V197 | **YES** |
| M355 | Cost center allocation + GL journal generation | V198 | **YES** |
| M356 | PDF payslip generation + MinIO storage + email delivery | — | No |
| M357 | Payroll control board (pre-flight checks + dashboard) | — | No |
| M358 | Payroll reports suite (7 reports) | — | No |

---

## 18. Key Business Rules Pinned (to avoid HARD STOP)

| Rule | Pinned value |
|---|---|
| Advance ceiling | 50% of monthly_base_salary (employer policy; AZ Labour Code sets no statutory ceiling) |
| Advance repayment | Next payroll period (default); HR can override to same period |
| Advance one-at-a-time | Per employee; the prior must be DEDUCTED or CANCELLED to open a new one |
| Advance on termination | Outstanding advance balance is deducted from the FinalSettlementService payout. If advance > final payout, HR manually writes off the remainder (audit-logged) |
| Loan on termination | Outstanding loan balance is deducted from the FinalSettlementService payout. If balance > final payout, HR writes off with approval (audit-logged) |
| Loan installment | Fixed amount entered by HR; min = 1 AZN; max = outstanding_balance |
| Off-cycle run approval | Same PAYROLL_APPROVAL 3-step workflow as regular runs |
| Annual tax cert year | Calendar year Jan 1 – Dec 31; generated after last December run is PAID |
| Cost center default | Employee's OrgUnit.cost_center_code (nullable); fallback = 'DEFAULT' |
| GL journal trigger | On APPROVED status (not PAID — posting at approval point, AZ practice) |
| Payslip PDF stored | After run reaches PAID status; employee sees it in portal |
| Component taxability | EARNING is_taxable=true → adds to taxable gross before income tax AND DSMF/MMI base; is_taxable=false + contribution_exempt=false → exempt from income tax but STILL in DSMF/MMI base, added to net after statutory; is_taxable=false + contribution_exempt=true → exempt from both income tax and DSMF/MMI, added to net after statutory |
| Deduction component | Subtracts from net pay AFTER all statutory taxes — does NOT reduce DSMF/MMI base |
| Pro-ration | Existing logic (worked_hours / expected_monthly_hours) applies to base salary only, not fixed components |

---

## 19. Integration Points

- **Attendance (M327/M331)**: `AttendanceDeductionBridge` already wired into `PayrollEngine` — no change.
- **Leave (M343/M344)**: `PayrollDeduction` UNPAID_LEAVE rows already picked up by `PayrollEngine` — no change.
- **Comp & Benefits allowances**: Already snapshotted into `payroll_allowance` — no change, but new `salary_component_assignment` rows augment this.
- **ERP export**: `ErpExportService` reused for GL journal output (new call path for journal).
- **MinIO**: `AttachmentService` reused for PDF payslip storage.
- **Email**: `EmailService` reused for payslip delivery.
- **Workflow Engine**: Existing PAYROLL_APPROVAL reused for off-cycle runs.

---

## 20. Common Mistakes to Avoid

- Do not delete payroll records — reverse or correct only.
- Do not run the same (year, month, jurisdiction) twice as REGULAR — enforce the UNIQUE constraint.
- Component assignments must not retroactively change finalized payroll results — changes only apply from next open run.
- Advance recovery must be idempotent — do not create duplicate deduction rows.
- Loan installments must stop when `outstanding_balance = 0`, even if the scheduled run triggers first.
- GL journal must balance: total debits = total credits for each run.
