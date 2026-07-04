# 8. Leave / Absence Management Module in HCM — Full Enterprise Features with Multi-Tenancy

The **Leave / Absence Management** module manages employee leave requests, absence policies, leave balances, accruals, carry-forward, encashment, approvals, calendars, absence visibility, payroll impact, and compliance controls.

In a professional HCM/ERP system, Leave / Absence Management is not just a form where employees request annual leave. It is a policy-driven engine that connects:

- Employee Management
- Organizational Management
- Position Management
- Time and Attendance
- Payroll
- Shift Scheduling
- Holiday Calendar
- Workflow and Approval Engine
- Employee Self-Service
- Manager Self-Service
- Notification Engine
- Document Management
- Reporting and Analytics
- Audit Log
- Multi-Tenant Configuration and Data Isolation

This module is commonly found in systems such as **Oracle Absence Management, SAP SuccessFactors Time Off, Workday Absence Management, Zoho People Leave Tracker, Microsoft Dynamics 365 Human Resources, UKG, ADP, Dayforce, BambooHR, Odoo HR, and enterprise payroll/HCM platforms**.

---

# 1. Purpose of Leave / Absence Management

The purpose of the module is to control all planned and unplanned employee absences while ensuring accurate leave balances, approvals, payroll treatment, compliance, and workforce visibility.

## Main objectives

- Define leave types per tenant/company/legal entity
- Manage annual leave, sick leave, maternity leave, paternity leave, unpaid leave, study leave, business leave, special leave, and custom leave types
- Calculate leave entitlement
- Calculate leave accrual
- Track leave balance
- Support leave carry-forward
- Support leave encashment
- Support leave requests and approvals
- Support partial-day and hourly leave
- Support leave cancellation and amendment
- Support leave adjustment
- Show leave calendar and team absence view
- Integrate leave with attendance
- Integrate unpaid leave and leave encashment with payroll
- Support holiday calendars and work calendars
- Enforce leave policies and validations
- Maintain full audit history
- Support multi-tenancy safely and correctly

## Standard leave flow

```text
Employee submits leave request
        ↓
System validates balance, policy, calendar, and conflicts
        ↓
Approval workflow is triggered
        ↓
Manager / HR approves or rejects
        ↓
Leave is posted to employee balance
        ↓
Attendance is updated as On Leave
        ↓
Payroll receives paid/unpaid/encashment impact
        ↓
Reports and audit history are updated
```

---

# 2. Multi-Tenancy Design Principles

Because your ERP is multi-tenant, Leave / Absence Management must be designed so that each tenant can have its own policies, calendars, workflows, leave types, payroll rules, and security boundaries.

## Tenant-level requirements

Every major leave entity must include a **tenant_id** or equivalent tenant partition key.

Examples:

- LeaveType
- LeavePolicy
- LeaveEntitlementRule
- LeaveAccrualRule
- LeaveBalance
- LeaveRequest
- LeaveApproval
- LeaveCalendar
- HolidayCalendar
- LeaveAdjustment
- LeaveEncashment
- LeaveCarryForward
- LeavePayrollImpact
- LeaveAuditLog

## Multi-tenant isolation rules

The system must ensure:

- Tenant A cannot see Tenant B leave types
- Tenant A cannot use Tenant B holiday calendars
- Tenant A cannot approve Tenant B leave requests
- Tenant A payroll cannot receive Tenant B leave payroll data
- Tenant A reports cannot aggregate Tenant B data unless platform-admin analytics explicitly supports anonymized cross-tenant reporting
- Tenant A custom fields cannot appear in Tenant B screens
- Tenant A workflow rules cannot trigger for Tenant B employees

## Tenant-level configuration examples

Tenant A may configure:

```text
Annual Leave: 21 days per year
Carry-forward: max 5 days
Approval: Manager → HR
Holiday Calendar: Azerbaijan public holidays
Payroll: unpaid leave deducts daily salary
```

Tenant B may configure:

```text
Annual Leave: 30 days per year
Carry-forward: max 10 days
Approval: Manager → Department Head → HR
Holiday Calendar: UAE public holidays
Payroll: unpaid leave deducts basic salary only
```

Both must work independently in the same SaaS platform.

---

# 3. Tenant, Company, and Legal Entity Scope

Leave rules should be configurable at different levels.

## Supported configuration levels

- Tenant level
- Company level
- Legal entity level
- Business unit level
- Department level
- Branch/location level
- Employee group level
- Employment type level
- Job/position level
- Grade level
- Individual employee level, by exception

## Rule inheritance hierarchy

Recommended hierarchy:

```text
Tenant Default
   ↓
Company / Legal Entity
   ↓
Business Unit / Department
   ↓
Location / Branch
   ↓
Employee Group / Grade / Position
   ↓
Employee-specific Override
```

## Business logic

The system should use the most specific applicable rule.

Example:

```text
Tenant default annual leave: 21 days
Legal entity override: 24 days
Manager grade override: 30 days
Employee is Manager grade
Result: employee receives 30 days
```

---

# 4. Leave / Absence Setup and Configuration

The module should provide a full setup area for HR administrators.

## Main setup areas

- Leave types
- Leave categories
- Leave policies
- Entitlement rules
- Accrual rules
- Carry-forward rules
- Encashment rules
- Negative balance rules
- Probation leave rules
- Notice period leave rules
- Approval workflows
- Holiday calendars
- Work calendars
- Leave request form settings
- Leave document requirements
- Leave reason setup
- Leave payroll rules
- Leave security rules
- Leave notification templates
- Tenant-specific custom fields

## Recommended settings menu

