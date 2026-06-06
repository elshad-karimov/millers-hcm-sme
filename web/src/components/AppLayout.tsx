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
} from '@ant-design/icons'
import type { ItemType } from 'antd/es/menu/interface'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'
import { brand } from '../theme'
import { RoleSets } from '../auth/roleSets'

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
  { prefix: '/organization', module: 'people', screen: 'organization' },
  { prefix: '/positions/control', module: 'people', screen: 'position-control' },
  { prefix: '/positions', module: 'people', screen: 'positions' },
  { prefix: '/presence', module: 'time', screen: 'presence-map' },
  { prefix: '/attendance/schedules', module: 'time', screen: 'attendance-schedules' },
  { prefix: '/attendance/roster', module: 'time', screen: 'attendance-roster' },
  { prefix: '/attendance/shift-patterns', module: 'time', screen: 'attendance-shift-patterns' },
  { prefix: '/attendance/variance', module: 'time', screen: 'attendance-variance' },
  { prefix: '/attendance/events', module: 'time', screen: 'attendance-events' },
  { prefix: '/attendance/summary', module: 'time', screen: 'attendance-summary' },
  { prefix: '/leave/types', module: 'absence', screen: 'leave-types' },
  { prefix: '/leave/blackouts', module: 'absence', screen: 'leave-blackouts' },
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
  { prefix: '/activity', module: 'reports', screen: 'activity-feed' },
  { prefix: '/timesheets', module: 'time', screen: 'timesheets' },
  { prefix: '/payroll/runs', module: 'payroll', screen: 'payroll-runs' },
  { prefix: '/payroll/compensation', module: 'payroll', screen: 'payroll-compensation' },
  { prefix: '/recruitment/vacancies', module: 'recruitment', screen: 'recruitment-vacancies' },
  { prefix: '/recruitment/candidates', module: 'recruitment', screen: 'recruitment-candidates' },
  { prefix: '/recruitment/interview-kits', module: 'recruitment', screen: 'recruitment-interview-kits' },
  { prefix: '/recruitment/interviews', module: 'recruitment', screen: 'recruitment-interviews' },
  { prefix: '/recruitment/talent-pool', module: 'recruitment', screen: 'recruitment-talent-pool' },
  { prefix: '/recruitment/analytics', module: 'recruitment', screen: 'recruitment-analytics' },
  { prefix: '/lifecycle/terminations', module: 'lifecycle', screen: 'lifecycle-terminations' },
  { prefix: '/lifecycle/contract-changes', module: 'lifecycle', screen: 'lifecycle-contract-changes' },
  { prefix: '/lifecycle/checklists', module: 'lifecycle', screen: 'lifecycle-checklists' },
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
      label: <Link to="/home">Home</Link>,
    },
    {
      key: 'my',
      icon: <UserOutlined />,
      label: <Link to="/my">My Workspace</Link>,
    },

    // ── My team (managers + HR) — M76 ───────────────────────────────────────
    ...(isManager || isHR
      ? [
          {
            key: 'my-team',
            icon: <TeamOutlined />,
            label: <Link to="/my/team">My team</Link>,
          },
        ]
      : []),

    // ── People (HR only) ───────────────────────────────────────────────────
    ...(isHR
      ? [
          {
            key: 'people',
            icon: <TeamOutlined />,
            label: 'People',
            children: [
              {
                key: 'employees',
                icon: <IdcardOutlined />,
                label: <Link to="/employees">Employees</Link>,
              },
              {
                key: 'hr-preboarding',
                icon: <UserAddOutlined />,
                label: <Link to="/hr/preboarding">Pre-boarding</Link>,
              },
              {
                key: 'hr-assets',
                icon: <InboxOutlined />,
                label: <Link to="/hr/assets">Assets</Link>,
              },
              {
                key: 'organization',
                icon: <ApartmentOutlined />,
                label: <Link to="/organization">Organization</Link>,
              },
              {
                key: 'positions',
                icon: <SolutionOutlined />,
                label: <Link to="/positions">Positions</Link>,
              },
              {
                key: 'position-control',
                icon: <FundOutlined />,
                label: <Link to="/positions/control">Position control</Link>,
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
            label: 'Time & Attendance',
            children: [
              {
                key: 'presence-map',
                icon: <GlobalOutlined />,
                label: <Link to="/presence">Presence map</Link>,
              },
              {
                key: 'attendance-schedules',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/schedules">Schedules</Link>,
              },
              {
                key: 'attendance-roster',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/roster">Roster</Link>,
              },
              {
                key: 'attendance-shift-patterns',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/shift-patterns">Shift patterns</Link>,
              },
              {
                key: 'attendance-variance',
                icon: <BarChartOutlined />,
                label: <Link to="/attendance/variance">Roster variance</Link>,
              },
              {
                key: 'attendance-events',
                icon: <SwapOutlined />,
                label: <Link to="/attendance/events">Events</Link>,
              },
              {
                key: 'attendance-summary',
                icon: <BarChartOutlined />,
                label: <Link to="/attendance/summary">Daily summary</Link>,
              },
              {
                key: 'timesheets',
                icon: <FileDoneOutlined />,
                label: <Link to="/timesheets">Timesheets</Link>,
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
            label: 'Absence',
            children: [
              {
                key: 'leave-requests',
                icon: <FileDoneOutlined />,
                label: <Link to="/leave/requests">Leave requests</Link>,
              },
              {
                key: 'leave-balances',
                icon: <WalletOutlined />,
                label: <Link to="/leave/balances">Leave balances</Link>,
              },
              ...(isHR
                ? [
                    {
                      key: 'leave-types',
                      icon: <SettingOutlined />,
                      label: <Link to="/leave/types">Leave types</Link>,
                    },
                    {
                      key: 'leave-blackouts',
                      icon: <SafetyCertificateOutlined />,
                      label: <Link to="/leave/blackouts">Blackout windows</Link>,
                    },
                    { type: 'divider' as const },
                  ]
                : []),
              {
                key: 'permission-requests',
                icon: <ClockCircleTwoTone twoToneColor="#5B3FE5" />,
                label: <Link to="/permission/requests">Permission requests</Link>,
              },
              {
                key: 'permission-balances',
                icon: <WalletOutlined />,
                label: <Link to="/permission/balances">Permission balances</Link>,
              },
              ...(isHR
                ? [
                    {
                      key: 'permission-types',
                      icon: <SettingOutlined />,
                      label: <Link to="/permission/types">Permission types</Link>,
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
            label: 'Travel',
            children: [
              {
                key: 'business-trips',
                icon: <RocketOutlined />,
                label: <Link to="/business-trips">Business trips</Link>,
              },
              {
                key: 'travel-expense-claims',
                icon: <WalletOutlined />,
                label: <Link to="/business-trips/expense-claims">Expense claims</Link>,
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
            label: 'HR letters',
            children: [
              {
                key: 'letters-requests',
                icon: <FileDoneOutlined />,
                label: <Link to="/letters">Letter requests</Link>,
              },
              ...(isHR
                ? [
                    {
                      key: 'letters-templates',
                      icon: <ProfileOutlined />,
                      label: <Link to="/letters/templates">Templates</Link>,
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
            label: <Link to="/personal-info-changes">Personal-info changes</Link>,
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
                key: 'payroll-runs',
                icon: <BankOutlined />,
                label: <Link to="/payroll/runs">Payroll runs</Link>,
              },
              {
                key: 'payroll-compensation',
                icon: <WalletOutlined />,
                label: <Link to="/payroll/compensation">Compensation</Link>,
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
            label: 'Recruitment',
            children: [
              {
                key: 'recruitment-vacancies',
                icon: <ProfileOutlined />,
                label: <Link to="/recruitment/vacancies">Vacancies</Link>,
              },
              {
                key: 'recruitment-candidates',
                icon: <SearchOutlined />,
                label: <Link to="/recruitment/candidates">Candidates</Link>,
              },
              {
                key: 'recruitment-interview-kits',
                icon: <ExperimentOutlined />,
                label: <Link to="/recruitment/interview-kits">Interview kits</Link>,
              },
              {
                key: 'recruitment-talent-pool',
                icon: <UserAddOutlined />,
                label: <Link to="/recruitment/talent-pool">Talent pool</Link>,
              },
              {
                key: 'recruitment-analytics',
                icon: <FundOutlined />,
                label: <Link to="/recruitment/analytics">Analytics</Link>,
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
            label: 'Lifecycle',
            children: [
              {
                key: 'lifecycle-terminations',
                icon: <FileDoneOutlined />,
                label: <Link to="/lifecycle/terminations">Terminations</Link>,
              },
              {
                key: 'lifecycle-contract-changes',
                icon: <SolutionOutlined />,
                label: <Link to="/lifecycle/contract-changes">Contract changes</Link>,
              },
              {
                key: 'lifecycle-checklists',
                icon: <FileTextOutlined />,
                label: <Link to="/lifecycle/checklists">Checklists</Link>,
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
            label: 'Performance',
            children: [
              {
                key: 'performance-cycles',
                icon: <CalendarOutlined />,
                label: <Link to="/performance/cycles">Review cycles</Link>,
              },
              {
                key: 'performance-goals',
                icon: <BarChartOutlined />,
                label: <Link to="/performance/goals">Goals</Link>,
              },
              {
                key: 'performance-reviews',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/reviews">Reviews</Link>,
              },
              {
                key: 'performance-feedback',
                icon: <CoffeeOutlined />,
                label: <Link to="/performance/feedback">360° feedback</Link>,
              },
              {
                key: 'performance-succession',
                icon: <RocketOutlined />,
                label: <Link to="/performance/succession">9-box succession</Link>,
              },
              {
                key: 'performance-bench',
                icon: <TeamOutlined />,
                label: <Link to="/performance/succession/bench">Bench depth</Link>,
              },
              {
                key: 'performance-nominations',
                icon: <ProfileOutlined />,
                label: <Link to="/performance/succession/nominations">Nominations</Link>,
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
            label: 'Learning',
            children: [
              {
                key: 'learning-courses',
                icon: <ReadOutlined />,
                label: <Link to="/learning/courses">Course catalog</Link>,
              },
              {
                key: 'learning-paths',
                icon: <SolutionOutlined />,
                label: <Link to="/learning/paths">Learning paths</Link>,
              },
              {
                key: 'learning-my',
                icon: <PlayCircleOutlined />,
                label: <Link to="/learning/my">My learning</Link>,
              },
              {
                key: 'learning-certificates',
                icon: <SafetyCertificateOutlined />,
                label: <Link to="/learning/certificates">Certificates</Link>,
              },
              {
                key: 'learning-competencies',
                icon: <ExperimentOutlined />,
                label: <Link to="/learning/competencies">Competencies</Link>,
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
            label: 'Comp & Benefits',
            children: [
              {
                key: 'compbenefits-matrix',
                icon: <BarChartOutlined />,
                label: <Link to="/compbenefits/matrix">Bonus matrix</Link>,
              },
              {
                key: 'compbenefits-allowances',
                icon: <WalletOutlined />,
                label: <Link to="/compbenefits/allowances">Allowances</Link>,
              },
              {
                key: 'compbenefits-bonus-runs',
                icon: <RocketOutlined />,
                label: <Link to="/compbenefits/bonus-runs">Bonus runs</Link>,
              },
              {
                key: 'compbenefits-salary-planning',
                icon: <FundOutlined />,
                label: <Link to="/compbenefits/salary-planning">Salary planning</Link>,
              },
              {
                key: 'compbenefits-comp-planning',
                icon: <DollarCircleOutlined />,
                label: <Link to="/compbenefits/comp-planning">Comp planning</Link>,
              },
              {
                key: 'compbenefits-benefits',
                icon: <DollarCircleOutlined />,
                label: <Link to="/compbenefits/benefits">Benefits plans</Link>,
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
            label: <Link to="/engagement/surveys">Engagement</Link>,
          } satisfies ItemType,
        ]
      : []),

    // ── Reports & Analytics (HR only; emp-mgmt also for managers) ─────────
    ...(isHR || isManager
      ? [
          {
            key: 'reports',
            icon: <BarChartOutlined />,
            label: 'Reports & Analytics',
            children: [
              ...(isHR
                ? [
                    {
                      key: 'reports',
                      icon: <BarChartOutlined />,
                      label: <Link to="/reports">Live reports</Link>,
                    },
                    {
                      key: 'reports-schedules',
                      icon: <ClockCircleOutlined />,
                      label: <Link to="/reports/schedules">Schedules & history</Link>,
                    },
                    {
                      key: 'reports-custom',
                      icon: <BarChartOutlined />,
                      label: <Link to="/reports/custom">Custom report builder</Link>,
                    },
                  ]
                : []),
              // M80 emp-mgmt reports — managers see their scope-restricted subset.
              {
                key: 'reports-emp-mgmt',
                icon: <FileDoneOutlined />,
                label: <Link to="/reports/emp-mgmt">Employee Management</Link>,
              },
              // M81 span-of-control — same gate as emp-mgmt.
              {
                key: 'reports-span',
                icon: <ApartmentOutlined />,
                label: <Link to="/reports/span-of-control">Span of control</Link>,
              },
              ...(isHR || isAdminOrAuditor
                ? [
                    {
                      key: 'activity-feed',
                      icon: <ClockCircleTwoTone twoToneColor="#1677ff" />,
                      label: <Link to="/activity">Activity feed</Link>,
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
            label: <Link to="/inbox">Approvals</Link>,
          } satisfies ItemType,
          ...(isHR || isAdminOrAuditor
            ? [{
                key: 'workflow-sla',
                icon: <ClockCircleOutlined />,
                label: <Link to="/hr/workflow-sla">Workflow SLA</Link>,
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
            label: 'Administration',
            children: [
              ...(isAdmin
                ? [
                    {
                      key: 'admin-users',
                      icon: <TeamOutlined />,
                      label: <Link to="/admin/users">User management</Link>,
                    },
                    {
                      key: 'admin-backups',
                      icon: <CloudServerOutlined />,
                      label: <Link to="/admin/backups">Backups</Link>,
                    },
                    {
                      key: 'admin-ldap',
                      icon: <LinkOutlined />,
                      label: <Link to="/admin/ldap">LDAP Sync</Link>,
                    },
                  ]
                : []),
              ...(isAdminOrAuditor
                ? [
                    {
                      key: 'admin-bi-export',
                      icon: <FundOutlined />,
                      label: <Link to="/admin/bi-export">BI Export</Link>,
                    },
                    {
                      key: 'admin-warehouse',
                      icon: <DatabaseOutlined />,
                      label: <Link to="/admin/warehouse">Analytics Warehouse</Link>,
                    },
                    {
                      key: 'admin-api-keys',
                      icon: <KeyOutlined />,
                      label: <Link to="/admin/api-keys">API keys</Link>,
                    },
                  ]
                : []),
              ...(canBrowseAudit
                ? [
                    {
                      key: 'admin-audit-log',
                      icon: <FileSearchOutlined />,
                      label: <Link to="/admin/audit-log">Audit log</Link>,
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
          <NotificationBell />
          <Typography.Text strong style={{ color: brand.ink }}>
            {user?.username}
          </Typography.Text>
          {/* M115 — quick link to notification preferences */}
          <Link to="/my/notifications" style={{ color: brand.purpleDeep, fontSize: 13 }}>
            Preferences
          </Link>
          {user?.roles.map((r) => {
            const label = r.replace('ROLE_', '')
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
          <Button onClick={logout}>Sign out</Button>
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
