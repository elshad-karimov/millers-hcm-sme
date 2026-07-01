# Payroll Multi-Tenant API Contract (M349–M358)

Version: 1.0 | Module: payroll | Base: `/api/payroll`

All endpoints require Bearer token (Keycloak OIDC). Amounts in AZN with 2 decimal precision.

---

## Uniform Error Format

```json
{ "error": "ERROR_CODE", "message": "Human readable", "field": "optional" }
```

Common codes: `COMPONENT_NOT_FOUND` 404, `COMPONENT_IS_STATUTORY` 400, `COMPONENT_IN_USE` 400, `ADVANCE_LIMIT_EXCEEDED` 400, `ADVANCE_PENDING_EXISTS` 400, `ADVANCE_ALREADY_DEDUCTED` 400, `LOAN_ALREADY_REPAID` 400, `ALLOCATION_SUM_INVALID` 400, `GL_MAPPING_MISSING` 400, `OFF_CYCLE_REQUIRES_EMPLOYEES` 400, `RUN_STATUS_INVALID` 400, `JOURNAL_NOT_BALANCED` 500.

---

## 1. Salary Component Catalog (`/api/payroll/components`)

### GET /api/payroll/components
Roles: `HR_ADMIN, PAYROLL_SPECIALIST, HR_SPECIALIST, SYSTEM_ADMIN`
Params: `kind`, `isActive`, `isStatutory`, `page`, `size`
Response: paginated list with `id, code, name, kind, calculationMethod, percentage, defaultAmount, isTaxable, contributionExempt, isStatutory, isActive`

### POST /api/payroll/components
Roles: `HR_ADMIN, PAYROLL_SPECIALIST, SYSTEM_ADMIN`
Body: `code*, name*, kind*(EARNING|DEDUCTION), calculationMethod*(FIXED_AMOUNT|PERCENTAGE_OF_BASE|FLAT_RATE), percentage (required if PERCENTAGE_OF_BASE), defaultAmount, isTaxable*, contributionExempt`
Response: 201 Created

### GET /api/payroll/components/{id}
Response: full component object

### PUT /api/payroll/components/{id}
Body: same as POST (code immutable); 400 if `isStatutory=true`

### DELETE /api/payroll/components/{id}
204; Errors: `COMPONENT_IS_STATUTORY`, `COMPONENT_IN_USE` (has active assignments)

---

## 2. Component Assignments (`/api/payroll/employees/{employeeId}/component-assignments`)

### GET /api/payroll/employees/{employeeId}/component-assignments
Roles: `HR_ADMIN, PAYROLL_SPECIALIST, HR_SPECIALIST` (salary amounts masked for HR_SPECIALIST)
Params: `activeOnly` (default true)
Response: list with `id, employeeId, componentId, componentCode, componentName, componentKind, amountOverride, effectiveFrom, effectiveTo`

### POST /api/payroll/employees/{employeeId}/component-assignments
Body: `componentId*, effectiveFrom*, amountOverride, reason`
Business: auto-closes prior active assignment for same (employee, component)

### DELETE /api/payroll/employees/{employeeId}/component-assignments/{assignmentId}
Sets `effectiveTo=today` (soft close); 204

---

## 3. Payroll Run Enhancements

### POST /api/payroll/runs (enhanced)
New fields: `runType` (REGULAR default | OFF_CYCLE), `description` (required for OFF_CYCLE), `employeeIds` (required for OFF_CYCLE)
Error: `OFF_CYCLE_REQUIRES_EMPLOYEES`

### POST /api/payroll/runs/{id}/hold-employees
Body: `{ "holds": [{ "employeeId": "uuid", "reason": "..." }] }`

### DELETE /api/payroll/runs/{id}/hold-employees/{employeeId}
204

### GET /api/payroll/runs/{id}/pre-flight
Response:
```json
{
  "runId": "uuid",
  "checklist": {
    "noCompensation": [{ "employeeId": "...", "employeeNo": "..." }],
    "noTimesheet": [...],
    "onHold": [{ "employeeId": "...", "reason": "..." }],
    "pendingAdvances": [...],
    "retroactiveSalaryChange": [...]
  },
  "summary": { "totalIssues": 5 }
}
```

---

## 4. Salary Advances (`/api/payroll/advances`)

