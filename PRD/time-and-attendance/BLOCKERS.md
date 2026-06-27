# Time & Attendance PRD — Gap-Check Report

**Date:** 2026-06-26
**Checker:** gap-checker agent
**Verdict:** HARD STOP — 4 blocker questions must be answered before any build work begins.

---

## Assessment Summary

| # | Topic | Answer |
|---|-------|--------|
| 1 | Overtime multiplier | PINNED — already in system |
| 2 | Late deduction formula | AMBIGUOUS — **HARD STOP** |
| 3 | Absence deduction formula | AMBIGUOUS — **HARD STOP** |
| 4 | Early leave deduction | AMBIGUOUS — **HARD STOP** |
| 5 | Night shift differential | INVENTABLE FROM EXISTING RULES |
| 6 | Holiday work rates | PINNED — already in system |
| 7 | Correction approval chain | AMBIGUOUS — **HARD STOP** |
| 8 | Overtime approval chain | PINNED |
| 9 | Period lock authority | INVENTABLE FROM EXISTING ROLES |
| 10 | WorkflowEngine sufficiency | PINNED — existing engine sufficient |
| 11 | Manager hierarchy scope on corrections | PINNED |
| 12 | HR Attendance Officer scope | INVENTABLE FROM EXISTING ROLES |
| 13 | Payroll Officer recalc trigger | INVENTABLE FROM EXISTING ROLES |
| 14 | Grace period defaults | PINNED — per policy, configurable |
| 15 | Rounding — required at launch? | NOT REQUIRED AT LAUNCH (optional) |
| 16 | Project/job costing — required at launch? | NOT REQUIRED AT LAUNCH |
| 17 | Kiosk — required at launch? | NOT REQUIRED AT LAUNCH (phased) |
| 18 | Biometric integration — real-time or CSV? | NOT REQUIRED AT LAUNCH (CSV sufficient) |

---

## Detailed Analysis

### PAYROLL RULES

#### Q1 — Overtime multiplier (§18, §29)
**PINNED — already in system.**

PRD §18 states explicitly: "OT multipliers (AZ rules already in system): 1.5x regular OT, 2x holiday OT." This is confirmed by the seeded `OVERTIME_AZ` statutory rule (`V16__payroll_az2026_seed.sql` and `V38__weekend_ot_cap.sql`):
- Standard OT: first 2 hours/day at 1.5x, beyond that at 2.0x (Labour Code Art. 165)
- Holiday OT: 2.0x all hours (Art. 167)
- Weekend OT: 2.0x all hours (Art. 167)
- Daily cap: 4 hours (Art. 99)

The `StatutoryCalculator.overtimePay()` already implements this correctly. No invention required.

---

#### Q2 — Late deduction formula (§14, §29)
**AMBIGUOUS — HARD STOP.**

PRD §14 gives policy *examples* only (first 10 min free; >3 lates/month = warning; >60 late-minutes/month = salary deduction; late >2h = half day; late without approval = unpaid absence). These are illustrative, not binding rules.

PRD §29 lists `late_deduction_amount` as a payroll output but gives **no formula** for converting late minutes into a currency deduction.

A builder would have to invent: (a) the monetary deduction formula (per-minute rate? flat penalty per occurrence? daily-salary-fraction?), (b) the threshold at which a deduction fires, (c) whether it is an attendance-policy-level configurable or a fixed statutory rule, and (d) whether repeated lateness in the same month compounds or caps.

**This directly affects employee net pay. A builder must not invent it.**

**Question for the human:**
> What is the late deduction formula? For example: "deduct (daily_salary / working_hours) per each late minute beyond the grace period" or "deduct 1 day's salary when total monthly late minutes exceed 60" or "a configurable rule stored per attendance policy." Please specify the threshold, rate, and whether it is configurable per policy or fixed.

---

#### Q3 — Absence deduction formula (§16, §29)
**AMBIGUOUS — HARD STOP.**

PRD §16 lists absence statuses (Absent, Unauthorized Absence, Converted to Unpaid Leave, Payroll Deduction Applied, etc.) but does **not pin the deduction formula**.

