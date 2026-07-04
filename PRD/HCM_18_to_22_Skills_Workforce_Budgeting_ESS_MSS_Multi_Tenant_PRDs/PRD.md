---
feature: hcm-18-22-skills-workforce-budgeting-ess-mss
module: self-service
payroll_impact: false
status: backlog
depends_on: []
---

# HCM Multi-Tenant PRDs — Modules 18 to 22

This document contains full enterprise PRDs for the following HCM modules:

18. Skills / Competency Management  
19. Workforce Planning  
20. HR Budgeting / Manpower Budgeting  
21. Employee Self-Service, ESS  
22. Manager Self-Service, MSS  

The system is assumed to be a **multi-tenant SaaS ERP/HCM platform**. Each module must support strict tenant data isolation, tenant-specific configuration, tenant-level workflows, tenant-specific reporting, tenant-level audit logs, and tenant-aware integrations.

---

# Global Multi-Tenancy Requirements for Modules 18–22

## 1. Tenant Data Isolation

Every table, transaction, configuration record, workflow record, document, report, dashboard, audit log, and integration payload must be scoped by `tenant_id`.

Core rules:

- No user from Tenant A can access Tenant B data.
- Every API must validate tenant context.
- Every background job must execute within tenant context.
- Every report query must filter by tenant.
- Every cache key must include tenant context.
- Every file/document path must include tenant isolation.
- Every integration token must belong to one tenant only.
- Global templates may exist, but tenant-specific copies must be created before modification.

Recommended tenant fields:

- `tenant_id`
- `legal_entity_id`
- `business_unit_id`
- `department_id`
- `location_id`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`
- `is_active`
- `effective_start_date`
- `effective_end_date`

## 2. Tenant-Specific Configuration

Each tenant must be able to configure:

- Skill libraries
- Competency frameworks
- Workforce planning cycles
- Budget cycles
- ESS permissions
- MSS permissions
- Approval workflows
- Notification templates
- Report layouts
- Dashboard widgets
- Security roles
- Numbering formats
- Import templates
- Integration mappings
- Localization rules
- Multi-currency rules
- Multi-language labels

## 3. Multi-Legal-Entity Support Inside a Tenant

A tenant may have multiple companies/legal entities. The modules must support:

- Legal entity-specific rules
- Department-specific planning
- Location-specific workforce assumptions
- Country-specific policies
- Currency-specific budgets
- Approval hierarchy per legal entity
- Reporting by group/company/legal entity/business unit/department/location

## 4. Shared Services Support

Some tenants may use shared HR teams across legal entities. The system must support:

- Group HR access across selected entities
- HR business partner access by department or business unit
- Finance access by cost center or budget owner
- Manager access by reporting hierarchy
- Project manager access by project assignments
- Auditor read-only access with tenant scope

## 5. Security and Audit Principles

All modules must include:

- Role-based access control
- Record-level access control
- Field-level security for sensitive fields
- Approval audit trail
- Change history
- Export/download audit
- API access audit
- Admin configuration audit
- Segregation of duties controls where needed

---

# 18. Skills / Competency Management PRD

## 18.1 Purpose

The **Skills / Competency Management** module manages organizational skill libraries, competency frameworks, employee skill profiles, required skills by job/position, skill verification, certification tracking, skill gap analysis, AI-based skill recommendations, and workforce skill inventory.

This module is the foundation for:

- Talent Management
- Learning Management
- Career Development
- Succession Planning
- Recruitment
- Performance Management
- Workforce Planning
- Internal Mobility
- Project Staffing
- Strategic Skills Analytics

Examples of similar capabilities exist in systems such as **Workday Skills Cloud, Oracle Dynamic Skills, and SAP Talent Intelligence Hub**.

## 18.2 Main Objectives

- Build a tenant-specific skill library.
- Define competency frameworks by job family, grade, and position.
- Maintain employee skill profiles.
- Map required skills to jobs and positions.
- Track skill proficiency levels.
- Verify and approve employee skills.
- Track certifications and licenses.
- Identify skill gaps.
- Recommend learning and career actions.
- Support AI-based skill suggestions.
- Provide workforce skill inventory analytics.

## 18.3 Multi-Tenant Skill Library

Each tenant must have its own skill library.

Features:

- Skill code
- Skill name
- Skill category
- Skill description
- Skill type
- Skill status
- Skill owner
- Skill aliases
- Skill synonyms
- Skill tags
- Related skills
- Parent skill
- Child skills
- Effective dates
- Tenant-specific localization
- Tenant-specific skill taxonomy

Skill categories may include:

- Technical skills
- Functional skills
- Leadership skills
- Language skills
- Digital skills
- Compliance skills
- Safety skills
- Sales skills
- Customer service skills
- Manufacturing skills
- Finance skills
- HR skills
- IT skills
- Retail skills
- Restaurant skills
- Project management skills

Skill types:

- Hard skill
- Soft skill
- Technical skill
- Behavioral competency
- Leadership competency
- Compliance skill
- Certification-backed skill
- Language skill
- Tool/software skill

Multi-tenancy rules:

- Global skill suggestions may be available, but tenants must be able to accept, rename, translate, or reject them.
- Tenant A’s skill names, mappings, proficiency levels, and employee skill profiles must never be visible to Tenant B.
- Skills imported by one tenant must not modify global libraries or other tenants.

## 18.4 Competency Framework

The competency framework defines expected behaviors, capabilities, and proficiency levels.

Features:

- Competency code
- Competency name
- Competency category
- Competency description
- Behavioral indicators
- Proficiency levels
- Rating scale
- Job family mapping
- Grade mapping
- Position mapping
- Department mapping
- Effective dates
- Version history
- Approval workflow

Competency categories:

- Core company competencies
- Leadership competencies
- Functional competencies
- Technical competencies
- Role-specific competencies
- Compliance competencies
- Customer-facing competencies

Example:

```text
Competency: Customer Orientation
Level 1: Responds politely to customer needs
Level 2: Resolves standard customer issues
Level 3: Anticipates customer needs
Level 4: Designs service improvements
Level 5: Leads customer experience strategy
```

## 18.5 Skill and Competency Levels

The system must support configurable levels per tenant.

Common models:

- Beginner / Intermediate / Advanced / Expert
- Level 1 to Level 5
- Awareness / Working Knowledge / Proficient / Advanced / Expert
- Not Demonstrated / Developing / Competent / Strong / Exceptional

Features:

- Level code
- Level name
- Numeric score
- Description
- Behavioral description
- Minimum evidence requirement
- Verification requirement
- Expiry rules

Business logic:

- Different tenants may use different level models.
- Different skill categories may use different level models.
- Required level by job can differ from employee current level.

## 18.6 Employee Skill Profile

Each employee should have a skill profile.

Features:

- Employee skills
- Skill level
- Self-assessed level
- Manager-assessed level
- HR-verified level
- Assessment-based level
- Certification-backed level
- Evidence attachment
- Skill source
- Skill expiry date
- Last verified date
- Verified by
- Skill interest flag
- Willingness to use skill
- Years of experience
- Skill notes

Skill sources:

- Employee self-entry
- Manager entry
- HR entry
- Performance review
- Learning completion
- Certification upload
- Recruitment candidate profile
- Assessment result
- AI suggestion
- Project assignment

## 18.7 Required Skills by Job / Position

Required skills should be mapped to jobs and positions.

Features:

- Required skill by job
- Required skill by position
- Required skill by grade
- Required skill by department
- Required proficiency level
- Mandatory/optional flag
- Critical skill flag
- Certification required flag
- Assessment required flag
- Effective dates

Business logic:

```text
Job: Senior Accountant
Required Skill: IFRS Reporting
Required Level: Advanced
Employee Current Level: Intermediate
Skill Gap: 1 level
Recommended Action: Assign IFRS Advanced Training
```

## 18.8 Skill Gap Analysis

The system should compare current skills with required skills.

Features:

- Employee-level skill gap
- Team-level skill gap
- Department-level skill gap
- Job-level skill gap
- Position-level skill gap
- Succession candidate skill gap
- Career path skill gap
- Project staffing skill gap
- Skill gap severity
- Recommended learning
- Recommended certification
- Development plan creation

Gap types:

- Missing skill
- Below required level
- Expired certification
- Unverified skill
- Insufficient experience

## 18.9 Skill Verification

Skills must be verified before being used for official decisions.

Features:

- Self-declared skill
- Manager verification
- HR verification
- Assessment verification
- Certification verification
- Peer endorsement
- Evidence upload
- Verification workflow
- Re-verification cycle
- Expiry rules

Verification statuses:

- Self-declared
- Pending verification
- Verified
- Rejected
- Expired
- Needs reassessment

## 18.10 Certification Tracking

Certifications often prove skills.

Features:

- Certification name
- Certification provider
- Certification number
- Issue date
- Expiry date
- Renewal date
- Attachment
- Verification status
- Required for job flag
- Required for position flag
- Certification expiry alerts
- Integration with LMS
- Integration with compliance reports

Business logic:

- If certification expires, linked skill may become expired or unverified.
- Mandatory certifications should block certain assignments if tenant policy requires it.

## 18.11 AI-Based Skill Recommendations

AI can suggest skills based on employee data.

Features:

- Suggest skills from job title
- Suggest skills from position
- Suggest skills from CV/profile
- Suggest skills from completed courses
- Suggest skills from project history
- Suggest related skills
- Suggest skill gaps
- Suggest learning plans
- Suggest career paths

Controls:

- AI suggestions require human confirmation.
- AI must not automatically overwrite verified skills.
- AI recommendations must be tenant-isolated.
- Tenant can disable AI features.
- AI model/provider can be configured per tenant.

## 18.12 Workforce Skill Inventory

The system should provide a complete inventory of organizational skills.

Features:

- Skills by department
- Skills by location
- Skills by job family
- Skills by grade
- Critical skill coverage
- Skill scarcity report
- Certification coverage
- Expiring certification report
- Skill bench strength
- Skill risk dashboard
- Skill distribution heatmap

## 18.13 Workflow and Approvals

Approval workflows:

- New skill creation approval
- Competency framework approval
- Employee skill verification
- Certification verification
- Skill level change approval
- Critical skill assignment approval

Approvers can include:

- Manager
- HR specialist
- Learning manager
- Compliance officer
- Department head
- Skill owner

## 18.14 Integration Requirements

Integrates with:

- Employee Management
- Job and Position Management
- Recruitment
- Performance Management
- LMS
- Career Development
- Succession Planning
- Workforce Planning
- Project Management
- Compliance
- AI services

## 18.15 Reports and Dashboards

Reports:

- Employee skill profile report
- Skill inventory report
- Skill gap report
- Required skills by job report
- Required skills by position report
- Certification expiry report
- Skill verification pending report
- Critical skill shortage report
- Skills by department report
- Skill growth trend report

Dashboards:

- Workforce skill inventory
- Critical skill coverage
- Skill gaps by department
- Certification compliance
- AI skill recommendations pending

## 18.16 Security and Audit

Sensitive controls:

- Employee can view and update own self-declared skills.
- Manager can view team skills.
- HR can manage skill frameworks.
- Learning team can map skills to courses.
- Succession team can view critical skill gaps.
- Tenant admins can configure skill models.

Audit must track:

- Skill creation
- Skill modification
- Skill deletion/inactivation
- Employee skill changes
- Verification decisions
- Certification changes
- AI recommendation acceptance/rejection

---

# 19. Workforce Planning PRD

## 19.1 Purpose

The **Workforce Planning** module supports strategic planning of workforce demand, supply, headcount, FTE, positions, hiring needs, workforce cost, attrition, vacancies, and long-term talent requirements.

It helps organizations answer:

- How many employees do we need?
- Where do we need them?
- What skills will we need?
- What will the workforce cost?
- Which roles are at risk?
- What vacancies must be filled?
- What scenarios should we prepare for?

Examples include **Workday Adaptive Planning, SAP Analytics Cloud Workforce Planning, and Oracle Strategic Workforce Planning**.

## 19.2 Main Objectives

- Plan future headcount.
- Plan FTE requirements.
- Plan positions and vacancies.
- Forecast workforce cost.
- Build hiring plans.
- Forecast attrition.
- Compare workforce demand vs supply.
- Run scenario planning.
- Integrate with budget and finance.
- Support long-term talent planning.

## 19.3 Workforce Planning Cycles

Features:

- Planning cycle name
- Planning year/period
- Legal entity
- Business unit
- Department
- Planning owner
- Submission deadline
- Approval workflow
- Scenario versions
- Baseline plan
- Approved plan
- Locked plan
- Archived plan

Planning frequencies:

- Annual
- Quarterly
- Monthly
- Rolling forecast
- Project-based
- Event-based, such as new store opening

## 19.4 Headcount Planning

Features:

- Current headcount
- Planned headcount
- Approved headcount
- Required headcount
- Forecast headcount
- New headcount request
- Replacement headcount
- Temporary headcount
- Contractor headcount
- Seasonal headcount
- Headcount by department
- Headcount by legal entity
- Headcount by location
- Headcount by job family
- Headcount by grade

Business logic:

```text
Current Headcount: 50
Expected Attrition: 5
New Demand: 10
Planned Headcount Required: 55
Hiring Need: 10
```

## 19.5 FTE Planning

Features:

- Current FTE
- Planned FTE
- Required FTE
- Vacant FTE
- Part-time FTE
- Contractor FTE
- Seasonal FTE
- FTE by position
- FTE by department
- FTE utilization

Example:

```text
Full-time employee = 1.0 FTE
Part-time employee = 0.5 FTE
Two half-time employees = 1.0 FTE
```

## 19.6 Position Planning

Features:

- Planned positions
- Approved positions
- New position request
- Position closure plan
- Position freeze plan
- Position transfer plan
- Position budget
- Position grade
- Position location
- Position effective date
- Vacancy plan
- Position approval workflow

Business logic:

- Workforce plan should feed Position Management.
- Approved planned positions can become position creation requests.
- Closed planned positions can trigger position closure workflows.

## 19.7 Hiring Plan

Features:

- Hiring plan by department
- Hiring plan by position
- Hiring plan by month
- Hiring priority
- Recruitment start date
- Target hire date
- Hiring manager
- Recruiter
- Recruitment budget
- Replacement hiring
- New headcount hiring
- Internal hiring
- External hiring

Integration:

- Approved hiring plan should feed Recruitment.
- Recruitment progress should update workforce plan actuals.

## 19.8 Workforce Cost Planning

Features:

- Salary cost planning
- Allowance cost planning
- Benefits cost planning
- Employer contribution planning
- Bonus cost planning
- Overtime cost planning
- Training cost planning
- Recruitment cost planning
- Total workforce cost
- Cost by department
- Cost by position
- Cost by legal entity
- Cost by cost center
- Multi-currency cost planning

Business logic:

```text
Total Workforce Cost = Salary + Allowances + Benefits + Employer Contributions + Bonus + Overtime + Planned Hiring Cost
```

## 19.9 Scenario Planning

Features:

- Create scenario
- Copy baseline plan
- Adjust headcount assumptions
- Adjust salary assumptions
- Adjust attrition assumptions
- Adjust hiring assumptions
- Adjust productivity assumptions
- Compare scenarios
- Scenario approval
- Scenario notes

Scenario examples:

- Open 5 new stores
- Reduce headcount by 10%
- Add night shift team
- Increase salaries by 8%
- Outsource warehouse operations
- Expand sales team in one region
- Hire 30 seasonal employees

## 19.10 Attrition Forecasting

Features:

- Historical attrition analysis
- Department attrition trend
- Grade attrition trend
- Location attrition trend
- Retirement forecast
- Contract expiry forecast
- Probation failure forecast
- Risk-based attrition forecast
- Replacement demand forecast

Inputs:

- Historical exits
- Tenure
- Performance
- Engagement
- Compensation position
- Manager history
- Critical roles
- Contract end dates

## 19.11 Demand vs Supply Planning

Features:

- Workforce demand forecast
- Internal workforce supply
- Skill supply
- Succession supply
- External hiring need
- Gap analysis
- Talent shortage alerts
- Workforce surplus alerts

Example:

```text
Required Data Analysts: 10
Current Available: 6
Internal Ready Candidates: 2
External Hiring Required: 2
```

## 19.12 Workforce Capacity Planning

Features:

- Required capacity
- Available capacity
- Productivity assumptions
- Working hours assumptions
- Shift capacity
- Project capacity
- Store capacity
- Manufacturing labor capacity
- Service workload capacity

Industries:

- Retail
- Restaurant
- Manufacturing
- Logistics
- Call center
- Healthcare
- Project services

## 19.13 Long-Term Talent Planning

Features:

- Critical role planning
- Future skill demand
- Leadership pipeline planning
- Succession demand
- Career path alignment
- Learning demand planning
- Internal mobility planning
- Talent pool planning

## 19.14 Budget Integration

Workforce Planning must integrate with HR Budgeting and Finance.

Features:

- Salary budget feed
- Position budget feed
- Hiring budget feed
- Training budget feed
- Benefits budget feed
- Approved plan to budget transfer
- Budget vs workforce plan comparison
- Finance approval workflow

## 19.15 Workflow and Approvals

Workflows:

- Workforce plan submission
- Department plan approval
- Finance budget approval
- HR review
- Executive approval
- Scenario approval
- Plan lock approval

Approvers:

- Department manager
- HRBP
- Finance controller
- HR director
- CFO
- CEO/executive

## 19.16 Multi-Tenant Planning Rules

Each tenant can configure:

- Planning calendar
- Planning templates
- Headcount categories
- FTE rules
- Cost assumptions
- Currency rules
- Approval workflow
- Scenario models
- Forecast methods

Tenant isolation:

- One tenant cannot see another tenant’s plans, assumptions, or workforce costs.
- Benchmarks may be aggregated only if anonymized and platform policy allows it.

## 19.17 Reports and Dashboards

Reports:

- Workforce plan report
- Headcount plan report
- FTE plan report
- Hiring plan report
- Vacancy plan report
- Attrition forecast report
- Workforce cost forecast
- Demand vs supply report
- Scenario comparison report
- Approved plan vs actual report

Dashboards:

- Planned vs actual headcount
- Workforce cost forecast
- Hiring demand by month
- Attrition risk forecast
- Future skill demand
- Workforce gaps

## 19.18 Security and Audit

Sensitive data:

- Future headcount changes
- Restructuring scenarios
- Workforce cost plans
- Salary assumptions
- Layoff/reduction scenarios

Audit must track:

- Plan creation
- Plan changes
- Scenario changes
- Approval decisions
- Budget transfer
- Plan locking/unlocking
- Export activity

---

# 20. HR Budgeting / Manpower Budgeting PRD

## 20.1 Purpose

The **HR Budgeting / Manpower Budgeting** module manages workforce-related budgets such as salary budget, position budget, headcount budget, benefits budget, training budget, recruitment budget, forecasted payroll cost, and actual vs budget comparison.

This module is especially important for ERP, public sector, enterprise groups, manufacturing companies, retail chains, and budget-controlled organizations.

## 20.2 Main Objectives

- Prepare salary budgets.
- Prepare position budgets.
- Prepare department budgets.
- Prepare headcount budgets.
- Prepare benefits budgets.
- Prepare training budgets.
- Prepare recruitment budgets.
- Forecast payroll costs.
- Compare actual vs budget.
- Control hiring and compensation against budget.
- Integrate with finance and general ledger.

## 20.3 Budget Cycles

Features:

- Budget cycle
- Budget year
- Budget period
- Budget version
- Budget owner
- Department budget owner
- Submission deadline
- Approval workflow
- Approved budget
- Revised budget
- Forecast budget
- Locked budget

Budget cycle types:

- Annual budget
- Quarterly budget
- Monthly forecast
- Rolling forecast
- Project budget
- Grant-funded budget
- Public sector staffing budget

## 20.4 Salary Budget

Features:

- Basic salary budget
- Monthly salary budget
- Annual salary budget
- Salary increase assumptions
- Merit increase budget
- Promotion increase budget
- Market adjustment budget
- New hire salary budget
- Replacement salary budget
- Salary budget by department
- Salary budget by position
- Salary budget by grade
- Salary budget by cost center

Business logic:

```text
Annual Salary Budget = Monthly Basic Salary × 12 + Planned Salary Increases + New Hire Salary Cost - Planned Exits
```

## 20.5 Position Budget

Features:

- Position-level budget
- Budgeted salary by position
- Budgeted allowances
- Budgeted employer contributions
- Budgeted benefits
- Budgeted bonus
- Budgeted total cost
- Funded/unfunded status
- Vacancy budget
- Position budget history

Business logic:

- Position Management should consume approved position budgets.
- Recruitment should be blocked or warned if position is unfunded.

## 20.6 Department Budget

Features:

- Department salary budget
- Department headcount budget
- Department benefits budget
- Department training budget
- Department recruitment budget
- Department overtime budget
- Department total manpower cost
- Budget owner
- Department approval workflow

## 20.7 Headcount Budget

Features:

- Approved headcount
- Budgeted headcount
- Actual headcount
- Vacant headcount
- New headcount requests
- Replacement headcount
- Temporary headcount
- Contractor headcount
- Seasonal headcount
- Headcount budget by month

Business logic:

```text
Approved Headcount: 100
Actual Headcount: 92
Vacancies: 8
Recruitment allowed up to 8, subject to budget and position control.
```

## 20.8 Benefits Budget

Features:

- Health insurance budget
- Life insurance budget
- Pension contribution budget
- Meal allowance budget
- Transport allowance budget
- Housing allowance budget
- Flexible benefit budget
- Employer contribution budget
- Employee contribution forecast
- Benefits by plan
- Benefits by department

## 20.9 Training Budget

Features:

- Training budget by department
- Training budget by course
- Training budget by skill gap
- Mandatory training budget
- Certification budget
- External training budget
- Instructor cost
- Venue cost
- Travel cost
- LMS cost integration

## 20.10 Recruitment Budget

Features:

- Recruitment budget by department
- Job board cost budget
- Agency fee budget
- Referral bonus budget
- Assessment cost budget
- Background check cost budget
- Campaign budget
- Campus recruitment budget
- Cost per hire target

## 20.11 Forecasted Payroll Cost

Features:

- Payroll forecast by month
- Salary forecast
- Allowance forecast
- Overtime forecast
- Bonus forecast
- Employer contribution forecast
- Benefits deduction forecast
- Loan deduction forecast
- New hire forecast
- Exit forecast
- Retro payroll forecast

## 20.12 Actual vs Budget Comparison

Features:

- Actual salary cost vs budget
- Actual headcount vs budget
- Actual benefits cost vs budget
- Actual overtime vs budget
- Actual recruitment cost vs budget
- Actual training cost vs budget
- Variance amount
- Variance percentage
- Variance reason
- Variance approval
- Forecast adjustment

Example:

```text
Department Salary Budget: 100,000 AZN
Actual Salary Cost: 108,000 AZN
Variance: -8,000 AZN / -8%
Status: Over budget
```

## 20.13 Budget Approval Workflow

Features:

- Budget submission
- Department manager approval
- HR review
- Finance review
- CFO approval
- Executive approval
- Return for correction
- Revision workflow
- Budget lock
- Budget unlock request
- Approval comments

Workflow example:

```text
Department Manager
→ HR Budget Owner
→ Finance Controller
→ HR Director
→ CFO
→ CEO
```

## 20.14 Budget Control Rules

The system should control HR transactions against budget.

Control points:

- New position creation
- Recruitment requisition
- Offer salary
- Salary increase
- Promotion salary adjustment
- Bonus payout
- Overtime approval
- Training request
- Benefits enrollment

Control actions:

- Allow
- Warn
- Require approval
- Block

Example:

```text
Offer salary exceeds position budget by 10%.
System requires finance exception approval.
```

## 20.15 Integration With Finance

Finance integration is mandatory.

Features:

- Cost center integration
- GL account mapping
- Budget account mapping
- Payroll actuals import
- Encumbrance/pre-commitment
- Budget consumption
- Budget transfer
- Journal posting
- Financial reporting

Finance-owned data:

- GL accounts
- Cost centers
- Budget accounts
- Fiscal calendar
- Approved financial budgets

HCM-owned data:

- Positions
- Employees
- Salary assumptions
- Headcount plans
- Workforce cost details

## 20.16 Multi-Currency Budgeting

Features:

- Budget currency
- Employee salary currency
- Legal entity currency
- Group reporting currency
- Exchange rate source
- Exchange rate date
- Currency conversion
- Currency variance report

## 20.17 Multi-Tenant Rules

Each tenant can configure:

- Budget calendars
- Budget templates
- Approval flows
- Currency rules
- Cost elements
- Budget control rules
- Finance integration mappings
- Variance thresholds

Tenant isolation:

- Budget plans, salary assumptions, and financial forecasts are highly sensitive and must be strictly tenant-isolated.

## 20.18 Reports and Dashboards

Reports:

- Salary budget report
- Position budget report
- Headcount budget report
- Benefits budget report
- Training budget report
- Recruitment budget report
- Forecast payroll cost report
- Actual vs budget report
- Budget variance report
- Over-budget departments
- Budget approval status report

Dashboards:

- Total manpower budget
- Budget vs actual
- Forecast payroll cost
- Over-budget departments
- Budget utilization
- Headcount budget utilization

## 20.19 Security and Audit

Security:

- Salary budget access restriction
- Department budget owner access
- Finance access
- HR budget access
- Executive access
- Export restriction

Audit must track:

- Budget creation
- Budget updates
- Budget approvals
- Budget revisions
- Budget transfers
- Budget lock/unlock
- Budget control exceptions
- Finance posting changes

---

# 21. Employee Self-Service, ESS PRD

## 21.1 Purpose

The **Employee Self-Service (ESS)** module allows employees to access and manage their own HR tasks without depending on HR staff for every request.

ESS improves employee experience, reduces HR workload, and provides a controlled digital channel for personal data updates, leave requests, documents, payslips, attendance corrections, policies, benefits, HR requests, and approvals.

## 21.2 Main Objectives

- Allow employees to view their profile.
- Allow employees to update allowed personal data.
- Allow leave requests.
- Show leave balances.
- Show payslips.
- Allow document submission.
- Allow expense requests.
- Allow attendance corrections.
- Show company policies.
- Show benefits.
- Show team calendar where allowed.
- Allow HR service requests.
- Show approvals and request statuses.

## 21.3 ESS Home Dashboard

Features:

- Employee profile summary
- Pending tasks
- Pending approvals
- Leave balance widget
- Attendance summary
- Payslip shortcut
- Benefits summary
- Company announcements
- Policy acknowledgements
- Upcoming holidays
- Team absences
- HR request status
- Birthday/work anniversary reminders, if enabled

## 21.4 View Employee Profile

Employee can view:

- Personal information
- Contact information
- Employment information
- Job and position
- Department
- Manager
- Work location
- Contract information
- Dependents
- Emergency contacts
- Education
- Skills
- Certifications
- Bank details, masked
- Documents

Security:

- Salary visibility depends on tenant policy.
- Bank details should be masked.
- Sensitive fields require field-level permissions.

## 21.5 Update Personal Information

Employee can request updates to:

- Personal phone
- Personal email
- Address
- Emergency contacts
- Dependents
- Marital status
- Education
- Certifications
- Skills
- Bank account details
- Tax/social insurance information, if allowed

Business logic:

- Some updates can be direct.
- Sensitive updates require approval.
- Required documents can be requested.
- Changes must be audited.

Example:

```text
Employee updates phone number → direct update
Employee updates bank account → payroll approval required
Employee adds dependent → HR approval and document required
```

## 21.6 Leave Requests

Employee can:

- Request annual leave
- Request sick leave
- Request unpaid leave
- Request maternity/paternity leave
- Request business leave
- Request study leave
- Request special leave
- Upload documents
- View approval workflow
- Cancel leave request
- Modify leave request
- View leave history

Validation:

- Leave balance availability
- Holiday/weekend exclusion
- Overlapping leave prevention
- Notice period rule
- Minimum/maximum leave duration
- Attachment requirement
- Probation restrictions

## 21.7 View Leave Balance

Employee can view:

- Current balance
- Accrued balance
- Used leave
- Pending leave
- Carry-forward balance
- Expiring leave
- Leave encashment eligibility
- Leave adjustment history

## 21.8 View Payslip

Employee can view:

- Payslip by period
- Earnings
- Deductions
- Allowances
- Bonuses
- Overtime
- Tax/social insurance
- Net pay
- Bank payment details, masked
- Year-to-date totals
- Download PDF, if allowed

Security:

- Payslip requires strong access control.
- Optional password/MFA before payslip download.
- Download actions should be audited.

## 21.9 Submit Documents

Employee can upload:

- ID documents
- Passport
- Education certificates
- Medical certificate
- Bank documents
- Dependent documents
- Training certificates
- Tax/social insurance documents
- Other HR requested documents

Features:

- Document type
- Expiry date
- Issue date
- Attachment
- Verification status
- Rejection reason
- Resubmission

## 21.10 Submit Expense Requests

Employee can:

- Submit expense claim
- Upload receipt
- Select expense category
- Select project/cost center
- Enter amount and currency
- Submit travel claim
- Submit mileage claim
- View approval status
- View reimbursement status

Integration:

- Finance
- Payroll reimbursement
- Project accounting
- Cost center budget

## 21.11 Attendance Corrections

Employee can:

- View attendance
- Submit missing punch request
- Correct clock-in/out
- Submit late reason
- Submit early leave reason
- Request overtime approval
- View approval status

Workflow:

```text
Employee correction request
→ Manager approval
→ HR/payroll review, if required
→ Attendance recalculation
```

## 21.12 Company Policies

Employee can:

- View policies
- Search policy library
- Download allowed policies
- Acknowledge policies
- View acknowledgement history
- Receive policy update notifications

Policy examples:

- HR policy
- Leave policy
- Code of conduct
- IT policy
- Safety policy
- Expense policy
- Remote work policy
- Data privacy policy

## 21.13 Benefits View and Enrollment

Employee can:

- View enrolled benefits
- View benefit eligibility
- Enroll in benefits, if allowed
- Add dependents
- Change benefit plan during open enrollment
- View employer/employee contribution
- View benefit provider details
- Submit benefit claims, if supported

## 21.14 Team Calendar

Employee can view, based on permission:

- Team leave calendar
- Public holidays
- Department events
- Training calendar
- Work schedule
- Team availability

Privacy:

- Leave type visibility can be restricted.
- Medical/sick leave details should be hidden unless policy allows.

## 21.15 HR Requests / Helpdesk

Employee can submit HR requests:

- Salary certificate
- Employment letter
- Experience letter
- Policy question
- Document request
- Profile correction
- Payroll inquiry
- Benefits inquiry
- Complaint/grievance
- General HR question

Features:

- Request category
- Priority
- SLA
- Attachments
- Comments
- Status tracking
- HR response
- Reopen request

## 21.16 View Approvals and Requests

Employee can view:

- My requests
- Request status
- Current approver
- Approval history
- Comments
- Rejection reason
- Pending action
- Completed requests

## 21.17 ESS Mobile App

Mobile features:

- Profile view
- Leave request
- Attendance correction
- Payslip view
- Document upload
- Push notifications
- Policy acknowledgement
- HR request submission
- Benefits view
- Approval status tracking

## 21.18 Multi-Tenant ESS Configuration

Each tenant can configure:

- ESS menu visibility
- ESS field visibility
- Self-update rules
- Approval workflows
- Document requirements
- Payslip visibility
- Policy acknowledgement rules
- Mobile access rules
- MFA requirements
- Branding/logo/theme
- Languages

## 21.19 Reports and Dashboards

Reports:

- ESS usage report
- Profile update requests
- Leave requests by status
- Document submissions
- Payslip downloads
- Policy acknowledgements
- HR requests report
- Attendance correction requests

Dashboards:

- Employee pending tasks
- HR pending ESS approvals
- Policy acknowledgement completion
- Self-service adoption rate

## 21.20 Security and Audit

Security:

- Employee can access only own records.
- Payslip and bank details require extra protection.
- Documents must be access-controlled.
- Mobile sessions must be secured.

Audit must track:

- Profile views where required
- Profile changes
- Document uploads/downloads
- Payslip downloads
- Leave requests
- Attendance corrections
- Policy acknowledgements
- HR request submissions

---

# 22. Manager Self-Service, MSS PRD

## 22.1 Purpose

The **Manager Self-Service (MSS)** module allows managers to manage team-related HR tasks directly from the system.

It gives managers visibility and controlled action capability over their teams, while HR maintains governance through workflows, security, policies, and audit trails.

## 22.2 Main Objectives

- Approve leave.
- Approve attendance corrections.
- Approve overtime.
- View team profiles.
- View team attendance.
- View team performance.
- Initiate transfers.
- Initiate promotions.
- Initiate terminations.
- Submit hiring requests.
- View team compensation, if allowed.
- View team analytics.
- Manage probation reviews.

## 22.3 Manager Dashboard

Features:

- Team headcount
- Direct reports
- Indirect reports
- Pending approvals
- Team leave today
- Team attendance today
- Late employees
- Absent employees
- Probation reviews due
- Performance reviews due
- Open positions
- Vacancies
- Hiring requests
- Team birthdays/work anniversaries
- HR alerts

## 22.4 Team Profile View

Manager can view:

- Employee name
- Employee ID
- Position
- Job title
- Department
- Work location
- Employment status
- Hire date
- Contract end date
- Probation status
- Skills
- Certifications
- Training status
- Leave balance summary, if allowed
- Attendance summary
- Performance summary

Security:

- Sensitive fields such as salary, bank details, national ID, medical documents, and disciplinary records require explicit permission.

## 22.5 Approve Leave

Features:

- Leave approval inbox
- Team leave calendar
- Leave balance view
- Overlap warning
- Coverage warning
- Leave approval/rejection
- Return for correction
- Approval comments
- Delegation
- Escalation

Business logic:

- Manager should see team coverage before approving leave.
- Some leave types may require HR approval after manager approval.
- Medical/sick leave details can be restricted.

## 22.6 Approve Attendance Corrections

Features:

- Missing punch approvals
- Clock-in correction approval
- Clock-out correction approval
- Late reason approval
- Early leave approval
- Attendance exception approval
- Comments
- Bulk approval

Validation:

- Manager cannot approve outside hierarchy unless delegated.
- Manager cannot approve own correction.
- Payroll-locked records require HR/payroll approval.

## 22.7 Approve Overtime

Features:

- Overtime request approval
- Pre-approved overtime
- Post-work overtime approval
- Overtime reason
- Overtime hours
- Overtime cost estimate
- Overtime budget warning
- Approval comments
- Escalation if threshold exceeded

Business logic:

```text
Requested overtime: 5 hours
Manager approval limit: 3 hours
System routes to Department Head after manager approval.
```

## 22.8 View Team Attendance

Manager can view:

- Daily team attendance
- Monthly team attendance
- Present employees
- Absent employees
- Late employees
- Early leave
- Missing punches
- Overtime
- Shift coverage
- Attendance trends

## 22.9 View Team Performance

Manager can view/manage:

- Team goals
- KPI progress
- Performance reviews
- Self-assessments
- Manager assessments
- 360 feedback tasks
- Rating history
- Development plans
- Performance improvement plans
- Calibration results, if allowed

## 22.10 Initiate Transfer

Manager can initiate:

- Department transfer
- Position transfer
- Location transfer
- Manager change
- Cost center change
- Temporary assignment
- Project assignment

Fields:

- Employee
- Current assignment
- Proposed assignment
- Effective date
- Reason
- Comments
- Attachments

Workflow:

```text
Manager request
→ Current department approval
→ New department approval
→ HR approval
→ Finance/cost center approval, if needed
→ Employee record update
```

## 22.11 Initiate Promotion

Manager can initiate promotion request.

Features:

- Proposed position
- Proposed grade
- Proposed salary
- Promotion reason
- Performance justification
- Effective date
- Budget validation
- Compensation review
- Approval workflow

Workflow may include:

- Department head
- HR
- Compensation team
- Finance
- HR director
- Executive approval

## 22.12 Initiate Termination

Manager can initiate termination request only if tenant policy allows it.

Features:

- Termination request
- Exit reason
- Proposed last working day
- Documentation
- HR review
- Legal review, if required
- Confidential handling
- Approval workflow

Security:

- This function must be highly restricted.
- Sensitive termination details must not be visible to unauthorized managers.

## 22.13 Submit Hiring Request

Manager can request:

- New headcount
- Replacement
- Temporary hire
- Contractor
- Intern
- Seasonal hire

Features:

- Position selection
- Vacancy validation
- Hiring reason
- Target start date
- Budget validation
- Requisition creation
- Approval workflow

Integration:

- Position Management
- Workforce Planning
- HR Budgeting
- Recruitment

## 22.14 View Team Compensation

If allowed by tenant policy, manager can view:

- Salary summary
- Pay grade
- Salary range position
- Compensation history
- Bonus eligibility
- Merit recommendation
- Total compensation statement
- Team compensation budget

Security:

- Compensation visibility must be configurable by tenant.
- Salary access must be field-level and role-controlled.
- Export should be restricted.

## 22.15 Team Analytics

Manager analytics:

- Team headcount
- Turnover
- Absence rate
- Overtime hours
- Performance distribution
- Skill gaps
- Training completion
- Probation status
- Vacancy status
- Cost center labor cost
- Leave liability
- Engagement score, if integrated

## 22.16 Manage Probation Reviews

Features:

- Probation review reminders
- Mid-probation review
- Final probation review
- Manager feedback
- HR feedback
- Confirmation recommendation
- Extension recommendation
- Termination recommendation
- Probation letter trigger

Outcomes:

- Confirm employment
- Extend probation
- Terminate employment
- Change role
- Require additional review

## 22.17 Manager Approvals Inbox

A unified approval inbox should include:

- Leave approvals
- Attendance corrections
- Overtime approvals
- Expense approvals
- Profile update approvals
- Training approvals
- Transfer requests
- Promotion requests
- Hiring requests
- Probation reviews
- Document approvals

Features:

- Filter by type
- Bulk approve where allowed
- Delegate approvals
- Add comments
- Reject/return
- SLA indicators
- Escalation indicators

## 22.18 Delegation and Acting Manager

Features:

- Temporary delegation
- Acting manager assignment
- Delegation start/end date
- Delegated approval types
- Delegation reason
- Audit trail

Business logic:

- Delegation must be time-bound.
- Sensitive approvals may be excluded from delegation.
- Employee cannot approve own requests through delegation loophole.

## 22.19 Multi-Tenant MSS Configuration

Each tenant can configure:

- Manager hierarchy rules
- Direct/indirect report access
- Matrix manager permissions
- MSS menus
- Approval authority
- Compensation visibility
- Termination request permission
- Transfer/promotion workflow
- Analytics visibility
- Delegation rules
- Mobile access

## 22.20 Reports and Dashboards

Reports:

- Manager pending approvals
- Team leave report
- Team attendance report
- Team overtime report
- Team performance report
- Team skill gap report
- Team training report
- Probation review report
- Manager action audit report

Dashboards:

- Manager team dashboard
- Team attendance dashboard
- Team performance dashboard
- Team talent dashboard
- Team workforce dashboard

## 22.21 Security and Audit

Security:

- Manager access must follow reporting hierarchy.
- Matrix manager access must be explicitly configured.
- Compensation visibility must be restricted.
- Termination and disciplinary data must be restricted.
- Managers must not access data after employee moves out of their hierarchy, except historical workflow records where allowed.

Audit must track:

- Approvals
- Rejections
- Comments
- Team data exports
- Compensation views, if required
- Transfer initiation
- Promotion initiation
- Termination initiation
- Delegation changes

---

# Combined Implementation Notes for Modules 18–22

## Cross-Module Dependencies

- Skills feed Learning, Career Development, Succession, Recruitment, and Workforce Planning.
- Workforce Planning feeds Position Management, Recruitment, HR Budgeting, and Finance.
- HR Budgeting controls Payroll, Compensation, Recruitment, Training, and Position Management.
- ESS uses Employee Management, Leave, Attendance, Payroll, Benefits, Documents, and HR Helpdesk.
- MSS uses Employee Management, Leave, Attendance, Performance, Recruitment, Compensation, Probation, and Workflow.

## Core Shared Services Required

These modules require common platform services:

- Multi-tenant identity and access management
- Workflow and approval engine
- Notification engine
- Document management
- Audit log
- Reporting engine
- Import/export engine
- Localization/multi-language service
- Multi-currency service
- Organization hierarchy service
- Role and permission service
- API integration framework

## Final Launch Scope Summary

The combined launch scope for modules 18–22 must include:

- Tenant-specific skill libraries
- Competency frameworks
- Employee skill profiles
- Required skills by job/position
- Skill gap analysis
- Skill verification
- Certification tracking
- AI-based skill recommendations
- Workforce skill inventory
- Headcount planning
- FTE planning
- Position planning
- Hiring planning
- Workforce cost planning
- Scenario planning
- Attrition forecasting
- Demand vs supply planning
- Long-term talent planning
- Salary budgets
- Position budgets
- Department budgets
- Headcount budgets
- Benefits budgets
- Training budgets
- Recruitment budgets
- Forecasted payroll cost
- Actual vs budget comparison
- Budget approval workflows
- ESS profile, leave, payslip, documents, attendance corrections, benefits, policies, HR requests
- MSS approvals, team profile, team attendance, team performance, transfers, promotions, terminations, hiring requests, compensation, analytics, probation reviews
- Full multi-tenant security, reporting, workflow, integrations, and audit controls

The most important design rule:

**Skills, workforce plans, budgets, ESS actions, and MSS actions must all be tenant-isolated, permission-controlled, workflow-driven, auditable, and integrated with the core HCM/ERP master data model.**
