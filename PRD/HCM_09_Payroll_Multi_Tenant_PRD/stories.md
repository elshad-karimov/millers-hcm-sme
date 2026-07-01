# Payroll Multi-Tenant PRD — User Stories (M349–M358)

## Dependencies

```
M349 → M350 → M351 → M352   (component catalog must exist before holds/off-cycle; advance/loan use deductions)
M349 → M353                  (variance report reads payroll_result_component)
M349 → M354                  (year-end reads component breakdowns)
M349 → M355                  (GL mapping references component_kind)
M349 → M356                  (PDF payslip renders payroll_result_component)
M351 + M352 → M357           (control board shows advance/loan counts)
M353 + M354 + M355 → M358   (reports suite aggregates all prior outputs)
```

## Out of Scope

- Multi-country payroll / multi-jurisdiction rules
- Non-AZN currencies
- Mobile app payslip (web portal only)
- Real-time GL streaming / API posting to external ERP
- Automatic DSMF e-declaration submission to DÖVLƏT
- Pension fund integration beyond existing DSMF
- Payroll outsourcing / bureau integration
- Forecasting / what-if salary simulation
- Benefits-in-kind monetary valuation
- Overtime policy management (already handled by AttendanceDeductionBridge)

---

## Epic M349 — Salary Component Catalog + PayrollEngine Integration

### Stories

**M349-S1** As an HR_ADMIN I want to create configurable salary components (e.g. TRANSPORT, HOUSING, MEAL) with taxability and contribution flags so that every pay element is consistently defined in one place and automatically applied to payslips.

Acceptance criteria:
- Given I POST a salary_component with code, name, kind=EARNING, is_taxable=false, contribution_exempt=false, When saved, Then it appears in the components list and is available for employee assignment.
- Given a component with is_statutory=true, When I try to delete it, Then the API returns 400 (system components cannot be removed).
- Given a component with calculation_method=PERCENTAGE_OF_BASE and percentage=0.10, When the engine runs, Then the component amount = 0.10 × employee's monthly_base_salary.
- Given I deactivate a component, When an employee still has an active assignment, Then the assignment finishes its effective period but no new assignments can be created.
- Every component create/update is recorded in the audit log.

**M349-S2** As an HR_ADMIN I want to assign salary components to individual employees with effective dates and optional amount overrides so that each employee's pay structure reflects their role and contract.

Acceptance criteria:
- Given I assign TRANSPORT component to employee A from 2026-07-01 with amount_override=150, When the July payroll runs, Then the payslip shows TRANSPORT 150 AZN.
- Given employee A already has an active TRANSPORT assignment, When I create a new TRANSPORT assignment from 2026-08-01, Then the prior assignment's effective_to is set to 2026-07-31 (auto-close).
- Given an assignment with effective_to < today, Then the component is NOT included in the current payroll run.
- Assignment create/close changes are audit-logged.

**M349-S3** As a PAYROLL_SPECIALIST I want the payroll engine to pick up all active salary component assignments automatically so that I do not need to manually add them for each employee each month.

Acceptance criteria:
- Given employee A has TRANSPORT (is_taxable=false, contribution_exempt=false, 150 AZN) and MEAL (is_taxable=true, 50 AZN) active on period start date, When I calculate the payroll run, Then: MEAL is added to taxable gross before income tax; TRANSPORT is still in DSMF/MMI base but exempt from income tax; TRANSPORT amount is added to net after statutory.
- Given employee A has a LOAN_DEDUCTION component (DEDUCTION kind, 200 AZN), When calculated, Then 200 AZN is subtracted from net pay AFTER income tax and DSMF — not before.
- Each component line snapshotted to payroll_result_component (component_code, kind, amount, is_taxable, contribution_exempt).
- calculation_details JSONB includes component breakdown section.

### Edge Cases
- EC-349-1: Component amount_override = 0 → component included with 0 AZN (shows on payslip; auditable)
- EC-349-2: PERCENTAGE_OF_BASE component with monthly_base_salary = 0 → component amount = 0, no error
- EC-349-3: Two components of same kind both DEDUCTION assigned → both deducted independently; net pay floored at 0

---

## Epic M350 — Employee Payroll Hold + Off-Cycle Run Type

### Stories

