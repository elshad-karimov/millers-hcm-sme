# 9. Payroll Module in HCM — Full Enterprise Features with Multi-Tenancy

## Document Purpose

This document defines the full functional and technical scope for the **Payroll** module in a multi-tenant HCM/ERP system. The module handles salary calculation, payroll processing, statutory deductions, allowances, overtime, benefits deductions, loans, advances, final settlement, bank payment generation, accounting entries, payroll approvals, payroll audit, reconciliation, multi-country payroll, and multi-currency payroll.

This document assumes the ERP is designed as a **multi-tenant SaaS and/or on-premise capable platform**, where each tenant can have different companies, legal entities, countries, payroll rules, salary structures, approval workflows, calendars, banks, GL mappings, reports, and statutory requirements.

Examples of mature payroll systems include **Oracle Payroll, SAP SuccessFactors Employee Central Payroll, Workday Payroll, ADP Payroll, Dayforce Payroll, UKG Payroll, Microsoft Dynamics 365 Payroll partner solutions, Sage Payroll, and regional payroll engines**.

---

# 1. Purpose of Payroll Module

The **Payroll** module calculates and processes employee compensation for a payroll period. It converts employee master data, salary structures, attendance, leave, overtime, benefits, loans, advances, taxes, social insurance, pension contributions, and other earnings/deductions into final net salary.

## Main objectives

- Maintain salary structures
- Maintain payroll elements
- Calculate earnings
- Calculate deductions
- Calculate allowances
- Calculate bonuses
- Calculate overtime payments
- Calculate taxes
- Calculate social insurance
- Calculate pension contributions
- Deduct benefits
- Deduct employee loans
- Deduct salary advances
- Manage payroll periods
- Run payroll calculations
- Generate payslips
- Generate bank payment files
- Generate payroll accounting entries
- Manage payroll approvals
- Process retroactive payroll
- Process final settlement
- Support multi-country payroll
- Support multi-currency payroll
- Provide payroll audit trail
- Provide payroll reconciliation
- Integrate with General Ledger, cost centers, banks, tax reporting, attendance, leave, benefits, and loans

## Standard payroll flow

```text
Payroll Period Opened
        ↓
Employee Eligibility Checked
        ↓
Payroll Inputs Collected
        ↓
Attendance / Leave / Overtime Imported
        ↓
Earnings Calculated
        ↓
Deductions Calculated
        ↓
Tax / Social Insurance / Pension Calculated
        ↓
Gross-to-Net Calculation
        ↓
Payroll Review
        ↓
Payroll Approval
        ↓
Payslip Generation
        ↓
Bank Payment File Generation
        ↓
GL Accounting Entries Posted
        ↓
Payroll Period Closed
```

---

# 2. Multi-Tenant Payroll Architecture

Payroll is one of the most sensitive modules in HCM. In a multi-tenant ERP, tenant isolation, configurability, payroll privacy, and country-specific flexibility are mandatory.

## Multi-tenancy requirements

Each tenant must have isolated payroll configuration and data.

The system must support:

- Tenant-specific payroll policies
- Tenant-specific salary structures
- Tenant-specific payroll elements
- Tenant-specific payroll calendars
- Tenant-specific tax rules
- Tenant-specific social insurance rules
- Tenant-specific pension rules
- Tenant-specific benefits deductions
- Tenant-specific payroll workflows
- Tenant-specific bank file formats
- Tenant-specific GL mappings
- Tenant-specific payslip templates
- Tenant-specific payroll reports
- Tenant-specific payroll security roles
- Tenant-specific audit logs
- Tenant-specific integrations

## Tenant isolation rules

Every payroll table must include `tenant_id` either directly or through strongly enforced tenant scoping.

Examples:

- PayrollPeriod.tenant_id
- PayrollRun.tenant_id
- PayrollElement.tenant_id
- SalaryStructure.tenant_id
- EmployeePayrollProfile.tenant_id
- Payslip.tenant_id
- PayrollJournal.tenant_id
- BankPaymentFile.tenant_id
- PayrollAuditLog.tenant_id

No user, API, report, background job, export, or integration should access payroll data outside the authorized tenant.

## Multi-tenant hierarchy

A tenant may contain multiple companies and legal entities.

Recommended structure:

```text
Tenant
 └── Company / Group
      └── Legal Entity
           └── Payroll Statutory Unit
                └── Payroll Group
                     └── Employee Payroll Profile
```

## Tenant-level vs legal-entity-level setup

Some payroll settings are tenant-wide. Others must be legal-entity-specific.

| Configuration | Recommended Scope |
|---|---|
| Payroll module enablement | Tenant |
| Payroll element catalog | Tenant or country |
| Salary structure templates | Tenant |
| Tax rules | Country/legal entity |
| Social insurance rules | Country/legal entity |
| Bank file format | Legal entity/bank |
| Payslip template | Tenant/legal entity |
| GL posting rules | Legal entity |
| Approval workflow | Tenant/legal entity/payroll group |
| Payroll calendar | Legal entity/payroll group |
| Currency | Legal entity/payroll group |

---

# 3. Payroll Setup / Configuration

Payroll requires detailed configuration before payroll can be processed.

## Main setup areas

### Payroll statutory setup

- Country
- Legal entity
- Tax registration number
- Social insurance registration number
- Pension registration number
- Employer contribution rules
- Employee contribution rules
- Statutory reporting format
- Statutory calendars
- Tax year
- Tax period
- Rounding rules
- Minimum wage rules
- Tax exemption rules

### Payroll calendar setup

- Payroll calendar name
- Payroll frequency
- Payroll period start date
- Payroll period end date
- Payment date
- Cutoff date
- Attendance cutoff date
- Leave cutoff date
- Payroll approval deadline
- Bank file generation date
- GL posting date
- Period open/close status