```text
Leave / Absence Settings
│
├── Leave Types
├── Leave Categories
├── Leave Policies
├── Entitlement Rules
├── Accrual Rules
├── Carry-Forward Rules
├── Encashment Rules
├── Negative Balance Rules
├── Holiday Calendars
├── Work Calendars
├── Approval Rules
├── Document Requirements
├── Payroll Mapping
├── Notifications
├── Security Rules
└── Tenant Configuration
```

---

# 5. Leave Type Management

Leave types define the kinds of absences employees can request or be assigned.

## Main features

- Create leave type
- Leave type code
- Leave type name
- Leave category
- Paid/unpaid flag
- Payroll impact flag
- Balance required flag
- Accrual required flag
- Carry-forward allowed flag
- Encashment allowed flag
- Half-day allowed flag
- Hourly leave allowed flag
- Attachment required flag
- Approval required flag
- Gender-specific applicability, where legally allowed
- Employment-type applicability
- Probation applicability
- Minimum service requirement
- Maximum days per request
- Maximum days per year
- Minimum notice days
- Calendar days vs working days rule
- Include holidays rule
- Include weekly offs rule
- Active/inactive status
- Effective start/end date

## Common leave types

- Annual leave
- Sick leave
- Maternity leave
- Paternity leave
- Parental leave
- Unpaid leave
- Study leave
- Business leave
- Special leave
- Bereavement leave
- Marriage leave
- Military leave
- Medical leave
- Compensatory leave
- Hajj/Umrah/religious leave, if applicable
- Public duty leave
- Jury duty leave, if applicable
- Work injury leave
- Remote work absence, if treated as absence
- Time off in lieu

## Multi-tenant rule

Leave type code only needs to be unique inside the tenant, not globally across all tenants.

Example:

```text
Tenant A: AL = Annual Leave
Tenant B: AL = Annual Leave
Both are valid because they belong to different tenants.
```

---

# 6. Leave Category Management

Categories help group leave types for reporting, payroll, and policies.

## Main categories

- Paid leave
- Unpaid leave
- Statutory leave
- Company benefit leave
- Medical leave
- Family leave
- Educational leave
- Business absence
- Emergency leave
- Compensatory leave
- Non-working absence

## Main features

- Category code
- Category name
- Paid/unpaid default
- Payroll treatment
- Reporting group
- Balance behavior
- Approval behavior
- Compliance flag
- Tenant-specific status

## Business logic

A tenant can decide that certain leave types share balances or reporting categories.

Example:

```text
Annual Leave and Emergency Leave may both deduct from Paid Leave Balance.
```

Or:

```text
Sick Leave and Medical Leave may share a Medical Absence category but have different document rules.
```

---

# 7. Annual Leave Management

Annual leave is usually the most used leave type.

## Main features

- Annual leave entitlement
- Annual leave accrual
- Annual leave request
- Annual leave balance
- Annual leave carry-forward
- Annual leave encashment
- Annual leave adjustment
- Annual leave expiration
- Annual leave calendar view
- Annual leave approval workflow
- Annual leave payroll impact

## Annual leave rule examples

- 21 days per year
- 24 days per year after 5 years of service
- 30 days for managers
- Monthly accrual of 1.75 days
- Full entitlement granted at beginning of year
- Leave allowed only after probation
- Maximum 10 continuous days per request
- Minimum 7 days notice before requesting
- Carry-forward maximum 5 days
- Carry-forward expires after 3 months

## Business logic

Annual leave should support both entitlement models:

### Front-loaded entitlement

```text
Employee receives full annual entitlement at beginning of year.
```

### Accrued entitlement

```text
Employee earns leave gradually each month or pay period.
```

---

# 8. Sick Leave Management

Sick leave often has special rules and document requirements.

## Main features

- Sick leave entitlement
- Paid sick leave
- Partially paid sick leave
- Unpaid sick leave
- Medical certificate requirement
- Sick leave without balance
- Sick leave approval
- Sick leave extension
- Sick leave payroll treatment
- Sick leave history
- Sick leave pattern monitoring

## Common sick leave rules

- Medical certificate required after X days
- First X days fully paid
- Next X days partially paid
- Remaining days unpaid
- Sick leave cannot be planned in advance, configurable
- Sick leave can be entered by employee or HR
- Manager can view status but not medical details

## Security rule

Medical attachments and detailed medical notes must be restricted.

Recommended access:

- Employee can upload/view own certificate
- HR authorized role can verify
- Manager can see sick leave dates and approval status
- Manager should not see confidential medical details unless policy allows

---

# 9. Maternity Leave Management

Maternity leave must support legal and company policy rules.

## Main features

- Maternity leave request
- Expected delivery date
- Actual delivery date
- Pre-birth leave period
- Post-birth leave period
- Document requirements
- Paid/unpaid split
- Payroll impact
- Benefit continuation
- Extension option
- Return-to-work date
- Maternity leave calendar blocking
- Replacement planning trigger

## Business logic

Maternity leave may be configured by:

- Country/legal entity
- Employment type
- Service period
- Number of children
- Medical document
- Company policy

## Integration points

- Payroll for paid/unpaid portions
- Benefits for insurance continuation
- Position Management for temporary replacement
- Attendance to mark employee as on maternity leave
- Leave calendar for visibility

---

# 10. Paternity Leave Management

Paternity leave is usually shorter but still requires policy control.

## Main features

- Paternity leave request
- Child birth date
- Required document
- Leave period eligibility
- Maximum days
- Request deadline
- Paid/unpaid flag
- Approval workflow
- Payroll impact

## Business logic

Example:

```text
Paternity leave: 5 working days
Must be used within 30 days of child birth
Birth certificate required
```

---

# 11. Unpaid Leave Management

Unpaid leave affects payroll and attendance directly.

## Main features

- Unpaid leave request
- Reason
- Date range
- Approval workflow
- Payroll deduction
- Benefits impact
- Leave accrual impact
- Service period impact
- Attendance update
- Maximum unpaid leave days
- HR approval requirement

## Business logic

Unpaid leave may affect:

- Salary deduction
- Leave accrual suspension
- Benefits deduction
- Seniority calculation
- Probation extension
- End-of-service calculation

Example:

```text
Employee takes 10 days unpaid leave.
Payroll deducts 10 unpaid days.
Leave accrual for the month may be reduced or suspended depending on policy.
```

---

# 12. Study Leave Management

Study leave supports education and exams.

## Main features

- Study leave request
- Education program
- Institution name
- Exam date
- Required document
- Paid/unpaid setting
- Maximum days per year
- Approval workflow
- HR verification
- Payroll impact

## Business logic

Study leave can be allowed only for approved programs or company-sponsored education.

---

# 13. Business Leave / Business Trip Absence

Business leave records approved work-related absence from normal workplace.

## Main features

- Business leave request
- Business trip linkage
- Destination
- Purpose
- Date range
- Manager approval
- Travel request linkage
- Expense/travel module integration
- Attendance status update
- Payroll neutral treatment

## Business logic

Business leave usually does not deduct leave balance and does not create salary deduction.

Attendance status should show:

```text
Business Leave / Business Trip
```

Not:

```text
Absent
```

---

# 14. Special Leave Management

Special leave covers company-specific or statutory special cases.

## Examples

- Marriage leave
- Bereavement leave
- Religious leave
- Military leave
- Emergency leave
- Public duty leave
- Work injury leave
- Compassionate leave
- Relocation leave
- Voting leave, where applicable

## Main features

- Special leave type setup
- Eligibility rule
- Maximum days
- Required document
- Paid/unpaid flag
- Approval workflow
- Payroll impact
- Expiry rule
- Reporting category

## Business logic

Special leave rules must be tenant-configurable because requirements differ by country, company, and industry.

---

# 15. Leave Entitlement Rules

Entitlement defines how many leave days/hours an employee is allowed.

## Main features

- Entitlement by leave type
- Entitlement by legal entity
- Entitlement by employment type
- Entitlement by grade
- Entitlement by position/job
- Entitlement by service years
- Entitlement by contract type
- Entitlement by gender, where legally allowed and relevant
- Entitlement by working schedule
- Entitlement effective dates
- Prorated entitlement
- Manual entitlement override

## Entitlement examples

```text
Annual leave: 21 days per year
After 5 years service: 24 days
After 10 years service: 30 days
```

```text
Part-time employee working 50% FTE receives 50% annual entitlement.
```

## Business logic

Entitlement should be recalculated when relevant employee data changes:

- Hire date
- Legal entity
- Employment type
- Grade
- FTE
- Work schedule
- Termination date
- Leave policy assignment

---

# 16. Leave Accrual Rules

Accrual defines how leave balance is earned over time.

## Main features

- Monthly accrual
- Daily accrual
- Pay-period accrual
- Annual front-loading
- Anniversary-based accrual
- Calendar-year accrual
- Fiscal-year accrual
- Service-year accrual
- Accrual during probation
- Accrual during unpaid leave
- Accrual during maternity leave
- Accrual suspension rules
- Accrual rounding
- Accrual cap
- Retroactive accrual recalculation

## Accrual examples

```text
Annual entitlement: 21 days
Monthly accrual: 1.75 days
```

```text
Employee hired on 15 June
Annual entitlement: 21 days
Prorated entitlement for year: calculated from hire date to year-end
```

## Business logic

The system should store accrual transactions, not only final balance.

Example accrual ledger:

```text
Jan: +1.75
Feb: +1.75
Mar: +1.75
Leave taken in Mar: -2.00
Balance: 3.25
```

---

# 17. Leave Balance Management

Leave balance is the employee’s available leave amount.

## Main features

- Opening balance
- Accrued balance
- Used balance
- Pending request balance
- Approved leave balance
- Available balance
- Carry-forward balance
- Expired balance
- Encashable balance
- Adjusted balance
- Negative balance
- Balance ledger
- Balance as of date

## Recommended balance calculation

```text
Available Balance = Opening Balance + Accrued + Adjustments + Carry Forward - Approved Leave - Pending Leave - Expired - Encashment
```

## Business logic

The system should support “as of date” balance.

Example:

```text
Balance as of today: 8 days
Balance as of requested leave start date: 10 days
```

Leave validation should use the correct balance date based on tenant policy.

---

# 18. Leave Balance Ledger

A ledger provides full traceability.

## Ledger transaction types

- Opening balance
- Accrual
- Leave taken
- Leave cancellation
- Leave adjustment
- Carry-forward
- Expiry
- Encashment
- Payroll correction
- Manual correction
- Migration import

## Ledger fields

- Tenant ID
- Employee
- Leave type
- Transaction date
- Effective date
- Transaction type
- Amount
- Unit: days/hours
- Source document
- Approval reference
- Balance after transaction
- Created by
- Created date

## Business logic

Never overwrite leave balance without a ledger transaction. This is critical for audit and payroll disputes.

---

# 19. Leave Carry-Forward

Carry-forward moves unused balance to the next period.

## Main features

- Carry-forward allowed flag
- Maximum carry-forward days/hours
- Carry-forward expiry date
- Carry-forward approval
- Auto carry-forward
- Manual carry-forward
- Carry-forward by leave type
- Carry-forward by legal entity
- Carry-forward by employee group
- Carry-forward ledger entry