### GET /api/payroll/advances
Roles: HR sees all; EMPLOYEE sees own only
Params: `employeeId`, `status` (PENDING|APPROVED|REJECTED|DEDUCTED|CANCELLED), `page`, `size`

### POST /api/payroll/advances
Roles: HR_ADMIN, PAYROLL_SPECIALIST (any employee); EMPLOYEE (own only — employeeId derived from context, not body)
Body: `{ "employeeId": "uuid", "requestedAmount": 1000.00, "reason": "..." }`
Validation: `requestedAmount ≤ 0.50 × monthlyBaseSalary`; no PENDING/APPROVED advance exists
Errors: `ADVANCE_LIMIT_EXCEEDED`, `ADVANCE_PENDING_EXISTS`

### GET /api/payroll/advances/{id}

### POST /api/payroll/advances/{id}/approve
Roles: `HR_ADMIN, SYSTEM_ADMIN`
Body: `{ "approvedAmount": 800.00, "repaymentYear": 2026, "repaymentMonth": 8 }`
Creates: `payroll_deduction` row with type=SALARY_ADVANCE, source_advance_id

### POST /api/payroll/advances/{id}/reject
Body: `{ "reason": "..." }`

### POST /api/payroll/advances/{id}/cancel
Error: `ADVANCE_ALREADY_DEDUCTED` (if status=DEDUCTED)

---

## 5. Payroll Loans (`/api/payroll/loans`)

### GET /api/payroll/loans
Params: `employeeId`, `status`, `page`, `size`

### POST /api/payroll/loans
Roles: `HR_ADMIN, PAYROLL_SPECIALIST, SYSTEM_ADMIN`
Body: `{ "employeeId": "uuid", "principalAmount": 3000.00, "monthlyInstallment": 500.00, "startDeductionYear": 2026, "startDeductionMonth": 7, "description": "..." }`
Computed: `termMonths = ceil(principalAmount / monthlyInstallment)`; status=ACTIVE

### GET /api/payroll/loans/{id}

### POST /api/payroll/loans/{id}/cancel
Only if `amountRepaid = 0`

### POST /api/payroll/loans/{id}/write-off
Roles: `HR_ADMIN, SYSTEM_ADMIN`
Body: `{ "reason": "..." }`; audit-logged

---

## 6. Variance Report

### GET /api/payroll/reports/variance
Params: `currentRunId*, priorRunId*` (both must be REGULAR runs)
Response:
```json
{
  "currentRunId": "uuid", "priorRunId": "uuid",
  "summary": { "totalGrossChange": 15000.00, "pctChange": 3.5, "highVarianceCount": 2, "newEmployeeCount": 1, "absentEmployeeCount": 0 },
  "employees": [
    { "employeeId": "uuid", "employeeNo": "E001", "name": "...", "priorGross": 2000.00, "currentGross": 2200.00, "grossDelta": 200.00, "grossDeltaPct": 10.0, "netDelta": 180.00, "flags": ["SALARY_CHANGE"] }
  ]
}
```
Flags: `SALARY_CHANGE, BONUS_ADDED, NEW_EMPLOYEE, EMPLOYEE_ABSENT, DEDUCTION_ADDED, COMPONENT_CHANGE`

---

## 7. YTD Summary

### GET /api/payroll/reports/ytd
Params: `year*`, `employeeId` (optional; all employees if omitted)
Response: per-employee `{ totalGross, totalIncomeTax, totalDsmf, totalMmi, totalUnemployment, totalBonuses, totalNet, monthsCount }`
Only PAID REGULAR runs included.

---

## 8. Year-End + Annual Tax Certificates

### POST /api/payroll/year-end/generate-summary?year={year}
Generates annual_payroll_summary for all employees with ≥1 PAID REGULAR run that year. Idempotent.

### GET /api/payroll/year-end/certificates?year={year}
List all annual_tax_certificate rows for the year.

### POST /api/payroll/year-end/certificates/generate?year={year}
Generate PDFs for all certificates for the year. Idempotent.

### GET /api/payroll/year-end/certificates/{id}/download
Streams PDF from MinIO.

### GET /api/payroll/employees/me/year-end/certificate?year={year}
Employee downloads own certificate (EMPLOYEE role).

---

## 9. Cost Center + GL Journal