**M350-S1** As an HR_ADMIN I want to flag specific employees as ON_HOLD for a payroll run so that I can exclude problematic cases without blocking the entire run.

Acceptance criteria:
- Given I set employee B ON_HOLD for run R with reason "Contract dispute", When I calculate run R, Then employee B is skipped and a warning appears in the run's audit trace.
- Given I remove the hold and recalculate, Then employee B is included in the next calculation.
- Hold flag/unflag is audit-logged with reason.
- ON_HOLD employees appear in the pre-flight checklist (M357).

**M350-S2** As an HR_ADMIN I want to create off-cycle payroll runs for specific employees so that I can process spot bonuses or termination corrections without affecting the main monthly run.

Acceptance criteria:
- Given I create an OFF_CYCLE run with employee_ids=[A, B] for period 2026-07, When I calculate, Then only A and B are included.
- Given an OFF_CYCLE run for July, When the main REGULAR July run exists, Then the UNIQUE constraint does not block the off-cycle run.
- Off-cycle run totals (total_gross, total_net) are separate from the REGULAR run totals.
- The Payroll Runs list shows run_type column (REGULAR / OFF_CYCLE).
- OFF_CYCLE run without explicit employee_ids is rejected with 400.

### Edge Cases
- EC-350-1: employee_ids list contains a TERMINATED employee → included in off-cycle calculation (final payout scenario valid)
- EC-350-2: Two OFF_CYCLE runs for the same employee in the same month → allowed; each is independent
- EC-350-3: ON_HOLD employee in an OFF_CYCLE run → hold still applies; employee skipped

---

## Epic M351 — Salary Advance Requests + Payroll Deduction Recovery

### Stories

**M351-S1** As an EMPLOYEE I want to request a salary advance so that I can handle unexpected personal expenses between pay periods.

Acceptance criteria:
- Given I request 1000 AZN advance and my monthly salary is 3000, Then 1000 ≤ 50% × 3000 = 1500, request is created PENDING.
- Given I request 2000 AZN and my salary is 3000, Then 2000 > 1500, request is rejected 400.
- Given I have a PENDING advance, When I try to submit another, Then the API returns 400 (one at a time).

**M351-S2** As an HR_ADMIN I want to approve or reject advance requests and specify the recovery period so that I control when the deduction hits payroll.

Acceptance criteria:
- Given I APPROVE advance for 800 AZN (partial of requested 1000) for repayment in July 2026, Then a PayrollDeduction SALARY_ADVANCE row is created for July 2026 with amount 800.
- Given I REJECT an advance, Then no deduction row is created and status = REJECTED.
- On approval: advance status = APPROVED; on deduction applied: status = DEDUCTED.
- Every status change audit-logged.

**M351-S3** As an HR_ADMIN I want to cancel an advance before it is deducted so that I can correct errors.

Acceptance criteria:
- Given advance is APPROVED and deduction period has not yet been calculated, When I cancel, Then PayrollDeduction row is marked CANCELLED and advance status = CANCELLED.
- Given advance is DEDUCTED, Then cancel is rejected 400.

**M351-S4** As an HR_ADMIN I want outstanding advances automatically deducted from the final settlement on termination so that the employer recovers the disbursed amount.

Acceptance criteria:
- Given employee has APPROVED advance of 500 AZN, When FinalSettlementService computes the terminal payout, Then 500 is deducted from the payout.
- Given advance > final payout, Then payout = 0 and a write-off record is created requiring HR acknowledgement.

### Edge Cases
- EC-351-1: Repayment period is the same as disbursement period → allowed if HR sets repayment_month = requested_for_month
- EC-351-2: Advance approved but payroll run for repayment month is already PAID → deduction moves to next open run (HR must reschedule manually)
- EC-351-3: Employee salary changes between request and deduction — deduction uses approved_amount (fixed), not a percentage

---

## Epic M352 — Payroll Loan + Installment Auto-Deduction

### Stories

**M352-S1** As an HR_ADMIN I want to record employee loans with fixed monthly installments so that repayments are tracked and deducted automatically.

Acceptance criteria:
- Given I create a loan: principal=3000, installment=500, start_deduction=2026-07, Then term_months = ceil(3000/500) = 6; status = ACTIVE.
- Given a loan with monthly_installment > outstanding_balance, When the engine deducts, Then deduction = outstanding_balance (last installment).
- On run PAID: amount_repaid += installment; outstanding_balance updates; when = 0 status → FULLY_REPAID.

