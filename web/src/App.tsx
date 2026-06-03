import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { EmployeesPage } from './pages/EmployeesPage'
import { EmployeeDetailPage } from './pages/EmployeeDetailPage'
import { EmployeeFormPage } from './pages/EmployeeFormPage'
import { OrgStructurePage } from './pages/OrgStructurePage'
import { OrgUnitFormPage } from './pages/OrgUnitFormPage'
import { PositionsPage } from './pages/PositionsPage'
import { PositionFormPage } from './pages/PositionFormPage'
import { InboxPage } from './pages/InboxPage'
import { AttendanceSchedulesPage } from './pages/AttendanceSchedulesPage'
import { ScheduleFormPage } from './pages/ScheduleFormPage'
import { ScheduleAssignmentFormPage } from './pages/ScheduleAssignmentFormPage'
import { AttendanceEventsPage } from './pages/AttendanceEventsPage'
import { AttendanceSummaryPage } from './pages/AttendanceSummaryPage'
import { LeaveTypesPage } from './pages/LeaveTypesPage'
import { LeaveTypeFormPage } from './pages/LeaveTypeFormPage'
import { LeaveBalancesPage } from './pages/LeaveBalancesPage'
import { LeaveRequestsPage } from './pages/LeaveRequestsPage'
import { LeaveRequestFormPage } from './pages/LeaveRequestFormPage'
import { BusinessTripsPage } from './pages/BusinessTripsPage'
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
import { TerminationsPage } from './pages/TerminationsPage'
import { TerminationFormPage } from './pages/TerminationFormPage'
import { TerminationDetailPage } from './pages/TerminationDetailPage'
import { ContractChangesPage } from './pages/ContractChangesPage'
import { ContractChangeFormPage } from './pages/ContractChangeFormPage'
import { ContractChangeDetailPage } from './pages/ContractChangeDetailPage'
import { ReviewCyclesPage } from './pages/ReviewCyclesPage'
import { CalibrationPage } from './pages/performance/CalibrationPage'
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
import { CertificatesPage } from './pages/CertificatesPage'
import { CompetenciesPage } from './pages/CompetenciesPage'
import { BonusMatrixPage } from './pages/BonusMatrixPage'
import { AllowancesPage } from './pages/AllowancesPage'
import { BonusRunsPage } from './pages/BonusRunsPage'
import { ReportsPage } from './pages/ReportsPage'
import { ReportSchedulesPage } from './pages/ReportSchedulesPage'
import { MyWorkspacePage } from './pages/MyWorkspacePage'
import { TeamPage } from './pages/TeamPage'
import { LetterTemplatesPage } from './pages/LetterTemplatesPage'
import { LetterRequestsPage } from './pages/LetterRequestsPage'
import { LetterRequestFormPage } from './pages/LetterRequestFormPage'
import { PersonalInfoChangesPage } from './pages/PersonalInfoChangesPage'
import { PersonalInfoRequestFormPage } from './pages/PersonalInfoRequestFormPage'
import { EmployeeManagementReportsPage } from './pages/EmployeeManagementReportsPage'
import { ActivityFeedPage } from './pages/ActivityFeedPage'
import { SpanOfControlPage } from './pages/SpanOfControlPage'
import { BulkReorgPage } from './pages/BulkReorgPage'
import { InterviewKitsPage } from './pages/InterviewKitsPage'
import { InterviewDetailPage } from './pages/InterviewDetailPage'
import { InterviewScheduleFormPage } from './pages/InterviewScheduleFormPage'
import { DashboardPage } from './pages/DashboardPage'
import { UserManagementPage } from './pages/UserManagementPage'
import { BackupsPage } from './pages/admin/BackupsPage'
import { LdapSyncPage } from './pages/admin/LdapSyncPage'
import { BiExportPage } from './pages/admin/BiExportPage'
import { WarehouseAnalyticsPage } from './pages/admin/WarehouseAnalyticsPage'
import { AppLayout } from './components/AppLayout'
import { RequireAuth } from './auth/RequireAuth'
import { useAuth } from './auth/AuthContext'

