/**
 * One line per screen, saying what it is for.
 *
 * Self-service already worked this way — its area boards name the action and
 * explain it ("Record your working days and submit the month") — while every
 * other module offered a bare icon and a title. Two presentations of the same
 * idea, and the terse one asks a non-technical HR user to already know what
 * "Timesheet Control" or "Leave Liability" means. These make every board read
 * the same way.
 *
 * Keyed by route, in one file, for the same reason HIDDEN_SCREENS is: this repo
 * tracks the enterprise product on `upstream`, and wording edited inline across
 * 247 tile definitions would conflict on every sync.
 *
 * Written to be read by someone doing the job, not by a developer: plain words,
 * no module names, and the answer to "what happens if I click this".
 */
export const SCREEN_DESCRIPTIONS: Readonly<Record<string, string>> = {
  // ── Self-service ──────────────────────────────────────────────────────────
  '/leave/requests/new': 'Request leave and send it for approval',
  '/business-trips/new': 'Request a business trip before you travel',
  '/letters/request': 'Ask HR for an employment or salary letter',
  '/personal-info/request': 'Ask HR to change your own details',
  '/my': 'Everything about you in one place',
  '/me/time-absences': 'Your timesheet, absences and what leave you have left',
  '/me/pay': 'Your payslips and pay details',
  '/me/personal': 'Your personal and contact information',
  '/me/travel': 'Your business trips and expense claims',
  '/ehs/incidents': 'Report an accident or a near miss',
  '/my/notifications': 'Choose which emails and alerts you receive',

  // ── Manager self-service ──────────────────────────────────────────────────
  '/manager/timesheets': "Approve the months your team has submitted",
  '/my/team': 'Your direct reports and their details',
  '/manager/analytics': 'Headcount, absence and overtime across your team',

  // ── Employee management ───────────────────────────────────────────────────
  '/employees/new': 'Add a new hire, with their contract and pay',
  '/employees': 'Find and open any employee record',
  '/personal-info-changes': 'Approve changes employees asked for themselves',

  // ── Organization ──────────────────────────────────────────────────────────
  '/organization/units/new': 'Create a department or team',
  '/organization/departments': 'The department list the employee screen picks from',
  '/organization': 'The department structure and who sits where',
  '/organization/legal-entities': 'The companies employees are contracted to',
  '/organization/locations': 'Offices, yards and vessels people work at',
  '/organization/unit-types': 'The kinds of unit your structure is built from',
  '/positions': 'The list of jobs employees can be assigned to',
  '/positions/new': 'Add a job title employees can be assigned to',

  // ── Employee lifecycle ────────────────────────────────────────────────────
  '/lifecycle/contract-changes/new': 'Move someone to a new manager, position or salary',
  '/lifecycle/contract-changes': 'Every change made to an employment, with its date',
  '/lifecycle/offboarding/resignations/new': 'Record a resignation an employee has handed in',
  '/lifecycle/offboarding/resignations': 'Resignations received and where each one stands',
  '/lifecycle/terminations/new': 'End an employment, with the date and the reason',
  '/lifecycle/terminations': 'Everyone who has left, and why',
  '/lifecycle/offboarding/notice-period-rules': 'How much notice each kind of leaver must give',

  // ── Time & attendance ─────────────────────────────────────────────────────
  '/my/timesheet': 'Fill in the monthly timesheet and submit it',
  '/timesheets/projects': 'The projects and cost codes timesheets book time to',
  '/timesheets/control': 'Open and close months, and chase what is missing',
  '/attendance/overtime-requests': 'Overtime asked for in advance, and its approval',

  // ── Leave & absence ───────────────────────────────────────────────────────
  '/leave/requests': 'Every absence requested, and its approval trail',
  '/leave/balances': 'What each employee is entitled to, has taken and has left',
  '/leave/types': 'The kinds of leave people can request',
  '/leave/types/new': 'Add a new kind of leave',
  '/leave/categories': 'Group leave types so entitlement rules can apply to them',
  '/leave/encashments': 'Pay out leave days instead of taking them',
  '/leave/period-locks': 'Stop leave being changed after payroll has run',
  '/leave/reports/liability': 'What the untaken leave on the books is worth',
  '/leave/team-calendar': 'Who is away, and when',
  '/leave/unauthorized-absences': 'Days someone did not work and did not book',
  '/leave/unpaid-deductions': 'Unpaid leave that has to reach payroll',
  '/leave/workspace': 'The day-to-day view for whoever runs leave',

  // ── Payroll ───────────────────────────────────────────────────────────────
  '/payroll/runs': 'Run the monthly payroll and see past runs',
  '/payroll/control-board': 'Check everything is ready before you run payroll',
  '/payroll/time-inputs': 'The hours and days coming into payroll from timesheets',
  '/payroll/compensation': 'Set and change monthly base salary',
  '/payroll/components': 'The earnings and deductions payroll can use',
  '/payroll/labor-rates': 'What an hour is worth, by grade or position',
  '/payroll/reports/variance': 'Compare this payroll with last month, to catch mistakes',
  '/payroll/gl-mappings': 'Which finance account each payroll figure posts to',
  '/payroll/gl-reconciliation': 'Check payroll and finance agree',
  '/payroll/year-end': 'Close the payroll year and produce the annual figures',

  // ── Reports & analytics ───────────────────────────────────────────────────
  '/reports': 'The standard HR and payroll reports',
  '/reports/custom': 'Build your own report by choosing the fields',
  '/home/overview': 'Headcount, absence and joiners at a glance',
  '/reports/schedules': 'Have a report emailed on a schedule',
  '/reports/labor-cost': 'What people cost, by project and location',
  '/reports/emp-mgmt': 'Headcount, joiners, leavers and turnover',

  // ── Workflow & approvals ──────────────────────────────────────────────────
  '/inbox': 'Everything waiting for your approval',
  '/workflow/definitions': 'Who approves what, and in which order',
  '/workflow/approval-groups': 'The groups an approval step can be sent to',

  // ── Platform & admin ──────────────────────────────────────────────────────
  '/admin/users': 'Accounts, and what each person may do',
  '/admin/settings': 'Company-wide settings and which modules are on',
  '/admin/permission-matrix': 'Who can see salary and other sensitive data',
  '/admin/notification-templates': 'The wording of the emails the system sends',
  '/admin/audit-log': 'A record of who changed what, and when',
  '/admin/backups': 'Back up the system and restore from a backup',
  '/admin/integrations': 'Connections to other systems',
  '/admin/api-keys': 'Keys that let other systems call this one',
}

/** The one-liner for a screen, or empty when none is written yet. */
export const describeScreen = (to: string): string => SCREEN_DESCRIPTIONS[to] ?? ''