## Carry-forward examples

```text
Unused annual leave: 12 days
Carry-forward limit: 5 days
Carried forward: 5 days
Expired/forfeited: 7 days
```

```text
Carried-forward leave expires on 31 March next year.
```

## Business logic

The system should separate current-year balance and carried-forward balance when policy requires older balance to be consumed first.

---

# 20. Leave Expiry and Forfeiture

Unused leave may expire based on policy.

## Main features

- Expiry date
- Expiry rule by leave type
- Automatic expiry
- Manual expiry
- Expiry warning notifications
- Expiry report
- Expiry ledger transaction
- Forfeiture approval, if required

## Business logic

Employees and managers should receive alerts before leave expires.

Example alerts:

- 90 days before expiry
- 60 days before expiry
- 30 days before expiry
- 7 days before expiry

---

# 21. Leave Encashment

Leave encashment converts unused leave into payment.

## Main features

- Encashment eligibility
- Encashment request
- Encashment approval
- Minimum balance required
- Maximum encashable days
- Encashment rate
- Payroll transfer
- Encashment tax treatment
- Encashment ledger entry
- Encashment history

## Encashment scenarios

- Annual encashment
- End-of-service encashment
- Company-initiated encashment
- Employee-requested encashment
- Carry-forward encashment

## Business logic

Example:

```text
Encashable balance: 5 days
Daily salary rate: 50 AZN
Encashment amount: 250 AZN
```

Encashment should reduce leave balance and send payment component to payroll.

---

# 22. Leave Request Management

Employees should request leave through Employee Self-Service or mobile app.

## Main features

- Create leave request
- Leave type
- Date range
- Start date
- End date
- Full-day / half-day / hourly
- Number of leave days/hours
- Reason
- Attachment upload
- Emergency leave flag
- Contact during leave
- Delegation during leave
- Replacement employee
- Handover notes
- Approval workflow
- Request status
- Request history

## Leave request statuses

- Draft
- Submitted
- Pending approval
- Approved
- Rejected
- Returned for correction
- Cancelled
- Cancellation requested
- Partially cancelled
- Amended
- Withdrawn
- Posted to payroll

## Business logic

Before submission, the system should validate:

- Employee eligibility
- Leave balance
- Minimum notice period
- Maximum leave duration
- Overlapping leave requests
- Holiday/work calendar
- Blackout dates
- Payroll lock period
- Required attachments
- Approval route

---

# 23. Partial-Day and Hourly Leave

Not all leave is full-day.

## Main features

- Half-day leave
- First half / second half
- Hourly leave
- Time-based leave request
- Partial-day deduction
- Attendance integration
- Payroll integration
- Shift-based calculation

## Business logic

Example:

```text
Employee requests leave from 14:00 to 18:00.
Shift is 09:00 to 18:00.
Leave deduction: 4 hours or 0.5 day depending on policy.
```

Partial leave should integrate with attendance so the employee is not marked absent for the approved leave period.

---

# 24. Leave Calendar

The leave calendar gives visibility into planned absences.

## Main features

- Employee leave calendar
- Team leave calendar
- Department leave calendar
- Company leave calendar
- Manager view
- HR view
- Public holiday overlay
- Weekly off overlay
- Approved leave display
- Pending leave display, configurable
- Filter by leave type
- Filter by department
- Filter by location
- Calendar export

## Calendar access rules

The calendar should respect access security.

Example:

- Employee can see own leave
- Manager can see team leave
- HR can see authorized employees
- Employees may see team availability without sensitive leave reason
- Sick leave reason may be hidden

---

# 25. Team Absence View

Managers need to see team availability before approving leave.

## Main features

- Team absence dashboard
- Who is off today
- Who is off this week
- Overlapping leave view
- Minimum staffing warning
- Critical role absence warning
- Department coverage view
- Shift coverage view
- Leave conflict warning

## Business logic

Example:

```text
Department has 5 cashiers.
3 cashiers already approved leave on same day.
Minimum staffing rule: 4 cashiers required.
System warns manager before approving another leave.
```

---

# 26. Holiday Calendar Management

Holiday calendars are required for correct leave calculations.

## Main features

- Tenant-level holiday calendar
- Legal entity holiday calendar
- Country holiday calendar
- Location holiday calendar
- Branch holiday calendar
- Custom holiday
- Public holiday
- Optional holiday
- Half-day holiday
- Recurring holiday
- Special working day
- Calendar effective dates
- Import holidays

## Multi-tenant rule

Each tenant must manage its own holiday calendars. A platform default calendar can be copied, but tenant-specific calendars must be isolated.

## Business logic

Leave calculation should know whether holidays are counted or excluded.

Example:

```text
Leave request: Monday to Friday
Wednesday is public holiday
Policy excludes public holidays
Deducted leave: 4 days
```

---

# 27. Work Calendar Management

Work calendars define working days and weekly offs.

## Main features

- 5-day workweek
- 6-day workweek
- Shift-based calendar
- Branch calendar
- Employee-specific calendar
- Remote work calendar
- Part-time calendar
- Rotating calendar
- Special working day
- Calendar effective dates

## Business logic

Leave deduction should use the employee’s assigned work calendar, not only tenant default.

Example:

```text
Head office works Monday-Friday.
Retail store works Sunday-Saturday with rotating offs.
Leave calculation must use the employee's actual schedule.
```

---

# 28. Leave Approval Workflow

Leave requests must follow configurable approval workflows.

## Main features

