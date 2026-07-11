---
feature: hcm-50-mobile-hr
module: self-service
payroll_impact: false
status: backlog
depends_on: []
---

# HCM Module 50 — Mobile HR PRD
## Multi-Tenant Enterprise HCM / ERP Product Requirements Document

## 0. Document Control

| Field | Value |
|---|---|
| Module | Mobile HR |
| Product Area | Human Capital Management |
| Scope | Full enterprise launch scope |
| Multi-Tenancy | Required |
| Target Users | Employees, Managers, HR, Payroll, IT Admins, System Admins |
| Related Modules | Employee Management, Leave, Payroll, Time & Attendance, Learning, HR Service Delivery, Workflow & Approvals, Document Management, Notifications, Security/RBAC, Integration Management |

---

# 1. Module Overview

The **Mobile HR** module provides mobile access to core HCM functionality for employees, managers, HR users, and field workers. It allows users to complete common HR actions from a mobile phone without needing desktop access.

Modern HCM systems usually provide mobile HR because employees and managers expect fast self-service access for leave requests, payslips, attendance, approvals, HR requests, learning, announcements, and employee profile updates.

The module should support both:

- Employee Self-Service mobile features
- Manager Self-Service mobile features
- HR operational mobile features where appropriate
- Field workforce and branch workforce mobile attendance
- Push notifications and mobile-first workflows

The module must be designed for a **multi-tenant SaaS ERP/HCM system**, where every tenant has isolated data, tenant-specific branding, tenant-specific policies, tenant-specific mobile access rules, and tenant-specific integrations.

---

# 2. Purpose of Mobile HR

The purpose of the Mobile HR module is to make HR services accessible anywhere, while keeping security, compliance, payroll accuracy, and tenant isolation intact.

## Main objectives

- Allow employees to access HR services from mobile
- Allow managers to approve requests quickly
- Support mobile leave requests
- Support mobile attendance and GPS clock-in
- Support mobile payslip viewing
- Support mobile employee profile access
- Support mobile HR requests and helpdesk cases
- Support mobile learning access
- Support mobile announcements and communication
- Support push notifications
- Support secure authentication
- Support role-based mobile access
- Support tenant-specific mobile configuration
- Support audit logging of mobile actions
- Support offline or poor-connectivity scenarios where required

---

# 3. Multi-Tenant Requirements

Mobile HR must be designed with multi-tenancy from the beginning.

## 3.1 Tenant isolation

Every mobile request must include and validate tenant context.

The system must ensure:

- One tenant cannot access another tenant’s employee data
- Mobile session is bound to a tenant
- API tokens are tenant-scoped
- Push notification tokens are tenant-scoped
- Mobile device records are tenant-scoped
- Mobile attendance locations are tenant-scoped
- Tenant branding is isolated
- Tenant settings are isolated
- Tenant documents and payslips are isolated

## 3.2 Tenant-specific configuration

Each tenant should configure:

- Enabled mobile features
- Mobile app branding
- Mobile login method
- Mobile attendance rules
- GPS/geofence requirements
- Selfie requirement for attendance
- Device binding rules
- Push notification templates
- Mobile approval rules
- Document visibility rules
- Payslip visibility rules
- Offline mode rules
- Mobile security rules
- Mobile session timeout
- Supported languages
- Tenant-specific terms and privacy notices

## 3.3 Tenant branding

Mobile app should support tenant-specific branding:

- Tenant logo
- Primary color
- Login screen branding
- App home screen branding
- Company name
- Announcement banner
- Helpdesk contact details
- Support links
- Policy links

## 3.4 Tenant-aware permissions

Mobile permissions must respect the same RBAC and data security rules as the web ERP.

Examples:

- Employee sees only own data
- Manager sees only reporting hierarchy
- HR officer sees only assigned legal entity or branch
- Payroll officer can view payroll-related screens if allowed
- Tenant admin can configure tenant-level mobile settings
- Super admin cannot casually access tenant HR data without controlled support access

## 3.5 Tenant-specific localization

Each tenant may require different:

- Language
- Date format
- Time format
- Currency
- Calendar
- Holiday rules
- Payroll period display
- Legal privacy text
- Document templates
- Approval terminology

---

# 4. User Roles

## Employee

Can use mobile self-service features:

