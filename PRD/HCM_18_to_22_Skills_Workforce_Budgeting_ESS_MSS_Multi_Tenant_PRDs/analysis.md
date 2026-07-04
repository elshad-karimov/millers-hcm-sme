# HCM_18–22 Bundle — Analysis & Milestone Plan

Analyzer + gap-checker both PASS (2026-07-04). Numbering: milestones M419+, migrations **V243+** (V242 reserved by the tenant-hardening branch). payroll_impact: false — budgeting is planning/comparison only, actuals read from payroll_result; no payroll execution.

## Coverage (existing platform ≈60–65% of PRD)

| Module | Built already | Coverage |
|---|---|---|
| 18 Skills/Competency | taxonomy+hierarchy M127/V87, proficiency 1–5, years+endorsement M136, position requirements, SkillGapAnalyzer M98, certifications M65, review assessment M393 | ~65% |
| 19 Workforce Planning | WorkforcePlan+lines+scenarios M247 (DRAFT→PENDING→ACTIVE→ARCHIVED, clone/compare/variance), PositionFunding M244, headcount reports, span-of-control M81 | ~50% |
| 20 HR Budgeting | CompensationBudget M364/V202, merit matrix M363, position funding gate M244, payroll GL V198, recruitment cost M297, training costs M408 | ~30% |
| 21 ESS | /api/self/* M67–70 (profile/leave/payslips/letters M77/personal-info M79/docs M263/checklists M266/goals/benefits M386), MyWorkspacePage, mobile app | ~85% |
| 22 MSS | ManagerTeamService M76, AccessScopeService scoping, TeamCalendar M131, approvals inbox, probation reviews, HeadcountChangeRequest M109, LeaveDelegation | ~70% |

## Milestones (17, phases A–E)

### Phase A — Skills (M419–M421)
- **M419 (S)** skill verification workflow: `learning.skill_verification_request` (employee, competency, requested_level, evidence attachment, status PENDING/APPROVED/REJECTED, verified_by/at). SkillVerificationService submit/approve/reject (workflow-light: direct manager-or-HR action, audit). POST /api/skills/verifications (+approve/reject). SPA queue. EMPLOYEE submit own, MANAGER reports, HR any — AccessScopeService.
- **M420 (S)** skill inventory dashboard/reports: SkillInventoryService (byDepartment, critical coverage, certification coverage, expiring certs). GET /api/reports/skills/*. SkillInventoryDashboardPage. HR full; MANAGER department-scoped.
- **M421 (S)** cert→skill auto-link + skill types: migration adds `skill_type` to learning.competency (HARD_SKILL/SOFT_SKILL/TECHNICAL/BEHAVIORAL/LANGUAGE/TOOL/COMPLIANCE) + `competency_id` FK on core_hr.employee_certification; on cert verify auto-award linked competency; cert editor gets competency picker.

### Phase B — Workforce Planning (M422–M424)
- **M422 (M)** hiring plan integration: `staffing.hiring_plan_line` (workforce_plan FK, position, target_start_date, recruiter, recruitment_status, vacancy FK). Approved NEW_HIRE plan lines generate hiring plan; vacancy creation links back. /api/workforce-plans/{id}/hiring-plan. Tab on WorkforcePlanDetailPage.
- **M423 (M)** attrition forecast: `staffing.attrition_forecast` (plan, org_unit, forecast_date, expected_exits, basis HISTORICAL/RISK/RETIREMENT/CONTRACT_EXPIRY). Historical = trailing-12mo turnover per org-unit; + contract-expiry + retirement (birth_date) inputs. GET /api/workforce-plans/{id}/attrition-forecast. Widget on plan detail. HR/Finance only.
- **M424 (S)** plan→budget transfer: WorkforcePlanService.transferToBudget() creates CompensationBudget rows from approved plan totals. POST /api/workforce-plans/{id}/transfer-to-budget. Button on approved plans. HR_ADMIN.

### Phase C — HR Budgeting (M425–M428)
- **M425 (M)** budget cycles + department budgets: `budgeting.budget_cycle` (code, name, ANNUAL/QUARTERLY/ROLLING, period, status DRAFT/OPEN/LOCKED/CLOSED, submission_deadline) + `budgeting.department_budget` (cycle, department/org_unit, salary/headcount/benefits/training/recruitment/overtime budgets NUMERIC(14,2) AZN, total, consumed, status, approved_by/at). BUDGET_APPROVAL workflow seed (HR_ADMIN step — copy V210/V237 seed pattern). BudgetCyclesPage + DepartmentBudgetPage. HR/Finance confidential.
- **M426 (M)** payroll cost forecast: PayrollForecastService.projectMonthlyCost(months): actives × monthly salary + planned hires (hiring plan) − attrition forecast; tenant annual-increase default 5%. GET /api/budgets/forecast/payroll?months=12. Chart on budget dashboard.
- **M427 (S)** budget-vs-actual variance: BudgetVarianceService compares payroll_result actuals to department_budget per period. GET /api/budgets/variance?cycleId. VarianceDashboardPage with over/under alerts. Managers may see ONLY own-department variance (no salary detail).
- **M428 (M)** budget control rules: `budgeting.budget_control_rule` (budget_type, trigger SALARY_CHANGE/NEW_HIRE/OVERTIME/TRAINING, action ALLOW/WARN/REQUIRE_APPROVAL/BLOCK, threshold_pct default 100). BudgetControlService.check() hooked into SalaryChangeRequestService + OfferService (non-fatal WARN default). Config UI + warnings in salary/offer forms.

### Phase D — ESS (M429–M431)
- **M429 (M)** HR helpdesk: `selfservice.hr_service_request` (employee, category SALARY_CERT/EMPLOYMENT_LETTER/PAYROLL_INQUIRY/POLICY_QUESTION/GRIEVANCE/OTHER, priority, subject, description, status OPEN/IN_PROGRESS/RESOLVED/CLOSED, assigned_to, sla_due, resolution_notes). SLA defaults: HIGH 1 / NORMAL 2 / LOW 5 business days. Employee submits+views own (/api/self/hr-requests); HR queue + resolve. Widget on MyWorkspace + HrServiceQueuePage.
- **M430 (S)** announcements: `selfservice.announcement` (title, body, publish window, audience ALL/DEPARTMENT/LOCATION, active). GET /api/self/announcements (audience-filtered). Card on MyWorkspace; HR admin CRUD.
- **M431 (S)** team calendar in ESS: GET /api/self/team-calendar → TeamCalendarService if caller has direct reports else 403; widget on MyWorkspace (role-gated).

### Phase E — MSS (M432–M435)
- **M432 (M)** movement requests: `lifecycle.employee_movement_request` (employee, TRANSFER/PROMOTION, proposed position/department/grade/salary, effective_date, justification, status, workflow_instance_id). EMP_MOVEMENT workflow (manager→HR). Manager initiates for own reports (AccessScopeService); HR approves; salary fields HR-only in responses to non-HR.
- **M433 (S)** team comp visibility toggle: tenant setting manager_can_view_salary (default FALSE). ManagerTeamService comp view guarded; masked otherwise. Conditional column in ManagerTeamPage.
- **M434 (M)** manager analytics: ManagerAnalyticsService (team turnover 12m, absence rate, overtime hours, training completion, skill gaps) — all AccessScopeService-scoped to own reports. GET /api/manager/analytics. ManagerAnalyticsPage with charts.
- **M435 (S)** inbox enhancements: WorkflowService.inbox type filter + SLA status; bulk approve (each item re-checks individual permission). Inbox filters + SLA badges + bulk checkbox on InboxPage.

## Adopted defaults (gap-checker PASS record)
1. Proficiency scale stays numeric 1–5 (schema-enforced); labels display-only.
2. Endorsement = single endorser (manager/HR) per V94 pattern; peer endorsement later seam.
3. Cert expiry → linked skill NEEDS_REASSESSMENT via ExpiryAlertScheduler; never delete.
4. Budget lines: BASIC_SALARY/ALLOWANCES/EMPLOYER_TAX/BONUS/OVERTIME/BENEFITS/TRAINING/RECRUITMENT, NUMERIC(14,2) AZN.
5. Budget approval via WorkflowService BUDGET_APPROVAL; versioning by effective date ranges.
6. Actuals read-only from payroll_result (+components) and staffing table; budgeting never runs payroll.
7. Workforce plan lifecycle DRAFT→SUBMITTED→APPROVED→LOCKED; HR/Finance visibility, employees never.
8. ESS/MSS reuse existing approval workflows; bank-detail change stays under PERSONAL_INFO_CHANGE (sensitive).
9. **Manager compensation visibility OFF by default**; budget data HR/Finance-only; managers see headcount/vacancy + own-dept variance only. M372 masking applies to all MSS surfaces.
10. Hierarchy scoping = existing AccessScopeService everywhere; no new logic.
11. Attrition forecast = trailing-12mo historical rate (labelled "historical projection"); ML/risk-based later.
12. Budget control threshold default 100%, action WARN; tenant-configurable.
13. Payroll forecast salary-growth assumption default 5%/yr, tenant-configurable.
14. Multi-currency: AZN only now; static-rate consolidation later seam.
15. AI skill suggestions deferred (seam only).
16. Skill library = learning.competency (no parallel skill table); skill_type column extension.
