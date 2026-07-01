---
feature: HCM_10_Compensation_Management_Multi_Tenant_PRD
module: compensation
payroll_impact: true
status: backlog
depends_on: [HCM_09_Payroll_Multi_Tenant_PRD]
---

# 10. Compensation Management Module in HCM — Multi-Tenant Full Enterprise PRD

## 1. Module Overview

The **Compensation Management** module manages salary structures, salary grades, salary bands, pay ranges, merit increases, bonuses, incentives, allowances, salary review cycles, compensation budgets, approval workflows, compensation history, market salary comparisons, and total compensation statements.

In a full ERP/HCM system, Compensation Management is the planning and governance layer between **Employee Management**, **Position Management**, **Payroll**, **Finance**, and **Performance Management**.

It should not be treated as payroll calculation itself. Payroll calculates and pays salaries. Compensation Management defines, plans, approves, governs, and tracks compensation decisions.

This module is commonly represented in enterprise HCM suites such as:

- SAP SuccessFactors Compensation
- Oracle Workforce Compensation
- Workday Compensation
- Dayforce Compensation
- UKG Compensation-related capabilities
- ADP Compensation-related capabilities
- Microsoft Dynamics 365 Human Resources compensation features

For your ERP, this module must support **multi-tenancy**, meaning each tenant/company must have isolated compensation policies, salary structures, currencies, approval workflows, budgets, reports, market comparison data, and security rules.

---

## 2. Core Purpose

The purpose of the Compensation Management module is to help organizations manage compensation fairly, securely, consistently, and within budget.

### Main objectives

- Define salary grades and pay ranges.
- Maintain salary bands by grade, job, location, legal entity, and currency.
- Manage salary review cycles.
- Plan merit increases.
- Plan bonuses and incentives.
- Manage allowances.
- Control compensation budgets.
- Validate salary changes against bands and budgets.
- Manage promotion salary adjustments.
- Support compensation approval workflows.
- Maintain compensation history.
- Generate total compensation statements.
- Compare employee pay against internal and market benchmarks.
- Integrate approved compensation changes with Payroll.
- Integrate compensation budgets with Finance.
- Provide audit, security, and compliance controls.

---

## 3. Multi-Tenant Design Principles

Because the ERP is SaaS and multi-tenant, Compensation Management must be designed with strict tenant isolation.

### 3.1 Tenant isolation

Every compensation record must belong to a tenant.

Required tenant-scoped records include:

- Salary grades
- Pay ranges
- Salary bands
- Compensation plans
- Review cycles
- Merit matrices
- Bonus plans
- Incentive plans
- Allowance policies
- Compensation budgets
- Compensation worksheets
- Approval workflows
- Market salary data
- Compensation history
- Total compensation statements
- Reports and dashboards
- Audit logs

### 3.2 Required tenant field

Every main table should include:

```text
TenantId
```

For large tenants with multiple legal entities, most compensation records should also support:

```text
LegalEntityId
BusinessUnitId
DepartmentId
LocationId
CurrencyId
```

### 3.3 Data isolation rules

The system must ensure:

- Tenant A cannot see Tenant B's salary grades.
- Tenant A cannot use Tenant B's salary bands.
- Tenant A cannot access Tenant B's employee compensation history.
- Tenant A cannot access Tenant B's market comparison data.
- Tenant A cannot approve compensation changes for Tenant B.
- Tenant reports must only show data from the authenticated tenant.
- APIs must validate TenantId on every compensation request.
- Background jobs must process data tenant-by-tenant.
- Audit logs must be tenant-scoped.

### 3.4 Tenant-specific configuration

Each tenant should be able to configure:

- Salary grade structure
- Grade naming convention
- Salary bands
- Pay ranges
- Currency rules
- Merit increase rules
- Bonus rules
- Incentive rules
- Allowance policies
- Compensation review cycles
- Budget allocation rules
- Approval workflows
- Payroll integration rules
- Total compensation statement format
- Security and visibility rules

### 3.5 Cross-tenant platform administration

Platform super-admins may need support-level access, but this must be controlled.

Rules:

- No unrestricted salary data browsing by platform users.
- Support access must be explicitly granted or break-glass controlled.
- Every access to tenant compensation data must be audited.
- Sensitive fields such as salary, bonus, incentives, and bank-related pay outputs should be masked where possible.
- Tenant admin must be able to see who accessed compensation data.

---

## 4. Compensation Management Scope

The module should include the following functional areas:

1. Compensation dashboard
2. Salary grade management
3. Salary band and pay range management
4. Employee compensation profile
5. Salary structure management
6. Allowance management
7. Merit increase planning
8. Salary review cycle management
9. Bonus planning
10. Incentive planning
11. Commission planning, if sales roles are supported
12. Promotion salary adjustment
13. Market salary comparison
14. Compensation budget control
15. Compensation worksheets
16. Compensation approval workflow
17. Compensation history
18. Total compensation statement
19. Compensation analytics and reports
20. Payroll integration
21. Finance/budget integration
22. Security and access control
23. Audit trail
24. Multi-currency and multi-country support
25. Tenant-specific configuration

