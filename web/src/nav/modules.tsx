import type { ReactNode } from 'react'
import {
  ApartmentOutlined, ApiOutlined, AppstoreOutlined, BankOutlined, BarChartOutlined,
  BellOutlined, CalendarOutlined, ClockCircleOutlined, ClusterOutlined, DatabaseOutlined,
  DollarCircleOutlined, FileDoneOutlined, FileProtectOutlined, FileTextOutlined, GiftOutlined,
  GlobalOutlined, HeartOutlined, KeyOutlined, ReadOutlined, RocketOutlined,
  SafetyCertificateOutlined, SearchOutlined, SettingOutlined, SolutionOutlined, TeamOutlined,
  TrophyOutlined, UserOutlined, WalletOutlined,
} from '@ant-design/icons'

/**
 * Single source of truth for the product's navigable modules.
 *
 * Both the Home springboard and the global Navigator panel render from this
 * list, so a module or app appears in exactly one place in the code.  Mirrors
 * MODULE_SCREEN_INVENTORY (the 25 user-navigable developed modules; the
 * "Home/Dashboard" and "Public" rows are not modules and are excluded).
 */

/* Icon registry — tiles reference an icon by name so this data stays plain. */
export const ICONS: Record<string, ReactNode> = {
  team: <TeamOutlined />, org: <ApartmentOutlined />, solution: <SolutionOutlined />,
  dollar: <DollarCircleOutlined />, gift: <GiftOutlined />, calendar: <CalendarOutlined />,
  clock: <ClockCircleOutlined />, file: <FileDoneOutlined />, read: <ReadOutlined />,
  trophy: <TrophyOutlined />, rocket: <RocketOutlined />, chart: <BarChartOutlined />,
  shield: <FileProtectOutlined />, safety: <SafetyCertificateOutlined />, filetext: <FileTextOutlined />,
  setting: <SettingOutlined />, apartment: <ClusterOutlined />, heart: <HeartOutlined />,
  appstore: <AppstoreOutlined />, user: <UserOutlined />, wallet: <WalletOutlined />,
  bell: <BellOutlined />, key: <KeyOutlined />, database: <DatabaseOutlined />,
  api: <ApiOutlined />, search: <SearchOutlined />, global: <GlobalOutlined />, bank: <BankOutlined />,
}

export const icon = (name: string): ReactNode => ICONS[name] ?? ICONS.appstore

/* Role groups (a category shows if the user has ANY of the listed roles; none = everyone).
   Routes are role-guarded server-side too, so an unauthorised click just redirects. */
export const MGR = ['DEPARTMENT_MANAGER', 'HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN']
export const HR = ['HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN']
export const PAY = ['PAYROLL_SPECIALIST', 'COMPENSATION_MANAGER', 'HR_ADMIN', 'SYSTEM_ADMIN']
export const INSIGHT = ['HR_ADMIN', 'HR_SPECIALIST', 'DEPARTMENT_MANAGER', 'AUDITOR', 'SYSTEM_ADMIN']
export const ADMIN = ['SYSTEM_ADMIN', 'HR_ADMIN', 'AUDITOR']

export type Tile = {
  label: string
  to: string
  icon: string
  /**
   * Module key this tile really belongs to, when that differs from the category
   * it is listed under.  The Self-Service board surfaces employee views of other
   * modules (pay, leave, learning …); `needs` keeps them gated by the module that
   * actually owns the data, so a LITE tenant never sees a tile that 403s.
   */
  needs?: string
}
export type Category = { key: string; label: string; roles?: string[]; quick: Tile[]; apps: Tile[] }
const t = (label: string, to: string, icon: string, needs?: string): Tile => ({ label, to, icon, needs })