- View profile
- Update allowed personal information
- Request leave
- View leave balance
- View payslip
- Clock in/out if allowed
- Submit attendance correction
- Submit HR request
- View policies
- Upload documents
- Access learning
- View announcements
- Receive push notifications

## Manager

Can use mobile manager self-service features:

- Approve leave
- Approve attendance correction
- Approve overtime
- View team attendance
- View team calendar
- View team profiles
- Approve HR requests
- View team learning progress
- View pending tasks
- Receive approval alerts
- Initiate selected HR actions if allowed

## HR User

Can use selected mobile HR features:

- View employee profile summaries
- Track HR requests
- Respond to cases
- Approve workflow tasks
- View onboarding/offboarding tasks
- View documents if permitted
- Send announcements if allowed

## Payroll User

Can use restricted mobile payroll features:

- View payroll approval tasks
- View payroll status summaries
- Approve payroll workflow if allowed
- View payroll notifications
- View final settlement tasks if allowed

## Tenant Admin

Can configure tenant-specific mobile settings:

- Enabled features
- Mobile attendance policies
- Push notification settings
- Branding
- Security rules
- Device management
- Allowed mobile roles

---

# 5. Mobile Home Dashboard

The mobile app should provide a role-based home screen.

## Employee dashboard widgets

- My profile
- Leave balance
- Request leave
- Today’s attendance status
- Clock-in / clock-out
- Latest payslip
- Pending HR requests
- Pending documents
- My learning
- Company announcements
- Upcoming holidays
- Upcoming birthdays/work anniversaries, if enabled
- Pending policy acknowledgements

## Manager dashboard widgets

- Pending approvals
- Team attendance today
- Team leave calendar
- Team requests
- Team members absent today
- Overtime pending approval
- Attendance corrections pending
- Probation reviews pending
- Learning completion alerts
- HR cases pending manager action

## HR dashboard widgets

- HR requests pending
- Onboarding tasks
- Offboarding tasks
- Document approvals
- Policy acknowledgements pending
- Employee exceptions
- Announcements
- Mobile attendance exceptions

## Tenant admin dashboard widgets

- Active mobile users
- Registered devices
- Failed login attempts
- Push notification status
- Mobile attendance exceptions
- App version distribution
- Device compliance warnings

---

# 6. Mobile Employee Profile

Employees should be able to view and, where allowed, update their profile.

## Main features

- View employee photo
- View employee ID
- View name
- View job title
- View department
- View manager
- View branch/location
- View work email
- View work phone
- View personal contact details
- View emergency contacts
- View dependents, if enabled
- View documents, if enabled
- Update allowed personal fields
- Submit change requests
- View approval status of profile changes

## Editable profile fields, configurable by tenant

- Personal phone
- Personal email
- Address
- Emergency contact
- Marital status, if allowed
- Dependent information
- Bank details, if allowed and approval-controlled
- Profile photo, if allowed
- Document uploads

## Business logic

Sensitive updates must follow approval workflows.

Examples:

```text
Phone number change → direct update or HR approval, tenant-configurable
Bank details change → payroll approval required
Dependent addition → HR approval required
Address change → HR approval optional
```

---

# 7. Mobile Leave Request

Employees should be able to request leave through mobile.

## Main features

- View leave balance
- View leave types
- Select leave type
- Select start date
- Select end date
- Select full-day or half-day
- Enter leave reason
- Attach document
- View replacement person, if required
- Submit leave request
- View approval status
- Cancel leave request
- View leave history
- View team calendar, if allowed

## Leave types

- Annual leave
- Sick leave
- Maternity leave
- Paternity leave
- Unpaid leave
- Study leave
- Business leave
- Special leave
- Compensatory leave
- Tenant-defined leave types

## Business logic

The mobile leave request should validate:

- Employee has enough balance
- Leave dates are valid
- Leave does not overlap existing leave
- Leave does not conflict with payroll lock
- Required attachment exists
- Employee is eligible for selected leave type
- Manager/approver exists
- Holiday calendar is applied correctly
- Tenant-specific rules are applied

Example:

```text
Employee requests 5 days annual leave.
Available balance: 3 days.
System blocks or routes for exception approval based on tenant policy.
```

---

# 8. Mobile Leave Balance

Employees should see accurate leave balances.

## Main features