---

## 5. Compensation Dashboard

The dashboard should provide visibility to HR, compensation managers, finance, executives, and line managers.

### HR / Compensation dashboard widgets

- Total employees with compensation records
- Employees without salary grade
- Employees outside salary band
- Employees below salary minimum
- Employees above salary maximum
- Employees pending salary review
- Active salary review cycles
- Pending compensation approvals
- Merit increase budget used
- Bonus budget used
- Incentive budget used
- Compensation changes this month
- Promotions with salary adjustments
- Salary exceptions pending review
- Compensation history changes

### Manager dashboard widgets

- Team compensation review status
- Team salary review worksheet
- Team merit recommendations
- Team bonus recommendations
- Team employees outside band
- Team approval tasks
- Team compensation budget remaining

### Finance dashboard widgets

- Compensation budget allocated
- Compensation budget consumed
- Forecasted payroll impact
- Merit budget variance
- Bonus budget variance
- Incentive budget variance
- Department compensation cost
- Legal entity compensation cost
- Approved salary changes pending payroll

### Executive dashboard widgets

- Total compensation cost
- Compensation cost by legal entity
- Compensation cost by department
- Average salary by grade
- Pay range penetration
- Compa-ratio analysis
- Merit increase distribution
- Bonus distribution
- Gender pay analysis, where legally allowed
- Market competitiveness analysis
- Compensation budget utilization

---

## 6. Salary Grade Management

Salary grades define employee levels and compensation ranges.

### Main features

- Create salary grade
- Grade code
- Grade name
- Grade level
- Grade group
- Grade description
- Legal entity applicability
- Job applicability
- Position applicability
- Department applicability
- Location applicability
- Minimum salary
- Midpoint salary
- Maximum salary
- Currency
- Effective start date
- Effective end date
- Active/inactive status
- Approval workflow
- Grade history

### Grade examples

```text
G1 — Junior Staff
G2 — Staff
G3 — Senior Staff
M1 — Supervisor
M2 — Manager
D1 — Director
E1 — Executive
```

### Multi-tenant behavior

Each tenant can define its own grade structure.

Examples:

- Tenant A may use G1–G10.
- Tenant B may use Junior/Mid/Senior/Lead.
- Tenant C may use public sector ranks.
- Tenant D may use restaurant/retail role levels.

The system must not force all tenants into one global grade structure.

### Business logic

Salary grade affects:

- Salary range validation
- Promotion rules
- Bonus eligibility
- Benefits eligibility
- Allowance eligibility
- Approval authority
- Compensation reporting
- Payroll classification

---

## 7. Salary Band and Pay Range Management

Salary bands define acceptable salary ranges for grades, jobs, positions, or locations.

### Main features

- Create salary band
- Band code
- Band name
- Grade
- Job
- Position
- Location
- Legal entity
- Currency
- Minimum salary
- Midpoint salary
- Maximum salary
- Market reference value
- Effective date
- Status
- Approval workflow
- Version history

### Pay range types

- Grade-based pay range
- Job-based pay range
- Position-based pay range
- Location-based pay range
- Legal entity-based pay range
- Country-based pay range
- Collective agreement-based range
- Public sector statutory range

### Example

```text
Grade: G5
Currency: AZN
Minimum: 1,500
Midpoint: 2,000
Maximum: 2,500
```

### Business logic

When HR proposes salary:

```text
Employee salary: 2,700 AZN
Grade range: 1,500–2,500 AZN
System result: Salary exception approval required.
```

Salary ranges should be effective-dated. Historical compensation reports must use the band that was valid at the time.

---

## 8. Employee Compensation Profile

Each employee should have a compensation profile linked to employee master data.

### Main features

- Employee current salary
- Salary currency
- Salary grade
- Salary band
- Pay frequency
- Payroll group
- Compensation plan
- Allowance eligibility
- Bonus eligibility
- Incentive eligibility
- Last salary change date
- Last increase percentage
- Current compa-ratio
- Range penetration
- Compensation history
- Pending compensation changes
- Payroll integration status

### Recommended compensation profile tabs

1. Current Compensation
2. Salary Grade and Band
3. Allowances
4. Bonuses
5. Incentives
6. Salary Review History
7. Promotion Adjustments
8. Market Comparison
9. Total Compensation
10. Payroll Sync History
11. Audit Trail

### Business logic

The employee compensation profile should not replace payroll. It should store approved compensation terms and send approved changes to Payroll.

Payroll will then calculate actual payslips based on salary, attendance, leave, deductions, tax, benefits, loans, and other payroll elements.

---

## 9. Salary Structure Management

Salary structure defines how employee pay is organized.

### Main features