- Manager approval
- HR approval
- Department head approval
- Multi-level approval
- Conditional approval
- Role-based approval
- Leave-type-based workflow
- Amount/duration-based workflow
- Department-based workflow
- Delegation
- Escalation
- Approval comments
- Rejection reason
- Return for correction
- Approval history

## Example workflow

```text
Employee
→ Direct Manager
→ Department Head, if leave > 10 days
→ HR, if special leave or unpaid leave
```

## Business logic

Approval can depend on:

- Leave type
- Leave duration
- Employee grade
- Department
- Legal entity
- Location
- Payroll impact
- Emergency leave
- Minimum staffing rule

---

# 29. Leave Cancellation and Amendment

Employees may need to cancel or modify leave.

## Main features

- Cancel pending leave
- Cancel approved leave
- Partial cancellation
- Amend leave dates
- Extend leave
- Shorten leave
- Change leave type
- Approval for cancellation/amendment
- Balance reversal
- Attendance update
- Payroll impact reversal
- Audit trail

## Business logic

If leave is already approved but not payroll processed, cancellation can reverse balance immediately after approval.

If leave is already payroll processed, cancellation should create payroll adjustment rather than silently changing history.

---

# 30. Leave Adjustment

HR may need to adjust balances manually.

## Main features

- Add leave balance
- Deduct leave balance
- Correct opening balance
- Migration adjustment
- Policy correction
- Payroll correction
- Adjustment reason
- Attachment
- Approval workflow
- Ledger transaction
- Audit trail

## Business logic

Leave adjustment should always create a ledger transaction.

Manual adjustment should require permission and approval if configured.

---

# 31. Negative Leave Balance

Some companies allow employees to take leave in advance.

## Main features

- Negative balance allowed flag
- Maximum negative limit
- Leave type-specific negative limit
- Employee group-specific negative limit
- Approval required for negative balance
- Payroll recovery rule
- Final settlement recovery rule

## Business logic

Example:

```text
Available annual leave: 2 days
Requested leave: 5 days
Negative balance limit: 5 days
Result: request allowed with -3 days balance
```

If employee exits before earning back the leave, Payroll should recover the negative balance in final settlement.

---

# 32. Leave During Probation

Probation employees may have special rules.

## Main features

- Leave allowed during probation
- Leave blocked during probation
- Leave accrues during probation but cannot be used
- Leave does not accrue during probation
- Manager/HR exception approval
- Probation extension due to unpaid leave

## Business logic

Example:

```text
Employee is on probation.
Annual leave accrues monthly but cannot be requested until confirmation.
```

---

# 33. Leave During Notice Period

Employees under resignation/termination notice may have restricted leave.

## Main features

- Block leave during notice
- Allow only HR-approved leave
- Allow sick leave with document
- Adjust last working day
- Impact final settlement
- Impact notice period calculation

## Business logic

Example:

```text
Employee under notice requests annual leave.
Policy requires HR approval and may extend last working day or deduct from final balance.
```

---

# 34. Leave Blackout Dates

Some periods may be blocked for business reasons.

## Main features

- Define blackout dates
- Blackout by tenant
- Blackout by legal entity
- Blackout by department
- Blackout by location
- Blackout by role/position
- Allow exceptions
- Approval for exceptions
- Warning or hard block

## Examples

- Year-end closing for Finance
- Inventory counting period for Warehouse
- Peak retail season
- Restaurant holiday season
- Payroll closing week
- Audit period

---

# 35. Minimum Staffing and Leave Conflict Rules

The system should help managers avoid staffing gaps.

## Main features

- Minimum staffing rule
- Maximum employees on leave per department
- Maximum percentage of team on leave
- Critical role coverage
- Shift coverage rule
- Same-role conflict warning
- Overlap detection
- Replacement required flag

## Business logic

Example:

```text
Only one pharmacist per branch can be absent at the same time.
```

Or:

```text
At least 70% of warehouse team must be available during stock count.
```

---

# 36. Absence Without Request / Unauthorized Absence

The system should handle attendance absences that are not approved leave.

## Main features

- Detect unauthorized absence from attendance
- Create absence case
- Request employee explanation
- Manager review
- Convert to leave
- Convert to unpaid leave
- Mark as unauthorized absence
- Payroll deduction
- Disciplinary trigger, if needed

## Business logic

Example:

```text
Employee did not attend and has no approved leave.
Attendance creates absence exception.
Manager can convert it to unpaid leave or request explanation.
```

---

# 37. Leave Request Delegation / Acting Replacement

When an employee is away, duties or approvals may need to be delegated.

## Main features

- Acting employee selection
- Delegation period
- Approval delegation
- Task delegation
- Manager approval for delegation
- Handover notes
- Delegation notifications
- Temporary role assignment, if integrated with security

## Business logic

For managers, leave approval workflow should route pending approvals to the acting/delegated manager if configured.

---

# 38. Leave Document Management

Some leave types require supporting documents.

## Main features

- Required document by leave type
- Optional document upload
- Medical certificate
- Birth certificate
- Marriage certificate
- Death certificate, for bereavement leave
- Education/exam document
- Business travel approval
- Document verification
- Document rejection
- Confidential document access
- Expiry date
- Document archive

## Business logic

Document requirement can depend on leave duration.

Example:

```text
Sick leave up to 2 days: no certificate required
Sick leave more than 2 days: certificate required
```

---

# 39. Leave Payroll Integration

Leave has direct payroll impact.

## Payroll outputs

- Paid leave days
- Unpaid leave days
- Sick leave paid/unpaid split
- Leave without pay
- Leave encashment amount
- Negative leave recovery
- Leave adjustment payroll impact
- Maternity/paternity paid days
- Absence deduction
- Benefit continuation flag