- Current leave balance
- Accrued balance
- Used leave
- Pending leave
- Approved leave
- Carried-forward leave
- Expiring leave
- Leave balance as of date
- Leave history
- Leave calendar
- Leave policy summary

## Business logic

Leave balance must be calculated using tenant-specific leave policies and employee eligibility rules.

The mobile app should not calculate balances independently. It should display balances from the backend leave engine.

---

# 9. Mobile Payslip

Employees should be able to securely view payslips.

## Main features

- View latest payslip
- View payslip history
- Download payslip PDF
- View gross salary
- View earnings
- View deductions
- View net pay
- View tax/social insurance, if applicable
- View employer contributions, if allowed
- View payment date
- View payroll period
- Payslip password protection, optional
- Payslip sharing/download restriction, tenant-configurable

## Security requirements

Payslips contain sensitive data.

The system should support:

- Re-authentication before opening payslip
- Biometric app unlock
- Screenshot blocking, if feasible by platform
- Download restriction, if enabled
- Watermarked PDF
- Audit log for payslip view/download
- Tenant-specific visibility rules

## Business logic

Payslips should be visible only after payroll is approved and published.

Example:

```text
Payroll status: Draft
Employee mobile access: Not visible

Payroll status: Approved and published
Employee mobile access: Visible
```

---

# 10. Mobile Attendance

Mobile attendance allows employees to record attendance from a phone.

## Main features

- Mobile clock-in
- Mobile clock-out
- Break start/end
- GPS capture
- Selfie capture
- Device ID capture
- Timestamp capture
- Location accuracy capture
- Offline attendance, if enabled
- Attendance reason
- Remote work attendance
- Field work attendance
- Project/site attendance
- Attendance history
- Missing punch request
- Attendance correction request

## Mobile attendance source data

Each mobile punch should store:

- Tenant ID
- Employee ID
- Device ID
- Timestamp UTC
- Local timestamp
- Latitude
- Longitude
- GPS accuracy
- Address, if available
- Attendance type
- Selfie image reference, if required
- App version
- Network status
- IP address
- Approval status
- Fraud warning status

## Business logic

Mobile attendance should create raw attendance events first. The attendance engine should process them later.

```text
Mobile Clock-In
      ↓
Raw Mobile Punch Created
      ↓
GPS / Device / Policy Validation
      ↓
Attendance Engine Processing
      ↓
Manager Review if Exception
      ↓
Payroll-Ready Attendance
```

---

# 11. GPS Clock-In and Geofencing

GPS clock-in is important for mobile attendance control.

## Main features

- GPS-based clock-in
- GPS-based clock-out
- Geofence validation
- Approved location list
- Branch/site radius
- Project site geofence
- Customer site geofence
- Location accuracy threshold
- Clock-in outside geofence exception
- Map view for managers/HR
- GPS fraud warning
- Location audit

## Tenant-specific geofence settings

Each tenant can configure:

- Whether GPS is required
- Whether geofence is required
- Allowed clock-in radius
- Minimum GPS accuracy
- Whether outside-geofence punch is blocked
- Whether outside-geofence punch requires approval
- Whether selfie is required
- Whether device binding is required

## Business logic

Example:

```text
Tenant policy:
GPS required: Yes
Geofence radius: 100 meters
Minimum accuracy: 50 meters

Employee location:
Distance from site: 75 meters
GPS accuracy: 20 meters

Result: Clock-in allowed
```

Another example:

```text
Distance from site: 1,500 meters
Result: Clock-in blocked or sent for approval, depending on tenant policy.
```

---

# 12. Mobile Attendance Corrections

Employees should be able to request attendance corrections from mobile.

## Main features

- Missing clock-in request
- Missing clock-out request
- Incorrect clock-in correction
- Incorrect clock-out correction
- Break correction
- Attendance reason
- Attachment upload
- Manager approval
- HR approval, if required
- Correction status tracking

## Correction reasons

- Forgot to clock in
- Forgot to clock out
- Device error
- Mobile app error
- GPS issue
- Business meeting
- Field visit
- Manager instruction
- Emergency
- Remote work

## Business logic

Mobile correction should not directly change payroll records. It should create a correction request.

---

# 13. Mobile Manager Approvals

Managers should approve HR tasks from mobile.

## Main approval types