- Basic salary
- Fixed allowances
- Variable allowances
- Benefits value
- Bonus eligibility
- Incentive eligibility
- Commission eligibility
- Employer contributions
- Total cash compensation
- Total compensation value
- Salary component rules
- Effective-dated salary structure
- Approval workflow

### Salary components

Examples:

- Basic salary
- Housing allowance
- Transport allowance
- Meal allowance
- Phone allowance
- Position allowance
- Responsibility allowance
- Hazard allowance
- Shift allowance
- Location allowance
- Performance bonus
- Sales incentive
- Commission
- Employer pension contribution
- Employer insurance contribution

### Business logic

Salary structure should support:

- Fixed monthly salaries
- Hourly salaries
- Daily wage structures
- Commission-based structures
- Mixed salary plus bonus structures
- Multi-currency structures
- Country-specific structures

---

## 10. Allowance Management

Allowances are additional pay components provided based on grade, position, location, work conditions, or policy.

### Main features

- Allowance type
- Allowance code
- Allowance name
- Fixed amount
- Percentage-based amount
- Formula-based amount
- Eligibility rules
- Grade applicability
- Position applicability
- Department applicability
- Location applicability
- Effective dates
- Taxability flag
- Payroll element mapping
- Approval workflow
- Allowance history

### Allowance examples

- Housing allowance
- Transport allowance
- Meal allowance
- Phone allowance
- Internet allowance
- Remote work allowance
- Shift allowance
- Night shift allowance
- Hazard allowance
- Travel allowance
- Uniform allowance
- Store manager allowance
- Field allowance
- Responsibility allowance

### Business logic

Allowances can be:

- Automatically assigned based on position or grade
- Manually assigned by HR
- Requested by manager
- Approved through workflow
- End-dated automatically after position change
- Sent to payroll as recurring element

Example:

```text
Position: Delivery Driver
Eligible allowances:
- Fuel allowance
- Phone allowance
- Field allowance
```

---

## 11. Merit Increase Planning

Merit increases are salary increases based on performance, budget, and compensation policy.

### Main features

- Merit cycle setup
- Merit budget
- Merit eligibility
- Performance rating integration
- Merit matrix
- Manager recommendation
- HR adjustment
- Budget validation
- Salary band validation
- Approval workflow
- Final approved increase
- Payroll effective date

### Merit increase inputs

- Current salary
- Salary grade
- Salary range
- Compa-ratio
- Performance rating
- Potential rating
- Tenure
- Promotion status
- Budget allocation
- Manager recommendation
- HR recommendation

### Merit matrix example

| Performance Rating | Low Range Position | Mid Range Position | High Range Position |
|---|---:|---:|---:|
| Excellent | 8% | 6% | 4% |
| Good | 5% | 4% | 2% |
| Meets Expectations | 3% | 2% | 1% |
| Below Expectations | 0% | 0% | 0% |

### Business logic

The system should recommend merit increase based on configured matrix, but allow authorized adjustment within budget.

Example:

```text
Employee salary: 1,800 AZN
Grade midpoint: 2,000 AZN
Compa-ratio: 90%
Performance: Excellent
Recommended merit: 6%
Manager proposed: 8%
System action: require budget and approval validation.
```

---

## 12. Salary Review Cycle Management

Salary review cycles control periodic salary changes.

### Main features

- Create review cycle
- Cycle name
- Cycle year
- Review period
- Eligible employees
- Excluded employees
- Budget allocation
- Manager worksheets
- HR review
- Finance review
- Approval workflow
- Effective date
- Payroll transfer
- Cycle status
- Cycle audit trail

### Review cycle statuses

- Draft
- Configured
- Open
- Manager review
- HR review
- Finance review
- Approval pending
- Approved
- Released to payroll
- Closed
- Cancelled

### Eligibility criteria

Employees can be included/excluded based on:

- Legal entity
- Department
- Grade
- Job
- Position
- Employment type
- Hire date
- Probation status
- Performance rating
- Disciplinary status
- Contract type
- Payroll group

### Business logic

Example:

```text
Salary review cycle: Annual Review 2026
Eligible employees: Active employees hired before 1 October 2025
Excluded: Employees on probation, terminated employees, contractors
Effective date: 1 January 2027
```

---

## 13. Compensation Worksheets

Worksheets are used by managers and HR to plan salary changes.

### Main features

- Manager worksheet
- HR worksheet
- Finance worksheet
- Employee list
- Current salary
- Current grade
- Current compa-ratio
- Performance rating
- Recommended increase
- Proposed increase
- New salary
- Budget impact
- Bonus proposal
- Incentive proposal
- Comments
- Approval status

### Worksheet capabilities

- Filter by department
- Filter by manager
- Filter by grade
- Bulk recommendation
- Inline editing
- Budget remaining display
- Out-of-range warning
- Exception justification
- Submit for approval
- Return for correction
- Export, permission-controlled

### Business logic

