# Payroll Multi-Tenant UI Screens (M349–M358)

Stack: React 18 + Ant Design 5 + TypeScript. Patterns from existing codebase.

---

## Route Map (new routes)

```
/payroll/components          SalaryComponentsPage (M349)
/payroll/advances            SalaryAdvancesPage (M351)
/payroll/loans               PayrollLoansPage (M352)
/payroll/gl-mappings         GLAccountMappingsPage (M355)
/payroll/year-end            YearEndPage (M354)
/payroll/control-board       PayrollControlBoardPage (M357)
/payroll/reports/variance    VarianceReportPage (M353)
```

Existing `/payroll/runs` and `/payroll/runs/:id` are extended (not replaced).

---

## Status Color Maps

```typescript
const ADVANCE_STATUS_COLOR: Record<string, string> = {
  PENDING: 'gold', APPROVED: 'cyan', REJECTED: 'red',
  DEDUCTED: 'green', CANCELLED: 'default'
}
const LOAN_STATUS_COLOR: Record<string, string> = {
  PENDING: 'orange', ACTIVE: 'blue', FULLY_REPAID: 'green',
  WRITTEN_OFF: 'purple', CANCELLED: 'default'
}
const RUN_TYPE_COLOR: Record<string, string> = { REGULAR: 'blue', OFF_CYCLE: 'orange' }
const COMPONENT_KIND_COLOR: Record<string, string> = { EARNING: 'green', DEDUCTION: 'red' }
const ACCOUNT_TYPE_COLOR: Record<string, string> = { DEBIT: 'red', CREDIT: 'green' }
```

---

## 1. SalaryComponentsPage (`/payroll/components`)

**Table columns:** Code | Name | Kind (colored Tag) | Calculation Method | Taxable (icon) | DSMF Exempt (icon) | Statutory (lock icon) | Active (Toggle) | Actions

**Create/Edit Drawer (400px):**
- Code (readonly after create), Name
- Kind: Select [EARNING, DEDUCTION]
- Calculation Method: Select [FIXED_AMOUNT, PERCENTAGE_OF_BASE, FLAT_RATE]
- Default Amount: InputNumber (shown if FIXED_AMOUNT or FLAT_RATE)
- Percentage: InputNumber 0–100% (shown if PERCENTAGE_OF_BASE)
- Is Taxable: Switch (default ON)
- Contribution Exempt: Switch (disabled+OFF tooltip when is_taxable=ON — "Taxable components are always in DSMF base")

**Actions:** Edit (if !isStatutory), Deactivate (if !isStatutory && no active assignments)

---

## 2. Employee Component Assignments (tab on EmployeeDetailPage → Compensation)

**Subtab: Salary Components**

Table: Component Name | Kind | Method | Override Amount | Effective From | Effective To | Close

**Add Assignment Drawer:**
- Component: Select (filtered to active, not already assigned in open period)
- Amount Override: InputNumber (optional; shown always)
- Effective From: DatePicker

Note: auto-close of prior assignment handled by backend; inform user in success message.

---

## 3. Payroll Run List Enhancements

**New column:** Run Type — `<Tag color={RUN_TYPE_COLOR[runType]}>{runType}</Tag>`

**Enhanced Create Run Modal:**
- Period Year/Month (existing)
- Run Type: Radio [REGULAR (default), OFF_CYCLE]
- Description: Textarea (required + shown only if OFF_CYCLE)
- Employee IDs: Multi-select employee search (required + shown only if OFF_CYCLE)

---

## 4. Pre-Flight Checklist Drawer (600px, opens before Calculate)

Accordion sections (collapsed with count badge):
- **No Active Compensation** [N] — table: Employee No, Name, Hire Date
- **No Approved Timesheet** [N] — table: Employee No, Name
- **On Hold** [N] — table: Employee No, Name, Reason, Held By
- **Pending Advances** [N] — table: Employee No, Name, Amount
- **Retroactive Salary Changes** [N] — table: Employee No, Name, Change Date

Footer: "Proceed to Calculate" button (active even with warnings, warnings are advisory)

---

## 5. Employee Hold Management (section in Run Detail page)

**Holds Section:**
Table: Employee | Reason | Held By | Held At | Release Action

**Add Hold Button → Modal:**
- Employee: searchable Select
- Reason: Textarea (required)

---

## 6. SalaryAdvancesPage (`/payroll/advances`)

**HR View:**
Table: Employee | Requested Amount | Approved Amount | Repayment Period | Status | Requested At | Actions

Filter bar: Status, Employee search, Date range

**Approve Modal:**
- Shows: Employee name, max advance (50% of base), requested amount
- Approved Amount: InputNumber (≤ requestedAmount)
- Repayment Period: Year select + Month select