/* One entry per developed module — mirrors MODULE_SCREEN_INVENTORY. */
export const CATEGORIES: Category[] = [
  /* Every employee lands here — this is the whole of what an employee can do
     for themselves, so each tile points at a route a plain EMPLOYEE can reach
     (the /my workspace tabs, the always-open request forms).  Tiles that read
     another module's data carry `needs` so they hide when that module is off. */
  { key: 'self-service', label: 'Self-Service',
    quick: [
      t('Request Leave','/leave/requests/new','calendar','leave-absence'),
      t('Request Permission','/permission/requests/new','clock','leave-absence'),
      t('Request Business Trip','/business-trips/new','global','travel-expense'),
      t('Request HR Letter','/letters/request','filetext','core-hr-hr-operations'),
      t('Update Personal Info','/personal-info/request','user','core-hr-employee-management'),
    ],
    apps: [
      t('My Workspace','/my','user'),
      /* /me/<area> tiles carry no `needs` — an area holds cards from several
         modules, so it hides only once EVERY card in it is gated (areaVisible). */
      t('Time and Absences','/me/time-absences','clock'),
      t('Pay','/me/pay','wallet'),
      t('Personal Information','/me/personal','solution'),
      t('Career and Performance','/me/career','trophy'),
      t('Travel and Expense','/me/travel','global'),
      t('Incidents','/ehs/incidents','safety','health-safety'),
      t('Notification Preferences','/my/notifications','bell'),
    ] },
  { key: 'manager-self-service', label: 'Manager Self-Service', roles: MGR,
    quick: [t('Timesheet Approvals','/manager/timesheets','apartment'), t('My Team','/my/team','team'), t('Manager Analytics','/manager/analytics','chart')],
    apps: [t('Timesheet Approvals','/manager/timesheets','apartment','time-attendance'), t('My Team','/my/team','team'), t('Manager Analytics','/manager/analytics','chart'), t('Movement Requests','/manager/movements','appstore'), t('Presence Map','/presence','clock'), t('Career','/career','rocket','core-hr-employee-management')] },
  { key: 'core-hr-employee-management', label: 'Employee Management', roles: HR,
    quick: [t('New Employee','/employees/new','team'), t('New Personal Info Request','/personal-info/request','appstore')],
    apps: [t('Employees','/employees','team'), t('Personal Info Changes','/personal-info-changes','appstore')] },
  { key: 'core-hr-organization', label: 'Organization', roles: HR,
    quick: [t('New Org Unit','/organization/units/new','org')],
    apps: [t('Org Structure','/organization','org'), t('Bulk Reorg','/organization/bulk-reorg','org'), t('Org Unit Documents','/organization/documents','org'), t('HR Partners','/organization/hr-partners','org'), t('Legal Entities','/organization/legal-entities','org'), t('Locations','/organization/locations','org'), t('Org Unit Types','/organization/unit-types','org')] },
  { key: 'core-hr-staffing-positions', label: 'Staffing & Positions', roles: HR,
    quick: [t('New Position','/positions/new','solution')],
    apps: [t('Positions','/positions','solution'), t('Position Control','/positions/control','solution'), t('Position Variance','/positions/variance','solution'), t('Staffing Tables','/staffing-tables','solution'), t('Workforce Plans','/workforce-plans','solution')] },
  { key: 'core-hr-hr-operations', label: 'HR Operations', roles: HR,
    quick: [t('New Letter Request','/letters/request','filetext')],
    apps: [t('Asset Categories','/assets/categories','appstore'), t('Damage Loss Cases','/assets/damage-loss','appstore'), t('Knowledge Base','/helpdesk/knowledge','filetext'), t('Announcements','/hr/announcements','filetext'), t('Assets Admin','/hr/assets','setting'), t('Document Categories','/hr/document-categories','filetext'), t('Preboarding','/hr/preboarding','appstore'), t('HR Service Queue','/hr/service-requests','appstore'), t('Signatures','/hr/signatures','filetext'), t('Workflow Sla','/hr/workflow-sla','apartment'), t('Letter Requests','/letters','filetext'), t('Letter Templates','/letters/templates','filetext'), t('Policies Admin','/policies','shield'), t('My Policies','/self/policies','shield')] },
  { key: 'employee-lifecycle', label: 'Employee Lifecycle', roles: HR,
    quick: [t('New Contract Change','/lifecycle/contract-changes/new','filetext'), t('New Resignation Request','/lifecycle/offboarding/resignations/new','filetext'), t('New Termination','/lifecycle/terminations/new','filetext')],
    apps: [t('Contractors','/contingent/contractors','team'), t('Checklists','/lifecycle/checklists','filetext'), t('Contract Changes','/lifecycle/contract-changes','filetext'), t('Offboarding Console','/lifecycle/offboarding','filetext'), t('Offboarding Analytics','/lifecycle/offboarding/analytics','chart'), t('Notice Period Rules','/lifecycle/offboarding/notice-period-rules','calendar'), t('Resignation Request','/lifecycle/offboarding/resignations','filetext'), t('Onboarding Console','/lifecycle/onboarding','solution'), t('Onboarding Analytics','/lifecycle/onboarding/analytics','chart'), t('Manager Onboarding','/lifecycle/onboarding/my-team','team'), t('Onboarding Requests','/lifecycle/onboarding/requests','solution'), t('Terminations','/lifecycle/terminations','filetext'), t('International Assignments','/mobility/assignments','global')] },
  { key: 'employee-relations', label: 'Employee Relations', roles: HR,
    quick: [t('ER Cases','/er/cases','appstore'), t('Corrective Actions','/er/corrective-actions','appstore'), t('Warnings','/er/warnings','appstore')],
    apps: [t('ER Cases','/er/cases','appstore'), t('Corrective Actions','/er/corrective-actions','appstore'), t('Warnings','/er/warnings','appstore')] },
  { key: 'time-attendance', label: 'Time & Attendance', roles: MGR,
    quick: [t('New Schedule','/attendance/schedules/new','calendar')],
    apps: [t('Attendance Corrections','/attendance/corrections','clock'), t('Device Master','/attendance/devices','clock'), t('Attendance Events','/attendance/events','clock'), t('Attendance Exceptions','/attendance/exceptions','clock'), t('Overtime Requests','/attendance/overtime-requests','clock'), t('Attendance Periods','/attendance/periods','calendar'), t('Attendance Policies','/attendance/policies','clock'), t('Attendance Reports','/attendance/reports','clock'), t('Roster','/attendance/roster','calendar'), t('Attendance Schedules','/attendance/schedules','calendar'), t('Shift Patterns','/attendance/shift-patterns','calendar'), t('Attendance Summary','/attendance/summary','clock'), t('Roster Variance','/attendance/variance','calendar'), t('Attendance Workspace','/attendance/workspace','clock'), t('Timesheets','/timesheets','file'), t('Timesheet Projects','/timesheets/projects','file'), t('Timesheet Approvals','/manager/timesheets','apartment'), t('Timesheet Control','/timesheets/control','shield')] },
  { key: 'leave-absence', label: 'Leave & Absence', roles: MGR,
    quick: [t('New Leave Request','/leave/requests/new','calendar'), t('New Leave Type','/leave/types/new','calendar'), t('New Permission Request','/permission/requests/new','clock'), t('New Permission Type','/permission/types/new','clock')],
    apps: [t('Leave Balances','/leave/balances','calendar'), t('Leave Blackouts','/leave/blackouts','calendar'), t('Leave Categories','/leave/categories','calendar'), t('Leave Encashment','/leave/encashments','calendar'), t('Leave Period Locks','/leave/period-locks','calendar'), t('Leave Liability','/leave/reports/liability','calendar'), t('Leave Requests','/leave/requests','calendar'), t('Team Leave Calendar','/leave/team-calendar','team'), t('Leave Types','/leave/types','calendar'), t('Unauthorized Absence','/leave/unauthorized-absences','calendar'), t('Unpaid Deductions','/leave/unpaid-deductions','calendar'), t('Leave Workspace','/leave/workspace','calendar'), t('Permission Balances','/permission/balances','clock'), t('Permission Requests','/permission/requests','clock'), t('Permission Types','/permission/types','clock')] },
  { key: 'travel-expense', label: 'Travel & Expense', roles: MGR,
    quick: [t('New Business Trip','/business-trips/new','appstore')],
    apps: [t('Business Trips','/business-trips','appstore'), t('Expense Claims','/business-trips/expense-claims','appstore'), t('Expense Policies','/business-trips/expense-policies','shield'), t('Mileage Claims','/business-trips/mileage-claims','appstore'), t('Per Diem Rules','/business-trips/per-diem-rules','appstore'), t('Reimbursement Batches','/business-trips/reimbursements','appstore')] },
  { key: 'payroll', label: 'Payroll', roles: PAY,
    quick: [t('Salary Advances','/payroll/advances','dollar'), t('Payroll Runs','/payroll/runs','dollar'), t('Control Board','/payroll/control-board','dollar')],
    apps: [t('Payroll Control Board','/payroll/control-board','dollar'), t('Payroll Runs','/payroll/runs','dollar'), t('Time \u0026 Attendance Inputs','/payroll/time-inputs','clock'), t('Payroll Compensation','/payroll/compensation','dollar'), t('Salary Components','/payroll/components','dollar'), t('Salary Advances','/payroll/advances','dollar'), t('Payroll Loans','/payroll/loans','dollar'), t('Loan Types','/payroll/loan-types','dollar'), t('Loan Requests','/payroll/loan-requests','dollar'), t('GL Account Mappings','/payroll/gl-mappings','dollar'), t('GL Reconciliation','/payroll/gl-reconciliation','dollar'), t('Variance Report','/payroll/reports/variance','dollar'), t('Labor Rates','/payroll/labor-rates','dollar'), t('Year End','/payroll/year-end','dollar')] },
  { key: 'compensation', label: 'Compensation', roles: PAY,
    quick: [t('Compensation Dashboard','/compensation/dashboard','dollar'), t('Salary Changes','/compensation/salary-changes','dollar'), t('Pay Bands','/compensation/pay-bands','dollar')],
    apps: [t('Compensation Dashboard','/compensation/dashboard','dollar'), t('Compensation Profile','/compensation/profile','dollar'), t('Pay Bands','/compensation/pay-bands','dollar'), t('Change Reasons','/compensation/change-reasons','dollar'), t('Salary Changes','/compensation/salary-changes','dollar'), t('Compensation Exceptions','/compensation/exceptions','dollar'), t('Merit Matrices','/compensation/merit-matrices','dollar'), t('Compensation Budgets','/compensation/budgets','dollar'), t('Incentive Plans','/compensation/incentive-plans','dollar'), t('Commission Plans','/compensation/commission-plans','dollar'), t('Market Data','/compensation/market','dollar'), t('Total Comp Statements','/compensation/total-comp-statements','dollar'), t('Comp Payroll Transfer','/compensation/payroll-transfer','dollar'), t('Compensation Config','/compensation/config','dollar')] },
  { key: 'benefits', label: 'Benefits', roles: PAY,
    quick: [t('Benefits','/compbenefits/benefits','gift'), t('Bonus Runs','/compbenefits/bonus-runs','dollar'), t('Allowances','/compbenefits/allowances','dollar')],
    apps: [t('Benefits','/compbenefits/benefits','gift'), t('Bonus Matrix','/compbenefits/matrix','dollar'), t('Allowances','/compbenefits/allowances','dollar'), t('Bonus Runs','/compbenefits/bonus-runs','dollar'), t('Salary Planning','/compbenefits/salary-planning','dollar'), t('Comp Planning','/compbenefits/comp-planning','gift')] },
  { key: 'budgeting', label: 'Budgeting', roles: PAY,
    quick: [t('Budget Cycles','/budgets/cycles','dollar'), t('Department Budgets','/budgets/departments','dollar'), t('Budget Forecast','/budgets/forecast','dollar')],
    apps: [t('Budget Cycles','/budgets/cycles','dollar'), t('Department Budgets','/budgets/departments','dollar'), t('Budget Forecast','/budgets/forecast','dollar'), t('Variance Dashboard','/budgets/variance','dollar'), t('Budget Settings','/budgets/settings','dollar')] },
  { key: 'recruitment', label: 'Recruitment', roles: HR,
    quick: [t('New Candidate','/recruitment/candidates/new','team'), t('New Interview Schedule','/recruitment/interviews/schedule','calendar'), t('New Vacancy','/recruitment/vacancies/new','rocket')],
    apps: [t('Vacancies','/recruitment/vacancies','rocket'), t('Candidates','/recruitment/candidates','team'), t('Candidate Duplicates','/recruitment/candidates/duplicates','team'), t('Candidate Retention','/recruitment/candidates/retention','team'), t('Interview Kits','/recruitment/interview-kits','rocket'), t('Talent Pool','/recruitment/talent-pool','trophy'), t('Referrals','/recruitment/referrals','rocket'), t('Agencies','/recruitment/agencies','bank'), t('Recruitment Analytics','/recruitment/analytics','chart')] },
  { key: 'performance', label: 'Performance', roles: MGR,
    quick: [t('New Review Cycle','/performance/cycles/new','trophy')],
    apps: [t('Performance Dashboard','/performance/dashboard','trophy'), t('Review Cycles','/performance/cycles','trophy'), t('Rating Scales','/performance/rating-scales','trophy'), t('Review Templates','/performance/templates','trophy'), t('KPIs','/performance/kpis','trophy'), t('OKRs','/performance/okrs','trophy'), t('Goals','/performance/goals','trophy'), t('Reviews','/performance/reviews','trophy'), t('Feedback','/performance/feedback','trophy'), t('360 Feedback','/performance/feedback360','trophy'), t('PIPs','/performance/pips','trophy'), t('Dev Plans','/performance/dev-plans','trophy'), t('Check Ins','/performance/checkins','clock'), t('Succession Grid','/performance/succession','org'), t('Bench Depth','/performance/succession/bench','org'), t('Succession Nominations','/performance/succession/nominations','org')] },
  { key: 'talent-succession', label: 'Talent & Succession', roles: HR,
    quick: [t('Talent Pools','/talent/pools','trophy'), t('Succession Plans','/talent/succession-plans','org'), t('Career Dashboard','/talent/career-dashboard','trophy')],
    apps: [t('Talent Pools','/talent/pools','trophy'), t('Talent Reviews','/talent/reviews','trophy'), t('Talent Analytics','/talent/analytics','chart'), t('Succession Plans','/talent/succession-plans','org'), t('Replacement Chart','/talent/replacement-chart','org'), t('Career Paths','/talent/career-paths','rocket'), t('Mentoring','/talent/mentoring','read'), t('Career Dashboard','/talent/career-dashboard','chart')] },
  { key: 'learning-lms', label: 'Learning (LMS)', roles: MGR,
    quick: [t('New Course','/learning/courses/new','read'), t('Course Catalog','/learning/courses','read'), t('My Learning','/learning/my','read')],
    apps: [t('Course Catalog','/learning/courses','read'), t('Learning Paths','/learning/paths','read'), t('My Learning','/learning/my','read'), t('Certificates','/learning/certificates','read'), t('Competencies','/learning/competencies','read'), t('Skill Verifications','/learning/skill-verifications','read'), t('Skill Inventory','/learning/skill-inventory','chart'), t('Training Plans','/learning/training-plans','read'), t('Classroom Training','/learning/classroom','read'), t('Mandatory Training','/learning/mandatory','shield')] },
  { key: 'engagement', label: 'Engagement', roles: HR,
    quick: [t('Surveys','/engagement/surveys','filetext'), t('Recognition','/engagement/recognition','heart'), t('Pulse Schedules','/engagement/pulse-schedules','calendar')],
    apps: [t('Surveys','/engagement/surveys','filetext'), t('Pulse Schedules','/engagement/pulse-schedules','calendar'), t('Recognition','/engagement/recognition','heart'), t('Action Plans','/engagement/action-plans','heart'), t('Participation','/engagement/participation','chart'), t('Reward Points','/engagement/rewards-admin','gift')] },
  { key: 'health-safety', label: 'Health & Safety', roles: HR,
    quick: [t('Incidents','/ehs/incidents','safety'), t('Inspections','/ehs/inspections','safety'), t('Risk Register','/ehs/risk-register','safety')],
    apps: [t('Incidents','/ehs/incidents','safety'), t('Return To Work','/ehs/return-to-work','safety'), t('Risk Register','/ehs/risk-register','safety'), t('Inspections','/ehs/inspections','safety'), t('Corrective Actions','/ehs/corrective-actions','safety'), t('PPE Items','/ehs/ppe-items','safety'), t('PPE Assignments','/ehs/ppe-assignments','safety')] },
  { key: 'compliance', label: 'Compliance', roles: INSIGHT,
    quick: [t('Compliance Calendar','/compliance/calendar','shield'), t('Statutory Reports','/compliance/statutory','chart'), t('Privacy Requests','/compliance/privacy-requests','shield')],
    apps: [t('Statutory Reports','/compliance/statutory','chart'), t('Compliance Calendar','/compliance/calendar','shield'), t('Work Authorization','/compliance/work-authorization','shield'), t('Privacy Requests','/compliance/privacy-requests','shield')] },
  { key: 'reports-analytics', label: 'Reports & Analytics', roles: INSIGHT,
    quick: [t('Reports','/reports','chart'), t('Custom Report Builder','/reports/custom','chart'), t('Executive Summary','/analytics/executive','chart')],
    apps: [t('HR Overview','/home/overview','chart'), t('Reports','/reports','chart'), t('Report Schedules','/reports/schedules','calendar'), t('Custom Report Builder','/reports/custom','chart'), t('Labor Cost Report','/reports/labor-cost','dollar'), t('Employee Management Reports','/reports/emp-mgmt','team'), t('Span Of Control','/reports/span-of-control','org'), t('Org Native Reports','/reports/org','org'), t('Activity Feed','/activity','chart'), t('KPI Definitions','/analytics/kpis','trophy'), t('My Dashboards','/analytics/dashboards','chart'), t('Executive Summary','/analytics/executive','chart'), t('Attrition Risk','/analytics/attrition-risk','chart')] },
  { key: 'workflow-approvals', label: 'Workflow & Approvals', roles: MGR,
    quick: [t('Approvals Inbox','/inbox','apartment'), t('Workflow Definitions','/workflow/definitions','apartment'), t('Approval Groups','/workflow/approval-groups','apartment')],
    apps: [t('Approvals Inbox','/inbox','apartment'), t('Workflow Definitions','/workflow/definitions','apartment'), t('Approval Groups','/workflow/approval-groups','apartment')] },
  { key: 'platform-admin', label: 'Platform & Admin', roles: ADMIN,
    quick: [t('User Management','/admin/users','user'), t('Tenant Settings','/admin/settings','setting'), t('Integrations','/admin/integrations','api')],
    apps: [t('User Management','/admin/users','user'), t('Tenant Settings','/admin/settings','setting'), t('Permission Matrix','/admin/permission-matrix','key'), t('Notification Templates','/admin/notification-templates','bell'), t('Integrations','/admin/integrations','api'), t('API Keys','/admin/api-keys','key'), t('Ldap Sync','/admin/ldap','database'), t('Audit Log','/admin/audit-log','filetext'), t('Backups','/admin/backups','database'), t('Bi Export','/admin/bi-export','chart'), t('Warehouse Analytics','/admin/warehouse','database')] },
]