Managers should see only employees within their permitted hierarchy.

HR and finance visibility must be controlled by legal entity, department, and role.

---

## 14. Bonus Planning

Bonus planning manages annual, quarterly, project, discretionary, or performance-related bonuses.

### Main features

- Bonus plan setup
- Bonus eligibility
- Bonus target amount
- Bonus target percentage
- Performance multiplier
- Company performance factor
- Department factor
- Individual factor
- Bonus pool
- Manager recommendation
- HR adjustment
- Approval workflow
- Payroll effective date
- Bonus history

### Bonus types

- Annual performance bonus
- Quarterly bonus
- Project completion bonus
- Discretionary bonus
- Retention bonus
- Signing bonus
- Referral bonus
- Sales bonus
- Holiday bonus
- Profit-sharing bonus

### Business logic

Bonus calculation can be formula-based.

Example:

```text
Bonus = Base salary × target bonus % × performance multiplier
```

Example calculation:

```text
Base salary: 2,000 AZN
Target bonus: 10%
Performance multiplier: 120%
Bonus = 2,000 × 10% × 120% = 240 AZN
```

---

## 15. Incentive Planning

Incentive planning manages variable pay linked to measurable results.

### Main features

- Incentive plan setup
- Incentive eligibility
- Target incentive
- Achievement measure
- KPI linkage
- Sales target linkage
- Production target linkage
- Service target linkage
- Incentive formula
- Payout curve
- Cap and floor
- Approval workflow
- Payroll transfer

### Incentive examples

- Sales incentive
- Collection incentive
- Production incentive
- Delivery incentive
- Customer service incentive
- Store performance incentive
- Restaurant service incentive
- Warehouse productivity incentive

### Business logic

The system should support threshold, target, and maximum payout logic.

Example:

```text
Achievement below 80% = no payout
Achievement 100% = target payout
Achievement 120% = maximum payout
```

---

## 16. Commission Management

For sales-driven tenants, commission may be part of compensation.

### Main features

- Commission plan
- Eligible employees
- Sales target
- Commission rate
- Tiered commission
- Product/category commission
- Customer commission
- Collection-based commission
- Margin-based commission
- Commission adjustments
- Commission approval
- Payroll transfer

### Commission examples

- 2% of sales revenue
- 5% of gross margin
- Tiered sales commission
- Commission only after customer payment
- Commission by product category
- Commission by sales territory

### Integration points

Commission may need integration with:

- CRM
- Sales orders
- Invoices
- POS sales
- Accounts receivable
- Collections
- Finance
- Payroll

---

## 17. Promotion Salary Adjustment

Promotion often changes grade, salary, allowances, bonus eligibility, and benefits.

### Main features

- Promotion salary proposal
- Old grade
- New grade
- Old salary
- New salary
- Promotion increase percentage
- Salary range validation
- New allowance eligibility
- Bonus eligibility update
- Approval workflow
- Effective date
- Payroll transfer
- Compensation history update

### Business logic

Example:

```text
Employee promoted from G4 to G5
Old salary: 1,700 AZN
New G5 range: 1,800–2,500 AZN
Proposed salary: 2,000 AZN
System validates range and budget.
```

Promotion salary adjustment should integrate with Employee Lifecycle Management and Position Management.

---

## 18. Market Salary Comparison

Market comparison helps determine whether pay is competitive.

### Main features

- Market salary data upload
- Market survey provider
- Job matching
- Grade matching
- Location matching
- Percentile comparison
- Market median
- Market minimum/maximum
- Internal salary comparison
- Compa-ratio
- Market ratio
- Pay competitiveness report

### Market data fields

- Survey provider
- Survey year
- Country
- City/location
- Job family
- Job title
- Grade
- Percentile 25
- Percentile 50
- Percentile 75
- Percentile 90
- Currency
- Effective date

### Business logic

Example:

```text
Employee salary: 2,000 AZN
Market median: 2,300 AZN
Market ratio: 87%
```

Market comparison data must be tenant-scoped. One tenant's market survey data must not be visible to another tenant.

---

## 19. Compensation Budget Control

Budget control ensures compensation decisions stay within approved financial limits.

### Main features

- Compensation budget setup
- Budget by legal entity
- Budget by department
- Budget by manager
- Budget by grade
- Merit budget
- Bonus budget
- Incentive budget
- Allowance budget
- Promotion budget
- Budget allocation
- Budget consumption
- Budget remaining
- Budget transfer
- Budget exception approval

### Budget types

- Salary increase budget
- Merit budget
- Bonus pool
- Incentive pool
- Promotion budget
- Allowance budget
- Retention budget
- Market adjustment budget

### Business logic

Example:

```text
Department merit budget: 50,000 AZN
Proposed increases: 52,000 AZN
System result: budget exceeded by 2,000 AZN; approval required or blocked.
```

Budget control should support:

- Hard stop
- Warning only
- Exception approval
- Budget transfer request

---

## 20. Compensation Approval Workflow

Compensation changes are sensitive and must be approved.

### Main features

- Multi-level approval
- Conditional approval
- Manager approval
- HR approval
- Finance approval
- Compensation manager approval
- Executive approval
- Budget exception approval
- Salary band exception approval
- Delegation
- Escalation
- Approval comments
- Rejection reason
- Return for correction
- Approval history

### Example workflow for salary increase

```text
Manager
→ Department Head
→ HR Business Partner
→ Compensation Manager
→ Finance
→ HR Director
```

### Example workflow for executive compensation

```text
HR Director
→ CEO
→ Board / Compensation Committee
→ Finance
```

### Workflow conditions

Approval route can depend on:

- Tenant
- Legal entity
- Department
- Grade
- Increase percentage
- Increase amount
- Salary band exception
- Budget exception
- Promotion status
- Employment type
- Country
- Currency

---

## 21. Compensation History

The system must maintain complete compensation history.

### Main features

Track history of:

- Basic salary changes
- Grade changes
- Band changes
- Allowance changes
- Bonus changes
- Incentive changes
- Promotion adjustments
- Market adjustments
- Currency changes
- Pay frequency changes
- Approval references
- Effective dates

### Required fields

- Employee
- Old value
- New value
- Change type
- Change reason
- Effective date
- End date
- Approved by
- Approval date
- Source transaction
- Payroll transfer status

### Business logic

Never overwrite salary history. Use effective-dated compensation records.

Example:

```text
Employee salary:
01 Jan 2025–31 Dec 2025: 1,800 AZN
01 Jan 2026–Current: 2,000 AZN
```

Historical payroll and reports must use the correct salary for the relevant period.

---

## 22. Total Compensation Statement

Total compensation statement shows the full value of employee compensation.

### Main features

- Statement generation
- Employee self-service access
- Manager visibility, if allowed
- Annual statement
- Monthly statement
- PDF export
- Multi-language template
- Currency display
- Employer contribution display
- Benefits value display
- Bonus and incentive display
- Statement approval/release

### Statement components

- Basic salary
- Fixed allowances
- Variable pay
- Bonuses
- Incentives
- Employer pension contribution
- Employer social insurance contribution
- Employer health insurance cost
- Benefits value
- Company car value
- Meal/transport value
- Training investment, optional
- Total cash compensation
- Total rewards value

### Business logic

The system should allow tenants to decide which components are shown to employees.

Example:

```text
Basic salary: 24,000 AZN/year
Allowances: 6,000 AZN/year
Bonus: 3,000 AZN/year
Employer benefits: 2,500 AZN/year
Total compensation: 35,500 AZN/year
```

---

## 23. Multi-Currency Compensation

Multi-country and international tenants may pay employees in different currencies.

### Main features

- Salary currency
- Budget currency
- Reporting currency
- Exchange rate type
- Exchange rate date
- Currency conversion
- Multi-currency budget
- Currency-specific pay ranges
- Currency-specific statements

### Business logic

Example:

```text
Employee salary currency: USD
Tenant reporting currency: AZN
Exchange rate date: compensation cycle effective date
Converted salary used for budget reporting.
```

Exchange rates should be tenant-configurable or integrated from Finance.

---

## 24. Multi-Country Compensation

The module should support tenants operating in multiple countries.

### Main features

- Country-specific grades
- Country-specific salary bands
- Country-specific allowances
- Country-specific bonus rules
- Country-specific currency
- Country-specific statutory components
- Country-specific total compensation statements
- Country-specific approval workflow

### Business logic

Example:

The same tenant may have:

- Azerbaijan salary bands in AZN
- Turkey salary bands in TRY
- UAE salary bands in AED
- Georgia salary bands in GEL

Each country may have different allowance practices, currencies, and statutory employer contribution display rules.

---

## 25. Compensation Change Reasons

Every compensation change should require a reason.

### Common change reasons

- Annual merit increase
- Promotion
- Market adjustment
- Internal equity adjustment
- Role change
- Grade change
- Transfer
- Retention adjustment
- Probation confirmation
- Contract renewal
- Bonus award
- Incentive payout
- Correction
- Legal/minimum wage adjustment
- Collective agreement adjustment

### Business logic

Change reason affects:

- Approval workflow
- Reporting
- Payroll element mapping
- Audit
- Budget category

---

## 26. Compensation Exceptions

The system should detect and manage exceptions.

### Exception types

- Salary below band minimum
- Salary above band maximum
- Increase exceeds allowed percentage
- Budget exceeded
- Missing grade
- Missing salary band
- Missing currency
- Missing approval
- Duplicate active compensation record
- Payroll transfer failed
- Salary effective date conflict
- Employee not eligible for review

### Main features

- Exception dashboard
- Exception severity
- Exception owner
- Exception workflow
- Exception approval
- Exception comments
- Exception resolution history