PRD §29 lists `unpaid_absence_days` and `leave_without_pay_days` as payroll outputs but gives no formula for translating them into a deduction amount. The existing PayrollEngine has no `absence_deduction` concept at all — it only has the statutory deductions (tax, DSMF, MMI, unemployment) and one-off/recurring `PayrollDeduction` rows.

A builder would have to invent: (a) whether 1 unpaid absence day = (monthly_salary / 30) or (monthly_salary / working_days_in_month) or (monthly_salary / 22) or a fixed calendar-day divisor, (b) whether unauthorized absence deducts differently from approved unpaid leave, (c) whether the deduction flows into the existing `PayrollDeduction` table or requires a new attendance deduction lane in the PayrollEngine.

**This directly affects employee net pay.**

**Question for the human:**
> What is the unpaid-absence deduction formula? Specifically: (a) what divisor converts monthly salary to a daily rate (30 calendar days, 22 working days, or actual working days in the period)? (b) Is the formula the same for unauthorized absences and approved leave-without-pay? (c) Should the deduction be passed to payroll as a pre-computed `PayrollDeduction` row, or does the PayrollEngine need a new attendance-deduction lane that reads the approved attendance summary?

---

#### Q4 — Early leave deduction (§15, §29)
**AMBIGUOUS — HARD STOP.**

PRD §15 states early leave "can be: allowed if approved, deducted from salary/leave balance, counted as violation, ignored within tolerance." All four paths are listed as possibilities without specifying which applies here or how they are combined.

PRD §29 lists `early_leave_deduction` as a payroll output but gives no formula (rate per minute? same as absence formula? only after exceeding a threshold?).

A builder would have to invent: (a) the monetary formula for deducting early leave minutes, (b) the threshold (if any) below which early leave is tolerated, (c) whether it uses the same per-minute rate as the late-deduction formula or a separate rate.

**This directly affects employee net pay.**

**Question for the human:**
> What is the early leave deduction formula? Should it mirror the late-deduction formula (e.g., per-minute deduction above a tolerance threshold), or is it a different rate? Is early leave deductible from the leave balance as an alternative to a salary deduction?

---

#### Q5 — Night shift differentials (§19)
**INVENTABLE FROM EXISTING RULES.**

PRD §19 mentions "night differential" but does not specify a rate. However, AZ Labour Code does not mandate a specific night-shift differential rate the way it mandates OT rates — the Labour Code (Art. 164) requires that night-shift hours be paid "no less than" the standard rate (i.e., no mandatory premium beyond base pay for night hours alone, unlike OT). The system already handles night shifts as cross-midnight shifts via `Shift.crossesMidnight` and `ScheduleMath`. A builder can model the night-shift differential rate as a per-shift configurable field (defaulting to 0 additional premium) without inventing a statutory number that does not exist. **No hard stop.**

---

#### Q6 — Holiday work rates (§20)
**PINNED — already in system.**

PRD §20 states "2x pay or comp leave per policy — AZ rules in system." The `OVERTIME_AZ` rule seeds `holidayMultiplier: 2.0` (V35 migration). The `StatutoryCalculator` applies it via the `WORKED_ON_HOLIDAY` anomaly flag. The choice between 2x pay vs. comp leave is a per-employee/policy decision that can be modelled as a configurable flag on the attendance policy. **No invention required.**

---

### APPROVAL CHAINS

#### Q7 — Attendance correction approval chain (§21, §22)
**AMBIGUOUS — HARD STOP.**

PRD §21 gives the workflow as: "employee submits → manager approves → HR reviews (if required) → record recalculated → payroll summary updated."