### Payroll element setup

- Element code
- Element name
- Element type
- Earning/deduction classification
- Taxable flag
- Social insurance applicable flag
- Pension applicable flag
- Proratable flag
- Recurring/non-recurring flag
- Fixed/variable flag
- Formula-based flag
- Input required flag
- Payslip visibility flag
- GL account mapping
- Cost center allocation rules
- Effective start/end dates

### Salary structure setup

- Structure code
- Structure name
- Grade eligibility
- Job eligibility
- Position eligibility
- Legal entity eligibility
- Payroll group eligibility
- Basic salary component
- Allowance components
- Deduction components
- Employer contribution components
- Formula rules
- Effective dates
- Approval status

### Payroll approval setup

- Payroll preparer
- Payroll reviewer
- Payroll approver
- HR approval
- Finance approval
- Legal entity approval
- Executive approval
- Threshold-based approvals
- Exception approval routing
- Payroll close approval

### Payroll integration setup

- Attendance integration rules
- Leave integration rules
- Overtime integration rules
- Benefits integration rules
- Loans integration rules
- GL integration rules
- Bank integration rules
- Tax reporting integration rules

---

# 4. Payroll Dashboard

Payroll dashboard should provide real-time visibility into payroll processing, exceptions, approvals, and payment readiness.

## Payroll officer dashboard widgets

- Open payroll periods
- Payroll runs in progress
- Employees included in payroll
- Employees excluded from payroll
- Payroll inputs pending
- Attendance exceptions
- Leave without pay deductions
- Overtime pending approval
- Loan deductions pending
- Advance deductions pending
- Payroll calculation errors
- Payslips pending generation
- Payroll approval pending
- Bank file pending
- GL posting pending
- Payroll periods pending closure

## HR dashboard widgets

- Employees missing payroll profile
- Employees without bank details
- Employees with salary changes pending
- Employees with unpaid leave
- Employees with payroll-impacting status changes
- New hires pending payroll inclusion
- Terminated employees pending final settlement
- Payroll approval pending HR review

## Finance dashboard widgets

- Gross payroll amount
- Net payroll amount
- Employer contribution amount
- Tax payable
- Social insurance payable
- Payroll journal pending
- Payroll variance vs previous month
- Payroll cost by legal entity
- Payroll cost by department
- Payroll cost by cost center
- Bank payment amount

## Executive dashboard widgets

- Total payroll cost
- Payroll cost trend
- Payroll cost by legal entity
- Payroll cost by department
- Headcount payroll cost
- Overtime cost
- Bonus cost
- Employer contribution cost
- Payroll variance
- Payroll budget vs actual

---

# 5. Employee Payroll Profile

Each employee must have a payroll profile to be included in payroll.

## Main features

- Employee payroll status
- Payroll group
- Payroll frequency
- Legal entity
- Payroll statutory unit
- Tax profile
- Social insurance profile
- Pension profile
- Salary structure
- Salary basis
- Payment method
- Bank account
- Currency
- Cost center
- Payroll start date
- Payroll end date
- Payroll eligibility
- Overtime eligibility
- Benefits deduction eligibility
- Loan deduction eligibility
- Final settlement eligibility

## Payroll statuses

- Not configured
- Active
- Suspended
- On hold
- Final settlement only
- Terminated
- Excluded from payroll
- Payroll closed

## Business logic

An employee should not be included in payroll unless:

- Employee is active or eligible for final settlement
- Payroll profile exists
- Payroll group is assigned
- Bank/payment method is configured
- Salary structure or salary amount is assigned
- Legal entity is valid
- Payroll period includes employee employment dates

---

# 6. Payroll Groups

Payroll groups organize employees by payroll frequency, legal entity, country, salary cycle, or business requirement.

## Main features

- Payroll group code
- Payroll group name
- Legal entity
- Country
- Payroll frequency
- Payroll calendar
- Default currency
- Default bank account
- Default approval workflow
- Default payslip template
- Default GL posting rule
- Active/inactive status

## Payroll group examples

- Monthly staff payroll
- Weekly workers payroll
- Store employees payroll
- Executive payroll
- Contractor payroll
- Intern payroll
- Final settlement payroll
- Azerbaijan monthly payroll
- UAE monthly payroll
- Turkey monthly payroll

## Business logic

Payroll runs are usually processed by payroll group and period.

Example:

```text
Payroll Group: Monthly Staff
Period: June 2026
Employees: 350
Status: Open
```

---

# 7. Payroll Period Management

Payroll periods control the calculation cycle.

## Main features

- Create payroll period
- Payroll period code
- Period start date
- Period end date
- Payment date
- Cutoff date
- Attendance cutoff date
- Leave cutoff date
- Input submission deadline
- Approval deadline
- Period status
- Period lock
- Period close
- Reopen period with approval

## Payroll period statuses

- Draft
- Open
- Input collection
- Calculation in progress
- Calculated
- Under review
- Pending approval
- Approved
- Bank file generated
- GL posted
- Paid
- Closed
- Reopened
- Cancelled

## Business logic

A payroll period should not be closed until:

- Payroll run is approved
- Payslips are generated or intentionally skipped
- Bank file is generated or payment marked manual
- GL entries are posted or export is completed
- Payroll reconciliation is completed

---

# 8. Salary Structure Management

Salary structure defines how employee compensation is composed.

## Main features

- Salary structure code
- Salary structure name
- Basic salary component
- Allowances
- Benefits
- Fixed deductions
- Employer contributions
- Employee contributions
- Grade-based structure
- Position-based structure
- Legal-entity-based structure
- Country-specific structure
- Formula-based components
- Effective-dated structure
- Structure approval workflow
- Salary structure history

