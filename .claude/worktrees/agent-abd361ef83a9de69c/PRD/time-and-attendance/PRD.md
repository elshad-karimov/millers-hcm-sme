---
feature: time-and-attendance
module: attendance
payroll_impact: true
status: backlog
depends_on: []
---

# 6. Time and Attendance Module — Full Enterprise Features

The Time and Attendance module tracks employee working time, clock-in/clock-out events, absences, lateness, early leave, breaks, overtime, shift compliance, timesheets, attendance corrections, approvals, and payroll-ready attendance results.

In a proper HCM/ERP system, Time and Attendance is not only a daily attendance table. It is a rule-driven engine that connects Employee Management, Organizational Management, Position Management, Shift Scheduling, Leave/Absence Management, Payroll, Mobile App, Biometric Devices, Access Control Devices, Manager Self-Service, Employee Self-Service, Notifications, Reporting and Analytics, and Audit Log.

## §1 Purpose

Accurately track employee working time and convert raw attendance data into approved payroll-ready attendance results.

Main objectives: record employee attendance, capture clock-in/clock-out, track working hours, late arrivals, early departures, absences, breaks, calculate overtime, validate attendance against shifts, support biometric/mobile/GPS attendance, support manual attendance correction, support manager approval, generate timesheets, integrate with payroll, provide attendance reports, maintain audit history.

Standard attendance flow:
```
Employee Clock-In / Device Punch / Mobile Check-In
  → Raw Attendance Event Captured
  → Attendance Rules Applied
  → Shift Matching
  → Late / Early / Absence / Overtime Calculated
  → Employee or Manager Correction (if needed)
  → Manager Approval
  → Payroll Attendance Summary Generated
  → Payroll Processing
```

## §2 Setup / Configuration

### Attendance policies
attendance_policy_name, legal_entity, department, location, employee_group, employment_type, shift_group, grace_period, late_rules, early_leave_rules, absence_rules, overtime_rules, break_rules, rounding_rules, approval_rules, payroll_integration_rules

### Attendance calendar
work_calendar, holiday_calendar, weekly_off_days, public_holidays, special_working_days, half_days, ramadan/seasonal, location-specific calendars, department-specific calendars

### Time rules
standard_working_hours, minimum_working_hours, maximum_working_hours, shift_start/end, break_duration, paid/unpaid_break, flexible_time_rules, core_working_hours, overtime_threshold, night_shift_rules, weekend_work_rules, holiday_work_rules

### Device settings
biometric_device_setup, device_location, device_code, device_ip, device_type, device_integration_method, employee_biometric_id_mapping, device_sync_frequency, offline_device_data_handling, duplicate_punch_handling

### Mobile attendance settings
enable_mobile_clock_in, gps_required, selfie_required, device_id_required, geofence_required, allowed_locations, allowed_distance_radius, offline_mobile_attendance, manager_approval_required, fraud_detection_rules

## §3 Attendance Dashboard

### HR dashboard widgets
Employees present/absent/late/early-leave/on-leave/remote/business-trip today, missing clock-outs, pending corrections, pending approvals, overtime pending, device sync errors, attendance exceptions

### Manager dashboard widgets
My team attendance today, team late/absent/early/missing-punches, pending corrections, pending OT approvals, employees present/not-clocked-in/on-break, shift coverage

### Payroll dashboard widgets
Attendance period status, approved records, pending approvals, payroll-ready summary, OT payable, absence deductions, late deductions, unpaid leave days, exceptions blocking payroll

### Operations dashboard widgets
Store/branch attendance, shift coverage, open shifts without employees, understaffed shifts, OT risk, attendance by location, real-time workforce availability

## §4 Attendance Record Management

### Daily attendance record fields
employee_id, attendance_date, shift, work_location, clock_in_time, clock_out_time, total_worked_hours, break_hours, net_worked_hours, late_minutes, early_leave_minutes, overtime_hours, absence_status, attendance_status, approval_status, source_of_attendance, correction_status, payroll_status

### Attendance statuses
Present, Absent, Late, Early Leave, Half Day, On Leave, Holiday, Weekly Off, Business Trip, Remote Work, Work From Home, Training, Suspended, Missing Punch, Pending Correction, Approved, Rejected, Payroll Processed