/**
 * Screens this edition does not show.
 *
 * The SME edition serves an oil-and-gas workforce whose HR data is a single
 * spreadsheet: employee master data, monthly timesheets, leave entitlement and
 * payroll. Whole modules outside that are already excluded by the LITE plan
 * (Recruitment, Performance, Learning, …) and 403 server-side. What is left over
 * are screens sitting INSIDE a module we keep, belonging to a way of working
 * this customer does not have — clocking terminals, staff loans, onboarding
 * workflows. Listing them was pure noise on the springboard.
 *
 * Kept as an exclusion list rather than deleting the tiles, for two reasons:
 * this repo tracks the enterprise product on `upstream`, and deleted lines
 * conflict on every sync; and a customer who later buys biometric terminals
 * needs one line removed here, not a set of tiles reconstructed from memory.
 *
 * This hides screens from the springboard, search and favourites. It is NOT a
 * security control — the routes still resolve if somebody types the URL. Role
 * checks and the server-side module gate remain the real protection.
 */
export const HIDDEN_SCREENS: ReadonlySet<string> = new Set([
  // Clocking. Attendance here is captured on monthly timesheets; there are no
  // badge terminals, rosters or shift patterns anywhere in the source data.
  '/attendance/corrections', '/attendance/devices', '/attendance/events',
  '/attendance/exceptions', '/attendance/periods', '/attendance/policies',
  '/attendance/reports', '/attendance/roster', '/attendance/schedules',
  '/attendance/schedules/new', '/attendance/shift-patterns', '/attendance/summary',
  '/attendance/variance', '/attendance/workspace',
  // Onboarding / offboarding workflows and contingent labour. Joining and
  // leaving are recorded through Contract Changes and Terminations instead.
  '/contingent/contractors', '/lifecycle/checklists', '/lifecycle/offboarding',
  '/lifecycle/offboarding/analytics', '/lifecycle/onboarding',
  '/lifecycle/onboarding/analytics', '/lifecycle/onboarding/my-team',
  '/lifecycle/onboarding/requests', '/mobility/assignments',
  // Hourly "permission" absence. Vacation is the only absence type tracked.
  '/permission/requests', '/permission/requests/new', '/permission/types',
  '/permission/types/new', '/permission/balances',
  '/leave/blackouts',
  // Staff loans and advances — not part of this payroll.
  '/payroll/advances', '/payroll/loans', '/payroll/loan-types', '/payroll/loan-requests',
  // Benefits administration. Only Allowances is kept: it is where the lunch,
  // transport, hardship and MEWA amounts live.
  '/compbenefits/benefits', '/compbenefits/bonus-runs', '/compbenefits/matrix',
  '/compbenefits/salary-planning', '/compbenefits/comp-planning',
  // BI layer. The standard reports and the custom report builder cover what is
  // actually asked for; these are dashboards nobody has configured.
  '/analytics/executive', '/analytics/kpis', '/analytics/dashboards',
  '/analytics/attrition-risk', '/reports/span-of-control', '/reports/org', '/activity',
  // Org tooling beyond the structure itself.
  '/organization/bulk-reorg', '/organization/documents', '/organization/hr-partners',
  // Talent and position-movement surfaces whose owning modules are not in LITE,
  // but which are listed under a module that is — so plan gating alone misses them.
  '/career', '/me/career', '/manager/movements',
  '/presence', // presence map is driven by clocking events
  // Admin integrations that have nothing behind them on this deployment.
  '/admin/ldap', '/admin/bi-export', '/admin/warehouse',
])