## Salary structure examples

```text
Salary Structure: Standard Monthly Staff
- Basic Salary
- Housing Allowance
- Transport Allowance
- Meal Allowance
- Social Insurance Deduction
- Income Tax
- Pension Contribution
```

```text
Salary Structure: Store Staff
- Basic Salary
- Shift Allowance
- Overtime Pay
- Meal Allowance
- Late Deduction
- Absence Deduction
```

## Business logic

Salary structure should be effective-dated. If salary structure changes from July, June payroll should still use the old structure.

---

# 9. Payroll Elements

Payroll elements are the building blocks of payroll calculation.

## Main element types

### Earnings

- Basic salary
- Hourly wage
- Overtime pay
- Bonus
- Commission
- Incentive
- Shift allowance
- Housing allowance
- Transport allowance
- Meal allowance
- Mobile allowance
- Travel allowance
- Hazard allowance
- Acting allowance
- Holiday pay
- Retroactive earning

### Deductions

- Income tax
- Social insurance employee contribution
- Pension employee contribution
- Loan deduction
- Salary advance deduction
- Benefit deduction
- Absence deduction
- Late deduction
- Early leave deduction
- Asset deduction
- Fine/penalty, if legally allowed
- Retroactive deduction

### Employer contributions

- Employer social insurance contribution
- Employer pension contribution
- Employer medical insurance contribution
- Employer unemployment contribution
- Employer benefit contribution

### Informational elements

- Gross salary
- Taxable income
- Net salary
- Employer cost
- Leave balance shown on payslip
- Cost center allocation

## Element attributes

Each payroll element should support:

- Tenant
- Country
- Legal entity
- Element code
- Element name
- Element type
- Tax treatment
- Social insurance treatment
- Pension treatment
- Payroll frequency
- Recurring flag
- Proration flag
- Arrears flag
- Retroactive flag
- Payslip display flag
- GL mapping
- Formula
- Effective date
- Status

---

# 10. Earnings Management

Earnings are amounts paid to employees.

## Main features

- Fixed earnings
- Variable earnings
- Recurring earnings
- One-time earnings
- Manual earnings input
- Bulk earnings input
- Formula-based earnings
- Attendance-based earnings
- Timesheet-based earnings
- Commission earnings
- Bonus earnings
- Retroactive earnings
- Earnings approval
- Earnings history

## Common earnings

- Basic salary
- Overtime
- Bonus
- Commission
- Incentive
- Allowances
- Holiday pay
- Acting pay
- Project pay
- Shift differential
- Night work pay

## Business logic

Earnings can come from:

- Salary structure
- Manual payroll input
- Attendance
- Overtime
- Sales commission
- Bonus plan
- Timesheet
- Retroactive adjustment
- Final settlement

---

# 11. Deductions Management

Deductions reduce employee gross salary.

## Main features

- Fixed deductions
- Variable deductions
- Recurring deductions
- One-time deductions
- Statutory deductions
- Voluntary deductions
- Loan deductions
- Benefit deductions
- Advance salary deductions
- Absence deductions
- Late deductions
- Asset deductions
- Manual deductions input
- Bulk deductions input
- Deduction approval
- Deduction limits
- Deduction priority
- Deduction history

## Deduction examples

- Income tax
- Social insurance
- Pension contribution
- Health insurance employee share
- Loan repayment
- Salary advance recovery
- Leave without pay
- Absence deduction
- Late deduction
- Asset damage deduction
- Union deduction, where applicable

## Business logic

Deductions should support priority order.

Example:

```text
Priority 1: Statutory tax/social insurance
Priority 2: Court/legal deduction, if applicable
Priority 3: Employee loan
Priority 4: Benefits
Priority 5: Other voluntary deductions
```

---

# 12. Allowances Management

Allowances are special payroll earnings based on policy, position, location, grade, or employee eligibility.

## Main features

- Fixed allowance
- Percentage allowance
- Grade-based allowance
- Position-based allowance
- Location-based allowance
- Shift-based allowance
- Attendance-based allowance
- Recurring allowance
- One-time allowance
- Taxable/non-taxable treatment
- Prorated allowance
- Allowance approval
- Allowance history

## Allowance examples

- Housing allowance
- Transport allowance
- Meal allowance
- Mobile allowance
- Internet allowance
- Vehicle allowance
- Fuel allowance
- Uniform allowance
- Field allowance
- Hazard allowance
- Night shift allowance
- Acting allowance
- Store manager allowance

## Business logic

Allowances may be automatically assigned based on:

- Position
- Grade
- Department
- Location
- Employment type
- Shift
- Contract
- Benefits plan

---

# 13. Bonuses and Incentives

The payroll module should support bonus and incentive payment processing.

## Main features

- One-time bonus
- Recurring bonus
- Performance bonus
- Annual bonus
- Sales commission
- Production incentive
- Attendance bonus
- Referral bonus
- Retention bonus
- Signing bonus
- Project completion bonus
- Bonus approval workflow
- Tax treatment
- Social insurance treatment
- Bonus payment period
- Bonus history

## Business logic

Bonus can be calculated from:

- Manual HR input
- Performance module
- Sales module
- Production module
- Recruitment referral module
- Attendance module
- Management approval

---

# 14. Overtime Payment

Overtime payment should integrate with Time and Attendance.

## Main features

- Approved overtime import
- Daily overtime
- Weekly overtime
- Weekend overtime
- Holiday overtime
- Night overtime
- Overtime rate multiplier
- Overtime cap
- Overtime approval validation
- Overtime payroll calculation
- Overtime audit

## Overtime calculation example

```text
Approved overtime hours: 10
Hourly rate: 5 AZN
Overtime multiplier: 1.5
Overtime pay: 75 AZN
```

## Business logic