---

## 27. Payroll Integration

Approved compensation changes must flow to Payroll.

### Data sent to Payroll

- Employee ID
- Effective date
- Basic salary
- Salary currency
- Pay frequency
- Allowances
- Bonus payment
- Incentive payment
- Commission payment
- Recurring compensation elements
- One-time compensation elements
- Payroll element codes
- Approval reference

### Payroll transfer statuses

- Not ready
- Pending approval
- Approved
- Sent to payroll
- Accepted by payroll
- Rejected by payroll
- Payroll processed
- Cancelled

### Business logic

Only approved compensation changes should be sent to Payroll.

Example:

```text
Salary increase approved effective 1 July.
Payroll period: July.
Payroll uses new salary from 1 July.
```

If change is retroactive:

```text
Salary increase approved in August, effective from 1 July.
Payroll calculates retroactive difference.
```

---

## 28. Finance and General Ledger Integration

Compensation affects budgets and payroll accounting.

### Finance integration points

- Department compensation budget
- Cost center budget
- Salary forecast
- Bonus accrual
- Incentive accrual
- Payroll expense forecast
- Approved salary increase impact
- GL account mapping
- Cost center allocation
- Budget vs actual reporting

### Business logic

Finance should own official budget structures and GL accounts.

Compensation Management should consume:

- Cost centers
- Budget versions
- Budget limits
- GL mappings
- Exchange rates

Approved compensation changes should update workforce cost forecasts.

---

## 29. Performance Management Integration

Compensation decisions often depend on performance results.

### Integration points

- Performance rating
- Goal achievement
- Competency score
- 360 feedback score, optional
- Potential rating
- Calibration result
- Performance cycle result

### Business logic

Example:

```text
Employee performance rating: Excellent
Merit matrix recommends 6% increase.
Manager can propose within allowed range.
```

The system should support tenants that do not use performance-based compensation as well.

---

## 30. Position Management Integration

Position data should influence compensation.

### Integration points

- Position grade
- Position salary range
- Position budget
- Position allowances
- Position criticality
- Position funding status
- Position cost center
- Position location

### Business logic

When employee moves to a new position:

- Validate salary against new position range
- Update allowance eligibility
- Update bonus/incentive eligibility
- Check position budget
- Trigger salary adjustment workflow if required

---

## 31. Employee Lifecycle Integration

Compensation must integrate with employee lifecycle events.

### Lifecycle events affecting compensation

- Hire
- Rehire
- Transfer
- Promotion
- Demotion
- Probation confirmation
- Contract renewal
- Assignment change
- Location change
- Grade change
- Termination
- Retirement

### Business logic

Example:

Promotion event should be able to trigger compensation adjustment automatically.

Termination should stop future compensation changes and inform final settlement/payroll.

---

## 32. Benefits Integration

Benefits may be part of total compensation.

### Integration points

- Health insurance employer cost
- Pension contribution
- Meal benefits
- Transport benefits
- Company car
- Housing benefit
- Life insurance
- Dependent benefits

### Business logic

Total compensation statement should show employer-paid benefit values if tenant chooses to display them.

---

## 33. Security and Access Control

Compensation data is highly sensitive.

### Main security features

- Role-based access control
- Legal entity-based access
- Department-based access
- Manager hierarchy access
- Field-level security
- Salary masking
- Bonus masking
- Export restriction
- Approval permission
- Worksheet access control
- Total compensation statement visibility control
- Audit log access control
- Segregation of duties

### Example roles

| Role | Access |
|---|---|
| Employee | View own released compensation statements only |
| Manager | View compensation worksheets for direct/authorized team |
| HR Officer | Limited compensation view, depending on policy |
| Compensation Manager | Manage salary plans, bands, cycles, worksheets |
| HR Director | Approve high-level compensation changes |
| Finance | View budget and forecast, restricted salary detail if configured |
| Payroll Officer | Receive approved payroll-ready compensation changes |
| Auditor | Read-only access to audit and approved history |
| Tenant Admin | Configure tenant compensation settings, not necessarily view all salaries |
| Platform Admin | No salary access unless break-glass/support permission is granted |

### Sensitive fields

- Basic salary
- Allowances
- Bonus
- Incentive
- Commission
- Total compensation
- Market comparison
- Salary history
- Approval comments

---

## 34. Audit Trail

Every compensation action must be auditable.

### Audit should track

- Salary grade creation
- Salary band changes
- Salary structure changes
- Employee salary changes
- Allowance assignment
- Bonus proposal
- Incentive proposal
- Merit recommendation
- Budget changes
- Approval actions
- Payroll transfer
- Compensation statement release
- Security access to sensitive compensation data
- Export/download actions

### Audit fields

- TenantId
- Action
- Entity name
- Entity ID
- Old value
- New value
- Changed by
- Changed date/time
- Reason
- Approval reference
- IP/device, optional

### Business logic

