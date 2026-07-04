import { Layout, Menu, Typography, Button, Space, Tag } from 'antd'
import {
  ApartmentOutlined,
  BankOutlined,
  BarChartOutlined,
  BookOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  CoffeeOutlined,
  ClockCircleTwoTone,
  CloudServerOutlined,
  DashboardOutlined,
  DollarCircleOutlined,
  ExperimentOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  GlobalOutlined,
  HomeOutlined,
  IdcardOutlined,
  InboxOutlined,
  LinkOutlined,
  PlayCircleOutlined,
  ProfileOutlined,
  ReadOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  SolutionOutlined,
  SwapOutlined,
  TeamOutlined,
  UserAddOutlined,
  UserOutlined,
  WalletOutlined,
  FundOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  KeyOutlined,
  AppstoreOutlined,
  LogoutOutlined,
  UserDeleteOutlined,
  WarningOutlined,
  TagsOutlined,
} from '@ant-design/icons'
import type { ItemType } from 'antd/es/menu/interface'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'
import { brand } from '../theme'
import { RoleSets } from '../auth/roleSets'
import { LanguageSwitcher } from '../i18n/LanguageSwitcher'

const { Header, Content } = Layout

/**
 * Maps a URL path to which module submenu (top-level group) it belongs to
 * and the screen key inside that module.
 *
 * Keep the prefixes ordered most-specific first.
 */