Payroll should only pay overtime that is:

- Approved
- Within payroll period
- Not already paid
- Eligible by employee/position policy
- Calculated using correct hourly rate

---

# 15. Attendance-Based Deductions

Payroll should calculate deductions from approved attendance data.

## Main features

- Unpaid absence deduction
- Late arrival deduction
- Early leave deduction
- Missing punch deduction
- Leave without pay deduction
- Half-day deduction
- Attendance penalty, if legally allowed
- Attendance deduction approval
- Payroll adjustment

## Business logic

Payroll must use **approved and locked attendance summaries**, not raw attendance punches.

Example:

```text
Unpaid absence: 2 days
Daily salary rate: 50 AZN
Deduction: 100 AZN
```

---

# 16. Tax Calculation

Payroll must support configurable tax calculation by country and legal entity.

## Main features

- Income tax rules
- Tax brackets
- Tax exemption
- Tax relief
- Taxable earnings
- Non-taxable earnings
- Taxable benefits
- Tax deduction limits
- Tax year-to-date calculation
- Monthly tax calculation
- Annualized tax calculation
- Tax adjustment
- Tax refund
- Tax reporting
- Tax audit

## Tax rule attributes

- Country
- Legal entity
- Tax year
- Tax period
- Tax bracket
- Rate
- Threshold
- Exemption
- Rounding rule
- Effective date
- Status

## Business logic

Tax rules must be effective-dated and country-specific. The system should not hard-code one country’s tax law into the payroll engine.

---

# 17. Social Insurance Calculation

The module must support employee and employer social insurance calculations.

## Main features

- Employee social insurance contribution
- Employer social insurance contribution
- Contribution base
- Minimum contribution
- Maximum contribution
- Exempt elements
- Contribution rate
- Legal entity registration
- Reporting file
- Social insurance reconciliation

## Business logic

Social insurance may be calculated on:

- Basic salary only
- Gross salary
- Taxable salary
- Selected payroll elements
- Capped salary base
- Country-specific statutory base

---

# 18. Pension Contribution

Pension contributions can be statutory or voluntary.

## Main features

- Employee pension contribution
- Employer pension contribution
- Voluntary pension contribution
- Pension provider
- Pension plan
- Contribution percentage
- Contribution amount
- Contribution cap
- Pension reporting
- Pension payment file
- Pension reconciliation

## Business logic

Pension rules may differ by:

- Country
- Legal entity
- Employee age
- Employment type
- Pension plan
- Salary threshold
- Employee opt-in/opt-out status

---

# 19. Benefits Deductions

Benefits deductions should integrate with Benefits Administration.

## Main features

- Benefit plan deduction
- Health insurance deduction
- Life insurance deduction
- Meal benefit deduction
- Transport benefit deduction
- Dependent coverage deduction
- Employee contribution
- Employer contribution
- Effective dates
- Payroll deduction frequency
- Arrears handling
- Benefit deduction history

## Business logic

Payroll should deduct only benefits that are:

- Active for the payroll period
- Assigned to the employee
- Approved
- Eligible by plan rules
- Not already deducted

---

# 20. Loan Deductions

Employee loans should be deducted through payroll.

## Main features

- Loan balance import
- Loan installment deduction
- Loan deduction schedule
- Early settlement
- Missed installment
- Partial deduction
- Loan deduction priority
- Loan deduction suspension
- Final settlement recovery
- Loan deduction history

## Business logic

Payroll should check:

- Active loan
- Installment due date
- Remaining balance
- Deduction limit
- Final settlement rules

Example:

```text
Loan balance: 1,000 AZN
Monthly installment: 100 AZN
Payroll deduction this month: 100 AZN
Remaining balance: 900 AZN
```

---

# 21. Advance Salary Deductions

Salary advances must be recovered through payroll.

## Main features

- Advance request integration
- Approved advance amount
- Recovery schedule
- Full deduction
- Installment deduction
- Partial deduction
- Advance balance
- Payroll recovery
- Final settlement recovery
- Advance history

## Business logic

Advance deduction should follow approved recovery rules.

Example:

```text
Advance paid: 300 AZN
Recovery: 3 installments
Monthly deduction: 100 AZN
```

---

# 22. Payroll Inputs

Payroll inputs are variable data entered for a payroll period.

## Main features

- Manual payroll input
- Bulk payroll input
- Recurring payroll input
- One-time payroll input
- Input by employee
- Input by department
- Input by payroll group
- Excel import
- API import
- Input approval
- Input validation
- Input history

## Payroll input examples

- Bonus amount
- Commission amount
- One-time allowance
- One-time deduction
- Adjustment amount
- Overtime correction
- Tax adjustment
- Loan correction
- Expense reimbursement

## Business logic

Inputs should be locked after payroll approval unless reopened with approval.

---

# 23. Payroll Run Management

Payroll run calculates salary for selected employees and period.

## Main features

- Create payroll run
- Select payroll group
- Select payroll period
- Select legal entity
- Select employees
- Include/exclude employees
- Validate employee eligibility
- Collect payroll inputs
- Calculate gross pay
- Calculate deductions
- Calculate employer cost
- Calculate net pay
- Generate errors/warnings
- Recalculate payroll
- Compare with previous run
- Submit for approval
- Approve payroll run
- Lock payroll run
- Close payroll run

## Payroll run statuses

- Draft
- Validating
- Validation failed
- Ready to calculate
- Calculating
- Calculated
- Calculation errors
- Under review
- Pending approval
- Approved
- Locked
- Paid
- Posted to GL
- Closed
- Cancelled

## Business logic

Payroll run should produce employee-level calculation details, not just final net salary.

Each payslip should show calculation breakdown.

---

# 24. Payroll Calculation Engine

The calculation engine is the core of Payroll.

