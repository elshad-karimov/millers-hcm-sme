import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { EmployeesPage } from './pages/EmployeesPage'
import { EmployeeDetailPage } from './pages/EmployeeDetailPage'
import { EmployeeFormPage } from './pages/EmployeeFormPage'
import { OrgStructurePage } from './pages/OrgStructurePage'
import { OrgUnitFormPage } from './pages/OrgUnitFormPage'
import { PositionsPage } from './pages/PositionsPage'
import { StaffingTablesPage } from './pages/StaffingTablesPage'
import { StaffingTableDetailPage } from './pages/StaffingTableDetailPage'
import { WorkforcePlansPage } from './pages/WorkforcePlansPage'
import { WorkforcePlanDetailPage } from './pages/WorkforcePlanDetailPage'
import { PositionFormPage } from './pages/PositionFormPage'
import { PositionVariancePage } from './pages/PositionVariancePage'
import { PositionControlPage } from './pages/PositionControlPage'
import { InboxPage } from './pages/InboxPage'
import { AttendanceSchedulesPage } from './pages/AttendanceSchedulesPage'
import { RosterPage } from './pages/RosterPage'
import { ShiftPatternsPage } from './pages/ShiftPatternsPage'
import { RosterVariancePage } from './pages/RosterVariancePage'
import { ScheduleFormPage } from './pages/ScheduleFormPage'
import { ScheduleAssignmentFormPage } from './pages/ScheduleAssignmentFormPage'
import { AttendanceEventsPage } from './pages/AttendanceEventsPage'
import { AttendanceSummaryPage } from './pages/AttendanceSummaryPage'
import { AttendancePoliciesPage } from './pages/AttendancePoliciesPage'
import { AttendanceCorrectionsPage } from './pages/AttendanceCorrectionsPage'
import { OvertimeRequestsPage } from './pages/OvertimeRequestsPage'
import { AttendancePeriodsPage } from './pages/AttendancePeriodsPage'
import { AttendanceExceptionsPage } from './pages/AttendanceExceptionsPage'
import { DeviceMasterPage } from './pages/DeviceMasterPage'
import { AttendanceWorkspacePage } from './pages/AttendanceWorkspacePage'
import { AttendanceReportsPage } from './pages/AttendanceReportsPage'
import { LeaveTypesPage } from './pages/LeaveTypesPage'
import { LeaveBlackoutsPage } from './pages/LeaveBlackoutsPage'
import { TeamLeaveCalendarPage } from './pages/TeamLeaveCalendarPage'
import { AssetsAdminPage } from './pages/AssetsAdminPage'
import { PresenceMapPage } from './pages/PresenceMapPage'
import { WorkflowSlaPage } from './pages/WorkflowSlaPage'
import { PositionSkillsPage } from './pages/PositionSkillsPage'
import { LeaveTypeFormPage } from './pages/LeaveTypeFormPage'
import { LeaveBalancesPage } from './pages/LeaveBalancesPage'
import { LeaveRequestsPage } from './pages/LeaveRequestsPage'
import { LeaveRequestFormPage } from './pages/LeaveRequestFormPage'
import { BusinessTripsPage } from './pages/BusinessTripsPage'
import { ExpenseClaimsPage } from './pages/ExpenseClaimsPage'
import { BusinessTripFormPage } from './pages/BusinessTripFormPage'
import { PermissionTypesPage } from './pages/PermissionTypesPage'
import { PermissionTypeFormPage } from './pages/PermissionTypeFormPage'
import { PermissionBalancesPage } from './pages/PermissionBalancesPage'
import { PermissionRequestsPage } from './pages/PermissionRequestsPage'
import { PermissionRequestFormPage } from './pages/PermissionRequestFormPage'
import { TimesheetsPage } from './pages/TimesheetsPage'
import { TimesheetDetailPage } from './pages/TimesheetDetailPage'
import { PayrollRunsPage } from './pages/PayrollRunsPage'
import { PayrollRunDetailPage } from './pages/PayrollRunDetailPage'
import { PayrollCompensationPage } from './pages/PayrollCompensationPage'
import { VacanciesPage } from './pages/VacanciesPage'
import { VacancyFormPage } from './pages/VacancyFormPage'
import { VacancyDetailPage } from './pages/VacancyDetailPage'
import { CandidatesPage } from './pages/CandidatesPage'
import { CandidateFormPage } from './pages/CandidateFormPage'
import { CandidateDuplicatesPage } from './pages/CandidateDuplicatesPage'
import { CandidateRetentionPage } from './pages/CandidateRetentionPage'
import { ReferralsPage } from './pages/ReferralsPage'
import { AgenciesPage } from './pages/AgenciesPage'
import { TerminationsPage } from './pages/TerminationsPage'
import { ChecklistsPage } from './pages/ChecklistsPage'
import { OnboardingConsolePage } from './pages/OnboardingConsolePage'
import { OnboardingRequestsPage } from './pages/OnboardingRequestsPage'
import { OffboardingConsolePage } from './pages/OffboardingConsolePage'
import { OffboardingAnalyticsPage } from './pages/OffboardingAnalyticsPage'
import { ResignationRequestPage } from './pages/ResignationRequestPage'
import { NoticePeriodRulesPage } from './pages/NoticePeriodRulesPage'
import { ManagerOnboardingPage } from './pages/ManagerOnboardingPage'
import { OnboardingAnalyticsPage } from './pages/OnboardingAnalyticsPage'
import { TerminationFormPage } from './pages/TerminationFormPage'
import { TerminationDetailPage } from './pages/TerminationDetailPage'
import { ContractChangesPage } from './pages/ContractChangesPage'
import { ContractChangeFormPage } from './pages/ContractChangeFormPage'
import { ContractChangeDetailPage } from './pages/ContractChangeDetailPage'
import { ReviewCyclesPage } from './pages/ReviewCyclesPage'
import { CalibrationPage } from './pages/performance/CalibrationPage'
import { SuccessionGridPage } from './pages/performance/SuccessionGridPage'
import { BenchDepthPage } from './pages/performance/BenchDepthPage'
import { SuccessionNominationsPage } from './pages/performance/SuccessionNominationsPage'
import CareerPage from './pages/career/CareerPage'
import { ReviewCycleFormPage } from './pages/ReviewCycleFormPage'
import { GoalsPage } from './pages/GoalsPage'
import { PerformanceReviewsPage } from './pages/PerformanceReviewsPage'
import { PerformanceReviewDetailPage } from './pages/PerformanceReviewDetailPage'
import { FeedbackPage } from './pages/FeedbackPage'
import { CoursesPage } from './pages/CoursesPage'
import { CourseFormPage } from './pages/CourseFormPage'
import { CourseDetailPage } from './pages/CourseDetailPage'
import { MyLearningPage } from './pages/MyLearningPage'
import { LearningPathsPage } from './pages/LearningPathsPage'
import { TrainingPlansPage } from './pages/TrainingPlansPage'
import { CertificatesPage } from './pages/CertificatesPage'
import { CompetenciesPage } from './pages/CompetenciesPage'
import { BonusMatrixPage } from './pages/BonusMatrixPage'
import { AllowancesPage } from './pages/AllowancesPage'
import { BonusRunsPage } from './pages/BonusRunsPage'
import { SalaryPlanningPage } from './pages/SalaryPlanningPage'
import { CompPlanningPage } from './pages/CompPlanningPage'
import { BenefitsPage } from './pages/BenefitsPage'
import { ReportsPage } from './pages/ReportsPage'
import { CustomReportBuilderPage } from './pages/CustomReportBuilderPage'
import { ReportSchedulesPage } from './pages/ReportSchedulesPage'
import { MyWorkspacePage } from './pages/MyWorkspacePage'
import { MyPoliciesPage } from './pages/MyPoliciesPage'
import { PoliciesAdminPage } from './pages/PoliciesAdminPage'
import { LegalEntitiesPage } from './pages/LegalEntitiesPage'
import { LocationsPage } from './pages/LocationsPage'
import { HrPartnersPage } from './pages/HrPartnersPage'
import { OrgUnitTypesPage } from './pages/OrgUnitTypesPage'
import { OrgNativeReportsPage } from './pages/OrgNativeReportsPage'
import { OrgUnitDocumentsPage } from './pages/OrgUnitDocumentsPage'
import { NotificationPreferencesPage } from './pages/NotificationPreferencesPage'
import { MySurveysPage } from './pages/MySurveysPage'
import { SurveysAdminPage } from './pages/SurveysAdminPage'
import { TeamPage } from './pages/TeamPage'
import { LetterTemplatesPage } from './pages/LetterTemplatesPage'
import { LetterRequestsPage } from './pages/LetterRequestsPage'
import { LetterRequestFormPage } from './pages/LetterRequestFormPage'
import { PersonalInfoChangesPage } from './pages/PersonalInfoChangesPage'
import { PersonalInfoRequestFormPage } from './pages/PersonalInfoRequestFormPage'
import { EmployeeManagementReportsPage } from './pages/EmployeeManagementReportsPage'
import { ActivityFeedPage } from './pages/ActivityFeedPage'
import { AuditLogPage } from './pages/AuditLogPage'
import { SpanOfControlPage } from './pages/SpanOfControlPage'
import { BulkReorgPage } from './pages/BulkReorgPage'
import { InterviewKitsPage } from './pages/InterviewKitsPage'
import { InterviewDetailPage } from './pages/InterviewDetailPage'
import { InterviewScheduleFormPage } from './pages/InterviewScheduleFormPage'
import { TalentPoolPage } from './pages/TalentPoolPage'
import { RecruitmentAnalyticsPage } from './pages/RecruitmentAnalyticsPage'
import { DashboardPage } from './pages/DashboardPage'
import { UserManagementPage } from './pages/UserManagementPage'
import { ApiKeysPage } from './pages/ApiKeysPage'
import { PreboardingPage } from './pages/PreboardingPage'
import { PublicPreboardingPage } from './pages/PublicPreboardingPage'
import { PublicLetterVerifyPage } from './pages/PublicLetterVerifyPage'
import { BackupsPage } from './pages/admin/BackupsPage'
import { LdapSyncPage } from './pages/admin/LdapSyncPage'
import { BiExportPage } from './pages/admin/BiExportPage'
import { WarehouseAnalyticsPage } from './pages/admin/WarehouseAnalyticsPage'
import { AppLayout } from './components/AppLayout'
import { RequireAuth } from './auth/RequireAuth'
import { useAuth } from './auth/AuthContext'
import { RoleSets } from './auth/roleSets'

