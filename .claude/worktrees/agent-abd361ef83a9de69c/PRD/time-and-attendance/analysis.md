---
feature: time-and-attendance
module: attendance
analyzer: prd-analyzer
date: 2026-06-26
status: analyzed
---

# Time and Attendance — PRD Analysis

## 1. Module Scope

This PRD defines the full enterprise Time & Attendance module for the Millers HCM platform. The existing codebase (M19–M23, M36–M39, M110–M113) has laid a foundation — raw punch capture, a daily-summary engine, shift catalog, shift patterns, roster, and timesheet management — but covers roughly 15–20% of what the PRD specifies. The PRD adds:

- A configurable **Attendance Policy Rules Engine** (grace periods, late/early/OT/break/absence rules, rounding, holiday, weekend) — currently hard-coded in the engine
- **Attendance corrections workflow** with manager/HR approval and payroll lock validation
- **Attendance exception management** (missing punch, geofence violations, duplicate punch, unauthorized OT, etc.) with severity and resolution tracking
- **Period locking / payroll cutoff** — a formal lock cycle that freezes records for payroll
- **Payroll-ready attendance summaries** (the 14 outputs listed in §29) as a distinct promoted record
- **Biometric device master** with health monitoring, sync, and mapping
- **Mobile attendance** with GPS, selfie, geofencing, and offline capability
- **Web / ESS clock-in** with IP/device tracking and remote-work reason
- **Kiosk attendance** (shared device, PIN/QR/face)
- **Flexible working hours** (core hours, weekly/monthly balance, flexitime carry-forward)
- **Remote Work / WFH attendance** tracking as a distinct capture mode
- **Break tracking** (punch-based or auto-deduct)
- **Overtime request/approval workflow** (pre-approved and auto-calculated models)
- **Night shift / cross-midnight** special handling (currently partially handled in the engine via crosses_midnight flag but not rule-driven)
- **Holiday and weekend work** tracking with compensatory leave and payroll rate mapping (AZ 2x rule partially landed in payroll via M155, not in attendance)
- **Attendance locking** per period with unlock request/approval and retroactive correction path
- **Timesheet project/task costing** — current timesheet tracks presence codes; PRD adds project/task/client/billable-hour allocation
- **Multi-timezone support** with raw timestamp preservation
- **Compliance controls** (max daily/weekly hours, mandatory rest, OT cap, labor law warnings)
- **Attendance notifications** (30+ triggers across employee, manager, HR/payroll, IT)
- **ESS/MSS attendance surfaces** (view own attendance, submit corrections, approve team)
- **HR Attendance Workspace** (manage all records, policies, exceptions, locks, bulk ops)
- **25 standard reports + 13 KPI metrics**
- **Full audit trail** with before/after values on every state change

---

## 2. PRD Sections Summary (50 sections)