## Main features

- Formula engine
- Rule engine
- Element calculation sequence
- Gross-to-net calculation
- Proration engine
- Retroactive calculation
- Tax calculation
- Social insurance calculation
- Pension calculation
- Rounding rules
- Currency conversion
- Arrears handling
- Error handling
- Calculation trace

## Calculation sequence example

```text
1. Load employee payroll profile
2. Load salary structure
3. Load recurring earnings
4. Load period inputs
5. Import attendance/leave/overtime
6. Calculate prorated salary
7. Calculate allowances
8. Calculate overtime
9. Calculate gross pay
10. Calculate statutory deductions
11. Calculate voluntary deductions
12. Calculate loans/advances
13. Calculate net pay
14. Calculate employer contributions
15. Generate payslip
16. Generate accounting distributions
```

## Business logic

The engine should provide a calculation trace showing how each amount was calculated.

This is critical for payroll audit and troubleshooting.

---

# 25. Proration

Proration is needed when employees join, leave, change salary, or have unpaid days during a payroll period.

## Main features

- New hire proration
- Termination proration
- Salary change proration
- Unpaid leave proration
- Allowance proration
- Benefits proration
- Calendar-day proration
- Working-day proration
- Fixed-day proration
- Hourly proration

## Proration methods

- Calendar days
- Working days
- Fixed 30 days
- Actual paid days
- Hours worked
- Shift hours

## Example

```text
Monthly salary: 3,000 AZN
Payroll period: 30 days
Employee joined on day 11
Paid days: 20
Prorated salary: 3,000 × 20 / 30 = 2,000 AZN
```

---

# 26. Retroactive Payroll

Retroactive payroll handles changes after payroll was already processed.

## Main features

- Retro salary change
- Retro promotion
- Retro allowance
- Retro deduction
- Retro attendance correction
- Retro leave correction
- Retro tax adjustment
- Retro overtime adjustment
- Retro calculation period
- Difference calculation
- Retro payslip line
- Retro approval
- Retro audit

## Business logic

Retro payroll should calculate the difference between:

```text
What was paid
vs
What should have been paid
```

Example:

```text
Employee salary was paid as 2,000 AZN in May.
Salary correction effective from May: 2,200 AZN.
Retro difference: +200 AZN in next payroll.
```

---

# 27. Final Settlement Payroll

Final settlement is triggered by Offboarding.

## Main features

- Final salary calculation
- Salary up to last working day
- Leave encashment
- Notice pay
- Short notice deduction
- Severance payment
- End-of-service benefit
- Bonus eligibility
- Overtime payout
- Loan recovery
- Advance recovery
- Asset deduction
- Expense reimbursement
- Benefit closure
- Final tax/social insurance
- Final payslip
- Settlement approval
- Settlement statement

## Business logic

Final settlement should require:

- Offboarding approval
- Last working day confirmation
- Attendance finalization
- Leave balance finalization
- Clearance status
- Asset liability confirmation
- Loan/advance balance confirmation
- Finance approval

---

# 28. Payslip Generation

Payslips provide salary details to employees.

## Main features

- Generate payslip
- Payslip template
- Multi-language payslip
- Multi-currency display
- Legal entity branding
- Employee details
- Payroll period
- Earnings breakdown
- Deductions breakdown
- Employer contributions
- Net pay
- Year-to-date values
- Leave balance display
- Overtime display
- Bank/payment details
- PDF generation
- Employee self-service access
- Payslip release control
- Payslip correction history

## Payslip statuses

- Draft
- Generated
- Under review
- Approved
- Released
- Recalled
- Regenerated
- Archived

## Business logic

Payslips should not be visible to employees until payroll is approved and payslips are released.

---

# 29. Bank Payment File

Payroll must generate bank payment files for salary transfer.

## Main features

- Bank file generation
- Bank format by bank/legal entity
- Salary transfer file
- Multiple bank support
- Employee bank validation
- IBAN validation
- Account number validation
- Payment currency
- Payment date
- Payment reference
- Bank file approval
- Bank file download
- Bank file encryption, if required
- Bank file history
- Payment confirmation import

## Payment methods

- Bank transfer
- Cash payment
- Cheque
- Manual transfer
- Mobile wallet, if supported
- Split payment to multiple accounts

## Business logic

The system should validate before generating bank file:

- Payroll approved
- Employee bank account exists
- Net pay is positive
- Currency is supported
- Legal entity bank account exists
- Bank format configured

---

# 30. Payroll Accounting Entries / GL Posting

Payroll must generate accounting entries for Finance.

## Main features

- Payroll journal generation
- Salary expense posting
- Allowance expense posting
- Overtime expense posting
- Bonus expense posting
- Employer contribution posting
- Tax payable posting
- Social insurance payable posting
- Pension payable posting
- Loan recovery posting
- Advance recovery posting
- Bank payable posting
- Cost center allocation
- Department allocation
- Project allocation
- GL posting approval
- GL export
- GL posting reversal
- GL reconciliation

## Example accounting entry

```text
Dr Salary Expense              50,000
Dr Employer Social Insurance    8,000
Cr Tax Payable                  5,000
Cr Social Insurance Payable     7,000
Cr Employee Loan Receivable     1,000
Cr Bank Payable                45,000
```

## Business logic

Payroll accounting should post to correct:

- Legal entity
- Cost center
- Department
- Project
- GL account
- Currency
- Accounting period

---

# 31. Cost Center Allocation

Payroll cost must be distributed to cost centers.

## Main features

- Cost center from employee
- Cost center from position
- Cost center from department
- Cost center from project timesheet
- Multiple cost center split
- Percentage allocation
- Hours-based allocation
- Project-based allocation
- Effective-dated allocation
- Allocation history