/** Lands HR/admins on the home dashboard; non-HR users on their My Workspace. */
function IndexRedirect() {
  const { hasRole } = useAuth()
  const target = hasRole(...RoleSets.HR_PLUS_MANAGERS_READ)
    ? '/home'
    : '/my'
  return <Navigate to={target} replace />
}

/**
 * Layout-route guard: renders its child routes only if the user holds one
 * of the required roles; otherwise redirects to /my (self-service workspace).
 *
 * Usage as a layout route (React Router v6):
 *   <Route element={<RequireRole roles={['HR_ADMIN', 'SYSTEM_ADMIN']} />}>
 *     <Route path="..." element={<SomePage />} />
 *   </Route>
 */
function RequireRole({ roles }: { roles: string[] }) {
  const { hasRole } = useAuth()
  if (!hasRole(...roles)) return <Navigate to="/my" replace />
  return <Outlet />
}

export default function App() {
  return (
    <Routes>
      {/* M122 — Public candidate-facing pre-boarding portal. OUTSIDE the
          RequireAuth wrapper because the candidate has no Keycloak
          account; the magic-link token IS the credential. */}
      <Route path="/preboarding/:token" element={<PublicPreboardingPage />} />
      {/* M139 — public letter verify (QR target). No auth. */}
      <Route path="/verify/letter/:token" element={<PublicLetterVerifyPage />} />

      {/* No /login route — RequireAuth hands off to Keycloak. */}
      <Route
        path="/"
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<IndexRedirect />} />

        {/* ── Always-accessible ─────────────────────────────────── */}
        <Route path="home" element={<DashboardPage />} />
        <Route path="my" element={<MyWorkspacePage />} />
        <Route path="my/team" element={<TeamPage />} />
        {/* M115 — per-user notification preferences */}
        <Route path="my/notifications" element={<NotificationPreferencesPage />} />
        {/* M116 — employee survey self-service */}
        <Route path="my/surveys" element={<MySurveysPage />} />
        {/* M138 — company policy library */}
        <Route path="self/policies" element={<MyPoliciesPage />} />
        <Route path="policies" element={<PoliciesAdminPage />} />
        {/* M140 — legal entities admin */}
        <Route path="organization/legal-entities" element={<LegalEntitiesPage />} />
        {/* M141 — location master */}
        <Route path="organization/locations" element={<LocationsPage />} />
        {/* M142 — HRBP assignments */}
        <Route path="organization/hr-partners" element={<HrPartnersPage />} />
        {/* M143 — configurable org unit types */}
        <Route path="organization/unit-types" element={<OrgUnitTypesPage />} />
        {/* M147 — org unit documents + expiry */}
        <Route path="organization/documents" element={<OrgUnitDocumentsPage />} />

        {/* ── Self-service request forms — any authenticated user ── */}
        <Route path="leave/requests/new" element={<LeaveRequestFormPage />} />
        <Route path="permission/requests/new" element={<PermissionRequestFormPage />} />
        <Route path="business-trips/new" element={<BusinessTripFormPage />} />
        <Route path="letters/request" element={<LetterRequestFormPage />} />
        <Route path="personal-info/request" element={<PersonalInfoRequestFormPage />} />
        {/* M108 — Benefits: HR sees admin tabs, employees see only "my benefits" */}
        <Route path="compbenefits/benefits" element={<BenefitsPage />} />

        {/* ── HR + Manager routes ────────────────────────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'AUDITOR', 'DEPARTMENT_MANAGER']} />}>
          {/* M125 — real-time presence map (manager + HR scope) */}
          <Route path="presence" element={<PresenceMapPage />} />
          {/* HR letters — request queue (scope-restricted in service) */}
          <Route path="letters" element={<LetterRequestsPage />} />

          {/* Personal-info change requests — scope-restricted in service */}
          <Route path="personal-info-changes" element={<PersonalInfoChangesPage />} />

          {/* Employee-Management reports (M80) — scope-restricted in service */}
          <Route path="reports/emp-mgmt" element={<EmployeeManagementReportsPage />} />
          <Route path="reports/span-of-control" element={<SpanOfControlPage />} />
          {/* M145 — org-native reports */}
          <Route path="reports/org" element={<OrgNativeReportsPage />} />

          {/* Time & Attendance */}
          <Route path="attendance/schedules" element={<AttendanceSchedulesPage />} />
          {/* M110 — shift catalog + weekly roster */}
          <Route path="attendance/roster" element={<RosterPage />} />
          {/* M111 — rotation patterns + auto-roster generation */}
          <Route path="attendance/shift-patterns" element={<ShiftPatternsPage />} />
          {/* M113 — roster-vs-actual variance dashboard */}
          <Route path="attendance/variance" element={<RosterVariancePage />} />
          <Route path="attendance/schedules/new" element={<ScheduleFormPage />} />
          <Route path="attendance/schedules/:id/edit" element={<ScheduleFormPage />} />
          <Route path="attendance/schedules/:scheduleId/assign" element={<ScheduleAssignmentFormPage />} />
          <Route path="attendance/events" element={<AttendanceEventsPage />} />
          <Route path="attendance/summary" element={<AttendanceSummaryPage />} />
          <Route path="attendance/policies" element={<AttendancePoliciesPage />} />
          <Route path="attendance/corrections" element={<AttendanceCorrectionsPage />} />
          <Route path="attendance/overtime-requests" element={<OvertimeRequestsPage />} />
          <Route path="attendance/periods" element={<AttendancePeriodsPage />} />
          <Route path="attendance/exceptions" element={<AttendanceExceptionsPage />} />
          <Route path="attendance/devices" element={<DeviceMasterPage />} />
          <Route path="attendance/workspace" element={<AttendanceWorkspacePage />} />
          <Route path="attendance/reports" element={<AttendanceReportsPage />} />
          <Route path="timesheets" element={<TimesheetsPage />} />
          <Route path="timesheets/:id" element={<TimesheetDetailPage />} />

          {/* Absence */}
          <Route path="leave/balances" element={<LeaveBalancesPage />} />
          <Route path="leave/requests" element={<LeaveRequestsPage />} />
          <Route path="permission/balances" element={<PermissionBalancesPage />} />
          <Route path="permission/requests" element={<PermissionRequestsPage />} />

          {/* Travel */}
          <Route path="business-trips" element={<BusinessTripsPage />} />
          {/* M104 — expense claims */}
          <Route path="business-trips/expense-claims" element={<ExpenseClaimsPage />} />

          {/* Performance */}
          <Route path="performance/cycles" element={<ReviewCyclesPage />} />
          <Route path="performance/cycles/new" element={<ReviewCycleFormPage />} />
          <Route path="performance/cycles/:id/edit" element={<ReviewCycleFormPage />} />
          <Route path="performance/cycles/:cycleId/calibration" element={<CalibrationPage />} />
          {/* M92 — 9-box succession grid */}
          <Route path="performance/succession" element={<SuccessionGridPage />} />
          <Route path="performance/succession/:cycleId" element={<SuccessionGridPage />} />
          {/* M94 — bench depth report */}
          <Route path="performance/succession/bench" element={<BenchDepthPage />} />
          <Route path="performance/succession/:cycleId/bench" element={<BenchDepthPage />} />
          {/* M103 — named successor nominations */}
          <Route path="performance/succession/nominations" element={<SuccessionNominationsPage />} />
          <Route path="career" element={<CareerPage />} />
          <Route path="performance/goals" element={<GoalsPage />} />
          <Route path="performance/reviews" element={<PerformanceReviewsPage />} />
          <Route path="performance/reviews/:id" element={<PerformanceReviewDetailPage />} />
          <Route path="performance/feedback" element={<FeedbackPage />} />

          {/* Learning */}
          <Route path="learning/courses" element={<CoursesPage />} />
          <Route path="learning/courses/new" element={<CourseFormPage />} />
          <Route path="learning/courses/:id" element={<CourseDetailPage />} />
          <Route path="learning/courses/:id/edit" element={<CourseFormPage />} />
          <Route path="learning/my" element={<MyLearningPage />} />
          {/* M95 — Learning path templates + assignments */}
          <Route path="learning/paths" element={<LearningPathsPage />} />
          <Route path="learning/certificates" element={<CertificatesPage />} />
          <Route path="learning/competencies" element={<CompetenciesPage />} />
          {/* M157 — Training plans */}
          <Route path="learning/training-plans" element={<TrainingPlansPage />} />

          {/* Approvals inbox */}
          <Route path="inbox" element={<InboxPage />} />

          {/* M118 — comp planning workbench (managers see their team, HR sees all) */}
          <Route path="compbenefits/comp-planning" element={<CompPlanningPage />} />
        </Route>

        {/* ── HR-only routes (no DEPARTMENT_MANAGER) ────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'AUDITOR']} />}>
          {/* People */}
          <Route path="employees" element={<EmployeesPage />} />
          {/* M122 — pre-boarding invite admin */}
          <Route path="hr/preboarding" element={<PreboardingPage />} />
          {/* M126 — workflow SLA breaches dashboard */}
          <Route path="hr/workflow-sla" element={<WorkflowSlaPage />} />
          {/* M124 — asset administration (cross-employee) */}
          <Route path="hr/assets" element={<AssetsAdminPage />} />
          <Route path="employees/new" element={<EmployeeFormPage />} />
          <Route path="employees/:id" element={<EmployeeDetailPage />} />
          <Route path="employees/:id/edit" element={<EmployeeFormPage />} />
          <Route path="organization" element={<OrgStructurePage />} />
          <Route path="organization/units/new" element={<OrgUnitFormPage />} />
          <Route path="organization/units/:unitId/edit" element={<OrgUnitFormPage />} />
          <Route path="organization/bulk-reorg" element={<BulkReorgPage />} />
          <Route path="positions" element={<PositionsPage />} />
          <Route path="positions/new" element={<PositionFormPage />} />
          <Route path="positions/:id/edit" element={<PositionFormPage />} />
          {/* M258 — budget vs actual variance dashboard (PRD §19) */}
          <Route path="positions/variance" element={<PositionVariancePage />} />
          {/* M127 — position competency requirements + candidate fit */}
          <Route path="positions/:id/skills" element={<PositionSkillsPage />} />
          {/* M109 — position control gate dashboard */}
          <Route path="positions/control" element={<PositionControlPage />} />
          {/* M245 — staffing table (ştat cədvəli) */}
          <Route path="staffing-tables" element={<StaffingTablesPage />} />
          <Route path="staffing-tables/:id" element={<StaffingTableDetailPage />} />
          {/* M247 — workforce plans + scenarios */}
          <Route path="workforce-plans" element={<WorkforcePlansPage />} />
          <Route path="workforce-plans/:id" element={<WorkforcePlanDetailPage />} />

          {/* Letter templates (HR admin) */}
          <Route path="letters/templates" element={<LetterTemplatesPage />} />

          {/* M116 — engagement surveys + eNPS (HR admin) */}
          <Route path="engagement/surveys" element={<SurveysAdminPage />} />

          {/* Absence admin */}
          <Route path="leave/types" element={<LeaveTypesPage />} />
          {/* M123 — leave blackout windows admin */}
          <Route path="leave/blackouts" element={<LeaveBlackoutsPage />} />
          {/* M131 — team time-off calendar */}
          <Route path="leave/team-calendar" element={<TeamLeaveCalendarPage />} />
          <Route path="leave/types/new" element={<LeaveTypeFormPage />} />
          <Route path="leave/types/:id/edit" element={<LeaveTypeFormPage />} />
          <Route path="permission/types" element={<PermissionTypesPage />} />
          <Route path="permission/types/new" element={<PermissionTypeFormPage />} />
          <Route path="permission/types/:id/edit" element={<PermissionTypeFormPage />} />

          {/* Payroll */}
          <Route path="payroll/runs" element={<PayrollRunsPage />} />
          <Route path="payroll/runs/:id" element={<PayrollRunDetailPage />} />
          <Route path="payroll/compensation" element={<PayrollCompensationPage />} />

          {/* Recruitment */}
          <Route path="recruitment/vacancies" element={<VacanciesPage />} />
          <Route path="recruitment/vacancies/new" element={<VacancyFormPage />} />
          <Route path="recruitment/vacancies/:id" element={<VacancyDetailPage />} />
          <Route path="recruitment/vacancies/:id/edit" element={<VacancyFormPage />} />
          <Route path="recruitment/candidates" element={<CandidatesPage />} />
          <Route path="recruitment/candidates/new" element={<CandidateFormPage />} />
          {/* M293 — duplicate detection + merge (static path before :id) */}
          <Route path="recruitment/candidates/duplicates" element={<CandidateDuplicatesPage />} />
          {/* M294 — consent retention + anonymisation */}
          <Route path="recruitment/candidates/retention" element={<CandidateRetentionPage />} />
          {/* M295 — employee referral program */}
          <Route path="recruitment/referrals" element={<ReferralsPage />} />
          {/* M296 — recruitment agencies + ownership + invoicing */}
          <Route path="recruitment/agencies" element={<AgenciesPage />} />
          <Route path="recruitment/candidates/:id/edit" element={<CandidateFormPage />} />
          {/* M85 — interview kit admin */}
          <Route path="recruitment/interview-kits" element={<InterviewKitsPage />} />
          {/* M86 — interview scheduling + scoring */}
          <Route path="recruitment/interviews/schedule" element={<InterviewScheduleFormPage />} />
          <Route path="recruitment/interviews/:id" element={<InterviewDetailPage />} />
          {/* M87 — talent pool / CRM */}
          <Route path="recruitment/talent-pool" element={<TalentPoolPage />} />
          {/* M88 — funnel + time-to-hire + source + stale outreach analytics */}
          <Route path="recruitment/analytics" element={<RecruitmentAnalyticsPage />} />

          {/* Lifecycle */}
          <Route path="lifecycle/terminations" element={<TerminationsPage />} />
          <Route path="lifecycle/terminations/new" element={<TerminationFormPage />} />
          <Route path="lifecycle/terminations/:id" element={<TerminationDetailPage />} />
          <Route path="lifecycle/contract-changes" element={<ContractChangesPage />} />
          <Route path="lifecycle/contract-changes/new" element={<ContractChangeFormPage />} />
          <Route path="lifecycle/contract-changes/:id" element={<ContractChangeDetailPage />} />
          {/* M105/M106 — onboarding & offboarding checklists */}
          <Route path="lifecycle/checklists" element={<ChecklistsPage />} />
          {/* M300 — onboarding journey hub + HR console */}
          <Route path="lifecycle/onboarding" element={<OnboardingConsolePage />} />
          {/* M301 — equipment/workspace provisioning queue */}
          <Route path="lifecycle/onboarding/requests" element={<OnboardingRequestsPage />} />
          {/* M311 — manager onboarding dashboard */}
          <Route path="lifecycle/onboarding/my-team" element={<ManagerOnboardingPage />} />
          {/* M312 — onboarding analytics report */}
          <Route path="lifecycle/onboarding/analytics" element={<OnboardingAnalyticsPage />} />
          {/* M313 — offboarding console + resignation management */}
          <Route path="lifecycle/offboarding" element={<OffboardingConsolePage />} />
          <Route path="lifecycle/offboarding/resignations" element={<ResignationRequestPage />} />
          <Route path="lifecycle/offboarding/resignations/new" element={<ResignationRequestPage />} />
          {/* M314 — notice period rules admin */}
          <Route path="lifecycle/offboarding/notice-period-rules" element={<NoticePeriodRulesPage />} />
          <Route path="lifecycle/offboarding/analytics" element={<OffboardingAnalyticsPage />} />

          {/* Comp & Benefits */}
          <Route path="compbenefits/matrix" element={<BonusMatrixPage />} />
          <Route path="compbenefits/allowances" element={<AllowancesPage />} />
          <Route path="compbenefits/bonus-runs" element={<BonusRunsPage />} />
          {/* M102 — salary planning */}
          <Route path="compbenefits/salary-planning" element={<SalaryPlanningPage />} />

          {/* Reports */}
          <Route path="reports" element={<ReportsPage />} />
          <Route path="reports/schedules" element={<ReportSchedulesPage />} />
          {/* M119 — custom report builder */}
          <Route path="reports/custom" element={<CustomReportBuilderPage />} />
        </Route>

        {/* ── SYSTEM_ADMIN only ──────────────────────────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN']} />}>
          <Route path="admin/users" element={<UserManagementPage />} />
          <Route path="admin/backups" element={<BackupsPage />} />
          <Route path="admin/ldap" element={<LdapSyncPage />} />
          <Route path="admin/warehouse" element={<WarehouseAnalyticsPage />} />
          {/* M120 — API keys + rate limiting */}
          <Route path="admin/api-keys" element={<ApiKeysPage />} />
        </Route>

        {/* ── SYSTEM_ADMIN + AUDITOR ─────────────────────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'AUDITOR']} />}>
          <Route path="admin/bi-export" element={<BiExportPage />} />
        </Route>

        {/* ── Activity feed — HR_ADMIN + SYSTEM_ADMIN + AUDITOR (M80) ── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'HR_ADMIN', 'AUDITOR']} />}>
          <Route path="activity" element={<ActivityFeedPage />} />
          {/* M114 — searchable audit-log browser */}
          <Route path="admin/audit-log" element={<AuditLogPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