**M352-S2** As a PAYROLL_SPECIALIST I want the engine to create loan deductions idempotently so that recalculating a run never double-deducts.

Acceptance criteria:
- Given loan L deduction for July 2026 already exists, When I recalculate the July run, Then no duplicate deduction row is created.
- Given loan is FULLY_REPAID, When the next run calculates, Then no deduction row is created for this loan.

**M352-S3** As an HR_ADMIN I want outstanding loan balances deducted from termination final settlements.

Acceptance criteria:
- Given employee has ACTIVE loan outstanding_balance=1200, When FinalSettlementService runs, Then 1200 is deducted from the payout.
- If loan > final payout, payout = 0; HR creates WRITTEN_OFF record with audit entry.

### Edge Cases
- EC-352-1: Two active loans for the same employee → both deducted independently per run
- EC-352-2: Loan start_deduction_month is in the past but no runs have been calculated → installments only apply from the earliest open run going forward (no retroactive deductions)
- EC-352-3: Employee on ON_HOLD for a run → loan deduction also skipped; deduction does NOT accumulate for the missed period

---

## Epic M353 — Payroll Variance Report + YTD Summary

### Stories

**M353-S1** As a PAYROLL_SPECIALIST I want a period-over-period variance report so that I can spot unexpected payroll changes before approving a run.

Acceptance criteria:
- Given I compare REGULAR May 2026 and June 2026 runs, Then the report shows per-employee: prior_gross, current_gross, gross_delta, gross_delta_pct, net_delta, flags.
- Employees in current but not prior: prior = 0, flag = NEW_EMPLOYEE.
- Employees in prior but not current: current = 0, flag = EMPLOYEE_ABSENT.
- Employees with gross_delta_pct > 10%: flagged in summary count.

**M353-S2** As an HR_ADMIN I want a YTD payroll summary so that I can review cumulative payroll costs at any point in the year.

Acceptance criteria:
- Given PAID REGULAR runs Jan–June 2026, When I request YTD for employee A, Then totals = sum of gross/tax/DSMF/MMI/unemployment/bonuses/allowances/net across those 6 runs.
- OFF_CYCLE runs are excluded from YTD unless explicitly requested.

### Edge Cases
- EC-353-1: Comparing an OFF_CYCLE run to a REGULAR run → API returns 400 (incompatible run types)
- EC-353-2: YTD with no PAID runs for the year → returns zero totals, not an error

---

## Epic M354 — Year-End Processing + AZ Annual Tax Certificate

### Stories

**M354-S1** As an HR_ADMIN I want to generate annual payroll summaries for all employees so that I have accurate year-end totals for reporting.

Acceptance criteria:
- Given PAID REGULAR runs for Jan–Dec 2026 for employee A, When I generate the annual summary for 2026, Then gross = sum of all 12 months' gross_amount; income_tax = sum of all income_tax fields.
- Employees with no PAID run in 2026 are excluded.
- Generating again replaces the prior record (idempotent).

**M354-S2** As an HR_ADMIN I want to generate AZ annual income tax certificates ("İllik gəlir arayışı") for each employee so that they can submit them for personal tax declarations.

Acceptance criteria:
- Given employee A's 2026 annual summary exists, When I generate the certificate, Then the record contains: employee name, employee VÖEN (national_id), employer VÖEN, annual gross, exempt amount (200 AZN × 12 months applicable months), taxable gross, total income tax withheld, period 2026-01-01 to 2026-12-31.
- Certificate status = GENERATED; stored in annual_tax_certificate.
- HR can download certificate as PDF (reuse ReportExportService).

### Edge Cases
- EC-354-1: Generating certificate for employee with no compensation record → skipped (no zero-salary certificates issued)
- EC-354-2: Employee had salary change mid-year → annual totals aggregate correctly across all runs regardless

---

## Epic M355 — Cost Center Allocation + GL Journal Generation

### Stories

**M355-S1** As an HR_ADMIN I want to define cost center allocations per employee so that payroll costs flow to the correct departments in the GL.