## Business logic

Example:

```text
Employee salary: 2,000 AZN
Cost allocation:
- Project A: 60% = 1,200 AZN
- Department Operations: 40% = 800 AZN
```

Payroll should generate accounting entries according to allocation.

---

# 32. Payroll Approvals

Payroll approval protects the company from incorrect salary payments.

## Main features

- Payroll run approval
- Payroll input approval
- Payroll exception approval
- Payslip approval
- Bank file approval
- GL posting approval
- Final settlement approval
- Retro payroll approval
- Multi-level approval
- Conditional approval
- Approval comments
- Rejection reason
- Return for correction
- Delegation
- Escalation

## Example workflow

```text
Payroll Officer
→ Payroll Manager
→ HR Manager
→ Finance Manager
→ CFO / Executive, if required
```

## Approval conditions

Workflow may depend on:

- Legal entity
- Payroll group
- Payroll amount
- Variance from previous payroll
- Number of employees
- Retro amount
- Final settlement amount
- Bonus amount
- Exception count

---

# 33. Payroll Exceptions and Error Handling

Payroll should detect problems before approval.

## Exception examples

- Employee missing bank details
- Employee missing salary structure
- Employee missing tax profile
- Employee not assigned to payroll group
- Negative net salary
- Attendance not approved
- Leave balance not finalized
- Overtime not approved
- Loan balance mismatch
- Duplicate payroll result
- Currency missing
- GL account missing
- Cost center missing
- Tax rule missing
- Social insurance rule missing
- Payroll input not approved

## Main features

- Exception dashboard
- Exception severity
- Exception owner
- Exception status
- Payroll-blocking flag
- Resolution comments
- Recalculate after resolution
- Exception audit

---

# 34. Payroll Reconciliation

Payroll reconciliation validates payroll correctness before and after payment.

## Main features

- Payroll register
- Gross-to-net reconciliation
- Previous month comparison
- Headcount reconciliation
- Bank file reconciliation
- GL reconciliation
- Tax reconciliation
- Social insurance reconciliation
- Loan deduction reconciliation
- Benefits deduction reconciliation
- Attendance-to-payroll reconciliation
- Leave-to-payroll reconciliation

## Reconciliation examples

- Total net salary equals bank file total
- Payroll journal total equals payroll register total
- Tax payable equals tax report total
- Loan deductions equal loan module recovery
- Overtime paid equals approved overtime
- Employees paid equals eligible employee count

---

# 35. Payroll Audit

Payroll audit is mandatory due to financial sensitivity.

## Main features

- Payroll calculation audit
- Payroll input audit
- Salary change audit
- Bank detail change audit
- Payroll approval audit
- Payroll run audit
- Payslip release audit
- Bank file download audit
- GL posting audit
- Retro payroll audit
- Final settlement audit
- User action audit

## Audit fields

- Tenant
- Legal entity
- Employee
- Payroll period
- Action
- Old value
- New value
- Changed by
- Changed date/time
- Approval reference
- Reason
- IP/device, optional

## Business logic

Payroll audit logs should not be editable by normal users or tenant admins.

---

# 36. Multi-Country Payroll

The payroll module should support different country rules under the same tenant.

## Main features

- Country-specific tax rules
- Country-specific social insurance rules
- Country-specific pension rules
- Country-specific payslip templates
- Country-specific statutory reports
- Country-specific payroll calendars
- Country-specific currencies
- Country-specific bank formats
- Country-specific termination rules
- Country-specific minimum wage rules

## Business logic

A tenant can operate in multiple countries.

Example:

```text
Tenant: ABC Group
Legal Entity 1: Azerbaijan
Legal Entity 2: Turkey
Legal Entity 3: UAE
```

Each legal entity must calculate payroll according to its own country configuration.

---

# 37. Multi-Currency Payroll

Multi-currency support is required for multinational companies.

## Main features

- Salary currency
- Payroll currency
- Payment currency
- Reporting currency
- Exchange rate table
- Exchange rate source
- Exchange rate date
- Currency conversion
- Payslip currency display
- GL posting currency
- Bank payment currency
- Multi-currency rounding

## Business logic

Example:

```text
Employee salary currency: USD
Legal entity payroll currency: AZN
Exchange rate: 1 USD = 1.70 AZN
Payroll calculation converts according to configured rate rule.
```

The system should store both original currency and converted currency.

---

# 38. Payroll Security and Access Control

Payroll contains highly sensitive financial and personal data.

## Main features

- Tenant-level isolation
- Legal entity-based access
- Payroll group-based access
- Department-based access
- Role-based access
- Field-level security
- Salary visibility restriction
- Bank detail restriction
- Payslip access restriction
- Payroll approval permission
- Payroll processing permission
- Bank file download permission
- GL posting permission
- Audit access permission
- Export restriction
- Segregation of duties

## Example roles

| Role | Access |
|---|---|
| Payroll Officer | Process assigned payroll groups |
| Payroll Manager | Review and approve payroll |
| HR Manager | Review payroll-impacting employee data |
| Finance Manager | Review payroll cost and GL postings |
| CFO | Final approval for high-value payroll |
| Employee | View own payslip only |
| Auditor | Read-only payroll audit access |
| Tenant Admin | Configure payroll but not necessarily view salary details |
| System Admin | Technical access without salary data visibility where possible |

## Segregation of duties examples

- User who changes bank account should not approve payroll payment
- User who creates payroll input should not be sole payroll approver
- User who generates bank file should not be the only approver
- System admin should not have default access to payroll amounts

---

# 39. Employee Self-Service Payroll

Employees should be able to view payroll-related information securely.

## Employee can view