| § | Title | What it adds |
|---|-------|-------------|
| §1 | Purpose | Defines the 8-step attendance flow from raw punch to payroll-ready record |
| §2 | Setup / Configuration | Attendance policy master, work calendar, time rules, device settings, mobile settings |
| §3 | Attendance Dashboard | Three-panel dashboard: HR / Manager / Payroll / Operations widgets |
| §4 | Attendance Record Management | Full record field set, 18 attendance statuses, 4-layer data model (raw→processed→approved→payroll-ready) |
| §5 | Clock-In / Clock-Out | Multi-punch-per-day model, 10 source types, 10 anomaly detections |
| §6 | Biometric Device Integration | Device master, employee biometric mapping, sync methods, health monitoring |
| §7 | Mobile Attendance | GPS/selfie/device-id capture, geofence, offline mode, fraud detection |
| §8 | GPS-Based Attendance and Geofencing | Lat/lng capture, radius geofences, map view, audit trail |
| §9 | Web Attendance / ESS | Browser-based clock-in, IP/device capture, remote work reason, policy-controlled |
| §10 | Kiosk Attendance | Shared-device clock-in, PIN/QR/face, location binding, offline sync |
| §11 | Shift-Based Attendance | Shift types (fixed/rotating/flexible/split/night), grace/tolerance, shift swap history |
| §12 | Flexible Working Hours | Core hours, daily/weekly/monthly balance, flexitime carry-forward, negative tracking |
| §13 | Remote Work / WFH Attendance | Remote clock-in, approval, WFH schedule, hybrid support |
| §14 | Late Arrival Tracking | Grace period, late minutes calculation, penalty/deduction, repeated-late warning |
| §15 | Early Leave Tracking | Early-leave minutes, reason, approval, deduction |
| §16 | Absence Tracking | Full/half-day, unauthorized, conversion to leave, payroll deduction |
| §17 | Break Tracking | Punch-based or auto-deduct breaks, paid/unpaid, violation detection |
| §18 | Overtime Calculation | Daily/weekly/monthly OT, two approval models, weekend/holiday/night OT, comp time |
| §19 | Night Shift Attendance | Cross-midnight logic, night differential, attendance-date = shift-start-date |
| §20 | Holiday and Weekend Work | Holiday OT tracking, compensatory leave, 2x pay vs comp-leave policy |
| §21 | Attendance Corrections | Missing punch, status/time correction, reason, attachment, approval workflow |
| §22 | Attendance Approval | Daily/weekly/monthly approval, bulk approval, delegation, escalation |
| §23 | Timesheet Management | Project/task/client time entry, billable flag, cost center, dual approval chain |
| §24 | Project/Job Costing Attendance | Hour-to-project/cost-center allocation, billable split, labor cost |
| §25 | Attendance Policy Rules Engine | Configurable rule set: all calculation rules driven by policy config, not hard-coded |
| §26 | Rounding Rules | Clock-in/out/OT/break rounding, nearest N minutes, round up/down/grace — applied only in processing |
| §27 | Attendance Exception Management | 15 exception types, severity, owner, resolution lifecycle |
| §28 | Attendance Locking and Payroll Cutoff | Period master, cutoff dates, lock/unlock workflow, retroactive correction path |
| §29 | Payroll Integration | 14 outputs: paid days, unpaid absence, late deduction, OT hours, night hours, holiday hours, weekend hours, LWP days, comp time, allowance eligibility flags |
| §30 | Leave/Absence Integration | Approved leave suppresses absence; leave hours reduce required hours; LWP → payroll deduction |
| §31 | Shift Scheduling Integration | Import planned shifts, planned vs actual variance, shift swap/change impact |
| §32 | Employee Self-Service Attendance | View attendance/history/OT/absences, submit corrections and OT requests |
| §33 | Manager Self-Service Attendance | View team attendance, approve corrections/OT/timesheets, convert absence to leave |
| §34 | HR Attendance Workspace | Full HR admin: policies, device sync, corrections, locks, import, export, recalculation |
| §35 | Attendance Import and Bulk Processing | Biometric/Excel/API import, bulk correction/approval/recalculation, validation, import history |
| §36 | Attendance Recalculation | Triggered recalculation after shift/leave/correction/policy changes; if payroll processed → adjustment, not history edit |
| §37 | Multi-Location and Multi-Timezone Support | Location timezone, 5-timestamp model (raw, device_tz, employee_tz, UTC, local) |
| §38 | Attendance Compliance Controls | Max hours/OT caps, mandatory rest/break, labor law warnings, violation alerts |
| §39 | Notifications | 30+ notification triggers to employee, manager, HR/payroll roles |
| §40 | Reports and Analytics | 25 standard reports + 13 KPI metrics |
| §41 | Security and Access Control | 7 roles with field-level, department-level, location-level access |
| §42 | Audit Trail | Full field-level audit: punch, correction, recalculation, lock, policy changes |
| §43 | Integration With Other Modules | Employee/Org/Leave/Shift/Payroll/Project Accounting integration contracts |
| §44 | Recommended Menu Structure | 20-item navigation tree |
| §45 | Attendance Record Tabs | 13-tab detail view per attendance record |
| §46 | Attendance List Columns | 20-column list with inline actions |
| §47 | Recommended Data Entities | 22 named entities |
| §48 | Validation Rules | 16 named validation rules |
| §49 | Common Mistakes to Avoid | 10 design anti-patterns (guard rails for the builder) |
| §50 | Launch Scope | Confirms all of the above is in scope — this is a full enterprise launch |

---

## 3. Pre-Built Inventory

### Database tables (already exist)

| Table | Migration | PRD coverage |
|-------|-----------|--------------|
| `attendance.work_schedule` | V8 | §11 partial — schedule type enum, work_start/end, grace, work_days bitmask, OT threshold |
| `attendance.schedule_assignment` | V8 | §11 employee-schedule assignment |
| `attendance.attendance_event` | V8 | §5 / §4 raw punch (employee_id, event_time, IN/OUT type, device_id, source, location) |
| `attendance.daily_summary` | V8 + V76 | §4 processed layer (worked/late/early/break/OT minutes, status, correction fields, shift_id, source) |
| `attendance.shift` | V74 | §11 shift catalog: start/end, break, crosses_midnight, color |
| `attendance.roster_entry` | V74 | §11 / §31 per-employee-per-date shift assignment, locked flag |
| `attendance.shift_pattern` | V75 | §11 rotating pattern: cycle_days |
| `attendance.shift_pattern_day` | V75 | §11 day-in-cycle to shift mapping, off day = NULL |
| `attendance.pattern_assignment` | V75 | §11 employee to pattern with date window, anchor |
| `timesheet.timesheet` | V14 | §23 monthly header: DRAFT/SUBMITTED/APPROVED/LOCKED, totals |
| `timesheet.timesheet_day` | V14 | §23 daily line: primary code W/L/S/BT/P/O/H/A, hours, anomalies |

### Services and controllers (already exist)

