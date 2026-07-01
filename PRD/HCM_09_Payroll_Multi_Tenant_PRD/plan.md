# Payroll Multi-Tenant PRD — Architecture Plan (M349–M358)

**Module:** payroll | **Jurisdiction:** AZ only | **Currency:** AZN

---

## 1. Module Boundaries

### New Spring @Service Beans

| Service | Responsibility |
|---------|----------------|
| `SalaryComponentService` | CRUD on `salary_component` catalog + assignment lifecycle (create closes prior open). Does NOT call engine — engine queries repository directly. |
| `SalaryAdvanceService` | Advance request lifecycle (PENDING→APPROVED/REJECTED→DEDUCTED/CANCELLED); creates `PayrollDeduction` SALARY_ADVANCE row on approval. |
| `PayrollLoanService` | Loan lifecycle (PENDING→ACTIVE→FULLY_REPAID/WRITTEN_OFF/CANCELLED); idempotent installment creation. |
| `PayrollHoldService` | Manage `payroll_run_hold` flags per run. |
| `PayrollVarianceService` | Period-over-period variance + YTD aggregation. |
| `AnnualTaxCertificateService` | Year-end summary + AZ Form 154 certificate generation (reuses `ReportExportService` for PDF). |
| `CostCenterAllocationService` | Per-employee cost center splits; sum=100% validation. |
| `GLAccountMappingService` | Maps component_kind + component_code → debit/credit accounts. |
| `GLJournalService` | Generates balanced GL journal on APPROVED; splits by cost center; validates debits=credits. |
| `PayslipPdfService` | PDF generation via `ReportExportService`, storage via `AttachmentService`, delivery via `EmailService`. |
| `PayrollControlBoardService` | Pre-flight checklist queries + dashboard aggregations. |
| `PayrollAdvanceLoanDeductionHook` | Called by `PayrollEngine` after statutory; creates idempotent deduction rows; nets floor=0. |

### Extended Beans (modify, not replace)

| Bean | Extension |
|------|-----------|
| `PayrollEngine` | Load active salary component assignments; classify taxable/non-taxable/deduction; call advance/loan hook; snapshot to `payroll_result_component`; skip ON_HOLD employees; filter by `run.employeeIds` for OFF_CYCLE. |
| `PayrollRunService` | Add `createOffCycle()`, expose `run_type`, `employee_ids`, `description`. |
| `FinalSettlementService` | Deduct outstanding advances + loans from terminal payout; write-off path with audit. |

### New Repositories

`SalaryComponentRepository`, `SalaryComponentAssignmentRepository`, `PayrollResultComponentRepository`, `SalaryAdvanceRepository`, `PayrollLoanRepository`, `PayrollRunHoldRepository`, `CostCenterAllocationRepository`, `PayrollResultCostSplitRepository`, `GLAccountMappingRepository`, `GLJournalRepository`, `GLJournalLineRepository`, `AnnualPayrollSummaryRepository`, `AnnualTaxCertificateRepository`

---

## 2. Shared Services (Reuse Points)

| Service | Reuse In |
|---------|----------|
| `AttachmentService` | PDF payslip storage (ownerModule=`payroll`, ownerEntity=`payslip`) |
| `EmailService` | Payslip email delivery with PDF attachment |
| `ReportExportService` | PDF generation for payslips and annual tax certificates |
| `ErpExportService` | GL journal CSV export |
| `AuditService.record(module, entityName, entityId, action, oldValue, newValue)` | All status changes (6-param signature) |
| `AccessScopeService` | Manager team scope on payroll reports (gross/net only) |
| `WorkflowService` | PAYROLL_APPROVAL workflow (UUID 77777777-...) for off-cycle runs |

---

## 3. PayrollEngine Extension Design

### Taxability Flow

```
calculate(PayrollRun run):
  for each employee:
    1. Skip if ON_HOLD (query payroll_run_hold)
    2. Skip if OFF_CYCLE and employeeId not in run.employeeIds

    assignments = SalaryComponentAssignmentRepository.findActiveOn(employeeId, periodStart)
    for each assignment:
      amount = resolveAmount(assignment, baseSalary)
      if EARNING + is_taxable=true:
        taxableGross += amount           // adds to income tax + DSMF/MMI base
      if EARNING + is_taxable=false + contribution_exempt=false:
        dsmfBase += amount               // still in DSMF/MMI base
        postStatutoryAdditions += amount // added to net after statutory
      if EARNING + is_taxable=false + contribution_exempt=true:
        postStatutoryAdditions += amount // excluded from DSMF/MMI base
      if DEDUCTION:
        postStatutoryDeductions += amount // subtracted from net AFTER all statutory

    3. StatutoryCalculator.incomeTax(taxableGross, jurisdiction, date)
    4. StatutoryCalculator dsmf/mmi/unemployment on dsmfBase (= taxableGross + non-exempt non-taxable)
    5. net = taxableGross − incomeTax − dsmfEE − mmiEE − unemploymentEE
    6. net += postStatutoryAdditions
    7. PayrollAdvanceLoanDeductionHook.apply(employeeId, year, month, net) → additional deductions
    8. net -= postStatutoryDeductions
    9. net = max(net, 0)   // floor
    10. Snapshot each component → PayrollResultComponent
```

### Amount Resolution