- Payslip
- Payroll history
- Salary summary, if allowed
- Tax summary, if allowed
- Year-to-date earnings
- Year-to-date deductions
- Bank payment details, masked
- Benefits deductions
- Loan deductions
- Leave balance shown on payslip
- Final settlement statement, if applicable

## Employee can request

- Bank account change
- Salary certificate
- Payslip download
- Tax certificate, where applicable
- Payroll inquiry

## Security logic

Employees should only see their own payroll data.

Payslip downloads should be logged.

---

# 40. Manager Payroll Visibility

Manager access to payroll must be configurable.

## Possible manager access

- No salary visibility
- Team payroll cost summary only
- Team salary visibility, restricted
- Overtime cost only
- Bonus approval visibility
- Allowance approval visibility
- Payroll budget vs actual for department

## Business logic

In many companies, managers should not see full payroll details unless explicitly permitted.

---

# 41. Payroll Reports

## Standard reports

- Payroll register
- Payroll summary
- Employee payslip report
- Gross-to-net report
- Earnings report
- Deductions report
- Allowance report
- Bonus report
- Overtime payment report
- Tax report
- Social insurance report
- Pension report
- Benefits deductions report
- Loan deductions report
- Advance deductions report
- Leave without pay report
- Absence deduction report
- Final settlement report
- Bank payment report
- Payroll journal report
- Cost center payroll report
- Department payroll report
- Legal entity payroll report
- Payroll variance report
- Retro payroll report
- Payroll reconciliation report
- Payroll audit report

## Analytics KPIs

- Total payroll cost
- Gross payroll trend
- Net payroll trend
- Employer contribution trend
- Overtime cost trend
- Payroll cost per employee
- Payroll cost by department
- Payroll cost by legal entity
- Payroll variance vs previous period
- Payroll variance vs budget
- Average salary
- Bonus payout ratio
- Absence deduction trend
- Payroll exception count
- Payroll processing duration

---

# 42. Payroll Notifications

## Notifications to payroll team

- Payroll period opened
- Payroll input deadline approaching
- Attendance not approved
- Payroll calculation completed
- Payroll exceptions detected
- Payroll approval required
- Payroll approved
- Bank file ready
- GL posting completed
- Payroll period closed

## Notifications to managers

- Attendance approval required before payroll
- Overtime approval pending
- Payroll-impacting correction pending
- Bonus approval pending

## Notifications to employees

- Payslip released
- Bank payment processed
- Payroll inquiry response
- Bank detail change approved/rejected
- Final settlement statement available

## Notifications to finance

- Payroll approval required
- Bank file ready
- Payroll journal ready
- Payroll variance exceeds threshold
- Payroll reconciliation pending

---

# 43. Integration With HCM and ERP Modules

## Employee Management integration

Payroll uses:

- Employee ID
- Employment status
- Hire date
- Termination date
- Legal entity
- Department
- Position
- Grade
- Manager
- Contract type
- Employment type
- Bank details
- Tax/social insurance details

## Organization Management integration

Payroll uses:

- Legal entities
- Business units
- Departments
- Branches
- Locations
- Cost centers
- Work calendars
- Holiday calendars

## Position Management integration

Payroll uses:

- Position budget
- Grade
- Salary range
- Cost center
- Allowance eligibility
- Overtime eligibility
- Funding source

## Time and Attendance integration

Payroll uses:

- Approved worked days
- Approved overtime
- Absence days
- Late deductions
- Early leave deductions
- Shift allowances
- Night hours
- Holiday work hours

## Leave Management integration

Payroll uses:

- Paid leave
- Unpaid leave
- Leave encashment
- Leave balance
- Sick leave payroll impact
- Maternity/paternity payroll impact
- Leave without pay

## Benefits integration

Payroll uses:

- Benefit enrollment
- Employee contribution
- Employer contribution
- Deduction frequency
- Dependent coverage
- Benefit arrears

## Loans and Advances integration

Payroll uses:

- Loan installment
- Salary advance recovery
- Outstanding balance
- Deduction schedule
- Final settlement recovery

## General Ledger integration

Payroll sends:

- Payroll journal
- Salary expense
- Employer contribution
- Tax payable
- Social insurance payable
- Bank payable
- Employee receivable
- Cost center distribution

## Bank integration

Payroll sends:

- Salary bank file
- Payment amount
- Employee bank details
- Legal entity bank account
- Payment date
- Payment reference

## Tax reporting integration

Payroll sends:

- Taxable income
- Tax deducted
- Employee tax ID
- Employer tax ID
- Period totals
- Statutory report files

---

# 44. Recommended Payroll Screens / Menu

```text
Payroll
│
├── Dashboard
├── Payroll Periods
├── Payroll Runs
├── Employee Payroll Profiles
├── Salary Structures
├── Payroll Elements
├── Payroll Inputs
├── Earnings
├── Deductions
├── Allowances
├── Bonuses & Incentives
├── Overtime Payments
├── Tax Calculation
├── Social Insurance
├── Pension Contributions
├── Benefits Deductions
├── Loan Deductions
├── Advance Deductions
├── Retroactive Payroll
├── Final Settlement
├── Payslips
├── Bank Payment Files
├── Payroll Accounting / GL Posting
├── Payroll Approvals
├── Payroll Reconciliation
├── Payroll Audit
├── Reports
└── Settings
```

---

# 45. Recommended Payroll Run Tabs

The payroll run detail screen should include:

1. Overview
2. Payroll Period
3. Included Employees
4. Payroll Inputs
5. Attendance / Leave Inputs
6. Earnings
7. Deductions
8. Employer Contributions
9. Gross-to-Net Results
10. Exceptions
11. Payslips
12. Bank Payment
13. GL Posting
14. Approvals
15. Reconciliation
16. Activity History
17. Audit Trail

---

# 46. Recommended Employee Payroll Profile Tabs