## Business logic

Payroll should receive approved and locked leave data only.

Example:

```text
Unpaid leave: 3 days
Daily salary rate: 50 AZN
Payroll deduction: 150 AZN
```

Encashment example:

```text
Encashed leave: 5 days
Daily salary rate: 60 AZN
Payroll earning: 300 AZN
```

---

# 40. Leave and Attendance Integration

Leave approval must update attendance.

## Main features

- Approved leave marks attendance as On Leave
- Partial leave reduces required working hours
- Unpaid leave creates payroll deduction
- Attendance absence can become leave request
- Leave cancellation recalculates attendance
- Sick leave affects attendance status
- Holiday and weekly off exclusion

## Business logic

Attendance must not mark employee absent when approved leave exists.

Example:

```text
Employee has approved annual leave on 10 July.
No clock-in exists.
Attendance status: Annual Leave, not Absent.
```

---

# 41. Leave and Shift Scheduling Integration

Leave affects workforce scheduling.

## Main features

- Show approved leave in shift planning
- Block shift assignment during approved leave
- Warn if employee has leave during shift
- Require replacement shift
- Update staffing coverage
- Leave request checks scheduled shifts
- Shift-based leave deduction

## Business logic

Example:

```text
Employee is scheduled for 8-hour shift.
Employee requests half-day leave.
System deducts 4 hours and updates schedule coverage.
```

---

# 42. Employee Self-Service Leave

Employees should manage their own leave requests.

## Employee can

- View leave balance
- View leave history
- Submit leave request
- Submit partial-day leave
- Submit hourly leave
- Attach documents
- View approval status
- Cancel leave request
- Amend leave request
- View team calendar, if allowed
- View holiday calendar
- View leave policy summary
- Request leave encashment, if allowed
- View carry-forward/expiry information

---

# 43. Manager Self-Service Leave

Managers need leave approval and team visibility.

## Manager can

- View team leave calendar
- View leave requests
- Approve/reject leave
- Return leave for correction
- View balance summary, if allowed
- View staffing conflict warnings
- View team absence trends
- Approve leave cancellation
- Approve leave amendment
- Assign acting replacement
- Convert attendance absence to leave/unpaid leave

---

# 44. HR Leave Workspace

HR needs a central workspace for policy control and exception handling.

## HR can

- Manage leave types
- Manage leave policies
- Manage balances
- Manage accruals
- Run accrual process
- Run carry-forward process
- Run expiry process
- Adjust balances
- Approve special leave
- Review medical documents, if authorized
- Lock leave period
- Export leave payroll data
- Recalculate balances
- Manage exceptions
- Generate reports

---

# 45. Leave Period Locking and Payroll Cutoff

Leave data should be controlled before payroll.

## Main features

- Leave period
- Payroll cutoff date
- Leave lock
- Approval deadline
- HR lock
- Payroll lock
- Unlock request
- Retroactive leave correction
- Payroll adjustment

## Business logic

After payroll lock, changes should require special approval and create payroll adjustments.

Example:

```text
May payroll is processed.
Employee submits sick leave correction for May.
System creates retroactive adjustment for next payroll period.
```

---

# 46. Leave Accrual Processing Engine

The system should run accrual calculations automatically.

## Main features

- Scheduled accrual process
- Manual accrual run
- Accrual by tenant
- Accrual by legal entity
- Accrual by employee group
- Accrual preview
- Accrual posting
- Accrual error report
- Accrual rollback, controlled
- Accrual recalculation
- Accrual ledger posting

## Multi-tenant processing rule

Accrual jobs must process tenant data separately.

Recommended approach:

```text
Run accrual by tenant_id and legal_entity_id.
Never run one shared calculation that mixes employees from different tenants.
```

---

# 47. Carry-Forward and Year-End Processing

Year-end leave processing is important.

## Main features

- Year-end carry-forward
- Year-end expiry
- Opening balance creation
- Year-end encashment
- Balance rollover
- Policy-based cap
- Preview before posting
- Exception report
- Approval before posting
- Rollback, controlled
- Audit history

## Business logic

Year-end process should provide preview.

Example:

```text
Employee balance: 12 days
Carry-forward max: 5 days
Expired: 7 days
Opening balance next year: 5 days
```

---

# 48. Leave Recalculation

Leave balances may need recalculation after policy or employee changes.

## Main features

- Recalculate individual employee
- Recalculate department
- Recalculate legal entity
- Recalculate tenant
- Recalculate date range
- Recalculate after hire date correction
- Recalculate after work schedule change
- Recalculate after leave cancellation
- Recalculate after policy change
- Recalculation preview
- Payroll impact warning

## Business logic

If payroll is already processed, recalculation should create adjustment transactions rather than silently changing past payroll results.

---

# 49. Multi-Country and Localization Support

Leave policies differ by country.

## Main features

- Country-specific leave types
- Legal entity-specific leave rules
- Local holiday calendars
- Local workweek rules
- Statutory leave entitlement
- Local maternity/paternity rules
- Local sick leave rules
- Local leave reporting
- Language localization
- Date format localization
- Calendar localization

## Business logic

A tenant with multiple legal entities in different countries must be able to configure different leave rules per legal entity.

---

# 50. Notifications and Alerts

Leave workflows require automatic notifications.

## Notifications to employee

- Leave request submitted
- Leave approved
- Leave rejected
- Leave returned for correction
- Leave balance updated
- Leave cancellation approved
- Leave expiry warning
- Carry-forward completed
- Encashment approved
- Missing document reminder

## Notifications to manager