- Leave approval
- Attendance correction approval
- Overtime approval
- Timesheet approval
- HR request approval
- Document approval
- Expense approval
- Travel approval
- Promotion approval, if allowed
- Transfer approval, if allowed
- Recruitment requisition approval, if allowed
- Offer approval, if allowed
- Probation review approval
- Policy acknowledgement reminders

## Approval features

- View request details
- View employee details
- View history
- View supporting documents
- Approve
- Reject
- Return for correction
- Add comments
- Delegate approval, if allowed
- Bulk approval, tenant-configurable
- Push notification for approval tasks

## Business logic

Approvals must respect workflow and RBAC rules from the backend workflow engine.

The mobile app should not duplicate workflow logic. It should call the central workflow service.

---

# 14. Mobile HR Requests / Helpdesk

Employees should submit HR requests from mobile.

## Main features

- Create HR request
- Select request category
- Select subcategory
- Add description
- Attach document/photo
- View case status
- Add comments
- Respond to HR agent
- Rate service
- View HR knowledge base
- Search FAQs
- Chatbot integration, if enabled

## Request categories

- Payroll question
- Leave question
- Attendance issue
- Document request
- Letter request
- Benefits question
- Policy question
- Profile update issue
- IT/HR access issue
- Complaint/grievance, if enabled
- General HR inquiry

## Business logic

HR requests should route to the proper HR queue based on:

- Tenant
- Legal entity
- Department
- Location
- Request category
- Employee group
- Language
- SLA priority

---

# 15. Mobile Learning Access

Employees should access learning content from mobile.

## Main features

- View assigned courses
- View mandatory training
- View learning paths
- View course catalog
- Start online course
- Continue course
- View training calendar
- Register for training
- Complete quizzes
- View certificates
- View learning progress
- Receive training reminders

## Business logic

Mobile learning should support:

- Tenant-specific course catalog
- Role-based course visibility
- Position-based training requirements
- Compliance training reminders
- Offline content access, if enabled
- Completion sync when online

---

# 16. Mobile Company Announcements

The mobile app should provide company communication.

## Main features

- Company announcements
- Department announcements
- Branch announcements
- Urgent alerts
- HR campaigns
- Policy updates
- Event announcements
- Birthday/work anniversary notifications
- Survey invitations
- Announcement acknowledgement
- Announcement read tracking

## Targeting rules

Announcements can be targeted by:

- Tenant
- Legal entity
- Department
- Branch/location
- Position
- Grade
- Employee group
- Language
- Country
- Manager group

## Business logic

Urgent announcements can trigger push notifications.

Example:

```text
Announcement target: Store employees in Baku
Only employees belonging to those tenant locations receive it.
```

---

# 17. Push Notifications

Push notifications are central to Mobile HR.

## Main notification types

- Leave request approved/rejected
- Attendance correction approved/rejected
- Payslip published
- Payroll announcement
- Clock-in reminder
- Clock-out reminder
- Missing punch alert
- Approval request
- HR case update
- Training reminder
- Policy acknowledgement reminder
- Document expiry reminder
- Company announcement
- Birthday/work anniversary notification
- Emergency alert

## Push notification requirements

- Tenant-scoped push tokens
- User-scoped notification preferences
- Role-based notifications
- Language-specific templates
- Delivery status tracking
- Retry support
- Notification history
- Deep links into mobile app screens
- User opt-in/opt-out rules where applicable

## Business logic

Notifications must not expose sensitive information on lock screen if tenant policy disables it.

Example:

```text
Sensitive notification mode:
"Your payslip is available" instead of showing salary amount.
```

---

# 18. Mobile Document Upload

Employees should upload required documents from mobile.

## Main features

- Upload document photo
- Upload PDF/image
- Take photo from camera
- Select file from device
- Document type selection
- Expiry date entry
- Document number entry
- Submit for approval
- HR verification
- Rejection reason
- Resubmission
- Document history

## Document examples

- National ID
- Passport
- Work permit
- Visa/residency
- Medical certificate
- Education certificate
- Bank document
- Marriage certificate, if required
- Dependent documents
- Training certificates

## Business logic

Uploaded documents must be stored in tenant-isolated storage and linked to the employee document management module.

---

# 19. Mobile Policy Acknowledgement

Employees should acknowledge policies from mobile.

## Main features