const NAV_MAP: Array<{ prefix: string; module: string; screen: string }> = [
  { prefix: '/home', module: 'home', screen: 'home' },
  { prefix: '/employees', module: 'people', screen: 'employees' },
  { prefix: '/hr/preboarding', module: 'people', screen: 'hr-preboarding' },
  { prefix: '/hr/assets', module: 'people', screen: 'hr-assets' },
  { prefix: '/organization/bulk-reorg', module: 'people', screen: 'organization-bulk-reorg' },
  { prefix: '/organization/legal-entities', module: 'people', screen: 'legal-entities' },
  { prefix: '/organization/locations', module: 'people', screen: 'locations' },
  { prefix: '/organization/hr-partners', module: 'people', screen: 'hr-partners' },
  { prefix: '/organization/unit-types', module: 'people', screen: 'unit-types' },
  { prefix: '/organization/documents', module: 'people', screen: 'org-documents' },
  { prefix: '/organization', module: 'people', screen: 'organization' },
  { prefix: '/positions/control', module: 'people', screen: 'position-control' },
  { prefix: '/positions', module: 'people', screen: 'positions' },
  { prefix: '/presence', module: 'time', screen: 'presence-map' },
  { prefix: '/attendance/schedules', module: 'time', screen: 'attendance-schedules' },
  { prefix: '/attendance/roster', module: 'time', screen: 'attendance-roster' },
  { prefix: '/attendance/shift-patterns', module: 'time', screen: 'attendance-shift-patterns' },
  { prefix: '/attendance/variance', module: 'time', screen: 'attendance-variance' },
  { prefix: '/attendance/policies', module: 'time', screen: 'attendance-policies' },
  { prefix: '/attendance/corrections', module: 'time', screen: 'attendance-corrections' },
  { prefix: '/attendance/overtime-requests', module: 'time', screen: 'attendance-overtime' },
  { prefix: '/attendance/periods', module: 'time', screen: 'attendance-periods' },
  { prefix: '/attendance/exceptions', module: 'time', screen: 'attendance-exceptions' },
  { prefix: '/attendance/devices', module: 'time', screen: 'attendance-devices' },
  { prefix: '/attendance/workspace', module: 'time', screen: 'attendance-workspace' },
  { prefix: '/attendance/reports', module: 'time', screen: 'attendance-reports' },
  { prefix: '/attendance/events', module: 'time', screen: 'attendance-events' },
  { prefix: '/attendance/summary', module: 'time', screen: 'attendance-summary' },
  { prefix: '/leave/types', module: 'absence', screen: 'leave-types' },
  { prefix: '/leave/blackouts', module: 'absence', screen: 'leave-blackouts' },
  { prefix: '/leave/categories', module: 'absence', screen: 'leave-categories' },
  { prefix: '/leave/team-calendar', module: 'absence', screen: 'leave-team-calendar' },
  { prefix: '/leave/balances', module: 'absence', screen: 'leave-balances' },
  { prefix: '/leave/requests', module: 'absence', screen: 'leave-requests' },
  { prefix: '/permission/types', module: 'absence', screen: 'permission-types' },
  { prefix: '/permission/balances', module: 'absence', screen: 'permission-balances' },
  { prefix: '/permission/requests', module: 'absence', screen: 'permission-requests' },
  { prefix: '/business-trips/expense-claims', module: 'travel', screen: 'travel-expense-claims' },
  { prefix: '/business-trips', module: 'travel', screen: 'business-trips' },
  { prefix: '/letters/templates', module: 'letters', screen: 'letters-templates' },
  { prefix: '/letters/request', module: 'letters', screen: 'letters-request' },
  { prefix: '/letters', module: 'letters', screen: 'letters-requests' },
  { prefix: '/personal-info/request', module: 'personal-info', screen: 'personal-info-request' },
  { prefix: '/personal-info-changes', module: 'personal-info', screen: 'personal-info-queue' },
  { prefix: '/reports/emp-mgmt', module: 'reports', screen: 'reports-emp-mgmt' },
  { prefix: '/reports/span-of-control', module: 'reports', screen: 'reports-span' },
  { prefix: '/reports/org', module: 'reports', screen: 'reports-org' },
  { prefix: '/activity', module: 'reports', screen: 'activity-feed' },
  { prefix: '/timesheets', module: 'time', screen: 'timesheets' },
  { prefix: '/payroll/runs', module: 'payroll', screen: 'payroll-runs' },
  { prefix: '/payroll/compensation', module: 'payroll', screen: 'payroll-compensation' },
  { prefix: '/compensation/dashboard', module: 'compensation', screen: 'compensation-dashboard' },
  { prefix: '/compensation/profile', module: 'compensation', screen: 'compensation-profile' },
  { prefix: '/compensation/pay-bands', module: 'compensation', screen: 'compensation-pay-bands' },
  { prefix: '/compensation/change-reasons', module: 'compensation', screen: 'compensation-change-reasons' },
  { prefix: '/compensation/salary-changes', module: 'compensation', screen: 'compensation-salary-changes' },
  { prefix: '/compensation/exceptions', module: 'compensation', screen: 'compensation-exceptions' },
  { prefix: '/compensation/merit-matrices', module: 'compensation', screen: 'compensation-merit-matrices' },
  { prefix: '/compensation/budgets', module: 'compensation', screen: 'compensation-budgets' },
  { prefix: '/compensation/incentive-plans', module: 'compensation', screen: 'compensation-incentive-plans' },
  { prefix: '/compensation/commission-plans', module: 'compensation', screen: 'compensation-commission-plans' },
  { prefix: '/compensation/market', module: 'compensation', screen: 'compensation-market' },
  { prefix: '/compensation/total-comp-statements', module: 'compensation', screen: 'compensation-total-comp-statements' },
  { prefix: '/compensation/payroll-transfer', module: 'compensation', screen: 'compensation-payroll-transfer' },
  { prefix: '/compensation/config', module: 'compensation', screen: 'compensation-config' },
  { prefix: '/recruitment/vacancies', module: 'recruitment', screen: 'recruitment-vacancies' },
  { prefix: '/recruitment/candidates', module: 'recruitment', screen: 'recruitment-candidates' },
  { prefix: '/recruitment/interview-kits', module: 'recruitment', screen: 'recruitment-interview-kits' },
  { prefix: '/recruitment/interviews', module: 'recruitment', screen: 'recruitment-interviews' },
  { prefix: '/recruitment/talent-pool', module: 'recruitment', screen: 'recruitment-talent-pool' },
  { prefix: '/recruitment/analytics', module: 'recruitment', screen: 'recruitment-analytics' },
  { prefix: '/lifecycle/terminations', module: 'lifecycle', screen: 'lifecycle-terminations' },
  { prefix: '/lifecycle/contract-changes', module: 'lifecycle', screen: 'lifecycle-contract-changes' },
  { prefix: '/lifecycle/onboarding/requests', module: 'lifecycle', screen: 'lifecycle-onboarding-requests' },
  { prefix: '/lifecycle/onboarding/my-team', module: 'lifecycle', screen: 'lifecycle-onboarding-myteam' },
  { prefix: '/lifecycle/onboarding/analytics', module: 'lifecycle', screen: 'lifecycle-onboarding-analytics' },
  { prefix: '/lifecycle/onboarding', module: 'lifecycle', screen: 'lifecycle-onboarding' },
  { prefix: '/lifecycle/checklists', module: 'lifecycle', screen: 'lifecycle-checklists' },
  { prefix: '/lifecycle/offboarding/resignations', module: 'lifecycle', screen: 'lifecycle-offboarding-resignations' },
  { prefix: '/lifecycle/offboarding/notice-period-rules', module: 'lifecycle', screen: 'lifecycle-notice-period-rules' },
  { prefix: '/lifecycle/offboarding/analytics', module: 'lifecycle', screen: 'lifecycle-offboarding-analytics' },
  { prefix: '/lifecycle/offboarding', module: 'lifecycle', screen: 'lifecycle-offboarding' },
  { prefix: '/performance/cycles', module: 'performance', screen: 'performance-cycles' },
  { prefix: '/performance/goals', module: 'performance', screen: 'performance-goals' },
  { prefix: '/performance/reviews', module: 'performance', screen: 'performance-reviews' },
  { prefix: '/performance/feedback', module: 'performance', screen: 'performance-feedback' },
  { prefix: '/performance/succession/nominations', module: 'performance', screen: 'performance-nominations' },
  { prefix: '/performance/succession/bench', module: 'performance', screen: 'performance-bench' },
  { prefix: '/performance/succession', module: 'performance', screen: 'performance-succession' },
  { prefix: '/learning/courses', module: 'learning', screen: 'learning-courses' },
  { prefix: '/learning/paths', module: 'learning', screen: 'learning-paths' },
  { prefix: '/learning/my', module: 'learning', screen: 'learning-my' },
  { prefix: '/learning/certificates', module: 'learning', screen: 'learning-certificates' },
  { prefix: '/learning/competencies', module: 'learning', screen: 'learning-competencies' },
  { prefix: '/learning/training-plans', module: 'learning', screen: 'learning-training-plans' },
  { prefix: '/compbenefits/matrix', module: 'compbenefits', screen: 'compbenefits-matrix' },
  { prefix: '/compbenefits/allowances', module: 'compbenefits', screen: 'compbenefits-allowances' },
  { prefix: '/compbenefits/bonus-runs', module: 'compbenefits', screen: 'compbenefits-bonus-runs' },
  { prefix: '/compbenefits/salary-planning', module: 'compbenefits', screen: 'compbenefits-salary-planning' },
  { prefix: '/compbenefits/comp-planning', module: 'compbenefits', screen: 'compbenefits-comp-planning' },
  { prefix: '/compbenefits/benefits', module: 'compbenefits', screen: 'compbenefits-benefits' },
  { prefix: '/reports/schedules', module: 'reports', screen: 'reports-schedules' },
  { prefix: '/reports/custom', module: 'reports', screen: 'reports-custom' },
  { prefix: '/reports', module: 'reports', screen: 'reports' },
  { prefix: '/engagement/surveys', module: 'engagement', screen: 'engagement-surveys' },
  { prefix: '/my/surveys', module: 'my', screen: 'my-surveys' },
  { prefix: '/self/policies', module: 'my', screen: 'self-policies' },
  { prefix: '/policies', module: 'people', screen: 'policies-admin' },
  { prefix: '/my/team', module: 'my-team', screen: 'my-team' },
  { prefix: '/my', module: 'my', screen: 'my' },
  { prefix: '/inbox', module: 'approvals', screen: 'approvals' },
  { prefix: '/hr/workflow-sla', module: 'approvals', screen: 'workflow-sla' },
  { prefix: '/admin/users', module: 'admin', screen: 'admin-users' },
  { prefix: '/admin/backups', module: 'admin', screen: 'admin-backups' },
  { prefix: '/admin/ldap', module: 'admin', screen: 'admin-ldap' },
  { prefix: '/admin/bi-export', module: 'admin', screen: 'admin-bi-export' },
  { prefix: '/admin/warehouse', module: 'admin', screen: 'admin-warehouse' },
  { prefix: '/admin/api-keys', module: 'admin', screen: 'admin-api-keys' },
  { prefix: '/admin/audit-log', module: 'admin', screen: 'admin-audit-log' },
]