Acceptance criteria:
- Given I set employee A: CC-ENGINEERING 60%, CC-PRODUCT 40%, effective 2026-07-01, Then sum = 100%, saved successfully.
- Given I set CC-ENGINEERING 70% without a second allocation, Then validation returns 400 (sum must = 100%).
- Creating a new allocation from 2026-08-01 closes the prior open allocation to 2026-07-31.

**M355-S2** As a PAYROLL_SPECIALIST I want a balanced GL journal generated when a payroll run is APPROVED so that the finance team can post it without manual entry.

Acceptance criteria:
- Given a payroll run reaches APPROVED status, When GL journal generation runs, Then the journal contains: debit lines per (cost_center, salary expense account), credit lines to payroll payable, tax payable, DSMF payable accounts.
- Given total debits and total credits, Then debits = credits (balanced).
- Re-generating replaces existing journal lines for that run.
- Journal can be exported as CSV.

### Edge Cases
- EC-355-1: Employee with no cost center allocation → 100% defaults to OrgUnit.cost_center_code; if null → 'DEFAULT'
- EC-355-2: GL mapping missing for a component_kind → warning logged; component excluded from journal (not a fatal error)
- EC-355-3: Journal generation attempted on a DRAFT run → 400 (only APPROVED or PAID)

---

## Epic M356 — PDF Payslip Generation + Delivery

### Stories

**M356-S1** As an EMPLOYEE I want to download my PDF payslip from the portal so that I have an official record of my monthly pay.

Acceptance criteria:
- Given my July 2026 payslip PDF has been generated, When I open My Workspace → Payslips, Then I see a Download PDF button for July 2026.
- Given I am employee A, Then I cannot download employee B's payslip (403).
- PDF content includes: company name, pay period, payslip number, employee name/ID/position, earnings table (base + components with taxable/non-taxable label), OT, bonuses, gross total, statutory deductions (tax/DSMF/MMI/unemployment), DEDUCTION components, net pay, employer contributions (informational), masked IBAN (last 4 digits).

**M356-S2** As an HR_ADMIN I want to bulk-generate PDF payslips for all employees in a run so that I do not need to trigger them one-by-one.

Acceptance criteria:
- Given run R is PAID, When I POST /api/payroll/runs/{id}/generate-payslips, Then PDFs for all employees are generated and stored in MinIO.
- Generation is idempotent: re-running replaces existing files.
- On completion: response includes count of generated PDFs.

**M356-S3** As an HR_ADMIN I want payslip PDFs emailed to employees after the run is PAID so that they receive prompt notification.

Acceptance criteria:
- Given email delivery is triggered for run R, Then each employee with a work email receives an email with their PDF attached.
- Employees without a work email are skipped with a log warning (not a fatal error).

### Edge Cases
- EC-356-1: Run not yet PAID → PDF generation returns 400
- EC-356-2: Employee has no payroll_result in the run → no PDF generated for that employee (OFF_CYCLE scenario)
- EC-356-3: MinIO unavailable → generation fails gracefully; HR can retry

---

## Epic M357 — Payroll Control Board

### Stories

**M357-S1** As an HR_ADMIN I want a pre-flight checklist before running payroll calculation so that I can fix issues before the run.

Acceptance criteria:
- Given employee C has no active compensation record, When I view the pre-flight checklist for July run, Then employee C appears under "No active salary".
- Given employee D has no APPROVED timesheet for July, Then D appears under "No approved timesheet".
- Given employee E is ON_HOLD, Then E appears under "On hold".
- Given employee F has a PENDING advance, Then F appears under "Pending advance".
- Given employee G had a salary change effective July 1, Then G appears under "Retroactive salary change".

**M357-S2** As a PAYROLL_SPECIALIST I want a dashboard showing the current payroll cycle status so that I always know where the run stands.

Acceptance criteria:
- Dashboard shows: current open run status, total headcount vs included-in-run headcount, total gross/net/tax, month-over-month gross variance %, outstanding loan count and total balance, pending advance request count.
- Dashboard updates when the run status changes.

### Edge Cases
- EC-357-1: No open run for current month → dashboard shows "No run opened" with a Create Run button
- EC-357-2: Pre-flight checklist requested for a PAID run → 400 (pre-flight only relevant for DRAFT/CALCULATED)

---

## Epic M358 — Payroll Reports Suite

### Stories

**M358-S1** As a PAYROLL_SPECIALIST I want all 7 payroll reports available in one place so that I can answer finance and HR queries without writing SQL.