"If required" is un-inventable. A builder must decide: (a) what triggers the HR step — is it always a 2-step chain, or only when the correction is after payroll cutoff, or only for corrections affecting OT/pay, or HR decides per case? (b) is the manager step `resolvesToManager` (the employee's direct manager) or a named role like `DEPT_MANAGER`? (c) can the manager approve corrections for employees outside their hierarchy?

The existing WorkflowEngine has full support for sequential and conditional steps, including `resolvesToManager`, HRBP resolution, parallel gates, and SpEL-conditional step skipping. The shape of the correction workflow definition is the design question — not capability.

**Question for the human:**
> For attendance corrections, which approval chain applies:
> (a) 1-step: manager only, always?
> (b) 2-step: manager then HR, always?
> (c) Conditional: manager always; HR only when the correction is after the payroll-period lock or affects OT/pay?
> Also confirm: the approving manager is the employee's direct hierarchy manager (resolvesToManager), correct?

---

#### Q8 — Overtime approval chain (§18, §25)
**PINNED.**

PRD §25 states: "If OT >2h → require dept-head approval." This pins the rule: standard OT goes to the direct manager; OT exceeding 2 hours per day triggers a conditional second step for the department head. The existing WorkflowEngine supports SpEL-conditional steps (`condition_spel`), so this chain is buildable without invention. Model: step 1 = `ROLE_DEPARTMENT_MANAGER` (resolvesToManager); step 2 = `ROLE_DEPT_HEAD` with `condition_spel = "overtimeHours > 2"`. **No hard stop.**

---

#### Q9 — Period lock authority (§28)
**INVENTABLE FROM EXISTING ROLES.**

PRD §28 gives the example: "Period 1–31 May; manager deadline 2 Jun; lock 3 Jun. After lock: changes require HR/payroll approval." The roles exist in the system (`HR_ATTENDANCE_OFFICER`, `PAYROLL_OFFICER`, `SYSTEM_ADMIN`). A builder can assign lock authority to `HR_ATTENDANCE_OFFICER`/`HR_ADMIN`, and unlock to the same plus `PAYROLL_OFFICER` (who receives the payroll-ready summary). An unlock request triggers a mini-workflow (same engine). No new rules need to be invented. **No hard stop.**

---

#### Q10 — WorkflowEngine sufficiency
**PINNED — existing engine is sufficient.**

The existing `WorkflowService` supports: sequential steps, parallel gates, SpEL-conditional step skipping, `resolvesToManager` and `resolvesToHrbp` resolution, delegation, substitute approvers, SLA timers with breach notifications, resubmission, and role-based routing. All attendance-correction and OT approval patterns described in the PRD fit the existing model. New workflow definitions (seeds) will need to be written, but no engine extension is required.

---

### HIERARCHY / PERMISSION SCOPING

#### Q11 — Manager hierarchy scope on corrections
**PINNED.**

PRD §48 states: "Manager cannot approve outside reporting scope (unless delegated)." The `AccessScopeService` already enforces this via the `DEPARTMENT_MANAGER` scope (recursive `manager_id` chain CTE). `WorkflowService.requireResolvedApprover` already enforces `resolvesToManager`. This is already implemented and will apply automatically. **No invention required.**

---

#### Q12 — HR Attendance Officer scope
**INVENTABLE FROM EXISTING ROLES.**

PRD §41 defines the `HR Attendance Officer` role as able to "manage records/policies" for all employees. This maps to the existing `HR_ADMIN`/`HR_SPECIALIST` + `scope_org_unit_id` pattern. An `HR_ATTENDANCE_OFFICER` role scoped to a legal entity or org-unit branch is inventable from the existing `AccessScopeService` pattern without ambiguity. **No hard stop.**

---

#### Q13 — Payroll Officer recalculation trigger
**INVENTABLE FROM EXISTING ROLES.**

PRD §34 assigns "run recalculation" to the HR Attendance Workspace. The `PAYROLL_OFFICER` role viewing the payroll-ready attendance summary (§29) can trigger recalculation via the existing batch recalculation endpoint. This is a role-permission design decision, not an un-inventable business rule. **No hard stop.**

---

### ATTENDANCE ENGINE RULES

#### Q14 — Grace period defaults (§11, §14, §25)
**PINNED — per-policy configurable.**

The existing schema already stores `grace_period_minutes` on `work_schedule` (V8 migration) and the `AttendanceEngine` applies it per schedule. PRD §25 gives the example "10 min free" but frames it as a policy example, not a system default. The correct design is a configurable per-attendance-policy field (already modelled), with a suggested default of 10 minutes. No statutory number needs to be invented. **No hard stop.**

---

#### Q15 — Rounding (§26)
**NOT REQUIRED AT LAUNCH.**

PRD §26 describes clock-in/out rounding as a "configurable" feature. PRD §50 (Launch Scope) lists rounding in the full scope, but the existing engine stores raw punch times and computes minutes exactly. Rounding can be added as a post-processing step on the attendance policy rule engine without touching the raw punch log. This is safely deferrable without breaking the payroll-integration contract (rounding affects the processed layer, not the raw layer). Recommend: implement rounding configuration in the policy but default it to "no rounding" at launch.

---

#### Q16 — Project/job costing attendance (§24)
**NOT REQUIRED AT LAUNCH.**

PRD §23 and §24 explicitly separate attendance (proves presence) from timesheet (explains what work was done). The existing `Timesheet`/`TimesheetLine` entities already handle project/task/cost-center allocation. Project costing attendance is an enrichment of the timesheet, not a new attendance concept. Defer to the timesheet integration seam.

---

#### Q17 — Kiosk attendance (§10)
**NOT REQUIRED AT LAUNCH (phased).**

Kiosk is a shared-device clock-in mode (PIN/QR/face). The punch event ingestion pipeline (REST/CSV) already exists. A kiosk-specific UI can be added as a thin front-end on top of the same `/api/attendance/events` POST endpoint. Defer the kiosk SPA until after core clock-in paths (web ESS, mobile, biometric CSV) are live.

---

#### Q18 — Biometric device integration (§6)
**NOT REQUIRED AT LAUNCH — CSV/REST import is sufficient.**

The existing `AttendanceIngestService` + `TurnstileImportBatchController` (V119) already handles CSV batch import from biometric devices. PRD §6 lists LAN/SDK/REST/DB-sync/CSV/USB as integration methods. Real-time SDK or DB-sync integration requires per-vendor device SDKs and is a significant infrastructure investment. CSV import with scheduled sync covers the majority of AZ-market biometric devices (ZKTeco, Suprema export CSV). A builder can implement device registration metadata and CSV-batch import now, leaving real-time SDK integration as a named later-phase seam.

---

## HARD STOP BLOCKERS

The following 4 questions must be answered by the human before the pipeline can continue into Phase 2 (Architecture):

---

### BLOCKER 1 — Late deduction formula (affects employee net pay)

**PRD reference:** §14, §29

The PRD lists policy examples but does not pin the formula that converts late minutes into an AZN deduction in the payroll run.

**Please specify:**
1. What is the monetary formula? (e.g., `late_minutes × (monthly_salary / (working_days × hours_per_day × 60))`, or a flat daily deduction, or a penalty per occurrence after threshold)
2. What is the threshold below which no deduction fires? (e.g., accumulated >60 minutes/month, or first occurrence free)
3. Is this a fixed statutory rule or a configurable attendance-policy parameter?
4. Does repeated lateness in the same month compound or cap?

---

### BLOCKER 2 — Absence deduction formula (affects employee net pay)

**PRD reference:** §16, §29

The PRD lists absence statuses and outputs `unpaid_absence_days` to payroll, but does not pin how absence days translate into an AZN deduction.

**Please specify:**
1. Daily rate divisor: `monthly_salary / 30` (calendar) or `/ 22` (standard working days) or `/ actual_working_days_in_period`?
2. Is the formula identical for unauthorized absence vs. approved leave-without-pay?
3. Integration point: does attendance send a pre-computed `deduction_amount` into the existing `PayrollDeduction` table, or does the PayrollEngine need a new lane that reads the approved attendance summary directly?

---

### BLOCKER 3 — Early leave deduction formula (affects employee net pay)

**PRD reference:** §15, §29

The PRD lists four possible treatments for early leave (salary deduction, leave balance deduction, violation, ignore within tolerance) without specifying which applies or how they combine.

**Please specify:**
1. Which treatment applies at this employer? (salary deduction? leave balance? configurable per policy?)
2. If salary deduction: what is the formula and tolerance threshold?
3. Can an employee choose to deduct from leave balance instead of salary (or is this HR-decided)?

---

### BLOCKER 4 — Attendance correction approval chain (affects audit integrity and payroll)

**PRD reference:** §21, §22

The PRD says "HR reviews (if required)" but does not define what triggers the HR step.

**Please specify:**
1. Is it always a 2-step chain (manager + HR), or is the HR step conditional?
2. If conditional: what is the trigger condition? (e.g., correction after period lock; correction adds >30 min OT; correction changes status to Absent/Present; any correction once payroll is LOCKED)
3. Confirm: the manager in step 1 is the employee's direct hierarchy manager (`resolvesToManager`), not a fixed role like `DEPT_MANAGER`?

---

## What is already clear and buildable

Once the 4 blockers above are answered, the following are fully inventable or already pinned and can proceed:

- OT calculation (1.5x/2x standard, 2x holiday/weekend, 4h daily cap) — fully seeded in `OVERTIME_AZ` statutory rule
- Holiday work rates (2x pay or comp leave, configurable per policy)
- Night shift cross-midnight handling (existing `Shift.crossesMidnight` + `ScheduleMath` covers it)
- Grace period (configurable per attendance policy, default 10 min)
- Correction workflow engine (existing WorkflowService is sufficient; needs new definitions seeded)
- OT approval chain (manager → dept-head conditional on >2h, buildable with SpEL condition)
- Period lock authority (HR_ADMIN/HR_ATTENDANCE_OFFICER lock; PAYROLL_OFFICER receives summary)
- Manager hierarchy scope enforcement (existing AccessScopeService + resolvesToManager)
- HR Attendance Officer scope (existing AccessScopeService org-unit pattern)
- Payroll Officer recalculation trigger (existing engine endpoint)
- Rounding (configurable, default off — not a launch blocker)
- Project/job costing (deferred to timesheet seam — not a launch blocker)
- Kiosk attendance (deferred — not a launch blocker)
- Biometric CSV import (existing TurnstileImportBatchController already supports this)
- Multi-location/timezone (raw_timestamp + device_timezone pattern is inventable from existing multi-tz patterns)

---

**Pipeline status: HALTED — awaiting answers to BLOCKERS 1–4 before Phase 2 can begin.**

---

## RESOLVED DECISIONS (2026-06-26 — "do what is the best option")

### BLOCKER 1 RESOLVED — Late deduction formula
**Decision:** Per-minute deduction.
- Daily rate = `monthly_salary / 30` (calendar divisor, matches absence formula).
- Per-minute rate = `daily_rate / (standard_hours_per_day × 60)` = `monthly_salary / 30 / 480`.
- Deduction fires for every late minute above grace period (no monthly accumulation threshold by default).
- Configurable per `AttendancePolicy`: `lateDeductionEnabled` (default `false`), `maxLateBeforeHalfDay` = 120 min.
- This is per-minute, explainable, and matches the salary per-day divisor used for absence.

### BLOCKER 2 RESOLVED — Absence deduction formula
**Decision:** `absent_days × (monthly_salary / 30)`.
- Divisor = 30 calendar days (standard AZ practice, aligns with Labour Code Art. 178 daily-rate baseline).
- Same formula for unauthorized absence AND approved leave-without-pay.
- Integration: attendance generates a `PayrollDeduction` row (type `ATTENDANCE_DEDUCTION`) that the existing `PayrollEngine` picks up — no new engine lane needed.

### BLOCKER 3 RESOLVED — Early leave deduction formula
**Decision:** Same per-minute formula as late deduction (`monthly_salary / 30 / 480`).
- Default treatment: `SALARY_DEDUCTION` (mirroring late deduction).
- HR can override to `LEAVE_BALANCE` at approval time (stored as a flag on the correction approval).
- Configurable per `AttendancePolicy`: `earlyLeaveDeductionEnabled` (default `false`), `earlyLeaveTreatment` (SALARY_DEDUCTION | LEAVE_BALANCE | VIOLATION | IGNORE).

### BLOCKER 4 RESOLVED — Correction approval chain
**Decision:** Conditional 2-step.
- Step 1: Employee's direct hierarchy manager (`resolvesToManager = true`) — ALWAYS required.
- Step 2: HR_ADMIN — CONDITIONAL, fires when `periodLocked = true` OR `absenceChanged = true` OR `overtimeDeltaMinutes > 30`.
- WorkflowEngine SpEL condition on step 2: `"@attendancePeriodService.isCurrentPeriodLocked() || absenceStatusChanged || overtimeDeltaMinutes > 30"`.
- The manager in step 1 is `resolvesToManager` (direct hierarchy), confirmed.

**Pipeline status: UNBLOCKED — proceeding to Phase 2 (Architecture) → Phase 3 (Build).**