function resolveLocation(pathname: string) {
  const match = NAV_MAP.find((m) => pathname.startsWith(m.prefix))
  return match ?? { module: 'home', screen: 'home' }
}

export function AppLayout() {
  const { user, logout, hasRole } = useAuth()
  // M228 — every label below either uses tNav (top-level module
  // names) or stays in English for now; sub-page Link labels migrate
  // to t() in follow-up milestones, namespace by namespace.
  const { t: tNav } = useTranslation('nav')
  // M241 — role tags pulled from the common namespace so the chips
  // read "System Admin / Sistem administratoru" instead of the raw
  // Keycloak enum value SYSTEM_ADMIN.
  const { t: tCommon } = useTranslation('common')
  const location = useLocation()
  const { module: activeModule, screen: selectedKey } = resolveLocation(location.pathname)

  // Role convenience flags — determine which nav groups are visible
  const isHR = hasRole(...RoleSets.HR_READ)
  const isManager = hasRole(...RoleSets.MANAGER_ONLY)
  const isAdmin = hasRole(...RoleSets.SYS_ADMIN_ONLY)
  /** SYSTEM_ADMIN or AUDITOR — can access BI Export and other audit tools */
  const isAdminOrAuditor = hasRole('SYSTEM_ADMIN', 'AUDITOR')
  /** SYSTEM_ADMIN + HR_ADMIN + AUDITOR — can browse the audit log (M114).
   *  Matches SecurityRoles.READ_AUDIT on the backend. */
  const canBrowseAudit = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'AUDITOR')
  /** Any role that can access team / HR data beyond self-service. */
  const hrOrManager = isHR || isManager

  // ─────────────────────────────────────────────────────────────────────────
  // Build navigation items conditionally based on the user's role(s).
  //
  // Matrix:
  //   EMPLOYEE         → Home + My Workspace only
  //   DEPARTMENT_MANAGER → + Time, Absence, Travel, Performance, Learning, Approvals
  //   HR_SPECIALIST    → + all HR modules
  //   HR_ADMIN         → same as HR_SPECIALIST
  //   AUDITOR          → same as HR_SPECIALIST (backend enforces read-only)
  //   SYSTEM_ADMIN     → everything + Administration
  // ─────────────────────────────────────────────────────────────────────────
  const items: ItemType[] = [
    {
      key: 'home',
      icon: <HomeOutlined />,
      label: <Link to="/home">{tNav('home')}</Link>,
    },
    {
      key: 'my',
      icon: <UserOutlined />,
      label: <Link to="/my">{tNav('myWorkspace')}</Link>,
    },
    // M138 — company policies (everyone)
    {
      key: 'self-policies',
      icon: <FileTextOutlined />,
      label: <Link to="/self/policies">{tNav('policies')}</Link>,
    },

    // ── My team (managers + HR) — M76 ───────────────────────────────────────
    ...(isManager || isHR
      ? [
          {
            key: 'my-team',
            icon: <TeamOutlined />,
            label: <Link to="/my/team">{tNav('myTeam')}</Link>,
          },
        ]
      : []),

    // ── People (HR only) ───────────────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'people',
            icon: <TeamOutlined />,
            label: tNav('people'),
            children: [
              {
                key: 'employees',
                icon: <IdcardOutlined />,
                label: <Link to="/employees">{tNav('sub.people.employees')}</Link>,
              },
              {
                // M138 — HR policy admin
                key: 'policies-admin',
                icon: <FileTextOutlined />,
                label: <Link to="/policies">{tNav('sub.people.policiesAdmin')}</Link>,
              },
              {
                key: 'hr-preboarding',
                icon: <UserAddOutlined />,
                label: <Link to="/hr/preboarding">{tNav('sub.people.preboarding')}</Link>,
              },
              {
                key: 'hr-assets',
                icon: <InboxOutlined />,
                label: <Link to="/hr/assets">{tNav('sub.people.assets')}</Link>,
              },
              {
                key: 'organization',
                icon: <ApartmentOutlined />,
                label: <Link to="/organization">{tNav('sub.people.organization')}</Link>,
              },
              {
                // M140 — Legal entities master
                key: 'legal-entities',
                icon: <BankOutlined />,
                label: <Link to="/organization/legal-entities">{tNav('sub.people.legalEntities')}</Link>,
              },
              {
                // M141 — Location master
                key: 'locations',
                icon: <GlobalOutlined />,
                label: <Link to="/organization/locations">{tNav('sub.people.locations')}</Link>,
              },
              {
                // M142 — HRBP assignments
                key: 'hr-partners',
                icon: <TeamOutlined />,
                label: <Link to="/organization/hr-partners">{tNav('sub.people.hrPartners')}</Link>,
              },
              {
                // M143 — configurable org unit types
                key: 'unit-types',
                icon: <AppstoreOutlined />,
                label: <Link to="/organization/unit-types">{tNav('sub.people.unitTypes')}</Link>,
              },
              {
                // M147 — org unit documents + expiry
                key: 'org-documents',
                icon: <FileTextOutlined />,
                label: <Link to="/organization/documents">{tNav('sub.people.unitDocuments')}</Link>,
              },
              {
                key: 'positions',
                icon: <SolutionOutlined />,
                label: <Link to="/positions">{tNav('sub.people.positions')}</Link>,
              },
              {
                key: 'position-control',
                icon: <FundOutlined />,
                label: <Link to="/positions/control">{tNav('sub.people.positionControl')}</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Time & Attendance (HR + Manager) ───────────────────────────────────
    ...(hrOrManager
      ? [
          {
            key: 'time',
            icon: <ClockCircleOutlined />,
            label: tNav('timeAttendance'),
            children: [
              {
                key: 'presence-map',
                icon: <GlobalOutlined />,
                label: <Link to="/presence">{tNav('sub.time.presenceMap')}</Link>,
              },
              {
                key: 'attendance-workspace',
                icon: <BarChartOutlined />,
                label: <Link to="/attendance/workspace">Attendance Workspace</Link>,
              },
              {
                key: 'attendance-schedules',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/schedules">{tNav('sub.time.schedules')}</Link>,
              },
              {
                key: 'attendance-roster',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/roster">{tNav('sub.time.roster')}</Link>,
              },
              {
                key: 'attendance-shift-patterns',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/shift-patterns">{tNav('sub.time.shiftPatterns')}</Link>,
              },
              {
                key: 'attendance-variance',
                icon: <BarChartOutlined />,
                label: <Link to="/attendance/variance">{tNav('sub.time.rosterVariance')}</Link>,
              },
              {
                key: 'attendance-events',
                icon: <SwapOutlined />,
                label: <Link to="/attendance/events">{tNav('sub.time.events')}</Link>,
              },
              {
                key: 'attendance-summary',
                icon: <BarChartOutlined />,
                label: <Link to="/attendance/summary">{tNav('sub.time.dailySummary')}</Link>,
              },
              {
                key: 'attendance-corrections',
                icon: <SwapOutlined />,
                label: <Link to="/attendance/corrections">Corrections</Link>,
              },
              {
                key: 'attendance-overtime',
                icon: <ClockCircleOutlined />,
                label: <Link to="/attendance/overtime-requests">Overtime Requests</Link>,
              },
              {
                key: 'attendance-periods',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/periods">Periods</Link>,
              },
              {
                key: 'attendance-exceptions',
                icon: <WarningOutlined />,
                label: <Link to="/attendance/exceptions">Exceptions</Link>,
              },
              {
                key: 'attendance-devices',
                icon: <InboxOutlined />,
                label: <Link to="/attendance/devices">Devices</Link>,
              },
              {
                key: 'attendance-reports',
                icon: <BarChartOutlined />,
                label: <Link to="/attendance/reports">Reports</Link>,
              },
              {
                key: 'timesheets',
                icon: <FileDoneOutlined />,
                label: <Link to="/timesheets">{tNav('sub.time.timesheets')}</Link>,
              },
              {
                key: 'attendance-policies',
                icon: <SettingOutlined />,
                label: <Link to="/attendance/policies">Attendance Policies</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Absence (HR + Manager) ─────────────────────────────────────────────
    ...(hrOrManager
      ? [
          {
            key: 'absence',
            icon: <CoffeeOutlined />,
            label: tNav('absence'),
            children: [
              {
                key: 'leave-requests',
                icon: <FileDoneOutlined />,
                label: <Link to="/leave/requests">{tNav('sub.absence.leaveRequests')}</Link>,
              },
              {
                key: 'leave-team-calendar',
                icon: <CoffeeOutlined />,
                label: <Link to="/leave/team-calendar">{tNav('sub.absence.teamCalendar')}</Link>,
              },
              {
                key: 'leave-balances',
                icon: <WalletOutlined />,
                label: <Link to="/leave/balances">{tNav('sub.absence.leaveBalances')}</Link>,
              },
              ...(isHR
                ? [
                    {
                      key: 'leave-types',
                      icon: <SettingOutlined />,
                      label: <Link to="/leave/types">{tNav('sub.absence.leaveTypes')}</Link>,
                    },
                    {
                      key: 'leave-blackouts',
                      icon: <SafetyCertificateOutlined />,
                      label: <Link to="/leave/blackouts">{tNav('sub.absence.blackoutWindows')}</Link>,
                    },
                    {
                      key: 'leave-categories',
                      icon: <TagsOutlined />,
                      label: <Link to="/leave/categories">{tNav('sub.absence.leaveCategories')}</Link>,
                    },
                    { type: 'divider' as const },
                  ]
                : []),
              {
                key: 'permission-requests',
                icon: <ClockCircleTwoTone twoToneColor="#5B3FE5" />,
                label: <Link to="/permission/requests">{tNav('sub.absence.permissionRequests')}</Link>,
              },
              {
                key: 'permission-balances',
                icon: <WalletOutlined />,
                label: <Link to="/permission/balances">{tNav('sub.absence.permissionBalances')}</Link>,
              },
              ...(isHR
                ? [
                    {
                      key: 'permission-types',
                      icon: <SettingOutlined />,
                      label: <Link to="/permission/types">{tNav('sub.absence.permissionTypes')}</Link>,
                    },
                  ]
                : []),
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Travel (HR + Manager) ──────────────────────────────────────────────
    ...(hrOrManager
      ? [
          {
            key: 'travel',
            icon: <GlobalOutlined />,
            label: tNav('travel'),
            children: [
              {
                key: 'business-trips',
                icon: <RocketOutlined />,
                label: <Link to="/business-trips">{tNav('sub.travel.businessTrips')}</Link>,
              },
              {
                key: 'travel-expense-claims',
                icon: <WalletOutlined />,
                label: <Link to="/business-trips/expense-claims">{tNav('sub.travel.expenseClaims')}</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── HR letters (M77) — queue is HR + Manager; templates HR-only ────────
    ...(hrOrManager
      ? [
          {
            key: 'letters',
            icon: <FileTextOutlined />,
            label: tNav('letters'),
            children: [
              {
                key: 'letters-requests',
                icon: <FileDoneOutlined />,
                label: <Link to="/letters">{tNav('sub.letters.requests')}</Link>,
              },
              ...(isHR
                ? [
                    {
                      key: 'letters-templates',
                      icon: <ProfileOutlined />,
                      label: <Link to="/letters/templates">{tNav('sub.letters.templates')}</Link>,
                    },
                  ]
                : []),
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Personal-info change queue (M79) — HR + Manager queue only ─────────
    ...(hrOrManager
      ? [
          {
            key: 'personal-info-queue',
            icon: <ProfileOutlined />,
            label: <Link to="/personal-info-changes">{tNav('sub.personalInfo.changes')}</Link>,
          } satisfies ItemType,
        ]
      : []),

    // ── Payroll (HR only) ──────────────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'payroll',
            icon: <DollarCircleOutlined />,
            label: 'Payroll',
            children: [
              {
                key: 'payroll-control-board',
                icon: <DashboardOutlined />,
                label: <Link to="/payroll/control-board">Control Board</Link>,
              },
              {
                key: 'payroll-runs',
                icon: <BankOutlined />,
                label: <Link to="/payroll/runs">{tNav('sub.payroll.runs')}</Link>,
              },
              {
                key: 'payroll-compensation',
                icon: <WalletOutlined />,
                label: <Link to="/payroll/compensation">{tNav('sub.payroll.compensation')}</Link>,
              },
              {
                key: 'payroll-components',
                icon: <AppstoreOutlined />,
                label: <Link to="/payroll/components">Components</Link>,
              },
              {
                key: 'payroll-advances',
                icon: <WalletOutlined />,
                label: <Link to="/payroll/advances">Advances</Link>,
              },
              {
                key: 'payroll-loans',
                icon: <BankOutlined />,
                label: <Link to="/payroll/loans">Loans</Link>,
              },
              {
                key: 'payroll-gl-mappings',
                icon: <SettingOutlined />,
                label: <Link to="/payroll/gl-mappings">GL Mappings</Link>,
              },
              {
                key: 'payroll-year-end',
                icon: <CalendarOutlined />,
                label: <Link to="/payroll/year-end">Year-End</Link>,
              },
              {
                key: 'payroll-variance-report',
                icon: <BarChartOutlined />,
                label: <Link to="/payroll/reports/variance">Variance Report</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Compensation (HR only) ─────────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'compensation',
            icon: <WalletOutlined />,
            label: 'Compensation',
            children: [
              {
                key: 'compensation-dashboard',
                icon: <DashboardOutlined />,
                label: <Link to="/compensation/dashboard">Dashboard</Link>,
              },
              {
                key: 'compensation-profile',
                icon: <UserOutlined />,
                label: <Link to="/compensation/profile">Compensation Profile</Link>,
              },
              {
                key: 'compensation-pay-bands',
                icon: <BarChartOutlined />,
                label: <Link to="/compensation/pay-bands">Pay Bands</Link>,
              },
              {
                key: 'compensation-change-reasons',
                icon: <TagsOutlined />,
                label: <Link to="/compensation/change-reasons">Change Reasons</Link>,
              },
              {
                key: 'compensation-salary-changes',
                icon: <SwapOutlined />,
                label: <Link to="/compensation/salary-changes">Salary Changes</Link>,
              },
              {
                key: 'compensation-exceptions',
                icon: <WarningOutlined />,
                label: <Link to="/compensation/exceptions">Exceptions</Link>,
              },
              {
                key: 'compensation-merit-matrices',
                icon: <AppstoreOutlined />,
                label: <Link to="/compensation/merit-matrices">Merit Matrices</Link>,
              },
              {
                key: 'compensation-budgets',
                icon: <DollarCircleOutlined />,
                label: <Link to="/compensation/budgets">Budgets</Link>,
              },
              {
                key: 'compensation-incentive-plans',
                icon: <RocketOutlined />,
                label: <Link to="/compensation/incentive-plans">Incentives</Link>,
              },
              {
                key: 'compensation-commission-plans',
                icon: <DollarCircleOutlined />,
                label: <Link to="/compensation/commission-plans">Commission</Link>,
              },
              {
                key: 'compensation-market',
                icon: <BarChartOutlined />,
                label: <Link to="/compensation/market">Market Data</Link>,
              },
              {
                key: 'compensation-total-comp-statements',
                icon: <FileTextOutlined />,
                label: <Link to="/compensation/total-comp-statements">Total Comp Statements</Link>,
              },
              {
                key: 'compensation-payroll-transfer',
                icon: <SwapOutlined />,
                label: <Link to="/compensation/payroll-transfer">Payroll Transfer</Link>,
              },
              {
                key: 'compensation-config',
                icon: <SettingOutlined />,
                label: <Link to="/compensation/config">Settings</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Recruitment (HR only) ──────────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'recruitment',
            icon: <UserAddOutlined />,
            label: tNav('recruitment'),
            children: [
              {
                key: 'recruitment-vacancies',
                icon: <ProfileOutlined />,
                label: <Link to="/recruitment/vacancies">{tNav('sub.recruitment.vacancies')}</Link>,
              },
              {
                key: 'recruitment-candidates',
                icon: <SearchOutlined />,
                label: <Link to="/recruitment/candidates">{tNav('sub.recruitment.candidates')}</Link>,
              },
              {
                key: 'recruitment-interview-kits',
                icon: <ExperimentOutlined />,
                label: <Link to="/recruitment/interview-kits">{tNav('sub.recruitment.interviewKits')}</Link>,
              },
              {
                key: 'recruitment-talent-pool',
                icon: <UserAddOutlined />,
                label: <Link to="/recruitment/talent-pool">{tNav('sub.recruitment.talentPool')}</Link>,
              },
              {
                key: 'recruitment-analytics',
                icon: <FundOutlined />,
                label: <Link to="/recruitment/analytics">{tNav('sub.recruitment.analytics')}</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Lifecycle (HR only) ────────────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'lifecycle',
            icon: <SwapOutlined />,
            label: tNav('lifecycle'),
            children: [
              {
                key: 'lifecycle-terminations',
                icon: <FileDoneOutlined />,
                label: <Link to="/lifecycle/terminations">{tNav('sub.lifecycle.terminations')}</Link>,
              },
              {
                key: 'lifecycle-contract-changes',
                icon: <SolutionOutlined />,
                label: <Link to="/lifecycle/contract-changes">{tNav('sub.lifecycle.contractChanges')}</Link>,
              },
              {
                key: 'lifecycle-onboarding',
                icon: <RocketOutlined />,
                label: <Link to="/lifecycle/onboarding">{tNav('sub.lifecycle.onboarding')}</Link>,
              },
              {
                key: 'lifecycle-onboarding-requests',
                icon: <RocketOutlined />,
                label: <Link to="/lifecycle/onboarding/requests">{tNav('sub.lifecycle.provisioning')}</Link>,
              },
              {
                key: 'lifecycle-onboarding-myteam',
                icon: <TeamOutlined />,
                label: <Link to="/lifecycle/onboarding/my-team">{tNav('sub.lifecycle.myTeamOnboarding')}</Link>,
              },
              {
                key: 'lifecycle-onboarding-analytics',
                icon: <BarChartOutlined />,
                label: <Link to="/lifecycle/onboarding/analytics">{tNav('sub.lifecycle.onboardingAnalytics')}</Link>,
              },
              {
                key: 'lifecycle-checklists',
                icon: <FileTextOutlined />,
                label: <Link to="/lifecycle/checklists">{tNav('sub.lifecycle.checklists')}</Link>,
              },
              {
                key: 'lifecycle-offboarding',
                icon: <LogoutOutlined />,
                label: <Link to="/lifecycle/offboarding">{tNav('sub.lifecycle.offboarding')}</Link>,
              },
              {
                key: 'lifecycle-offboarding-resignations',
                icon: <UserDeleteOutlined />,
                label: <Link to="/lifecycle/offboarding/resignations">{tNav('sub.lifecycle.resignations')}</Link>,
              },
              {
                key: 'lifecycle-notice-period-rules',
                icon: <CalendarOutlined />,
                label: <Link to="/lifecycle/offboarding/notice-period-rules">Notice Period Rules</Link>,
              },
              {
                key: 'lifecycle-offboarding-analytics',
                icon: <BarChartOutlined />,
                label: <Link to="/lifecycle/offboarding/analytics">Offboarding Analytics</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Performance (HR + Manager) ─────────────────────────────────────────
    ...(hrOrManager
      ? [
          {
            key: 'performance',
            icon: <RocketOutlined />,
            label: tNav('performance'),
            children: [
              {
                key: 'performance-cycles',
                icon: <CalendarOutlined />,
                label: <Link to="/performance/cycles">{tNav('sub.performance.cycles')}</Link>,
              },
              {
                key: 'performance-rating-scales',
                icon: <BarChartOutlined />,
                label: <Link to="/performance/rating-scales">Rating scales</Link>,
              },
              {
                key: 'performance-templates',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/templates">Review templates</Link>,
              },
              {
                key: 'performance-kpis',
                icon: <BarChartOutlined />,
                label: <Link to="/performance/kpis">KPIs</Link>,
              },
              {
                key: 'performance-okrs',
                icon: <BarChartOutlined />,
                label: <Link to="/performance/okrs">OKRs</Link>,
              },
              {
                key: 'performance-feedback360',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/feedback360">360° nominations</Link>,
              },
              {
                key: 'performance-pips',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/pips">PIPs</Link>,
              },
              {
                key: 'performance-dev-plans',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/dev-plans">Development plans</Link>,
              },
              {
                key: 'performance-checkins',
                icon: <CalendarOutlined />,
                label: <Link to="/performance/checkins">Check-ins</Link>,
              },
              {
                key: 'performance-dashboard',
                icon: <BarChartOutlined />,
                label: <Link to="/performance/dashboard">Dashboard</Link>,
              },
              {
                key: 'performance-goals',
                icon: <BarChartOutlined />,
                label: <Link to="/performance/goals">{tNav('sub.performance.goals')}</Link>,
              },
              {
                key: 'performance-reviews',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/reviews">{tNav('sub.performance.reviews')}</Link>,
              },
              {
                key: 'performance-feedback',
                icon: <CoffeeOutlined />,
                label: <Link to="/performance/feedback">{tNav('sub.performance.feedback360')}</Link>,
              },
              {
                key: 'performance-succession',
                icon: <RocketOutlined />,
                label: <Link to="/performance/succession">{tNav('sub.performance.succession9Box')}</Link>,
              },
              {
                key: 'performance-bench',
                icon: <TeamOutlined />,
                label: <Link to="/performance/succession/bench">{tNav('sub.performance.benchDepth')}</Link>,
              },
              {
                key: 'performance-nominations',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/succession/nominations">{tNav('sub.performance.nominations')}</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Learning (HR + Manager) ────────────────────────────────────────────
    ...(hrOrManager
      ? [
          {
            key: 'learning',
            icon: <BookOutlined />,
            label: tNav('learning'),
            children: [
              {
                key: 'learning-courses',
                icon: <ReadOutlined />,
                label: <Link to="/learning/courses">{tNav('sub.learning.catalog')}</Link>,
              },
              {
                key: 'learning-paths',
                icon: <SolutionOutlined />,
                label: <Link to="/learning/paths">{tNav('sub.learning.paths')}</Link>,
              },
              {
                key: 'learning-my',
                icon: <PlayCircleOutlined />,
                label: <Link to="/learning/my">{tNav('sub.learning.my')}</Link>,
              },
              {
                key: 'learning-certificates',
                icon: <SafetyCertificateOutlined />,
                label: <Link to="/learning/certificates">{tNav('sub.learning.certificates')}</Link>,
              },
              {
                key: 'learning-competencies',
                icon: <ExperimentOutlined />,
                label: <Link to="/learning/competencies">{tNav('sub.learning.competencies')}</Link>,
              },
              {
                key: 'learning-training-plans',
                icon: <ProfileOutlined />,
                label: <Link to="/learning/training-plans">{tNav('sub.learning.trainingPlans')}</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Comp & Benefits (HR only) ──────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'compbenefits',
            icon: <DollarCircleOutlined />,
            label: tNav('compBenefits'),
            children: [
              {
                key: 'compbenefits-matrix',
                icon: <BarChartOutlined />,
                label: <Link to="/compbenefits/matrix">{tNav('sub.compBenefits.matrix')}</Link>,
              },
              {
                key: 'compbenefits-allowances',
                icon: <WalletOutlined />,
                label: <Link to="/compbenefits/allowances">{tNav('sub.compBenefits.allowances')}</Link>,
              },
              {
                key: 'compbenefits-bonus-runs',
                icon: <RocketOutlined />,
                label: <Link to="/compbenefits/bonus-runs">{tNav('sub.compBenefits.bonusRuns')}</Link>,
              },
              {
                key: 'compbenefits-salary-planning',
                icon: <FundOutlined />,
                label: <Link to="/compbenefits/salary-planning">{tNav('sub.compBenefits.salaryPlanning')}</Link>,
              },
              {
                key: 'compbenefits-comp-planning',
                icon: <DollarCircleOutlined />,
                label: <Link to="/compbenefits/comp-planning">{tNav('sub.compBenefits.compPlanning')}</Link>,
              },
              {
                key: 'compbenefits-benefits',
                icon: <DollarCircleOutlined />,
                label: <Link to="/compbenefits/benefits">{tNav('sub.compBenefits.benefitsPlans')}</Link>,
              },
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Engagement (HR — M116) ────────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'engagement',
            icon: <ExperimentOutlined />,
            label: <Link to="/engagement/surveys">{tNav('engagement')}</Link>,
          } satisfies ItemType,
        ]
      : []),

    // ── Reports & Analytics (HR only; emp-mgmt also for managers) ─────────
    ...(isHR || isManager
      ? [
          {
            key: 'reports',
            icon: <BarChartOutlined />,
            label: tNav('reportsAnalytics'),
            children: [
              ...(isHR
                ? [
                    {
                      key: 'reports',
                      icon: <BarChartOutlined />,
                      label: <Link to="/reports">{tNav('sub.reports.live')}</Link>,
                    },
                    {
                      key: 'reports-schedules',
                      icon: <ClockCircleOutlined />,
                      label: <Link to="/reports/schedules">{tNav('sub.reports.schedulesHistory')}</Link>,
                    },
                    {
                      key: 'reports-custom',
                      icon: <BarChartOutlined />,
                      label: <Link to="/reports/custom">{tNav('sub.reports.customBuilder')}</Link>,
                    },
                  ]
                : []),
              // M80 emp-mgmt reports — managers see their scope-restricted subset.
              {
                key: 'reports-emp-mgmt',
                icon: <FileDoneOutlined />,
                label: <Link to="/reports/emp-mgmt">{tNav('sub.reports.empMgmt')}</Link>,
              },
              // M81 span-of-control — same gate as emp-mgmt.
              {
                key: 'reports-span',
                icon: <ApartmentOutlined />,
                label: <Link to="/reports/span-of-control">{tNav('sub.reports.spanOfControl')}</Link>,
              },
              // M145 org-native reports.
              {
                key: 'reports-org',
                icon: <ApartmentOutlined />,
                label: <Link to="/reports/org">{tNav('sub.reports.org')}</Link>,
              },
              ...(isHR || isAdminOrAuditor
                ? [
                    {
                      key: 'activity-feed',
                      icon: <ClockCircleTwoTone twoToneColor="#1677ff" />,
                      label: <Link to="/activity">{tNav('sub.reports.activityFeed')}</Link>,
                    },
                  ]
                : []),
            ],
          } satisfies ItemType,
        ]
      : []),

    // ── Approvals (HR + Manager) ───────────────────────────────────────────
    ...(hrOrManager
      ? [
          {
            key: 'approvals',
            icon: <InboxOutlined />,
            label: <Link to="/inbox">{tNav('sub.inbox.approvals')}</Link>,
          } satisfies ItemType,
          ...(isHR || isAdminOrAuditor
            ? [{
                key: 'workflow-sla',
                icon: <ClockCircleOutlined />,
                label: <Link to="/hr/workflow-sla">{tNav('sub.inbox.workflowSla')}</Link>,
              } satisfies ItemType]
            : []),
        ]
      : []),

    // ── Administration ────────────────────────────────────────────────────
    // SYSTEM_ADMIN + AUDITOR get the full menu. HR_ADMIN gets a slim version
    // with the audit-log browser only (M114).
    ...(isAdminOrAuditor || canBrowseAudit
      ? [
          {
            key: 'admin',
            icon: <SettingOutlined />,
            label: tNav('admin'),
            children: [
              ...(isAdmin
                ? [
                    {
                      key: 'admin-users',
                      icon: <TeamOutlined />,
                      label: <Link to="/admin/users">{tNav('sub.admin.userManagement')}</Link>,
                    },
                    {
                      key: 'admin-backups',
                      icon: <CloudServerOutlined />,
                      label: <Link to="/admin/backups">{tNav('sub.admin.backups')}</Link>,
                    },
                    {
                      key: 'admin-ldap',
                      icon: <LinkOutlined />,
                      label: <Link to="/admin/ldap">{tNav('sub.admin.ldapSync')}</Link>,
                    },
                  ]
                : []),
              ...(isAdminOrAuditor
                ? [
                    {
                      key: 'admin-bi-export',
                      icon: <FundOutlined />,
                      label: <Link to="/admin/bi-export">{tNav('sub.admin.biExport')}</Link>,
                    },
                    {
                      key: 'admin-warehouse',
                      icon: <DatabaseOutlined />,
                      label: <Link to="/admin/warehouse">{tNav('sub.admin.analyticsWarehouse')}</Link>,
                    },
                    {
                      key: 'admin-api-keys',
                      icon: <KeyOutlined />,
                      label: <Link to="/admin/api-keys">{tNav('sub.admin.apiKeys')}</Link>,
                    },
                  ]
                : []),
              ...(canBrowseAudit
                ? [
                    {
                      key: 'admin-audit-log',
                      icon: <FileSearchOutlined />,
                      label: <Link to="/admin/audit-log">{tNav('sub.admin.auditLog')}</Link>,
                    },
                  ]
                : []),
            ],
          } satisfies ItemType,
        ]
      : []),
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* Top brand bar — logo + wordmark on the left, user controls on
          the right. Mirrors the Millers ERP layout. */}
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#ffffff',
          borderBottom: '1px solid rgba(91, 63, 229, 0.10)',
          padding: '0 24px',
          height: 64,
        }}
      >
        <Link to="/home" style={{ textDecoration: 'none', color: brand.ink }}>
          <Logo size={36} withWordmark wordmarkColor={brand.ink} />
        </Link>
        <Space size="middle">
          {/* M228 — language picker (AZ default / EN fallback). Lives in
              the top bar so it's reachable from every page; the actual
              switch logic is in i18n/LanguageProvider so there's only
              ever one source of truth for the active locale. */}
          <LanguageSwitcher color={brand.purpleDeep} />
          <NotificationBell />
          <Typography.Text strong style={{ color: brand.ink }}>
            {user?.username}
          </Typography.Text>
          {/* M115 — quick link to notification preferences */}
          <Link to="/my/notifications" style={{ color: brand.purpleDeep, fontSize: 13 }}>
            {tNav('preferences')}
          </Link>
          {user?.roles.map((r) => {
            // Keycloak ships realm roles as e.g. ROLE_SYSTEM_ADMIN;
            // strip the prefix to match the enum keys in
            // common.json → roles.*. Unknown roles fall through to
            // the raw stripped value so a freshly-added role still
            // renders (just not pretty).
            const enumKey = r.replace('ROLE_', '')
            const label = tCommon(`roles.${enumKey}`, { defaultValue: enumKey })
            return (
              <Tag
                key={r}
                style={{
                  background: 'rgba(91, 63, 229, 0.08)',
                  borderColor: 'rgba(91, 63, 229, 0.20)',
                  color: brand.purpleDeep,
                  fontWeight: 500,
                }}
              >
                {label}
              </Tag>
            )
          })}
          <Button onClick={logout}>{tNav('signOut')}</Button>
        </Space>
      </Header>

      {/* Horizontal nav row — the modules that used to live in the
          sidebar are now top-level menu entries with dropdown
          children. Each top item highlights when one of its child
          screens is active. */}
      <div
        style={{
          background: '#ffffff',
          borderBottom: '1px solid rgba(0,0,0,0.06)',
          padding: '0 16px',
        }}
      >
        <Menu
          mode="horizontal"
          selectedKeys={[selectedKey, activeModule]}
          items={items}
          style={{
            border: 'none',
            background: 'transparent',
            lineHeight: '52px',
          }}
        />
      </div>

      {/* Full-width content — no more sidebar reservation. */}
      <Content style={{ padding: 24, background: brand.cream }}>
        <Outlet />
      </Content>
    </Layout>
  )
}