- Leave request pending approval
- Team member leave approved
- Leave conflict warning
- Minimum staffing warning
- Leave cancellation pending
- Unauthorized absence pending review

## Notifications to HR/payroll

- Special leave pending HR approval
- Unpaid leave payroll impact
- Leave encashment approved
- Negative balance warning
- Payroll cutoff approaching
- Leave period locked

---

# 51. Reports and Analytics

The module should provide operational and strategic reports.

## Standard reports

- Employee leave balance report
- Leave request report
- Approved leave report
- Pending leave report
- Rejected leave report
- Leave calendar report
- Team absence report
- Department absence report
- Legal entity leave report
- Leave accrual report
- Leave carry-forward report
- Leave expiry report
- Leave encashment report
- Leave adjustment report
- Unpaid leave report
- Sick leave report
- Maternity/paternity leave report
- Unauthorized absence report
- Leave payroll impact report
- Leave audit report

## Analytics KPIs

- Leave utilization rate
- Absence rate
- Average leave balance
- High absence employees
- High absence departments
- Sick leave frequency
- Unpaid leave trend
- Leave liability value
- Encashment cost
- Carry-forward liability
- Manager approval delay
- Leave cancellation rate
- Leave conflict rate

---

# 52. Leave Liability Reporting

Unused paid leave can represent financial liability.

## Main features

- Leave balance valuation
- Daily salary rate calculation
- Leave liability amount
- Liability by employee
- Liability by department
- Liability by legal entity
- Liability by cost center
- Accrued leave liability
- Finance export
- GL provision report

## Business logic

Example:

```text
Employee balance: 10 days
Daily salary rate: 60 AZN
Leave liability: 600 AZN
```

Finance may use this for accrual/provision accounting.

---

# 53. Security and Access Control

Leave data affects payroll, privacy, and workforce planning.

## Main features

- Role-based access
- Tenant-based access
- Legal entity-based access
- Department-based access
- Manager hierarchy access
- Employee self-service access
- HR special access
- Payroll access
- Medical document restriction
- Sick leave detail restriction
- Balance adjustment permission
- Encashment permission
- Export restriction
- Audit log access

## Example roles

| Role | Access |
|---|---|
| Employee | View own balance and submit requests |
| Manager | View/approve team leave |
| HR Leave Officer | Manage leave policies and balances |
| HR Manager | Approve special leave and adjustments |
| Payroll Officer | View payroll-impacting leave data |
| Finance Officer | View leave liability and encashment reports |
| Auditor | Read-only audit access |
| Tenant Admin | Configure tenant-specific leave settings |
| Platform Admin | Manage platform only, no tenant HR data by default |

---

# 54. Audit Trail

Every important leave action must be traceable.

## Audit should track

- Leave type creation
- Policy changes
- Accrual rule changes
- Leave request submission
- Leave request approval/rejection
- Leave cancellation
- Leave amendment
- Balance adjustment
- Accrual posting
- Carry-forward posting
- Expiry posting
- Encashment approval
- Payroll transfer
- Period lock/unlock
- Document verification
- Security changes

## Audit fields

- Tenant ID
- Action
- Old value
- New value
- Changed by
- Changed date/time
- Reason
- Approval reference
- Source transaction
- IP/device, optional

## Multi-tenant audit rule

Tenant administrators should see audit logs only for their tenant. Platform administrators should access tenant audit logs only under controlled support/audit permissions.

---

# 55. Integration With Other HCM / ERP Modules

## Employee Management

Leave uses:

- Employee ID
- Employment status
- Hire date
- Termination date
- Gender, where legally applicable
- Employment type
- Grade
- Department
- Position
- Manager
- FTE
- Work location

## Organizational Management

Leave uses:

- Legal entity
- Department
- Branch
- Location
- Work calendar
- Holiday calendar
- Cost center

## Position Management

Leave uses:

- Position eligibility
- Critical role flag
- Replacement requirement
- Minimum staffing rules
- Approval authority

## Time and Attendance

Leave sends:

- Approved leave dates
- Partial leave hours
- Unpaid leave days
- Absence conversion
- Leave cancellation updates

## Payroll

Leave sends:

- Paid leave
- Unpaid leave
- Leave encashment
- Negative balance recovery
- Sick leave pay split
- Maternity/paternity pay treatment
- Leave liability, if needed

## Finance

Leave sends:

- Leave liability
- Encashment cost
- Payroll deduction impact
- Cost center reporting

## Shift Scheduling

Leave sends:

- Employee unavailable dates
- Shift coverage impact
- Replacement requirement

## Document Management

Leave stores:

- Medical certificates
- Special leave documents
- Approval attachments
- Encashment documents

## Notification Engine

Leave triggers:

- Approval notifications
- Balance alerts
- Expiry alerts
- Payroll cutoff alerts
- Team absence alerts

---

# 56. External Integrations

Potential external integrations:

- Payroll providers
- Government leave/statutory portals, where applicable
- Insurance providers
- Medical certificate verification providers, where applicable
- Calendar systems such as Google Calendar or Microsoft Outlook
- Mobile app push notification service
- BI/reporting tools

---

# 57. Recommended Leave / Absence Menu

A practical ERP/HCM menu:

```text
Leave / Absence Management
│
├── Dashboard
├── My Leave
├── Leave Requests
├── Team Leave
├── Leave Calendar
├── Leave Balances
├── Leave Adjustments
├── Leave Accruals
├── Carry-Forward
├── Leave Encashment
├── Unauthorized Absences
├── Leave Approvals
├── Holiday Calendars
├── Work Calendars
├── Payroll Leave Summary
├── Reports
└── Settings
```