/** Whether a screen is switched off for this edition. See {@link HIDDEN_SCREENS}. */
export const isHiddenScreen = (to: string): boolean => HIDDEN_SCREENS.has(to)

/** A tile plus the module (category key) it belongs to. */
export type IndexedTile = Tile & { module: string }

/** Flat, de-duplicated index of every app tile — used by search + Recents.
    A tile's module is the one that owns its data (`needs`), not necessarily the
    category it is listed under, so plan/tenant gating stays with the real owner. */
export const ALL_TILES: IndexedTile[] = (() => {
  const seen = new Set<string>()
  const out: IndexedTile[] = []
  for (const c of CATEGORIES) {
    for (const tile of c.apps) {
      if (seen.has(tile.to)) continue
      seen.add(tile.to)
      out.push({ ...tile, module: tile.needs ?? c.key })
    }
  }
  return out
})()

/** Resolve the tile that best matches a route (longest `to` prefix).
    Tiles whose `to` carries a query string are shortcuts INTO a page that another
    tile already owns (e.g. `/my?tab=payroll` → `/my`), so they are skipped here —
    otherwise `/my` would resolve to whichever tab happened to be listed first. */
export function findTile(pathname: string): IndexedTile | undefined {
  let best: IndexedTile | undefined
  for (const tile of ALL_TILES) {
    if (tile.to.includes('?')) continue
    if (pathname === tile.to || pathname.startsWith(tile.to + '/')) {
      if (!best || tile.to.length > best.to.length) best = tile
    }
  }
  return best
}

/** Which module (category key) a route belongs to, if any. */
export function moduleOfRoute(pathname: string): string | undefined {
  return findTile(pathname)?.module
}