/** Lands HR/admins on the home dashboard; non-HR users on their My Workspace. */
function IndexRedirect() {
  const { hasRole } = useAuth()
  const target = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'AUDITOR', 'DEPARTMENT_MANAGER')
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

        {/* ── Self-service request forms — any authenticated user ── */}
        <Route path="leave/requests/new" element={<LeaveRequestFormPage />} />
        <Route path="permission/requests/new" element={<PermissionRequestFormPage />} />
        <Route path="business-trips/new" element={<BusinessTripFormPage />} />
        <Route path="letters/request" element={<LetterRequestFormPage />} />
        <Route path="personal-info/request" element={<PersonalInfoRequestFormPage />} />

        {/* ── HR + Manager routes ────────────────────────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'AUDITOR', 'DEPARTMENT_MANAGER']} />}>
          {/* HR letters — request queue (scope-restricted in service) */}
          <Route path="letters" element={<LetterRequestsPage />} />

          {/* Personal-info change requests — scope-restricted in service */}
          <Route path="personal-info-changes" element={<PersonalInfoChangesPage />} />

          {/* Employee-Management reports (M80) — scope-restricted in service */}
          <Route path="reports/emp-mgmt" element={<EmployeeManagementReportsPage />} />
          <Route path="reports/span-of-control" element={<SpanOfControlPage />} />

          {/* Time & Attendance */}
          <Route path="attendance/schedules" element={<AttendanceSchedulesPage />} />
          <Route path="attendance/schedules/new" element={<ScheduleFormPage />} />
          <Route path="attendance/schedules/:id/edit" element={<ScheduleFormPage />} />
          <Route path="attendance/schedules/:scheduleId/assign" element={<ScheduleAssignmentFormPage />} />
          <Route path="attendance/events" element={<AttendanceEventsPage />} />
          <Route path="attendance/summary" element={<AttendanceSummaryPage />} />
          <Route path="timesheets" element={<TimesheetsPage />} />
          <Route path="timesheets/:id" element={<TimesheetDetailPage />} />

          {/* Absence */}
          <Route path="leave/balances" element={<LeaveBalancesPage />} />
          <Route path="leave/requests" element={<LeaveRequestsPage />} />
          <Route path="permission/balances" element={<PermissionBalancesPage />} />
          <Route path="permission/requests" element={<PermissionRequestsPage />} />

          {/* Travel */}
          <Route path="business-trips" element={<BusinessTripsPage />} />

          {/* Performance */}
          <Route path="performance/cycles" element={<ReviewCyclesPage />} />
          <Route path="performance/cycles/new" element={<ReviewCycleFormPage />} />
          <Route path="performance/cycles/:id/edit" element={<ReviewCycleFormPage />} />
          <Route path="performance/cycles/:cycleId/calibration" element={<CalibrationPage />} />
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
          <Route path="learning/certificates" element={<CertificatesPage />} />
          <Route path="learning/competencies" element={<CompetenciesPage />} />

          {/* Approvals inbox */}
          <Route path="inbox" element={<InboxPage />} />
        </Route>

        {/* ── HR-only routes (no DEPARTMENT_MANAGER) ────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'AUDITOR']} />}>
          {/* People */}
          <Route path="employees" element={<EmployeesPage />} />
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

          {/* Letter templates (HR admin) */}
          <Route path="letters/templates" element={<LetterTemplatesPage />} />

          {/* Absence admin */}
          <Route path="leave/types" element={<LeaveTypesPage />} />
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
          <Route path="recruitment/candidates/:id/edit" element={<CandidateFormPage />} />
          {/* M85 — interview kit admin */}
          <Route path="recruitment/interview-kits" element={<InterviewKitsPage />} />
          {/* M86 — interview scheduling + scoring */}
          <Route path="recruitment/interviews/schedule" element={<InterviewScheduleFormPage />} />
          <Route path="recruitment/interviews/:id" element={<InterviewDetailPage />} />

          {/* Lifecycle */}
          <Route path="lifecycle/terminations" element={<TerminationsPage />} />
          <Route path="lifecycle/terminations/new" element={<TerminationFormPage />} />
          <Route path="lifecycle/terminations/:id" element={<TerminationDetailPage />} />
          <Route path="lifecycle/contract-changes" element={<ContractChangesPage />} />
          <Route path="lifecycle/contract-changes/new" element={<ContractChangeFormPage />} />
          <Route path="lifecycle/contract-changes/:id" element={<ContractChangeDetailPage />} />

          {/* Comp & Benefits */}
          <Route path="compbenefits/matrix" element={<BonusMatrixPage />} />
          <Route path="compbenefits/allowances" element={<AllowancesPage />} />
          <Route path="compbenefits/bonus-runs" element={<BonusRunsPage />} />

          {/* Reports */}
          <Route path="reports" element={<ReportsPage />} />
          <Route path="reports/schedules" element={<ReportSchedulesPage />} />
        </Route>

        {/* ── SYSTEM_ADMIN only ──────────────────────────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN']} />}>
          <Route path="admin/users" element={<UserManagementPage />} />
          <Route path="admin/backups" element={<BackupsPage />} />
          <Route path="admin/ldap" element={<LdapSyncPage />} />
          <Route path="admin/warehouse" element={<WarehouseAnalyticsPage />} />
        </Route>

        {/* ── SYSTEM_ADMIN + AUDITOR ─────────────────────────────── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'AUDITOR']} />}>
          <Route path="admin/bi-export" element={<BiExportPage />} />
        </Route>

        {/* ── Activity feed — HR_ADMIN + SYSTEM_ADMIN + AUDITOR (M80) ── */}
        <Route element={<RequireRole roles={['SYSTEM_ADMIN', 'HR_ADMIN', 'AUDITOR']} />}>
          <Route path="activity" element={<ActivityFeedPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