### Business logic — CRITICAL separation of concerns:
1. Raw punch events (immutable — never deleted)
2. Processed attendance (rules applied)
3. Approved attendance (manager-signed-off)
4. Payroll-ready attendance (locked for payroll)

## §5 Clock-In / Clock-Out

Multiple punches per day (clock-in, clock-out, break-start, break-end, lunch-start, lunch-end, OT-start, OT-end), manual/device/mobile/web/kiosk/offline punch.

Punch sources: biometric device, RFID card, PIN terminal, face recognition, mobile app, web portal, manager entry, HR manual entry, API import, Excel import, access control system.

Detections: missing clock-in, missing clock-out, duplicate punch, out-of-sequence, outside allowed time, unauthorized location, unregistered device, punch after termination, punch during approved leave, punch on holiday/off day.

## §6 Biometric Device Integration

device_registration (code, name, ip, location, type, status), employee_biometric_id_mapping, fingerprint/face/RFID integration, device_log_import, real-time/scheduled/offline sync, device_health_monitoring, failed_sync_alerts, duplicate_punch_filtering.

Integration methods: LAN/IP, SDK, REST API, DB sync, CSV import, USB, middleware, cloud gateway.

Business logic: device punch → raw attendance event → engine processes per shift/policy.

## §7 Mobile Attendance

mobile_clock_in/out, GPS capture, selfie capture, device_id capture, geofence validation, offline mode, manager approval, location history, fraud detection.

Validation: Is employee allowed mobile attendance? Within geofence? Device registered? Location accurate enough? Scheduled to work? From approved site? Selfie required? Manager approval required?

## §8 GPS-Based Attendance and Geofencing

Capture lat/lng/address/accuracy, define geofence locations with radius (meters), multiple locations, restrict/allow exceptions, map view, suspicious location detection, audit trail.

Business logic: employee within radius → clock-in allowed; outside radius → blocked or sent for manager approval.

## §9 Web Attendance / ESS

Web clock-in/out, browser location (if allowed), IP address capture, device tracking, remote work reason, approval requirement, attendance history, missing punch request, correction request.

Policy-controlled: office employees biometric only; remote employees web/mobile; managers can approve exceptions.

## §10 Kiosk Attendance

Shared device clock-in, PIN login, QR code scan, face/photo capture, employee search, shift display, break tracking, offline mode, sync on reconnect, device location binding. Restricted to branch/location.

## §11 Shift-Based Attendance

fixed/rotating/flexible/split/night/cross-midnight/open/weekend/holiday shifts, grace_period, shift_tolerance, shift_swap, shift_change_history.

Shift fields: code, name, start/end time, break start/end, standard hours, minimum hours, grace period, late threshold, early leave threshold, OT threshold, night hours, paid/unpaid break, cross-day flag.

## §12 Flexible Working Hours

flexible_start/end time, core_working_hours (required window), daily/weekly/monthly required hours, flexible break rules, flexitime balance, carry-forward hours, negative hour tracking.

Business logic: focus on total required hours and core-hour compliance rather than fixed clock-in time.

## §13 Remote Work / WFH Attendance

Remote work clock-in, approval, WFH schedule, remote location capture, remote reason, manager approval, remote work calendar, hybrid schedule. May require: approved remote work request, GPS, web/mobile clock-in, daily timesheet, manager confirmation.

## §14 Late Arrival Tracking

late_minutes, late_count, grace_period, late_threshold, late_reason, late_approval, late_penalty, late_deduction, repeated_late_warning, late_exception.

Calculation: shift_start + grace_period = late boundary. Late minutes = clock_in - (shift_start + grace_period).

Policy examples: first 10 min free; >3 late/month = warning; >60 late-min/month = salary deduction; late >2h = half day; late without approval = unpaid absence.

## §15 Early Leave Tracking

early_leave_minutes, early_leave_count, reason, approval, deduction, exception. Can be: allowed if approved, deducted from salary/leave balance, counted as violation, ignored within tolerance.

## §16 Absence Tracking

Full-day/half-day/unauthorized/approved absence, reason, deduction, conversion to leave, approval, warning. Statuses: Absent, Unauthorized Absence, Approved Absence, Leave-Covered Absence, Half-Day Absence, No-Show, Pending Explanation, Converted to Unpaid Leave, Converted to Annual Leave, Payroll Deduction Applied.