- View policy
- Download policy, if allowed
- Read policy summary
- Acknowledge policy
- Decline/ask question, if allowed
- Capture acknowledgement date/time
- Capture device info
- Capture policy version
- Reminder notification
- Compliance report

## Business logic

Policy acknowledgement should store:

- Tenant ID
- Employee ID
- Policy ID
- Policy version
- Acknowledgement timestamp
- Device ID
- IP address
- Language version

---

# 20. Mobile Directory

The mobile app can provide an employee directory.

## Main features

- Search employees
- View public profile
- View department
- View job title
- View work phone
- View work email
- View manager
- View location
- Call employee
- Email employee
- Organizational chart shortcut

## Security logic

The directory must not expose sensitive fields:

- Salary
- National ID
- Passport
- Home address
- Bank details
- Medical data
- Disciplinary records
- Personal documents

Tenant can configure whether employee directory is enabled.

---

# 21. Mobile Team Calendar

Managers and employees may need team availability visibility.

## Main features

- Team leave calendar
- Team attendance status
- Team birthdays, if enabled
- Team work anniversaries, if enabled
- Public holidays
- Shift schedule
- Training schedule
- Business trip calendar

## Business logic

Employee privacy rules must apply.

Example:

The calendar may show “On Leave” without exposing medical leave details.

---

# 22. Offline and Low Connectivity Support

Mobile HR may be used by field workers and branch employees with poor connectivity.

## Offline-capable features

- View cached profile summary
- View cached leave balance, read-only
- Draft leave request
- Draft HR request
- Offline clock-in/out, if tenant allows
- Offline learning content, if enabled
- View cached announcements
- Queue actions for sync

## Offline sync rules

- Store offline actions securely
- Encrypt local cache
- Sync when internet returns
- Detect conflicts
- Mark offline punches clearly
- Require manager review for offline attendance if configured
- Keep audit trail of offline action time and sync time

## Business logic

For offline attendance, store both:

- Actual device-captured time
- Server sync time

This prevents manipulating attendance by delaying sync.

---

# 23. Mobile Security

Mobile HR contains sensitive HR and payroll data, so security is critical.

## Authentication features

- Username/password
- SSO
- Azure AD / Entra ID integration
- Google Workspace login, if enabled
- Multi-factor authentication
- Biometric app unlock
- PIN lock
- Session timeout
- Device trust
- Login attempt limit
- Password reset

## Device security features

- Device registration
- Device binding
- Device approval
- Device blacklist
- Remote logout
- Force logout
- App version control
- Root/jailbreak detection, if feasible
- Screen capture restriction, where feasible
- Local data encryption
- Secure token storage

## Business logic

Tenant can configure:

- Whether employees can use multiple devices
- Whether device approval is required
- Whether biometric unlock is required
- Whether payslip requires re-authentication
- Whether mobile attendance requires registered device

---

# 24. Mobile RBAC and Field-Level Security

The mobile app must use the same security model as the web system.

## Main features

- Role-based access
- Employee self-access
- Manager hierarchy access
- Department-based access
- Legal entity-based access
- Branch/location-based access
- Position-based access
- Field-level security
- Document-level security
- Payroll data restriction
- GPS data restriction
- Approval authority validation

## Business logic

Never rely only on mobile UI hiding fields. Backend APIs must enforce access control.

---

# 25. Mobile Audit Trail

Every sensitive mobile action should be audited.

## Audit should track

- Login
- Failed login
- Logout
- Device registration
- Device approval
- Profile update
- Leave request
- Attendance punch
- GPS clock-in
- Attendance correction
- Payslip view
- Payslip download
- Document upload
- HR request submission
- Approval action
- Policy acknowledgement
- Announcement acknowledgement
- Learning completion
- Push notification delivery

## Audit fields

- Tenant ID
- User ID
- Employee ID
- Action
- Old value
- New value
- Timestamp
- Device ID
- App version
- IP address
- GPS location, where relevant
- Approval reference
- Source module

---

# 26. Mobile Privacy and Compliance

Mobile HR must protect personal data.

## Main features

- Privacy notice
- Consent management, where required
- GPS consent
- Biometric/punch consent, if required by law/policy
- Data minimization
- Sensitive notification masking
- Local cache encryption
- Retention rules
- Right-to-access support
- Right-to-delete/anonymize support where legally applicable
- Tenant-specific privacy text