### GET /api/payroll/employees/{employeeId}/cost-allocations
Returns active and historical allocations.

### POST /api/payroll/employees/{employeeId}/cost-allocations
Body: `{ "allocations": [{ "costCenterCode": "CC-ENG", "allocationPct": 60.00 }, ...], "effectiveFrom": "2026-07-01" }`
Validation: `sum(allocationPct) = 100.00`; replaces current open allocations (closes prior with effectiveTo=effectiveFrom-1day)

### GET /api/payroll/gl/account-mappings
### POST /api/payroll/gl/account-mappings
Body: `{ "componentKind": "EARNING", "componentCode": null, "accountType": "DEBIT", "glAccountCode": "5001", "glAccountName": "Salary Expense" }`

### POST /api/payroll/runs/{id}/gl-journal/generate
Only on APPROVED or PAID status. Validates debits=credits; replaces existing journal.
Errors: `RUN_STATUS_INVALID`, `GL_MAPPING_MISSING`, `JOURNAL_NOT_BALANCED`

### GET /api/payroll/runs/{id}/gl-journal
Returns journal header + all lines.

### GET /api/payroll/runs/{id}/gl-journal/export
Returns CSV file.

---

## 10. PDF Payslips

### POST /api/payroll/runs/{id}/generate-payslips
Roles: `HR_ADMIN, PAYROLL_SPECIALIST`
Only on PAID status. Idempotent (replaces existing PDFs).
Response: `{ "generated": N }`

### GET /api/payroll/runs/{id}/payslips/{employeeId}/download
Roles: HR roles download any; EMPLOYEE 403 for others' payslips.

### GET /api/payroll/employees/me/payslips
EMPLOYEE role: lists own payslips `[{ runId, periodYear, periodMonth, netAmount, generatedAt, downloadUrl }]`

### GET /api/payroll/employees/me/payslips/{runId}/download
EMPLOYEE role: downloads own payslip PDF.

### POST /api/payroll/runs/{id}/send-payslips
Sends email with PDF attachment to all employees in run.
Response: `{ "sent": N, "failed": M }`

---

## 11. Control Board

### GET /api/payroll/control-board
Response:
```json
{
  "currentRun": { "id": "uuid", "periodYear": 2026, "periodMonth": 7, "status": "DRAFT", "runType": "REGULAR" },
  "headcount": 42, "totalGross": 180000.00, "totalNet": 140000.00, "momGrossVariancePct": 2.1,
  "outstandingLoanBalance": 15000.00, "pendingAdvanceCount": 3
}
```

---

## 12. Reports Suite

### GET /api/payroll/reports/period-summary?runId={id}
Per-run totals + per-employee breakdown with component lines.

### GET /api/payroll/reports/employer-cost?runId={id}
Gross + employer DSMF + employer MMI + employer unemployment per employee.

### GET /api/payroll/reports/loan-advance-status
All active loans + PENDING/APPROVED advances with outstanding balances.

### GET /api/payroll/reports/bank-reconciliation?runId={id}
Sum of bank file export vs sum of payroll_result.net_amount; delta flagged if non-zero.

---

## Appendix: Payroll Formulas

**Component Processing Order:**
1. Base salary (pro-rated for partial month on base only)
2. EARNING is_taxable=true → add to taxableGross (income tax + DSMF/MMI base)
3. EARNING is_taxable=false, contribution_exempt=false → add to DSMF/MMI base; add to net after statutory
4. EARNING is_taxable=false, contribution_exempt=true → skip DSMF/MMI base; add to net after statutory
5. Statutory: incomeTax(taxableGross) + dsmf(dsmfBase) + mmi(dsmfBase) + unemployment(dsmfBase)
6. Net = taxableGross − statutory
7. Net += non-taxable EARNING amounts
8. DEDUCTION components (salary advances, loan installments, DEDUCTION-kind components) − from net
9. Net floor = 0

**Advance Ceiling:** `maxAdvance = monthlyBaseSalary × 0.50`
**Loan Term:** `termMonths = ceil(principalAmount / monthlyInstallment)`
**Cost Allocation:** `allocatedAmount = payrollAmount × (allocationPct / 100)`
**GL Trigger:** On APPROVED (AZ accounting practice — accrual at obligation, not payment)