## §17 Break Tracking

break_start/end, lunch break, tea break, paid/unpaid break, auto-deduction, manual punch, max/min duration, break violation. Methods: employee punches, auto-deduct, manager enters, shift-defined, flexible within window.

## §18 Overtime Calculation

Daily/weekly/monthly OT, pre/post-approved, unauthorized OT, OT request/approval, OT rate, weekend/holiday/night OT, compensatory time off, OT cap, OT budget control, payroll transfer.

Two models:
- Model 1: pre-approval required — extra hours not payable until approved
- Model 2: auto-calculated — shift end vs clock-out = OT hours, payable per policy

OT multipliers (AZ rules already in system): 1.5x regular OT, 2x holiday OT.

## §19 Night Shift Attendance

Cross-midnight shift (e.g. 22:00–06:00), night differential, next-day clock-out handling, attendance date = shift start date, must not split into two absences.

## §20 Holiday and Weekend Work

holiday/weekend work tracking, holiday OT, holiday allowance, compensatory leave, manager approval, payroll rate mapping (2x pay or comp leave per policy — AZ rules in system).

## §21 Attendance Corrections

Missing punch request, clock-in/out/break/status correction, reason selection, attachment support, manager/HR approval, correction history, payroll lock validation.

Reasons: forgot to clock in/out, device not working, business meeting, field work, approved remote, biometric failure, manager instruction, emergency, sync issue.

Workflow: employee submits → manager approves → HR reviews (if required) → record recalculated → payroll summary updated.

## §22 Attendance Approval

Daily/weekly/monthly approval, exception/late/early/OT/missing-punch/timesheet approval, bulk approval, comments, rejection reason, approval delegation, escalation.

Statuses: Not Required, Pending Approval, Approved, Rejected, Returned for Correction, Escalated, Payroll Locked.

Payroll uses approved records ONLY — not raw punches.

## §23 Timesheet Management

Daily/weekly/monthly timesheet, project/task/client time entry, billable/non-billable hours, activity codes, cost center allocation, submission, manager approval, project manager approval, rejection, payroll/project-accounting integration.

Fields: employee, date, project, task, activity, hours, billable flag, description, approval status, cost center, customer.

Attendance proves presence; timesheet explains what work was done. Payroll may use attendance; project costing uses timesheet.

## §24 Project/Job Costing Attendance

Allocate hours to project/job order/production order/cost center/customer, billable/non-billable split, labor cost calculation.

## §25 Attendance Policy Rules Engine

Configurable rules: shift matching, grace period, late calculation, early leave, absence, OT, break, rounding, holiday, weekend, night shift, payroll deduction, exception, approval routing.

Example rules:
- If clock-in within 10 min after shift start → not late
- If late >30 min → require manager approval
- If late >2h → half-day
- If no clock-out → missing punch
- If missing punch not corrected before payroll cutoff → unpaid
- If OT >2h → require dept-head approval
- If works on holiday → 2x OT
- If on approved leave → ignore missing punch

## §26 Rounding Rules

Clock-in/out rounding, OT rounding, break rounding, nearest 5/10/15 min, round up/down, grace rounding, payroll rounding. Raw punches unchanged — rounding applies only in processed/payroll calculations.

## §27 Attendance Exception Management

Types: missing clock-in, missing clock-out, late arrival, early leave, unauthorized absence, OT without approval, clock-in outside geofence, duplicate punch, device mismatch, attendance during leave, punch after termination, excessive break, below min hours, over max hours, shift mismatch.

Per exception: status, owner, severity, reason, resolution, employee explanation, manager decision, HR review, payroll impact.

## §28 Attendance Locking and Payroll Cutoff

attendance_period, payroll_cutoff_date, attendance_lock, manager_approval_deadline, HR_lock, payroll_lock, unlock_request, unlock_approval, retroactive_correction, payroll_adjustment.

Example: Period 1–31 May; manager deadline 2 Jun; lock 3 Jun. After lock: changes require HR/payroll approval.

## §29 Payroll Integration