Audit records must be immutable for normal users.

Even tenant admins should not be able to delete compensation audit records.

---

## 35. Reports and Analytics

### Standard reports

- Salary grade report
- Salary band report
- Employee compensation report
- Employees below band minimum
- Employees above band maximum
- Compensation history report
- Salary increase report
- Merit cycle report
- Bonus plan report
- Incentive plan report
- Allowance report
- Compensation budget report
- Budget vs proposed report
- Budget vs approved report
- Payroll transfer report
- Promotion salary adjustment report
- Market comparison report
- Compa-ratio report
- Range penetration report
- Total compensation report
- Compensation approval report
- Compensation audit report

### Analytics KPIs

- Average salary by grade
- Average salary by department
- Average salary by legal entity
- Compa-ratio distribution
- Range penetration distribution
- Percentage of employees below range
- Percentage of employees above range
- Average merit increase
- Total salary increase cost
- Bonus payout ratio
- Incentive payout ratio
- Compensation budget utilization
- Pay equity indicators, where legally allowed
- Payroll impact forecast

---

## 36. Compensation Calculations

The module should support configurable formulas.

### Common formulas

#### Compa-ratio

```text
Compa-ratio = Employee salary / Salary range midpoint × 100
```

#### Range penetration

```text
Range penetration = (Employee salary - Range minimum) / (Range maximum - Range minimum) × 100
```

#### Merit increase amount

```text
Merit amount = Current salary × Merit percentage
```

#### New salary

```text
New salary = Current salary + Merit amount + Promotion adjustment + Market adjustment
```

#### Bonus amount

```text
Bonus = Eligible salary × Target bonus % × Performance multiplier
```

#### Total compensation

```text
Total compensation = Base salary + Allowances + Bonus + Incentives + Employer benefits + Employer contributions
```

---

## 37. Notifications

### Notifications to managers

- Compensation cycle opened
- Worksheet assigned
- Budget exceeded warning
- Employee outside salary band
- Worksheet returned for correction
- Approval required
- Cycle deadline reminder

### Notifications to HR / compensation team

- Salary exception created
- Budget exception created
- Manager submitted worksheet
- Approval overdue
- Payroll transfer failed
- Compensation cycle ready for closure

### Notifications to finance

- Budget exception approval required
- Compensation forecast updated
- Bonus pool exceeded
- Salary change requiring finance approval

### Notifications to employees

- Compensation statement released
- Salary change letter available, if tenant allows
- Bonus letter available, if tenant allows

---

## 38. Document Generation

### Main document types

- Salary increase letter
- Promotion salary letter
- Bonus award letter
- Allowance approval letter
- Total compensation statement
- Compensation review summary
- Salary exception approval document
- Compensation budget approval document

### Template features

- Multi-language templates
- Dynamic placeholders
- PDF generation
- Digital signature
- QR verification
- Document numbering
- Employee document archive

### Common placeholders

- Employee name
- Employee ID
- Position
- Department
- Grade
- Old salary
- New salary
- Increase amount
- Increase percentage
- Effective date
- Bonus amount
- Allowance amount
- Approver name
- Legal entity

---

## 39. Recommended Compensation Management Menu

```text
Compensation Management
│
├── Dashboard
├── Employee Compensation Profiles
├── Salary Grades
├── Salary Bands / Pay Ranges
├── Salary Structures
├── Allowances
├── Merit Planning
├── Salary Review Cycles
├── Compensation Worksheets
├── Bonus Planning
├── Incentive Planning
├── Commission Plans
├── Promotion Salary Adjustments
├── Market Salary Comparison
├── Compensation Budgets
├── Compensation Approvals
├── Total Compensation Statements
├── Compensation History
├── Payroll Transfer
├── Reports
└── Settings
```

---

## 40. Recommended Form Tabs

### Salary grade form tabs

1. Overview
2. Pay Range
3. Applicable Jobs / Positions
4. Applicable Locations
5. Eligibility Rules
6. History
7. Audit Trail

### Employee compensation profile tabs

1. Overview
2. Current Salary
3. Grade and Band
4. Allowances
5. Bonus / Incentives
6. Compensation History
7. Market Comparison
8. Total Compensation Statement
9. Payroll Sync
10. Audit Trail

### Salary review cycle tabs

1. Overview
2. Eligibility
3. Budget
4. Merit Matrix
5. Worksheets
6. Approvals
7. Payroll Transfer
8. Reports
9. Audit Trail

---

## 41. Recommended List Columns

### Employee compensation list

- Employee ID
- Employee name
- Department
- Position
- Grade
- Current salary
- Currency
- Salary band
- Compa-ratio
- Range penetration
- Last salary change
- Pending change
- Payroll status
- Actions

### Salary grade list

- Grade code
- Grade name
- Grade level
- Currency
- Minimum salary
- Midpoint salary
- Maximum salary
- Status
- Effective date
- Actions