```java
BigDecimal resolveAmount(SalaryComponentAssignment a, BigDecimal baseSalary) {
    if (a.getAmountOverride() != null) return a.getAmountOverride();
    return switch (a.getComponent().getCalculationMethod()) {
        case FIXED_AMOUNT       -> a.getComponent().getDefaultAmount();
        case PERCENTAGE_OF_BASE -> baseSalary.multiply(a.getComponent().getPercentage());
        case FLAT_RATE          -> a.getComponent().getDefaultAmount();
    };
}
```

---

## 4. Advance/Loan Hook (PayrollAdvanceLoanDeductionHook)

Called AFTER statutory deductions. Idempotent — checks existence before creating deduction rows.

```
apply(employeeId, year, month, currentNet):
  // Salary advances for this period
  advances = SalaryAdvanceRepository.findApprovedForPeriod(employeeId, year, month)
  for each advance:
    if NOT PayrollDeductionRepository.existsBySalaryAdvanceIdAndPeriod(advance.id, year, month):
      create PayrollDeduction(type=SALARY_ADVANCE, source_advance_id=advance.id, amount=approved_amount)
    deductions += approved_amount

  // Active loan installments
  loans = PayrollLoanRepository.findActiveForPeriod(employeeId, year, month)
  for each loan:
    if loan.outstanding_balance <= 0: skip
    if NOT PayrollDeductionRepository.existsByLoanIdAndPeriod(loan.id, year, month):
      installment = min(loan.monthly_installment, loan.outstanding_balance)
      create PayrollDeduction(type=LOAN_INSTALLMENT, source_loan_id=loan.id, amount=installment)
    deductions += installment

  return deductions
```

---

## 5. FinalSettlementService Extension

On termination, before computing net payout:

```
1. Outstanding APPROVED advances → deduct from terminal payout
2. Active loans outstanding_balance > 0 → deduct from terminal payout
3. payout = max(payout − totalDeduction, 0)
4. If totalDeduction > payout:
   → Create write-off record (audit-logged, requiresHrAcknowledgement=true)
5. Mark advances DEDUCTED; mark loans WRITTEN_OFF or FULLY_REPAID
```

---

## 6. GL Journal Design (Double-Entry)

**Trigger:** Run reaches APPROVED status.

**Debits:** Salary expense per cost center (split by `cost_center_allocation`)
**Credits:** Net pay payable, income tax payable, DSMF EE+ER payable, MMI EE+ER payable, unemployment EE+ER payable

Balance enforcement: `sum(DEBIT) == sum(CREDIT)` — `BadRequestException` if unbalanced.

Default cost center: `employee.orgUnit.cost_center_code` → fallback `'DEFAULT'`

---

## 7. Security Boundaries

### 3 Blocking Issues from Security Review (must enforce during build)

1. **HR_SPECIALIST salary masking**: Create `PayrollAccessRoles.canSeeSalaryAmounts()` helper. Component assignment amount, compensation amounts, payslip details masked for HR_SPECIALIST. DTO builders check this and return `null` for salary fields.

2. **Manager hierarchy scope**: All payroll report endpoints for MANAGER/DEPARTMENT_MANAGER MUST pipe through `AccessScopeService.scopeForCurrentUser()`. Manager sees only gross/net totals for their team — no deduction or component breakdown.

3. **Employee advance self-verification**: Advance request endpoint MUST ignore any `employeeId` in the request body — always derive from `EmployeeContextService.currentEmployee()`.

### Role Matrix (key endpoints)

| Resource | HR_ADMIN | PAYROLL_SPECIALIST | HR_SPECIALIST | EMPLOYEE | MANAGER |
|----------|----------|-------------------|---------------|----------|---------|
| Component catalog RW | ✅ | ✅ | R (masked) | — | — |
| Component assignments RW | ✅ | ✅ | R (masked) | — | — |
| Run create/calculate | ✅ | ✅ | — | — | — |
| Advance approve | ✅ | — | — | — | — |
| Advance request (own) | ✅ | ✅ | — | ✅ | — |
| Loan create | ✅ | ✅ | — | — | — |
| GL journal | ✅ | ✅ | R | — | — |
| Annual cert generate | ✅ | ✅ | — | — | — |
| Own payslip PDF | — | — | — | ✅ | — |
| Team payroll summary | — | — | — | — | ✅ gross/net |

---

## 8. Build Order

```
M349 (Salary Component Catalog + PayrollEngine Integration)
  └→ M350 (Employee Payroll Hold + Off-Cycle Run Type)
       └→ M351 (Salary Advance Requests)
            └→ M352 (Payroll Loan + Installment Auto-Deduction)

After M349 (can build in parallel once M349 is done):
  M353 (Variance Report + YTD)
  M354 (Year-End + Annual Tax Certificate)
  M355 (Cost Center + GL Journal)
  M356 (PDF Payslip + Delivery)
  M357 (Control Board)
  M358 (Reports Suite — after M353, M354, M355)
```

---

## 9. Test Fixtures Required (payroll-validation Phase 4)

Create in `prd/HCM_09_Payroll_Multi_Tenant_PRD/fixtures/`:

**M349-fixtures.json** — employee with:
- Base salary: 2000 AZN
- TRANSPORT component: is_taxable=false, contribution_exempt=false, 150 AZN
- MEAL component: is_taxable=true, 50 AZN
- Expected: taxable gross = 2000 + 50 = 2050; DSMF base = 2050 + 150 = 2200; income tax on 2050; TRANSPORT added to net after statutory

**M351-fixtures.json** — advance ceiling validation (50%), termination recovery

**M352-fixtures.json** — loan installment (partial last), termination write-off

**M355-fixtures.json** — GL journal balance (multi-cost-center split)
