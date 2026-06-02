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
} from '@ant-design/icons'
import type { ItemType } from 'antd/es/menu/interface'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'
import { brand } from '../theme'

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
  { prefix: '/organization', module: 'people', screen: 'organization' },
  { prefix: '/positions', module: 'people', screen: 'positions' },
  { prefix: '/attendance/schedules', module: 'time', screen: 'attendance-schedules' },
  { prefix: '/attendance/events', module: 'time', screen: 'attendance-events' },
  { prefix: '/attendance/summary', module: 'time', screen: 'attendance-summary' },
  { prefix: '/leave/types', module: 'absence', screen: 'leave-types' },
  { prefix: '/leave/balances', module: 'absence', screen: 'leave-balances' },
  { prefix: '/leave/requests', module: 'absence', screen: 'leave-requests' },
  { prefix: '/permission/types', module: 'absence', screen: 'permission-types' },
  { prefix: '/permission/balances', module: 'absence', screen: 'permission-balances' },
  { prefix: '/permission/requests', module: 'absence', screen: 'permission-requests' },
  { prefix: '/business-trips', module: 'travel', screen: 'business-trips' },
  { prefix: '/letters/templates', module: 'letters', screen: 'letters-templates' },
  { prefix: '/letters/request', module: 'letters', screen: 'letters-request' },
  { prefix: '/letters', module: 'letters', screen: 'letters-requests' },
  { prefix: '/personal-info/request', module: 'personal-info', screen: 'personal-info-request' },
  { prefix: '/personal-info-changes', module: 'personal-info', screen: 'personal-info-queue' },
  { prefix: '/reports/emp-mgmt', module: 'reports', screen: 'reports-emp-mgmt' },
  { prefix: '/activity', module: 'reports', screen: 'activity-feed' },
  { prefix: '/timesheets', module: 'time', screen: 'timesheets' },
  { prefix: '/payroll/runs', module: 'payroll', screen: 'payroll-runs' },
  { prefix: '/payroll/compensation', module: 'payroll', screen: 'payroll-compensation' },
  { prefix: '/recruitment/vacancies', module: 'recruitment', screen: 'recruitment-vacancies' },
  { prefix: '/recruitment/candidates', module: 'recruitment', screen: 'recruitment-candidates' },
  { prefix: '/lifecycle/terminations', module: 'lifecycle', screen: 'lifecycle-terminations' },
  { prefix: '/lifecycle/contract-changes', module: 'lifecycle', screen: 'lifecycle-contract-changes' },
  { prefix: '/performance/cycles', module: 'performance', screen: 'performance-cycles' },
  { prefix: '/performance/goals', module: 'performance', screen: 'performance-goals' },
  { prefix: '/performance/reviews', module: 'performance', screen: 'performance-reviews' },
  { prefix: '/performance/feedback', module: 'performance', screen: 'performance-feedback' },
  { prefix: '/learning/courses', module: 'learning', screen: 'learning-courses' },
  { prefix: '/learning/my', module: 'learning', screen: 'learning-my' },
  { prefix: '/learning/certificates', module: 'learning', screen: 'learning-certificates' },
  { prefix: '/learning/competencies', module: 'learning', screen: 'learning-competencies' },
  { prefix: '/compbenefits/matrix', module: 'compbenefits', screen: 'compbenefits-matrix' },
  { prefix: '/compbenefits/allowances', module: 'compbenefits', screen: 'compbenefits-allowances' },
  { prefix: '/compbenefits/bonus-runs', module: 'compbenefits', screen: 'compbenefits-bonus-runs' },
  { prefix: '/reports/schedules', module: 'reports', screen: 'reports-schedules' },
  { prefix: '/reports', module: 'reports', screen: 'reports' },
  { prefix: '/my/team', module: 'my-team', screen: 'my-team' },
  { prefix: '/my', module: 'my', screen: 'my' },
  { prefix: '/inbox', module: 'approvals', screen: 'approvals' },
  { prefix: '/admin/users', module: 'admin', screen: 'admin-users' },
  { prefix: '/admin/backups', module: 'admin', screen: 'admin-backups' },
  { prefix: '/admin/ldap', module: 'admin', screen: 'admin-ldap' },
  { prefix: '/admin/bi-export', module: 'admin', screen: 'admin-bi-export' },
  { prefix: '/admin/warehouse', module: 'admin', screen: 'admin-warehouse' },
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
  const isHR = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'HR_SPECIALIST', 'AUDITOR')
  const isManager = hasRole('DEPARTMENT_MANAGER')
  const isAdmin = hasRole('SYSTEM_ADMIN')
  /** SYSTEM_ADMIN or AUDITOR — can access BI Export and other audit tools */
  const isAdminOrAuditor = hasRole('SYSTEM_ADMIN', 'AUDITOR')
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
                key: 'organization',
                icon: <ApartmentOutlined />,
                label: <Link to="/organization">Organization</Link>,
              },
              {
                key: 'positions',
                icon: <SolutionOutlined />,
                label: <Link to="/positions">Positions</Link>,
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
                key: 'attendance-schedules',
                icon: <CalendarOutlined />,
                label: <Link to="/attendance/schedules">Schedules</Link>,
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
            ],
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
                  ]
                : []),
              // M80 emp-mgmt reports — managers see their scope-restricted subset.
              {
                key: 'reports-emp-mgmt',
                icon: <FileDoneOutlined />,
                label: <Link to="/reports/emp-mgmt">Employee Management</Link>,
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
        ]
      : []),

    // ── Administration (SYSTEM_ADMIN + AUDITOR) ───────────────────────────
    ...(isAdminOrAuditor
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