**Reject Modal:** Reason textarea

**Employee Self-Service (My Workspace → Payroll tab):**
"My Advances" section: table with Requested Amount, Approved Amount, Status, Period
"Request Advance" button → Modal: Amount (shows max available), Reason

---

## 7. PayrollLoansPage (`/payroll/loans`)

**Table:** Employee | Principal | Installment/mo | Outstanding | Repaid | Status | Start | Actions

**Create Loan Drawer:**
- Employee: Select
- Principal Amount: InputNumber
- Monthly Installment: InputNumber (min 1 AZN)
- Disbursement Date: DatePicker (optional)
- Start Deduction: Year + Month selects
- Description: Textarea
- Computed: Term = ceil(principal/installment) shown as info text

**Actions:** Write Off (button visible only if ACTIVE, HR_ADMIN only)

**EmployeeDetailPage Compensation tab:** "Loans" subtable showing active loans summary.

---

## 8. GLAccountMappingsPage (`/payroll/gl-mappings`)

**Table:** Component Kind | Component Code | Account Type (colored Tag) | GL Code | GL Name | Active | Actions

**Create/Edit Drawer:**
- Component Kind: Select [EARNING, DEDUCTION, TAX, DSMF_EE, DSMF_ER, MMI_EE, MMI_ER, UNEMPL_EE, UNEMPL_ER, NET_PAY]
- Component Code: Input (optional, leave blank for kind-level default)
- Account Type: Radio [DEBIT, CREDIT]
- GL Account Code: Input
- GL Account Name: Input

---

## 9. GL Journal Tab (in PayrollRunDetailPage)

**New "GL Journal" tab:**
Header: Status badge (DRAFT/POSTED), Total Debit, Total Credit, Balanced indicator (green check / red warning icon)

Line table: # | Cost Center | Component Kind | Acct Type | GL Code | GL Name | Amount

**Buttons:**
- "Generate Journal" (visible when status=APPROVED or PAID; replaces existing)
- "Export CSV" (visible when journal exists)

---

## 10. Cost Center Allocations (section in EmployeeDetailPage → Compensation)

**Current Allocations:**
Table: Cost Center Code | % | Effective From | Effective To

**"Update Allocations" Button → Modal:**
Dynamic rows: [Cost Center Code Input | % InputNumber] + Add Row button
Running total badge (green=100%, red=other)
Effective From: DatePicker

---

## 11. Payslips Tab (in PayrollRunDetailPage)

**New "Payslips" tab:**
Table: Employee | Position | Net Pay | Generated | Download

**Bulk Actions:**
- "Generate All Payslips" → confirm count → progress/result toast
- "Send by Email" → confirm "Send to N employees?" → result: N sent, M failed

**Employee Self-Service (My Workspace → Payroll tab):**
"My Payslips" table: Period | Net Pay | Download PDF button

---

## 12. YearEndPage (`/payroll/year-end`)

Year selector: `<Select options={[2026, 2027, ...]} />`

Section 1 — Annual Summaries:
"Generate Summaries" button → confirm → success count
Table: Employee | Gross | Income Tax | DSMF EE | MMI EE | Net | Months

Section 2 — Tax Certificates:
"Generate All Certificates" button
Table: Employee | Annual Gross | Exempt | Taxable | Tax Withheld | Status | Download

---

## 13. PayrollControlBoardPage (`/payroll/control-board`)

**Stat Cards (top row):**
- Current Run Status (badge)
- Headcount in Run
- Total Gross (AZN formatted)
- Total Net (AZN formatted)
- MoM Gross Variance % (green/red with arrow icon)
- Outstanding Loan Balance
- Pending Advance Requests (count badge, clickable → /payroll/advances)

**Quick Actions:** Create Run | View Current Run | Open Pre-flight

**Empty state (no open run):** Empty illustration with "Create Payroll Run" button

---

## 14. VarianceReportPage (`/payroll/reports/variance`)

**Selectors:** "Prior Run" select + "Current Run" select (both filtered to PAID REGULAR runs)

**Summary bar:** Total Gross Change | Change % | High Variance Count (>10%) | New Employees | Absent Employees

**Table:** Employee | Prior Gross | Current Gross | Delta (colored) | Delta % (colored) | Flags (Tag array)

Flag colors: `NEW_EMPLOYEE=blue`, `EMPLOYEE_ABSENT=default`, `SALARY_CHANGE=orange`, `BONUS_ADDED=cyan`, `DEDUCTION_ADDED=red`, `COMPONENT_CHANGE=purple`

Summary totals row at bottom.