---

# 58. Recommended Leave Request Form Tabs

The leave request detail screen should include:

1. Overview
2. Employee Details
3. Leave Details
4. Balance Information
5. Calendar and Conflicts
6. Attachments
7. Delegation / Replacement
8. Approval Workflow
9. Payroll Impact
10. Activity History
11. Audit Trail

---

# 59. Recommended Leave Balance Screen Columns

- Employee ID
- Employee name
- Department
- Position
- Leave type
- Opening balance
- Accrued balance
- Used balance
- Pending balance
- Available balance
- Carry-forward balance
- Expiring balance
- Encashable balance
- Unit: days/hours
- As-of date
- Actions

Useful actions:

- View ledger
- Adjust balance
- Recalculate
- Encash
- Export
- View audit

---

# 60. Recommended Leave Request List Columns

- Request number
- Employee ID
- Employee name
- Department
- Leave type
- Start date
- End date
- Days/hours
- Request status
- Approval status
- Current approver
- Payroll status
- Submitted date
- Actions

Useful actions:

- View
- Approve
- Reject
- Return
- Cancel
- Amend
- Attach document
- View balance
- View audit

---

# 61. Recommended Main Data Entities

For technical design, do not store everything in one table.

Recommended entities:

- LeaveType
- LeaveCategory
- LeavePolicy
- LeaveEntitlementRule
- LeaveAccrualRule
- LeaveCarryForwardRule
- LeaveEncashmentRule
- LeaveBalance
- LeaveBalanceLedger
- LeaveRequest
- LeaveRequestLine
- LeaveApproval
- LeaveDocument
- LeaveAdjustment
- LeaveAccrualRun
- LeaveAccrualRunDetail
- LeaveCarryForwardRun
- LeaveEncashmentRequest
- HolidayCalendar
- HolidayCalendarDate
- WorkCalendar
- WorkCalendarDay
- LeaveBlackoutPeriod
- LeaveDelegation
- LeavePayrollSummary
- LeavePeriodLock
- LeaveAuditLog

Each entity should include tenant_id and audit fields.

---

# 62. Important Validation Rules

The system should validate:

- Employee must belong to the same tenant as the leave policy
- Leave type must belong to the employee’s tenant
- Employee must be active during requested leave dates
- Leave cannot be requested before hire date unless allowed
- Leave cannot be requested after termination date unless special policy allows
- Employee must be eligible for selected leave type
- Employee must have sufficient balance unless negative balance is allowed
- Leave request cannot overlap another approved or pending leave unless policy allows
- Required documents must be attached
- Request must respect minimum notice period
- Request must not exceed maximum days per request
- Leave cannot be submitted for locked payroll period unless authorized
- Leave cannot violate blackout dates unless exception approved
- Leave approval must follow tenant-specific workflow
- Manager cannot approve own leave
- Leave cancellation after payroll processing requires adjustment
- Sick leave documents must follow restricted access rules
- Accrual run must not process employees across tenants together
- Payroll export must include only approved and locked leave records

---

# 63. Common Mistakes to Avoid

## 1. Not designing for multi-tenancy from the start

Leave policies differ heavily by company and country. Tenant isolation and tenant-specific configuration are mandatory.

## 2. Storing only current balance

Always maintain a leave balance ledger. Without it, audits and disputes become difficult.

## 3. Ignoring work calendars

Leave calculation must use the employee’s actual work calendar and shift rules.

## 4. Weak payroll integration

Unpaid leave, encashment, and negative balance recovery must flow correctly to payroll.

## 5. No carry-forward expiry

Carry-forward without expiry control creates uncontrolled leave liability.

## 6. Exposing medical details to managers

Sick leave details and medical certificates require restricted access.

## 7. No cutoff lock

Leave changes after payroll must be controlled with retroactive adjustment.

## 8. No overlap and staffing checks

Managers need visibility into team availability before approving leave.

## 9. Hard-coding leave rules

Use a configurable rules engine. Different tenants, legal entities, and countries need different rules.

## 10. Mixing attendance absence and approved leave incorrectly

Approved leave should update attendance. Unauthorized absence should follow review workflow.

---

# 64. Final Recommended Launch Scope

For your ERP, the **Leave / Absence Management** module should launch as a complete multi-tenant enterprise solution covering:

- Tenant-specific leave configuration
- Legal entity-specific leave rules
- Leave type management
- Annual leave
- Sick leave
- Maternity leave
- Paternity leave
- Unpaid leave
- Study leave
- Business leave
- Special leave
- Leave entitlement rules
- Leave accrual rules
- Leave balance ledger
- Leave carry-forward
- Leave expiry
- Leave encashment
- Leave request workflow
- Manager approval
- HR approval
- Leave cancellation
- Leave amendment
- Leave adjustment
- Negative balance control
- Probation leave rules
- Notice period leave rules
- Blackout dates
- Minimum staffing rules
- Unauthorized absence handling
- Delegation and acting replacement
- Leave document management
- Leave calendar
- Team absence view
- Holiday calendars
- Work calendars
- Payroll integration
- Attendance integration
- Shift scheduling integration
- Employee self-service
- Manager self-service
- HR leave workspace
- Leave period locking
- Accrual processing
- Year-end processing
- Leave recalculation
- Multi-country localization
- Notifications
- Reports and analytics
- Leave liability reporting
- Security and access control
- Audit trail
- Full multi-tenant isolation

The most important design rule:

**Do not treat leave as only a request form. Build it as a tenant-configurable absence policy engine with balance ledger, approvals, calendars, payroll integration, attendance integration, and strict tenant-level data isolation.**