| Component | File | PRD coverage |
|-----------|------|--------------|
| `AttendanceIngestService` | attendance/service | §5 REST + CSV ingest (IN/OUT only), dedup, turnstile CSV batch with retry |
| `AttendanceEngine` | attendance/service | §4/§11/§19 core engine — roster-aware, cross-midnight, worked/late/early/OT minutes, manual correction |
| `AttendanceEngineScheduler` | attendance/service | §4 cron-driven nightly recalculation |
| `ScheduleMath` | attendance/service | §14/§15/§18 metrics calculation (late, early, OT via threshold) |
| `ShiftService` | attendance/service | §11 shift CRUD |
| `ShiftPatternService` | attendance/service | §11 pattern management + auto-roster generation |
| `RosterService` | attendance/service | §11/§31 roster CRUD + pattern expansion |
| `RosterVarianceService` | attendance/service | §31 planned vs actual variance |
| `WorkScheduleService` | attendance/service | §11 legacy schedule management |
| `AttendanceEventController` | attendance/api | §5 REST + CSV import endpoints |
| `AttendanceSummaryController` | attendance/api | §4 summary list/run-engine/correct endpoints |
| `ShiftController`, `ShiftPatternController` | attendance/api | §11 CRUD |
| `RosterController`, `RosterVarianceController` | attendance/api | §31 roster + variance |
| `TurnstileImportBatchController` | attendance/api | §35 import batch tracking |
| `WorkScheduleController` | attendance/api | §11 legacy schedules |

### SPA pages (already exist)

| Page | File | PRD coverage |
|------|------|--------------|
| `AttendanceEventsPage` | web/src/pages | §5 raw event list + manual punch |
| `AttendanceSummaryPage` | web/src/pages | §4 daily summary list + manual correction |
| `AttendanceSchedulesPage` | web/src/pages | §11 work schedule management |
| `RosterPage` | web/src/pages | §31 roster grid |
| `RosterVariancePage` | web/src/pages | §31 planned vs actual |
| `ShiftPatternsPage` | web/src/pages | §11 shift patterns + auto-roster |
| `TimesheetsPage` | web/src/pages | §23 timesheet list |
| `TimesheetDetailPage` | web/src/pages | §23 monthly grid per employee |

### Other system assets reusable for attendance

| Asset | Location | Relevance |
|-------|----------|-----------|
| `HolidayService` | core_hr / V34 | §20 holiday calendar already seeded with AZ 2026 dates |
| `WorkflowService` | workflow/ | §21 correction approval, §22 OT approval — reuse existing engine |
| `EmailService` (MailHog) | notification/ | §39 notifications |
| `NotificationPreferences` | M115 | §39 per-employee notification opt-in already built |
| `AuditService` | audit/ | §42 existing audit infrastructure |
| `AccessScopeService` | security/ | §41 hierarchy scoping already in place |
| `AttachmentService` (MinIO) | attachment/ | §21 correction attachments |
| `IcsCalendarBuilder` | calendar/ M290 | §32 ESS calendar views |
| `PayrollEngine` | payroll/ | §29 receives approved attendance summaries |
| `LeaveService` | leave/ | §30 leave approval already suppresses absence in timesheet H-code logic |

---

## 4. Net-New Requirements (the Delta)

### 4A. Policy and Configuration Layer (§2, §25, §26)
- `attendance_policy` master: named policy per legal entity/department/location/employee group with all configurable rules
- `attendance_rule` rows: configurable grace period, late/early thresholds, absence rules, OT rules, break rules, rounding rules, approval routing rules
- `work_calendar` / `holiday_calendar` references per department/location — holiday calendar exists as a table but is not linked into the attendance engine; engine does not currently recognize holidays/weekends from a calendar
- Rounding rule engine: nearest-N-minute rounding for clock-in/out/OT/break, applied only in processing layer (raw punches unchanged)
- Policy matching: resolve which policy applies to a given employee on a given date (department/location/employment-type hierarchy)

### 4B. Extended Daily Summary / Processed Attendance Record (§4)
- Current `daily_summary` is missing: `attendance_status` (18 statuses from §4), `approval_status`, `payroll_status`, `source_of_attendance` (biometric/mobile/web/kiosk/manual), `correction_status`, `work_location`, `absence_status`
- Half-day support (currently only PRESENT/PARTIAL/ABSENT/NON_WORKING_DAY/NO_SCHEDULE)
- Business-trip / remote-work / WFH / suspended / on-leave status tracking in the processed record

### 4C. Attendance Corrections Workflow (§21, §22)
- `attendance_correction_request` entity: employee submission of missing punch / time / status corrections with reason codes, attachment support
- Correction approval workflow: employee to manager to HR (if required) and then recalculation trigger
- Manager bulk approval surface
- Payroll-lock validation: corrections after lock require HR/Payroll approval
- Employee cannot approve own correction (validation rule)
- Manager cannot approve outside reporting scope (ABAC guard)

### 4D. Overtime Request and Approval (§18)
- `overtime_request` entity: employee/manager-initiated OT request with date, hours, reason
- Two OT models: pre-approval (OT not payable until approved) vs auto-calculated (clock-out vs shift-end)
- OT approval workflow (dept-head for >2h per §25 example rule — configurable)
- Unauthorized OT exception detection
- OT cap enforcement (daily/weekly/monthly budget)
- Compensatory time off tracking