## Privacy rules

- GPS should be captured only for attendance or approved business use
- GPS tracking should not become continuous employee surveillance unless legally allowed and explicitly configured
- Sensitive data should not appear in push notification previews if disabled
- Payslips and documents should require strong access controls

---

# 27. Mobile Localization

The mobile app should support localization.

## Main features

- Multi-language UI
- Tenant default language
- User language preference
- Azerbaijani language support
- English language support
- Turkish/Russian support if needed
- Date format localization
- Time format localization
- Currency localization
- RTL language support, if required

## Business logic

Notifications, announcements, policies, and forms should be shown in the user’s preferred language where templates exist.

---

# 28. Mobile App Version Management

Tenant admins and system admins need control over app versions.

## Main features

- App version tracking
- Minimum supported app version
- Force upgrade
- Optional upgrade notice
- Deprecated version blocking
- Feature flags by app version
- Crash/error logging
- Device OS version tracking
- App distribution status

## Business logic

If a security issue exists in an old app version, system should force upgrade before allowing login.

---

# 29. Mobile Feature Flags

Features should be configurable per tenant.

## Main feature flags

- Enable mobile leave
- Enable mobile payslip
- Enable mobile attendance
- Enable GPS clock-in
- Enable selfie clock-in
- Enable mobile HR requests
- Enable mobile learning
- Enable mobile announcements
- Enable document upload
- Enable mobile approvals
- Enable mobile profile updates
- Enable employee directory
- Enable team calendar
- Enable offline attendance
- Enable biometric unlock

## Business logic

Feature flags must be tenant-scoped and role-scoped.

Example:

```text
Tenant A:
Mobile attendance enabled only for sales employees.

Tenant B:
Mobile attendance disabled for all employees.
```

---

# 30. Mobile Integration Requirements

## Integrates with Employee Management

Mobile uses:

- Employee profile
- Employee status
- Department
- Manager
- Position
- Location
- Contact details

## Integrates with Leave / Absence

Mobile supports:

- Leave request
- Leave balance
- Leave history
- Leave approval
- Team leave calendar

## Integrates with Payroll

Mobile supports:

- Payslip viewing
- Payslip download
- Payroll notifications
- Salary-related document access, if allowed

## Integrates with Time and Attendance

Mobile supports:

- Clock-in/out
- GPS attendance
- Attendance correction
- Attendance history
- Attendance approval

## Integrates with Workflow and Approvals

Mobile supports:

- Approval inbox
- Request details
- Approve/reject
- Delegation
- Comments
- Workflow history

## Integrates with HR Service Delivery

Mobile supports:

- HR request submission
- Case status
- HR knowledge base
- Chatbot
- Service feedback

## Integrates with Learning

Mobile supports:

- Course access
- Learning paths
- Quizzes
- Certificates
- Training reminders

## Integrates with Document Management

Mobile supports:

- Document upload
- Document view
- Document approval
- Policy acknowledgement

## Integrates with Notification Module

Mobile supports:

- Push notifications
- In-app notifications
- Announcement alerts
- Approval alerts

## Integrates with Security / IAM

Mobile supports:

- SSO
- MFA
- Device trust
- Role-based access
- Audit log

---

# 31. Mobile API Requirements

Mobile app should use secure backend APIs.

## API design principles

- Tenant-scoped APIs
- Token-based authentication
- Short-lived access tokens
- Refresh tokens with rotation
- API rate limiting
- Request signing for sensitive actions, optional
- Device ID validation
- Role-based access enforced server-side
- Data minimization
- Audit logging
- Idempotency for offline sync actions

## Example API areas

- Authentication API
- Employee profile API
- Leave API
- Attendance API
- Payslip API
- Approval API
- HR requests API
- Learning API
- Document API
- Notification API
- Announcement API
- Device management API

---

# 32. Mobile Data Storage Requirements

## Local storage rules

- Store minimum data locally
- Encrypt cached data
- Do not store payslip files unencrypted
- Clear cache after logout
- Tenant-specific cache separation
- Auto-delete stale cache
- Prevent data leakage between users
- Secure offline queue

## Business logic

If two employees use the same mobile device, the app must fully isolate sessions and clear previous user data at logout.

---

