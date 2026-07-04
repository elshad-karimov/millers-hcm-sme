# Leave / Absence Management — Analysis

## Module Scope
Enterprise leave management engine: leave types, categories, entitlement rules, balance ledger, accrual, carry-forward with expiry, encashment, leave requests and approvals, partial/hourly leave, period locking, payroll integration (unpaid deduction + encashment earning), unauthorized absence handling, HR workspace, analytics, and leave liability reporting.

**payroll_impact: true** (encashment → payroll earning; unpaid leave → payroll deduction; negative balance recovery → payroll deduction)

## What Exists vs What is New

### EXISTING (do not re-implement, only extend)
- `leave_mgmt` schema: `leave_type`, `leave_balance`, `leave_request`, `leave_group`, `leave_group_entitlement`, `blackout_window`
- Services: `LeaveTypeService`, `LeaveBalanceService`, `LeaveRequestService`, `LeaveAccrualService`, `LeaveYearEndService`, `LeaveGroupService`, `BlackoutWindowService`, `LeaveOverlapAnalyzer`, `TeamCalendarService`
- `leave_type` has: `code`, `name`, `paid`, `requires_attachment`, `requires_replacement`, `default_annual_entitlement_days`, `carry_forward_limit_days`, `accrues_monthly`, `monthly_accrual_days`, `max_consecutive_days`, `exclude_weekends`, `exclude_holidays`, `seniority_brackets_json`
- `leave_balance` has: `entitlement_days`, `carried_forward_days`, `adjustment_days`, `used_days`, `reserved_days` (summary only — no ledger)
- `leave_request` has: `half_day` boolean, `replacement_employee_id`, `workflow_instance_id`, `blackout_flag`
- `LeaveAccrualService`: monthly accrual with seniority brackets, group overrides, org-unit policy chain
- `LeaveYearEndService`: carry-forward with `carry_forward_limit_days` cap (no expiry date)
- Workflow definition: `LEAVE_REQUEST_APPROVAL` (Manager → HR)
- HolidayService, ABAC via AccessScopeService

### NEW (this PRD)
1. **Leave Balance Ledger** (§18) — transaction-by-transaction audit trail
2. **Leave Categories** (§6) — group leave types for reporting + payroll treatment
3. **Leave Entitlement Rules Engine** (§15) — grade/employment-type/legal-entity specific rules
4. **Carry-Forward Expiry** (§19-20) — expiry date tracking + scheduled expiry job
5. **Negative Balance Control** (§31) — allow/reject/payroll-recover
6. **Partial/Hourly Leave** (§23) — hours-based leave beyond half_day boolean
7. **Leave Period Locking** (§45) — payroll cutoff lock (like AttendancePeriod)
8. **Unpaid Leave → Payroll Bridge** (§39) — systematic deduction into PayrollRun
9. **Leave Encashment** (§21) — convert unused balance to payroll earning [PAYROLL SIGN-OFF]
10. **Unauthorized Absence → Leave Conversion** (§36) — extend attendance exceptions
11. **Leave Delegation** (§37) — per-leave-request delegation tracking
12. **HR Leave Workspace** (§44) — operational console + analytics
13. **Leave Liability Report** (§52) — balance × daily rate financial report

## Milestone Plan

| Milestone | Feature | Flyway | Payroll Impact |
|-----------|---------|--------|----------------|
| M338 | Leave Categories + Balance Ledger | V185 | No |
| M339 | Leave Entitlement Rules Engine | V186 | No |
| M340 | Carry-Forward Expiry + Negative Balance | V187 | No |
| M341 | Partial/Hourly Leave + LeaveType flags | V188 | No |
| M342 | Leave Period Locking | V189 | No |
| M343 | Unpaid Leave → Payroll Bridge | V190 | **YES** |
| M344 | Leave Encashment | V191 | **YES** |
| M345 | Unauthorized Absence → Leave Conversion | V192 | No |
| M346 | Leave Delegation per Request | V193 | No |
| M347 | HR Leave Workspace + Analytics | V194 | No |
| M348 | Leave Liability Report + Reports Suite | V195 | No |

## Key Business Rules

### Balance Formula
`Available = entitlement_days + carried_forward_days + adjustment_days - used_days - reserved_days`

### Accrual (existing, validated)
- Monthly: `default_annual_entitlement_days / 12` (e.g., 21/12 = 1.75 days/month)
- Seniority brackets override the flat rate
- LeaveGroup entitlement overrides the type default

### Encashment Rate
`encashment_amount = days × (base_salary / 30)` — same divisor as T&A AttendanceDeductionBridge

### Unpaid Leave Deduction
`deduction = unpaid_days × (base_salary / 30)` — consistent with encashment rate

### Carry-Forward Expiry
- `carry_forward_expiry_months` (nullable INT on leave_type): months after year-end when CF expires
- Example: value=3 → CF granted Dec 31 expires Mar 31 of next year
- Scheduled job checks monthly; writes EXPIRY ledger entries

### Negative Balance
- `negative_balance_allowed` BOOLEAN on leave_type (default FALSE)
- `max_negative_days` NUMERIC(6,2) on leave_type
- At termination: negative balance creates payroll deduction in final settlement

### Period Lock Behaviour
- `leave_period.status` = OPEN | LOCKED | CLOSED
- LOCKED: new requests for that period are BLOCKED (unless HR override)
- Post-payroll retroactive corrections create PAYROLL_ADJUSTMENT records

## Roles
| Role | Access |
|------|--------|
| Employee | Own balance, own requests, team calendar (read-only) |
| Manager | Team leave approvals, team calendar, staffing conflict view |
| HR Leave Officer (HR_SPECIALIST) | Leave types, policies, balance adjustments, accrual run, period lock |
| HR Admin (HR_ADMIN) | All HR access + encashment approval + period lock + unlock |
| Payroll Officer (PAYROLL_OFFICER) | Read leave payroll summary, encashment amounts |
| Finance / Auditor | Leave liability report, read-only |

## Validation Rules
1. Employee must be active during requested leave dates
2. Leave type must be active
3. Balance sufficient (unless negative_balance_allowed)
4. No overlapping approved/pending leave (LeaveOverlapAnalyzer)
5. Not in blocked blackout window
6. Not in LOCKED leave period
7. required_attachment met
8. max_consecutive_days not exceeded
9. Manager cannot approve own leave (workflow enforces)
10. Half-day requests must be single date (existing)

## Integration Points
- **Payroll**: M343 unpaid deduction, M344 encashment earning → PayrollRun
- **Attendance**: Approved leave → attendance ON_LEAVE (existing PresenceResolver)
- **Workflow**: LeaveRequestWorkflowListener (existing) handles approve/reject callbacks
- **Holiday**: HolidayService (existing) for day-count exclusion
- **AttendanceException**: M345 adds convert-to-leave path

## Gap Checker Findings — No HARD STOPs
All formulas are defined (salary/30 per day). Approval chains reuse existing WorkflowService.
Multi-tenancy gap acknowledged: system uses single-tenant `defaultTenantId()` pattern throughout; follow same approach.