### 4E. Break Tracking (§17)
- `break_record` entity: break_start, break_end, type (lunch/tea/other), paid/unpaid flag
- Current engine auto-deducts break_minutes from worked time but does not track individual break events
- Break violation detection (excessive break, missing break)
- Punch-based vs auto-deduct vs manager-entry break modes

### 4F. Exception Management (§27)
- `attendance_exception` entity: 15 exception types (missing clock-in, missing clock-out, late, early, unauthorized absence, OT without approval, outside geofence, duplicate punch, device mismatch, attendance during leave, punch after termination, excessive break, below min hours, over max hours, shift mismatch)
- Per exception: severity, owner, status (Open/Pending/Resolved/Waived), reason, employee explanation, manager decision, HR review, payroll impact flag
- Exception resolution workflow
- Payroll-blocking exception flag (blocks period lock until resolved)

### 4G. Attendance Period Locking (§28)
- `attendance_period` entity: period name, start/end, payroll_cutoff_date, manager_approval_deadline, HR_lock_date, payroll_lock_date
- `attendance_period_lock` per period: lock/unlock actions, unlock request, unlock approval
- Retroactive correction path: after lock, changes must go through HR/Payroll approval and trigger an adjustment (not silent history edit)
- Lock status surfaced in all summary queries

### 4H. Payroll-Ready Attendance Summary (§29)
- `attendance_payroll_summary` entity: aggregated per-employee per-period record containing the 14 payroll outputs
- Separate from `daily_summary` (which is the processed layer)
- Only populated from APPROVED + LOCKED records
- Outputs: paid_working_days, unpaid_absence_days, late_deduction_amount, early_leave_deduction, overtime_hours (regular/weekend/holiday/night), leave_without_pay_days, comp_time, attendance_allowance_eligible, shift_allowance_eligible, meal_allowance_eligible, transport_allowance_eligible
- Payroll engine reads this summary, not raw daily_summary rows (current payroll reads daily_summary directly — this is the §49 anti-pattern #1)

### 4I. Biometric Device Master (§6)
- `device_master` entity: code, name, IP, location, type (fingerprint/face/RFID/PIN), status, sync_frequency, integration_method
- `employee_biometric_id_mapping`: employee to device employee code, per device
- Device health monitoring: last_sync_time, sync_status, failed_sync_count
- Failed sync alerts wired to §39 notification
- Device punch log (currently attendance_event captures device_id but there is no device master table)

### 4J. Mobile Attendance (§7, §8)
- `mobile_attendance_log` entity: GPS coordinates (lat/lng/accuracy/address), selfie_attachment_id, device_id, geofence_result, approval_required flag
- `geofence_location` entity: name, center lat/lng, radius_meters, allowed/restricted flag
- Mobile clock-in/out creates a punch event tagged as source=MOBILE with location proof attached
- Geofence validation at clock-in time
- Offline mode: queue locally, sync on reconnect
- Fraud detection rule hooks

### 4K. Web Attendance / ESS Clock-In (§9)
- Web clock-in endpoint tagged source=WEB with IP address, user-agent, browser location (if allowed)
- Remote work reason capture at clock-in
- Policy check at clock-in time (is web clock-in allowed for this employee?)

### 4L. Kiosk Attendance (§10)
- Kiosk session mode: PIN/QR/photo login, employee search
- Kiosk device binding to location
- Offline mode + sync on reconnect

### 4M. Flexible Working Hours (§12)
- `flexitime_balance` entity: running balance of flex hours per employee per period
- Core hours window enforcement (must be present during defined window regardless of start/end)
- Weekly/monthly required-hours target (not fixed clock-in time)
- Carry-forward hours, negative hour tracking

### 4N. Remote Work / WFH (§13)
- Remote work request and approval
- WFH schedule master per employee
- Remote clock-in validation: must have approved WFH, GPS required, daily timesheet confirmation

### 4O. Absence Tracking Enhancement (§16)
- Absence reason, manager explanation requirement, auto-conversion to unpaid leave
- Absence statuses (9 statuses in §16 vs the 3 current states: ABSENT/PARTIAL/PRESENT)
- Absence to leave conversion workflow

### 4P. Night Shift Enhancement (§19)
- Night differential flag on summary record for payroll
- Explicit validation: must not split cross-midnight attendance into two absence days

### 4Q. Holiday and Weekend Work Enhancement (§20)
- Compensatory leave generation from holiday/weekend work (payroll currently handles 2x rate via M155 but comp leave is not created)
- Holiday work tracking in the attendance record distinct from regular OT
- Policy-controlled: 2x pay OR comp leave (not both)

### 4R. Timesheet Enhancement (§23, §24)
- Project/task/client/activity code per timesheet day line (current `timesheet_day` has no project allocation columns)
- Billable/non-billable flag
- Cost center allocation per line
- Dual approval: manager approval + project manager approval
- Project accounting integration seam

### 4S. Multi-Location / Multi-Timezone (§37)
- `attendance_event` currently stores `event_time` as a single TIMESTAMPTZ; needs: `raw_timestamp`, `device_timezone`, `employee_work_timezone`, `utc_timestamp`, `processed_local_time` (5-column model)
- Location-based timezone resolution

### 4T. ESS / MSS Surfaces (§32, §33)
- `/api/self/attendance/*` endpoints: own daily attendance, monthly summary, OT hours, correction submission
- Manager attendance view: team presence today, approve corrections/OT for direct reports

### 4U. HR Attendance Workspace (§34)
- Policy management UI
- Bulk operations: correction, shift assignment, approval, recalculation, absence marking
- Exception queue management
- Period lock/unlock controls
- Import history + retry UI (partially exists via TurnstileImportBatch)

### 4V. Attendance Dashboards (§3)
- HR dashboard: present/absent/late/early/on-leave today, missing clock-outs, pending corrections, device sync errors
- Manager dashboard: team presence, shift coverage
- Payroll dashboard: period status, approved records, payroll-ready summary, blocking exceptions

### 4W. Compliance Controls (§38)
- Max daily/weekly hours guardrail
- Mandatory rest period validation (minimum hours between shifts)
- OT cap enforcement
- Night work limits
- Labor law violation alerts

### 4X. Reports and Analytics (§40)
- 25 standard reports (full list in Section 10)
- 13 KPIs (full list in Section 10)

### 4Y. Notifications (§39)
- 30+ notification triggers wired into M115 notification framework + EmailService

### 4Z. Audit Trail Enhancement (§42)
- Need to capture: raw punch import, correction request/approval, recalculation, late/absence override, OT approval, shift change, lock/unlock, payroll transfer, policy changes, GPS coordinates on mobile events

---

## 5. Roles and Actors (§41)

| Role | Access Scope | Key Permissions |
|------|-------------|-----------------|
| **Employee** | Own data only | View own attendance/timesheets, submit correction requests, submit OT requests, download own report, clock-in/out via web/mobile/kiosk |
| **Manager** | Direct reports (hierarchy-scoped via AccessScopeService) | Approve corrections/OT/timesheets for team, view team attendance dashboard, convert absence to leave, enter attendance for team |
| **HR Attendance Officer** | All employees in scope (org-unit scoped for HR_SPECIALIST) | Full record management, policy config, exception resolution, manual corrections, import/export, recalculation, period lock |
| **Payroll Officer** | Read-only on attendance; payroll summary access | View payroll-ready summaries, approve/lock payroll period, unlock request approval |
| **IT / Admin** | Devices only | Device master CRUD, biometric mapping, sync monitoring |
| **Auditor** | Read-only all records | View audit trail, raw punches, summaries, lock history, no edit capability |
| **System Admin** | Configuration only | Policy config, calendar setup, rule engine config — SoD: restricted from approving payroll records |

Field-level controls:
- GPS coordinates: HR Attendance Officer + Auditor only (not visible to employees)
- Salary/deduction amounts in attendance summaries: Payroll Officer only
- Device configuration: IT/Admin only
- Correction approval: manager for own team, HR for override/special cases

---

## 6. Approval Workflows

### 6.1 Attendance Correction (§21)
```
Employee submits correction request
  → Manager reviews (approve / reject / return for correction)
  → HR review (if required by policy or if payroll period is locked)
  → On approval: AttendanceEngine.recalculate() triggered for the affected day
  → If payroll already processed: creates adjustment record, does NOT silently edit history
```
Guard: employee cannot approve own correction. Manager cannot approve outside AccessScopeService hierarchy.

### 6.2 Overtime Request (§18, §22)
```
Employee (or manager on behalf) submits OT request
  → Manager approves if OT <= 2h
  → Department Head required if OT > 2h (configurable per policy rule)
  → On approval: OT hours added to approved record and payroll summary
```
Model 1 (pre-approval): OT hours not counted until approved.
Model 2 (auto-calculated): engine records actual OT, approval confirms payability.

### 6.3 Timesheet Approval (§23)
```
Employee submits monthly timesheet
  → Manager approves (existing TIMESHEET_APPROVAL workflow: step 1 = HR_SPECIALIST, step 2 = HR_ADMIN)
  → Payroll sign-off → LOCKED
```
Enhancement needed: add project manager approval step when timesheet has project allocations.

### 6.4 Attendance Period Lock (§28)
```
System generates period (e.g. 1-31 May)
  → Manager approval deadline (e.g. 2 Jun): managers must approve all team exceptions
  → HR Lock (e.g. 3 Jun): HR Attendance Officer locks the period
  → Payroll Lock: Payroll Officer locks for payroll processing
  → Post-lock correction: employee submits → HR + Payroll Officer approval → retroactive adjustment
  → Unlock: HR Attendance Officer requests unlock → Payroll Officer approves (with reason)
```

---

## 7. Payroll Integration Points (§29)

The attendance module must produce an `attendance_payroll_summary` record per employee per period containing these 14 outputs consumed by the payroll engine:

| # | Output field | Payroll use |
|---|-------------|-------------|
| 1 | `paid_working_days` | Base salary proration |
| 2 | `unpaid_absence_days` | Deduction from gross |
| 3 | `late_deduction_amount` | Direct deduction (if policy applies) |
| 4 | `early_leave_deduction` | Direct deduction |
| 5 | `overtime_hours` (regular) | OT pay at 1.5x (AZ statutory) |
| 6 | `night_shift_hours` | Night differential pay |
| 7 | `holiday_work_hours` | 2x OT pay (AZ statutory, M155) |
| 8 | `weekend_work_hours` | 1.5x or 2x per policy |
| 9 | `leave_without_pay_days` | Deduction from gross |
| 10 | `comp_time_hours` | Reduces OT payable |
| 11 | `attendance_allowance_eligible` | Boolean — triggers allowance payment (M159) |
| 12 | `shift_allowance_eligible` | Boolean — night/shift allowance (M159) |
| 13 | `meal_allowance_eligible` | Boolean — per-day meal allowance |
| 14 | `transport_allowance_eligible` | Boolean — per-day transport allowance |

**Critical rule (§49 anti-pattern #1):** Payroll engine must read `attendance_payroll_summary` (approved + locked) ONLY — never raw `daily_summary` or `attendance_event`. Current payroll engine reads `daily_summary` directly. This must be corrected in Phase D.

---

## 8. Leave Integration Points (§30)

| Integration | Direction | Rule |
|-------------|-----------|------|
| Approved leave suppresses absence | Leave → Attendance | If employee has approved leave for a date, daily_summary.status = ON_LEAVE, not ABSENT |
| Leave hours reduce required hours | Leave → Attendance | Half-day leave: only 50% of standard hours required |
| Unpaid leave → LWP deduction | Leave → Payroll via Attendance | leave_without_pay_days passed to payroll |
| Sick leave integration | Leave → Attendance | Sick day: status = ON_LEAVE (SICK), no late/absent penalty |
| Absence → leave conversion | Attendance → Leave | Manager/HR can convert unauthorized absence to annual leave deduction |
| Leave request from absence | Attendance → Leave | Employee can submit leave request retroactively to cover an absence |
| Leave balance check | Leave → Attendance | If employee has no remaining leave balance, retroactive leave conversion rejected |

**Current state bug:** The `daily_summary` engine does NOT currently query approved leave records — absences are marked ABSENT regardless of approved leave. The timesheet H-code and L-code logic partially handles leave suppression for timesheets but the attendance engine layer does not. This must be fixed in Phase A.

---

## 9. Key Validation Rules (§48)

| # | Rule | Build status |
|---|------|--------------|
| 1 | Employee must be active on attendance date | AttendanceIngestService at ingest — BUILT |
| 2 | Cannot clock in after termination date | AttendanceIngestService + engine — NOT BUILT |
| 3 | Cannot clock in before hire date (unless pre-hire training flag) | NOT BUILT |
| 4 | Clock-out cannot be before clock-in (unless cross-midnight with crosses_midnight=true) | Implicit in engine — NOT EXPLICIT |
| 5 | Duplicate punches detected | existsByEmployeeIdAndEventTime — BUILT |
| 6 | Missing clock-in/out → exception record | Engine produces PARTIAL status but no exception entity — PARTIAL |
| 7 | Attendance during approved leave → review required | NOT BUILT |
| 8 | Outside geofence → blocked or approval required | Mobile layer — NOT BUILT |
| 9 | OT must follow policy approval | OT request workflow — NOT BUILT |
| 10 | Correction after payroll lock → special approval | NOT BUILT |
| 11 | Employee cannot approve own correction | NOT BUILT |
| 12 | Manager cannot approve outside reporting scope | Not wired into corrections — NOT BUILT |
| 13 | Device punch must map to valid employee | Employee lookup built; device master — NOT BUILT |
| 14 | Shift must exist for shift-based employees | Engine returns NO_SCHEDULE status — PARTIAL |
| 15 | Raw punch data must NEVER be deleted after processing | No delete endpoint on attendance_event — IMPLICITLY SATISFIED |
| 16 | Payroll summary uses only approved/locked records | Payroll reads raw daily_summary — NOT BUILT (anti-pattern exists) |

---

## 10. Report Set (§40)

### 25 Standard Reports

| # | Report | Key dimensions |
|---|--------|---------------|
| 1 | Daily Attendance Report | Date, all employees, status breakdown |
| 2 | Monthly Attendance Report | Employee, month, worked days, absences, OT |
| 3 | Employee Attendance Report | Per-employee detail, configurable period |
| 4 | Team Attendance Report | Manager-scoped, team breakdown |
| 5 | Department Attendance Report | Dept-level aggregate |
| 6 | Branch/Location Attendance Report | Location-level aggregate |
| 7 | Late Arrival Report | Employee, late minutes, frequency, trend |
| 8 | Early Leave Report | Employee, early minutes, frequency |
| 9 | Absence Report | Absence type, reason, trend |
| 10 | Missing Punch Report | Unresolved missing punch list |
| 11 | Overtime Report | Employee/dept OT hours, cost, approved vs actual |
| 12 | Break Violation Report | Excessive/missing break incidents |
| 13 | Holiday/Weekend Work Report | Holiday work hours, comp leave generated |
| 14 | Night Shift Work Report | Night hours by employee/dept |
| 15 | Attendance Correction Report | Correction requests, approval rate, resolution time |
| 16 | Timesheet Report | Submitted/approved/locked timesheets, project hours |
| 17 | Payroll Attendance Summary Report | Per-employee payroll inputs, period aggregate |
| 18 | Device Punch Log Report | Raw device punches, sync issues |
| 19 | Mobile GPS Attendance Report | Mobile clock-ins with location, geofence result |
| 20 | Geofence Violation Report | Outside-fence incidents |
| 21 | Attendance Exception Report | Exception types, severity, resolution status |
| 22 | Attendance Audit Report | Full change history per employee/period |
| 23 | Shift Compliance Report | Planned vs actual shift, variance analysis |
| 24 | Approval Delay Report | Manager approval time vs deadline |
| 25 | Attendance vs Leave Reconciliation Report | Cross-check attendance and leave records |

### 13 KPI Metrics

| # | KPI | Definition |
|---|-----|-----------|
| 1 | Attendance Rate | % employees present / expected per day |
| 2 | Absenteeism Rate | Unauthorized absent days / total working days |
| 3 | Average Late Minutes | Mean late minutes per day/month, by dept |
| 4 | OT Hours by Department | Total approved OT hours per dept per period |
| 5 | OT Cost by Department | OT hours times OT rate per dept per period |
| 6 | Missing Punch Frequency | Missing punches per employee per month |
| 7 | Correction Frequency | Correction requests per employee per month |
| 8 | Shift Compliance Rate | % rostered shifts with compliant actual attendance |
| 9 | Unauthorized Absence Rate | Unauthorized absences / total absences |
| 10 | Payroll-Blocking Exceptions | Count of open exceptions blocking payroll lock |
| 11 | Device Sync Reliability | % successful device syncs / total attempts |
| 12 | Manager Approval Delay | Average hours from submission to approval |
| 13 | Correction Resolution Time | Average hours from correction request to resolution |

---

## 11. Proposed Build Phases (A–F)

### Phase A — Attendance Policy Engine + Configuration (~2 milestones)
**Sections:** §2, §25, §26, §30 (leave integration fix), §4 (status expansion)

- New DB entities: `attendance_policy`, `attendance_rule`
- PolicyService: resolve which policy applies to a given employee/date
- Wire policy into AttendanceEngine: replace hard-coded thresholds with policy lookups
- Rounding engine: nearest-N-minute rounding applied in processed layer only
- Holiday calendar integration into the engine (use HolidayService to suppress absence on holidays and flag holiday work)
- **Leave integration fix:** query approved leave before marking ABSENT (existing bug)
- Daily summary status expansion: add LATE, EARLY_LEAVE, HALF_DAY, ON_LEAVE, HOLIDAY, WEEKLY_OFF, BUSINESS_TRIP, REMOTE_WORK, MISSING_PUNCH, PENDING_CORRECTION, APPROVED, PAYROLL_PROCESSED
- SPA: Attendance Policy management page (HR admin)

**Dependency:** Foundation for all subsequent phases.

### Phase B — Capture Sources (~4 milestones)
**Sections:** §6, §7, §8, §9, §10

- B.1: Biometric device master — `device_master`, `employee_biometric_id_mapping`; device health monitoring; sync status endpoint
- B.2: Mobile attendance — `mobile_attendance_log`, `geofence_location`; GPS capture + geofence validation; selfie attachment; source=MOBILE events
- B.3: Web attendance / ESS clock-in — web punch endpoint with IP/UA capture; policy-controlled; remote work reason capture
- B.4: Kiosk attendance — kiosk session mode (PIN/QR); device location binding; source=KIOSK events
- SPA: Device management (IT admin), Mobile attendance log view, Web clock-in widget (ESS)

**Dependency:** Phase A (policy determines which capture source is allowed per employee)

### Phase C — Advanced Processing (~5 milestones)
**Sections:** §17, §18, §21, §22, §27, §28

- C.1: Break tracking — `break_record` entity; punch-based vs auto-deduct; break violation detection
- C.2: Overtime request/approval — `overtime_request`, OT approval workflow; pre-approval vs auto-calculated models; OT cap; comp time tracking
- C.3: Attendance correction workflow — `attendance_correction_request`; employee to manager to HR approval chain; recalculate-on-approve; attachment support; payroll-lock guard
- C.4: Attendance exception management — `attendance_exception`; 15 exception types; severity; resolution lifecycle; payroll-blocking flag; exception queue UI
- C.5: Attendance period locking — `attendance_period`, `attendance_period_lock`; lock/unlock workflow; retroactive correction path; period status surfaced in all queries
- SPA: Correction request portal (ESS/MSS), Exception queue (HR), Period lock management (HR + Payroll)

**Dependency:** Phase A (policy drives OT approval model and correction rules)

### Phase D — Payroll Integration Hardening (~3 milestones)
**Sections:** §29, §36, §19, §20

- D.1: `attendance_payroll_summary` entity — 14 payroll outputs per employee per period; generation from approved+locked records only
- D.2: Recalculation engine — triggered recalculation after shift/leave/correction/policy changes; if payroll already processed → create adjustment record (not silent edit)
- D.3: Payroll engine migration — swap current `daily_summary` read in PayrollEngine for `attendance_payroll_summary` read; add leave integration (LWP days, leave-suppresses-absence); holiday/weekend work differentiation; night shift differential flag
- SPA: Payroll Officer attendance summary view

**Dependency:** Phase C (must have approved + locked records before generating payroll summaries)

**PAYROLL SIGN-OFF REQUIRED** — this phase changes how payroll reads attendance data.

### Phase E — Self-Service + Dashboards (~3 milestones)
**Sections:** §3, §32, §33, §34, §35

- E.1: Employee Self-Service — `/api/self/attendance/*`; view own daily/monthly attendance; clock-in/out via web; submit correction/OT requests; payroll attendance summary
- E.2: Manager Self-Service — team attendance dashboard; who is present/absent/late today; approve corrections/OT; team exception list; shift coverage
- E.3: HR Attendance Workspace — real-time HR dashboard widgets; bulk operations; policy management; import/export; recalculation trigger; exception queue; lock management
- Payroll Officer dashboard: period status, blocking exceptions, payroll-ready summary view

**Dependency:** Phase C + D (dashboards show exception/lock/payroll-summary data)

### Phase F — Analytics, Compliance, and Notifications (~3 milestones)
**Sections:** §38, §39, §40, §12 (flexitime), §13 (WFH), §37 (timezone), §24 (project costing)

- F.1: Compliance controls — max hours guardrail, mandatory rest, OT cap, night work limits, labor law violation alerts
- F.2: Notifications — wire 30+ attendance notification triggers into M115 notification framework + EmailService
- F.3: Reports + Analytics — 25 standard reports + 13 KPIs; integrate into existing report hub and custom report builder (M119)
- F.4 (deferred seams): Flexible working hours (§12), Remote WFH tracking (§13), Multi-timezone 5-column model (§37), Timesheet project costing (§23/§24)

**Dependency:** Phase D + E (reports read from payroll summaries and approved records)

---

## 12. Payroll Impact Assessment

| Section | Payroll impact | Severity |
|---------|---------------|----------|
| §18 Overtime Calculation | Direct — OT hours and rates → payroll line items | HIGH |
| §20 Holiday/Weekend Work | Direct — 2x or comp-leave decision affects gross | HIGH |
| §28 Period Locking | Enables/blocks payroll run; unlock changes finalized salary | HIGH |
| §29 Payroll Integration | The 14 output fields are direct payroll inputs | CRITICAL |
| §14 Late Arrival Tracking | Late-deduction-amount → payroll deduction | MEDIUM |
| §15 Early Leave Tracking | Early-leave deduction → payroll deduction | MEDIUM |
| §16 Absence Tracking | Unpaid absence → payroll deduction | MEDIUM |
| §30 Leave Integration | LWP days, half-day leave → payroll deduction | MEDIUM |
| §36 Recalculation | Post-payroll recalculation must create adjustment, not overwrite | HIGH |
| §11 Shift Allowance | Shift/night/meal/transport allowance eligibility flags → allowance payments | MEDIUM |
| §12 Flexible Hours | Under/over hours may trigger deductions or OT | MEDIUM |
| §19 Night Shift | Night differential pay | MEDIUM |
| §38 Compliance | OT cap enforcement prevents over-payroll | LOW-MEDIUM |

All of Phase D is PAYROLL SIGN-OFF REQUIRED. The §29 output contract and the payroll engine migration must be human-reviewed and signed off before merging.

The correction/OT approval workflows in Phase C also affect payroll indirectly — an approved correction changes the attendance record that feeds payroll summaries. These must be validated against `fixtures/` expected values in the payroll-validation gate.

---

## 13. Functional Gaps Requiring Clarification (Pre-Build Blockers)

These are not hard blockers for Phase A/B but must be pinned before Phase C/D:

1. **Late deduction formula**: is `late_deduction_amount` = (late_minutes / standard_daily_minutes) × daily_salary, or a fixed AZN penalty per occurrence? Must be specified for Phase D.

2. **OT approval threshold**: §25 says ">2h requires dept-head approval" as an example rule. Must confirm whether this is the actual Millers rule or configurable-per-policy.

3. **Comp time vs cash**: §20 says holiday work = "2x OT or comp leave per policy." The choice between these two modes must be made per legal entity / employee group. Must confirm the Millers default.

4. **Absence-to-leave conversion**: which leave type does an unauthorized absence convert to — annual leave deduction, or unpaid leave? Does the employee have to consent?

5. **Payroll period alignment**: does the attendance period (§28) align 1:1 with the payroll period, or can they differ? Must confirm before building period lock.

6. **Flexitime carry-forward limit**: §12 mentions carry-forward hours but does not specify the limit or expiry. Must be defined before Phase F.4.

7. **Device integration method**: §6 lists 8 integration methods. CSV (turnstile import) is already built. Which other method(s) does Millers actually use for Phase B.1?

8. **Geofence enforcement mode**: §8 says "outside radius → blocked or sent for manager approval." Must confirm which mode Millers uses (hard block vs soft approval) for Phase B.2.