Outputs: paid_working_days, unpaid_absence_days, late_deduction_amount, early_leave_deduction, overtime_hours, night_shift_hours, holiday_work_hours, weekend_work_hours, leave_without_pay_days, comp_time, attendance/shift/meal/transport allowance eligibility.

Payroll receives APPROVED + LOCKED attendance summaries only.

## §30 Leave/Absence Integration

Approved leave suppresses absence. Leave hours reduce required work hours. Half-day/unpaid/sick leave integration. Leave request from absence. Convert absence to leave. Leave without pay → payroll deduction.

## §31 Shift Scheduling Integration

Import scheduled shifts, compare planned vs actual, shift swap/change impact, open shift attendance, schedule variance, understaffing detection, shift compliance report.

## §32 Employee Self-Service Attendance

View daily/monthly attendance, clock-in/out history, late minutes, OT hours, absences, break records. Submit missing punch request, correction, OT request. View approval status, payroll attendance summary, download report.

## §33 Manager Self-Service Attendance

View team attendance, who is present/absent/late today, shift coverage. Approve corrections/OT/timesheets. Convert absence to leave. Add comments. Export team attendance. View trends.

## §34 HR Attendance Workspace

View all records, manage policies, resolve device sync, approve special corrections, lock/unlock periods, import data, export payroll summaries, manage exceptions, run recalculation, audit changes, generate reports.

## §35 Attendance Import and Bulk Processing

Import from biometric device, Excel/CSV, API. Bulk: correction, shift assignment, approval, recalculation, absence marking, OT calculation. Import validation (invalid employee, duplicate punch, outside employment period, device not mapped, invalid datetime, inactive employee, missing shift, timezone mismatch). Import history.

## §36 Attendance Recalculation

Recalculate employee/department, date range, after shift change/leave approval/correction approval/policy change. History. If payroll already processed → create adjustment, not silent history change.

## §37 Multi-Location and Multi-Timezone Support

Location-based timezone, employee/device/shift timezone, cross-timezone attendance, branch-level rules, country-specific policies, public holiday by location.

Store: raw_timestamp, device_timezone, employee_work_timezone, utc_timestamp, processed_local_time.

## §38 Attendance Compliance Controls

Maximum daily/weekly hours, minimum rest period, mandatory break, OT cap, night work limits, minor worker restrictions, labor law warnings, compliance reports, policy violation alerts.

## §39 Notifications

To employee: clock-in/out reminder, missing punch alert, late warning, OT request approved/rejected, correction approved/rejected, attendance summary, payroll cutoff reminder.
To manager: employee absent/late, missing punch pending, OT approval pending, correction pending, team exception, payroll approval deadline.
To HR/payroll: period pending lock, payroll-blocking exceptions, device sync failure, high OT warning, policy violation, late approval overdue.

## §40 Reports and Analytics

Standard reports: daily/monthly/employee/team/department/branch attendance, late arrival, early leave, absence, missing punch, OT, break, holiday/weekend/night-shift work, correction, timesheet, payroll attendance summary, device punch, mobile GPS, geofence violation, exception, audit.

Analytics KPIs: attendance rate, absenteeism rate, avg late minutes, OT hours/cost by dept, missing punch frequency, correction frequency, shift compliance rate, unauthorized absence rate, payroll-blocking exceptions, device sync reliability, manager approval delay.

## §41 Security and Access Control

Role-based, legal entity, department, branch/location, manager hierarchy, field-level security, device management permission, correction approval permission, payroll lock permission, export restriction, GPS data restriction, audit log access.

Roles: Employee (own data + corrections), Manager (team approval), HR Attendance Officer (manage records/policies), Payroll Officer (payroll-ready attendance), IT/Admin (devices), Auditor (read-only), System Admin (config, SoD restricted from payroll).

## §42 Audit Trail

Track: raw punch creation/import, manual punch, correction request/approval, recalculation, late/absence override, OT approval, shift change, attendance lock/unlock, payroll transfer, device sync changes, policy changes.

Fields: action, old_value, new_value, changed_by, changed_datetime, reason, approval_reference, source_device, ip/device (optional), gps_coordinates (if relevant).

## §43 Integration With Other Modules