### Review cycle list

- Cycle name
- Cycle year
- Legal entity
- Eligible employees
- Budget
- Proposed amount
- Approved amount
- Status
- Effective date
- Actions

---

## 42. Recommended Main Data Entities

Recommended entities:

- CompensationPlan
- SalaryGrade
- SalaryBand
- PayRange
- SalaryStructure
- SalaryComponent
- EmployeeCompensationProfile
- EmployeeCompensationRecord
- AllowancePolicy
- EmployeeAllowance
- MeritCycle
- MeritMatrix
- SalaryReviewCycle
- CompensationWorksheet
- CompensationWorksheetLine
- BonusPlan
- BonusAward
- IncentivePlan
- IncentivePayout
- CommissionPlan
- CommissionPayout
- CompensationBudget
- CompensationBudgetAllocation
- CompensationApproval
- CompensationException
- MarketSalarySurvey
- MarketSalaryData
- TotalCompensationStatement
- PayrollCompensationTransfer
- CompensationDocument
- CompensationAuditLog

Every entity must include TenantId.

For enterprise tenants, many records should also include LegalEntityId, DepartmentId, LocationId, CurrencyId, and EffectiveDate fields.

---

## 43. Important Validation Rules

The system should validate:

- Salary grade code must be unique within tenant.
- Salary band code must be unique within tenant.
- Salary minimum cannot exceed midpoint.
- Salary midpoint cannot exceed maximum.
- Employee salary cannot be negative.
- Currency is required for salary records.
- Effective start date cannot be after effective end date.
- Only one active compensation record should exist for the same employee and effective period.
- Salary change outside band requires exception approval.
- Increase above allowed percentage requires approval.
- Budget exceeded requires approval or blocking based on tenant policy.
- Compensation worksheet cannot be submitted if required employees are missing.
- Compensation change cannot be sent to payroll before approval.
- Payroll transfer cannot happen for terminated employees unless final settlement rule allows it.
- Employee cannot approve their own compensation change.
- Manager cannot view compensation outside authorized hierarchy.
- Tenant users cannot access another tenant's compensation data.
- Platform support access must be audited.
- Historical salary records must not be overwritten.

---

## 44. API and Integration Requirements

### Internal APIs

The module should expose APIs for:

- Get employee compensation profile
- Create salary grade
- Create salary band
- Start salary review cycle
- Submit compensation worksheet
- Approve compensation change
- Transfer approved compensation to payroll
- Generate total compensation statement
- Fetch compensation history
- Fetch compensation budget status

### API security rules

- TenantId must be validated from authentication context, not trusted from request body alone.
- Salary fields must be permission-checked.
- Bulk export APIs must require explicit permission.
- Payroll transfer APIs must be idempotent.
- All write APIs must create audit records.

### Integration events

Recommended event examples:

```text
CompensationChangeApproved
CompensationTransferredToPayroll
SalaryBandUpdated
CompensationCycleOpened
CompensationCycleClosed
BonusApproved
IncentiveApproved
TotalCompensationStatementReleased
```

---

## 45. Common Mistakes to Avoid

### 1. Treating compensation as payroll only

Compensation is planning and governance. Payroll is calculation and payment.

### 2. No salary history

Never overwrite previous salary records. Use effective-dated history.

### 3. Weak salary security

Salary data needs field-level security, export restrictions, and audit logs.

### 4. No budget validation

Salary increases without budget control create financial risk.

### 5. No salary band validation

Employees outside bands should be visible and controlled.

### 6. No payroll integration

Approved compensation changes must flow to Payroll with effective dates.

### 7. No multi-currency support

Enterprise and multi-country tenants need currency-specific rules.

### 8. Tenant-global configuration mistake

Do not share salary structures, bands, or compensation plans between tenants.

### 9. No approval workflow

Compensation changes must not be applied without authorization.

### 10. No audit trail

Every salary, bonus, and allowance change must be traceable.

---

## 46. Final Recommended Launch Scope

For your ERP, the **Compensation Management** module should launch with full enterprise functionality covering:

- Multi-tenant compensation configuration
- Salary grades
- Salary bands and pay ranges
- Employee compensation profiles
- Salary structures
- Allowance management
- Merit increase planning
- Salary review cycles
- Compensation worksheets
- Bonus planning
- Incentive planning
- Commission planning
- Promotion salary adjustments
- Market salary comparison
- Compensation budget control
- Compensation approval workflows
- Compensation history
- Total compensation statements
- Multi-currency support
- Multi-country support
- Payroll integration
- Finance and GL integration
- Performance integration
- Position integration
- Benefits integration
- Document generation
- Notifications
- Reports and analytics
- Security and access control
- Audit trail
- API and event integration

The most important design rule:

**Compensation Management should define, plan, validate, approve, and govern compensation changes. Payroll should only process compensation that has been approved, effective-dated, audited, and transferred through controlled integration.**