# 33. Mobile Notifications Center

The mobile app should include an in-app notifications center.

## Main features

- Notification list
- Read/unread status
- Notification categories
- Deep link to related request
- Mark as read
- Delete/archive notification
- Notification preferences
- Notification history
- Tenant-branded notifications

## Notification categories

- Approvals
- Leave
- Attendance
- Payroll
- HR requests
- Learning
- Announcements
- Documents
- Policies
- System alerts

---

# 34. Mobile HR Request Forms

The mobile app should support dynamic forms.

## Main features

- Tenant-configurable forms
- Form fields by request type
- Required fields
- Conditional fields
- Attachment fields
- Dropdowns from master data
- Validation rules
- Submit workflow
- Form history

## Example forms

- Address change request
- Bank details change request
- Document request
- Salary certificate request
- Experience letter request
- Attendance correction request
- Leave cancellation request
- HR inquiry
- Complaint/grievance, if enabled

---

# 35. Mobile Approval Inbox

Managers and approvers need one place for approvals.

## Main features

- Pending approvals list
- Approval type filter
- Employee/requester search
- Priority filter
- Due date
- Request details
- Approve
- Reject
- Return for correction
- Comment
- Attachment view
- Approval history
- Delegation indicator
- SLA warning

## Approval types

- Leave
- Attendance correction
- Overtime
- Timesheet
- HR request
- Expense
- Travel
- Document
- Recruitment
- Promotion
- Transfer
- Payroll, restricted
- Offboarding, restricted

---

# 36. Mobile Analytics for Managers

Managers should have lightweight team analytics.

## Main features

- Team headcount
- Team leave balance summary
- Team attendance today
- Team absenteeism trend
- Team overtime trend
- Pending approvals
- Learning completion
- Probation due
- Birthday/work anniversary list
- Team HR cases, if allowed

## Security logic

Manager analytics must respect hierarchy and field-level permissions.

---

# 37. Mobile Admin and Support

Tenant admins and support users need operational controls.

## Main features

- View registered devices
- Revoke device access
- Force logout user
- View mobile app versions
- View failed login attempts
- View push token status
- View mobile attendance exceptions
- View device compliance issues
- Disable mobile access for employee
- Reset mobile device binding
- Support access audit

---

# 38. Reports and Analytics

## Standard reports

- Mobile active users report
- Mobile login report
- Mobile failed login report
- Mobile device report
- Mobile attendance report
- GPS exception report
- Mobile leave request report
- Mobile approval report
- Payslip view/download report
- Mobile HR request report
- Push notification delivery report
- App version report
- Mobile audit report

## Analytics KPIs

- Mobile adoption rate
- Daily active users
- Monthly active users
- Mobile leave request percentage
- Mobile approval turnaround time
- Mobile attendance exception rate
- Push notification delivery rate
- Mobile HR request volume
- Average mobile session duration
- App crash rate
- Offline sync failure rate

---

# 39. Security and Access Control

## Main features

- RBAC
- Field-level security
- Tenant isolation
- Device binding
- MFA
- Biometric unlock
- Session timeout
- Token rotation
- API rate limiting
- Sensitive data masking
- Audit logs
- Segregation of duties
- Super admin access control

## Example access rules

| Role | Mobile Access |
|---|---|
| Employee | Own profile, leave, payslip, attendance, HR requests |
| Manager | Own data plus team approvals and team views |
| HR Officer | HR operational tasks within assigned scope |
| Payroll Officer | Payslip/payroll-related approvals if allowed |
| Tenant Admin | Tenant mobile settings and device control |
| Auditor | Read-only audit reports if allowed |

---

# 40. Recommended Mobile HR Menu

```text
Mobile HR
│
├── Home
├── My Profile
├── Leave
├── Attendance
├── Payslips
├── Approvals
├── HR Requests
├── Documents
├── Learning
├── Announcements
├── Team
├── Calendar
├── Notifications
├── Policies
├── Settings
└── Help / Support
```

---

# 41. Recommended Mobile Screens

## Employee screens

1. Login
2. Home dashboard
3. My Profile
4. Leave Balance
5. Leave Request
6. Attendance Clock-In/Out
7. Attendance History
8. Payslips
9. HR Requests
10. Documents
11. Learning
12. Announcements
13. Notifications
14. Policies
15. Settings