Employee payroll profile should include:

1. Overview
2. Payroll Assignment
3. Salary Structure
4. Earnings
5. Allowances
6. Deductions
7. Tax Profile
8. Social Insurance
9. Pension
10. Benefits Deductions
11. Loans / Advances
12. Bank Payment
13. Cost Allocation
14. Payroll History
15. Payslips
16. Audit Trail

---

# 47. Recommended Payroll Run List Columns

- Payroll run number
- Tenant
- Legal entity
- Payroll group
- Payroll period
- Period start date
- Period end date
- Payment date
- Employee count
- Gross pay
- Total deductions
- Net pay
- Employer contributions
- Payroll status
- Approval status
- Bank file status
- GL posting status
- Created by
- Created date
- Actions

Useful actions:

- View
- Validate
- Calculate
- Recalculate
- Submit for approval
- Approve
- Reject
- Generate payslips
- Generate bank file
- Generate GL posting
- Close period
- View audit

---

# 48. Recommended Main Data Entities

For technical design, do not store everything in one table.

Recommended entities:

- TenantPayrollConfig
- PayrollCountryConfig
- PayrollLegalEntityConfig
- PayrollGroup
- PayrollCalendar
- PayrollPeriod
- EmployeePayrollProfile
- SalaryStructure
- SalaryStructureElement
- PayrollElement
- PayrollElementRule
- PayrollInput
- PayrollRun
- PayrollRunEmployee
- PayrollResult
- PayrollResultLine
- PayrollException
- PayrollApproval
- Payslip
- BankPaymentFile
- BankPaymentLine
- PayrollJournal
- PayrollJournalLine
- TaxRule
- TaxCalculationResult
- SocialInsuranceRule
- SocialInsuranceResult
- PensionRule
- PensionResult
- BenefitPayrollDeduction
- LoanPayrollDeduction
- AdvancePayrollDeduction
- RetroPayrollRecord
- FinalSettlement
- PayrollReconciliation
- PayrollAuditLog

All payroll entities must support tenant isolation.

---

# 49. Important Validation Rules

The system should validate:

- Tenant must be assigned to every payroll record
- User cannot access payroll outside authorized tenant
- Employee must belong to same tenant as payroll run
- Employee must have payroll profile
- Employee must be active or final-settlement eligible
- Payroll group must be active
- Payroll period must be open before calculation
- Employee bank details must exist for bank transfer
- Salary structure must be active and effective for period
- Payroll elements must be valid for period
- Tax rules must exist for country/legal entity
- Social insurance rules must exist where applicable
- Pension rules must exist where applicable
- Attendance must be approved/locked if used
- Leave without pay must be finalized if used
- Overtime must be approved before payment
- Payroll input must be approved before calculation if workflow requires it
- Net pay should not be negative unless authorized
- Bank file cannot be generated before payroll approval
- Payslips cannot be released before payroll approval
- GL posting cannot be generated without GL mapping
- Payroll period cannot close before reconciliation
- Payroll changes after closure require retro payroll or approved reopening
- Final settlement requires offboarding approval and last working day

---

# 50. Common Mistakes to Avoid

## 1. Hard-coding country-specific payroll rules

Payroll rules change by country and over time. Use configurable effective-dated rules.

## 2. Weak tenant isolation

Payroll data must never leak between tenants. Tenant scoping must be enforced in database, API, reports, background jobs, exports, and integrations.

## 3. Sending raw attendance to payroll

Payroll must use approved and locked attendance summaries, not raw punches.

## 4. No payroll audit trail

Salary, bank, tax, and payroll calculation changes must be fully traceable.

## 5. No GL integration

Payroll without accounting integration creates manual finance workload.

## 6. No reconciliation

Bank file, payroll register, GL posting, tax reports, and deductions must reconcile.

## 7. No retro payroll

Real payroll often needs retroactive salary, attendance, tax, and leave corrections.

## 8. Weak approval workflow

Payroll should not be paid without HR/Payroll/Finance approval.

## 9. No effective dating

Salary, payroll elements, tax rules, and employee payroll profiles must be effective-dated.

## 10. System admin can see all payroll data by default

Technical administrators should not automatically have payroll amount visibility unless explicitly authorized.

---

# 51. Final Recommended Launch Scope

For your ERP, the **Payroll** module should launch as a complete enterprise-grade, multi-tenant payroll engine covering:

- Multi-tenant payroll isolation
- Payroll configuration by tenant/legal entity/country
- Employee payroll profiles
- Payroll groups
- Payroll periods
- Salary structures
- Payroll elements
- Earnings
- Deductions
- Allowances
- Bonuses and incentives
- Overtime payment
- Attendance-based deductions
- Tax calculation
- Social insurance calculation
- Pension contribution
- Benefits deductions
- Loan deductions
- Advance salary deductions
- Payroll inputs
- Payroll run management
- Payroll calculation engine
- Proration
- Retroactive payroll
- Final settlement
- Payslip generation
- Bank payment file generation
- Payroll accounting entries
- Cost center allocation
- Payroll approvals
- Payroll exceptions
- Payroll reconciliation
- Payroll audit
- Multi-country payroll
- Multi-currency payroll
- Employee self-service payroll
- Manager payroll visibility controls
- Reports and analytics
- Notifications
- Integration with Employee Management, Organization Management, Position Management, Attendance, Leave, Benefits, Loans, General Ledger, Banks, and Tax Reporting
- Full security and access control
- Full audit trail

The most important design rule:

**Payroll must be tenant-isolated, effective-dated, approval-controlled, audit-ready, and integrated with attendance, leave, benefits, loans, banks, tax reporting, and General Ledger. Never design payroll as a simple salary table. Design it as a configurable gross-to-net calculation engine with strong controls.**