Uses from Employee Mgmt: employee_id, status, hire/termination date, department, position, manager, work location, employment type.
Uses from Org Mgmt: legal entity, department, branch, location, work calendar, holiday calendar, cost center.
Uses from Leave Mgmt: approved leave, unpaid/sick/half-day leave, balance adjustments.
Uses from Shift Scheduling: planned shifts, swaps, rotating schedules, shift coverage.
Sends to Payroll: worked days, paid days, OT hours, absence deductions, late deductions, LWP, allowance eligibility.
Sends to Project Accounting: project hours, cost allocation, billable hours, labor cost.

## §44 Recommended Menu Structure

Dashboard → Attendance Records → Clock-In/Clock-Out → Daily Attendance → Monthly Attendance → Attendance Exceptions → Missing Punches → Attendance Corrections → Overtime Requests → Timesheets → Shift Attendance → Mobile Attendance → GPS/Geofence Attendance → Biometric Devices → Device Punch Logs → Attendance Approvals → Payroll Attendance Summary → Attendance Lock/Cutoff → Reports → Settings

## §45 Attendance Record Tabs

1. Overview, 2. Raw Punches, 3. Processed Attendance, 4. Shift Details, 5. Breaks, 6. Late/Early/Absence, 7. Overtime, 8. Location/Device Info, 9. Corrections, 10. Approvals, 11. Payroll Impact, 12. Activity History, 13. Audit Trail

## §46 Attendance List Columns

employee_id, name, department, position, date, shift, clock_in, clock_out, worked_hours, break_hours, late_minutes, early_leave_minutes, overtime_hours, attendance_status, exception_status, approval_status, payroll_status, source, actions (view/correct/approve/reject/recalculate/comment/raw-punches/audit)

## §47 Recommended Data Entities

AttendancePolicy, AttendanceRule, WorkCalendar, HolidayCalendar, Shift, ShiftAssignment, AttendanceRecord, RawPunchEvent, BreakRecord, OvertimeRecord, AttendanceException, AttendanceCorrectionRequest, AttendanceApproval, Timesheet, TimesheetLine, DeviceMaster, DevicePunchLog, MobileAttendanceLog, GeofenceLocation, AttendancePayrollSummary, AttendancePeriodLock, AttendanceAuditLog

## §48 Validation Rules

- Employee must be active on attendance date
- Cannot clock in after termination date
- Cannot clock in before hire date (unless pre-hire training allowed)
- Clock-out cannot be before clock-in (unless cross-midnight)
- Duplicate punches detected
- Missing clock-in/out → exception
- Attendance during approved leave → review required
- Outside geofence → blocked or approval required
- OT must follow policy approval
- Correction after payroll lock → special approval
- Employee cannot approve own correction
- Manager cannot approve outside reporting scope (unless delegated)
- Device punch must map to valid employee
- Shift must exist for shift-based employees
- Raw punch data must NEVER be deleted after processing
- Payroll summary uses only approved/locked records

## §49 Common Mistakes to Avoid

1. Raw punches directly to payroll — never; use approved processed attendance
2. No shift integration — cannot calculate late/early/OT correctly
3. Weak correction control — affects salary, requires approval and audit
4. No payroll cutoff lock — attendance changes make salary unstable
5. Not storing raw punches — always keep for audit
6. No device monitoring — sync failures cause major payroll issues
7. No geofence for mobile — can be abused
8. Mixing leave and absence — approved leave must not become absence
9. Not handling night shifts — cross-midnight needs special logic
10. No audit trail — attendance affects pay, every change must be traceable

## §50 Launch Scope

Complete enterprise-grade: attendance records, clock-in/out, raw punch logs, biometric device integration, mobile attendance, GPS/geofence, web attendance, kiosk attendance, shift-based, flexible hours, remote work, late/early/absence/break tracking, OT calculation, night shift, holiday/weekend work, corrections, approvals, timesheets, project/job costing, policy rules engine, rounding, exceptions, payroll cutoff/lock, payroll integration, leave integration, shift scheduling integration, ESS, MSS, HR workspace, bulk import/recalculation, multi-location/timezone, compliance, notifications, reports/analytics, security, audit trail.

**Most important design rule:** Do NOT send raw attendance punches directly to payroll. Capture raw punches → process through rules → approve exceptions → lock period → send payroll-ready summaries.