Reports:
1. **Period payroll summary** — gross/net/tax/contributions totals + per-employee breakdown for a run
2. **Variance report** — period-over-period (delegates to M353 logic)
3. **YTD payroll summary** — per-employee cumulative for a calendar year
4. **Employer cost report** — gross + employer DSMF + employer MMI + employer unemployment per employee per run
5. **Loan & advance status** — outstanding balances, monthly installment, expected payoff date, pending advances
6. **Bank file reconciliation** — sum of bank file export vs sum of payroll_result.net_amount (must match)
7. **Annual tax certificate list** — all generated certificates for a year with download links

Acceptance criteria:
- All 7 reports return data filtered by the caller's access scope (HR_ADMIN = all; MANAGER = team only)
- Reports 1, 2, 4 require a run_id parameter
- Reports 3, 7 require a year parameter
- Report 5 requires no parameter (current state)
- Report 6 requires a run_id and bank file export must already exist
- Each report can be exported as CSV

### Edge Cases
- EC-358-1: Bank file reconciliation — mismatch detected → report flags the discrepancy with the delta amount; does not auto-correct
- EC-358-2: Employer cost report for an OFF_CYCLE run → valid; shows only the employees included in that run

---

## Permission Matrix

| Feature | HR_ADMIN | PAYROLL_SPECIALIST | HR_SPECIALIST | SYSTEM_ADMIN | EMPLOYEE | MANAGER |
|---------|----------|-------------------|---------------|--------------|---------|---------|
| Salary component catalog: read | ✅ | ✅ | ✅ | — | — | — |
| Salary component catalog: write | ✅ | ✅ | — | — | — | — |
| Component assignments: read | ✅ | ✅ | read (masked salary) | — | — | — |
| Component assignments: write | ✅ | ✅ | — | — | — | — |
| Payroll run: create/calculate | ✅ | ✅ | — | — | — | — |
| Payroll run: approve (step 1-2) | ✅ HR_SPECIALIST (step 1) | ✅ | — | — | — | — |
| Payroll run: approve (final step) | — | — | — | ✅ | — | — |
| Employee payroll hold: set/clear | ✅ | ✅ | — | — | — | — |
| Off-cycle run: create | ✅ | ✅ | — | — | — | — |
| Salary advance: request | ✅ | ✅ | — | — | ✅ (own) | — |
| Salary advance: approve/reject | ✅ | — | — | — | — | — |
| Payroll loan: create/approve | ✅ | ✅ | — | — | — | — |
| GL journal: generate/view | ✅ | ✅ | read | — | — | — |
| Cost center allocation: write | ✅ | ✅ | — | — | — | — |
| Annual tax certificate: generate | ✅ | ✅ | — | — | — | — |
| PDF payslip: bulk generate | ✅ | ✅ | — | — | — | — |
| PDF payslip: view own | — | — | — | — | ✅ | — |
| Payroll reports: all employees | ✅ | ✅ | read | — | — | — |
| Payroll reports: team only | — | — | — | — | — | ✅ (gross/net only) |
| Payroll summary: own | — | — | — | — | ✅ | — |

---

## Payroll Consultant Validated Rules

The following rules were validated by the payroll consultant and are binding for implementation:

| Rule | Resolution |
|------|-----------|
| is_taxable vs DSMF base | is_taxable=false → income tax exempt but STILL in DSMF/MMI base. contribution_exempt=true needed to exclude from DSMF base |
| DEDUCTION components position | Subtract from net AFTER statutory taxes. DO NOT reduce DSMF/MMI base |
| Advance recovery on termination | Deduct from FinalSettlementService payout; write-off path if advance > payout |
| Loan recovery on termination | Deduct from FinalSettlementService payout; HR write-off with audit if loan > payout |
| Off-cycle runs + monthly DSMF | Off-cycle run results are aggregated with REGULAR run results for same-month DSMF declaration |
| Annual tax certificate name | "İllik gəlir arayışı" per AZ Tax Code; "Form 154" is informal internal label |
| GL journal at APPROVED | Correct AZ practice (accrual on obligation arising, not cash payment) |
| Pro-ration of fixed components | FIXED_AMOUNT components are NOT pro-rated; PERCENTAGE_OF_BASE components auto-scale |