## Manager screens

1. Manager dashboard
2. Approval Inbox
3. Team Attendance
4. Team Leave Calendar
5. Team Profiles
6. Team Learning
7. Team Requests
8. Probation Reviews
9. Notifications

## Admin/support screens

1. Registered Devices
2. Mobile User Activity
3. Push Notification Status
4. Mobile Attendance Exceptions
5. App Version Monitoring
6. Security Events

---

# 42. Recommended Main Data Entities

For technical design, Mobile HR should not duplicate core HR records. It should reference core HCM entities and maintain mobile-specific records.

## Mobile-specific entities

- MobileDevice
- MobileSession
- MobileUserPreference
- MobileFeatureFlag
- MobilePushToken
- MobileNotificationLog
- MobileAttendanceLog
- MobileOfflineQueue
- MobileAuditLog
- MobileAppVersion
- MobileDeviceSecurityStatus
- MobileTenantBranding
- MobileConfiguration
- MobileErrorLog
- MobileConsentRecord

## Referenced core entities

- Employee
- LeaveRequest
- LeaveBalance
- AttendanceRecord
- Payslip
- WorkflowTask
- HRCase
- Document
- Course
- Announcement
- Policy
- Notification

---

# 43. Important Validation Rules

The system should validate:

- User must belong to the tenant
- Device must be registered if tenant requires device binding
- Feature must be enabled for tenant and user role
- Employee must be active unless post-exit access is allowed
- Employee cannot view another employee’s data unless authorized
- Manager can access only team data within reporting hierarchy
- Payslip must be published before mobile viewing
- Leave request must follow leave policy rules
- Mobile attendance must follow attendance policy rules
- GPS clock-in must satisfy geofence rules if enabled
- Offline attendance must sync with original device timestamp
- Attendance correction after payroll lock requires special approval
- Push token must be tenant-scoped
- Documents must be stored in tenant-isolated storage
- Approvals must be validated by central workflow engine
- API must enforce RBAC, not only mobile UI
- Sensitive actions must create audit records

---

# 44. Common Mistakes to Avoid

## 1. Building mobile as a separate HR system

Mobile HR should be a front-end to central HCM services, not a separate data store.

## 2. Weak tenant isolation

Mobile APIs must strictly enforce tenant context.

## 3. Sending sensitive data in push notifications

Avoid exposing salary, medical, disciplinary, or private details on lock screens.

## 4. Mobile attendance without controls

GPS, device binding, geofence, and approval rules are needed to prevent misuse.

## 5. Letting mobile bypass approval workflows

All approvals must go through the same workflow engine as web.

## 6. Storing too much data on device

Local mobile storage should be minimized and encrypted.

## 7. No audit log for mobile actions

Attendance, payslip, documents, and approvals must be auditable.

## 8. No app version control

Old app versions can create security and compatibility problems.

## 9. Not supporting poor connectivity

Field and branch employees may need offline queueing or low-bandwidth design.

## 10. Not respecting field-level security

Mobile should not show sensitive fields just because the API returns them.

---

# 45. Final Recommended Launch Scope

For your ERP/HCM product, the **Mobile HR** module should launch with full enterprise functionality covering:

- Tenant-specific mobile configuration
- Tenant branding
- Mobile employee dashboard
- Mobile manager dashboard
- Employee profile view/update
- Mobile leave request
- Mobile leave balance
- Mobile payslip
- Mobile attendance
- GPS clock-in
- Geofence attendance
- Mobile attendance correction
- Manager approvals
- HR requests/helpdesk
- Learning access
- Company announcements
- Push notifications
- Document upload
- Policy acknowledgement
- Employee directory
- Team calendar
- Offline/low-connectivity support
- Mobile security
- Device management
- RBAC and field-level security
- Mobile audit trail
- Privacy and compliance
- Localization
- App version management
- Mobile feature flags
- Integration with all core HCM modules
- Mobile API layer
- Secure local storage
- Notification center
- Dynamic HR request forms
- Approval inbox
- Manager analytics
- Admin/support controls
- Reports and analytics

The most important design rule:

**Mobile HR must not become a separate HR system. It must be a secure, tenant-aware, role-based mobile interface on top of the same HCM workflow, payroll, attendance, leave, document, and security services used by the main ERP.**
