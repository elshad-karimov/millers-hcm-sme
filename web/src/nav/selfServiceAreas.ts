import type { Tile } from './modules'

/**
 * Second level of employee self-service.
 *
 * The Home springboard shows one tile per AREA ("Time and Absences", "Pay", …);
 * opening it lands on /me/<key>, a card board of the concrete things the
 * employee can do there.  Two levels keep the springboard readable while still
 * naming every action, instead of dropping people into a wall of tabs.
 *
 * A card's `to` is a route a plain EMPLOYEE can reach — the always-open request
 * forms, or a /my?tab=… deep link into their workspace.  `needs` is the module
 * that owns the data, so a card disappears when the tenant's plan or admin has
 * that module off (rather than 403-ing on click).
 */

export type AreaCard = Tile & { desc: string }
export type Area = {
  key: string
  label: string
  /** One line under the page title — what this area is for. */
  blurb: string
  cards: AreaCard[]
}

const card = (label: string, desc: string, to: string, icon: string, needs?: string): AreaCard => ({
  label,
  desc,
  to,
  icon,
  needs,
})

export const AREAS: Area[] = [
  {
    key: 'time-absences',
    label: 'Time and Absences',
    blurb: 'Request time off, check what you have left, and fill in your timesheet.',
    cards: [
      card('My Timesheet', 'Record your working days and submit the month', '/my/timesheet', 'clock', 'time-attendance'),
      card('Add Absence', 'Request leave and submit it for approval', '/leave/requests/new', 'calendar', 'leave-absence'),
      card('Absence Balance', 'Review your leave balances and past requests', '/my?tab=leave', 'file', 'leave-absence'),
      card('Add Permission', 'Ask for a few hours off during the working day', '/permission/requests/new', 'clock', 'leave-absence'),
      card('Permission Balance', 'Review your permission hours and requests', '/my?tab=permission', 'file', 'leave-absence'),
      card('Timesheet History', 'Your submitted timesheets and their status', '/my?tab=timesheets', 'file', 'time-attendance'),
      card('Team Calendar', 'See who in your team is away', '/my?tab=teamCalendar', 'team', 'leave-absence'),
    ],
  },
  {
    key: 'pay',
    label: 'Pay',
    blurb: 'Your payslips, tax certificate, salary advances and loans.',
    cards: [
      card('Payslips and Advances', 'Download payslips, your tax certificate, and request an advance', '/my?tab=payroll', 'wallet', 'payroll'),
      card('My Loans', 'Your loan requests and repayment schedule', '/my?tab=loans', 'bank', 'payroll'),
      card('My Benefits', 'Your benefit enrolments and allowances', '/my?tab=benefits', 'gift', 'benefits'),
    ],
  },
  {
    key: 'personal',
    label: 'Personal Information',
    blurb: 'Your details, company documents and how we contact you.',
    cards: [
      card('My Profile', 'Your details, position and department', '/my', 'user'),
      card('Update Personal Info', 'Request a change to your personal details', '/personal-info/request', 'solution', 'core-hr-employee-management'),
      card('Request HR Letter', 'Ask HR for an employment or salary letter', '/letters/request', 'filetext', 'core-hr-hr-operations'),
      card('My Assets', 'Equipment issued to you', '/my?tab=assets', 'appstore', 'core-hr-hr-operations'),
      card('Company Policies', 'Read and acknowledge company policies', '/self/policies', 'shield', 'core-hr-hr-operations'),
      card('Notification Preferences', 'Choose what you are notified about', '/my/notifications', 'bell'),
    ],
  },
  {
    key: 'career',
    label: 'Career and Performance',
    blurb: 'Your goals, reviews, courses and certificates.',
    cards: [
      card('Performance and Goals', 'Your review cycle, goals and feedback', '/my?tab=performance', 'trophy', 'performance'),
      card('Learning', 'Your courses, learning paths and certificates', '/my?tab=learning', 'read', 'learning-lms'),
      card('My Surveys', 'Surveys waiting for your response', '/my/surveys', 'filetext', 'engagement'),
    ],
  },
  {
    key: 'travel',
    label: 'Travel and Expense',
    blurb: 'Business trips and the money you spent on them.',
    cards: [
      card('New Business Trip', 'Request a trip and submit it for approval', '/business-trips/new', 'global', 'travel-expense'),
      card('My Trips and Claims', 'Your trips, expense claims and mileage', '/my?tab=businessTrips', 'appstore', 'travel-expense'),
    ],
  },
]

export const findArea = (key: string | undefined): Area | undefined =>
  AREAS.find((a) => a.key === key)

/**
 * Should a springboard tile pointing at an area still be shown?
 *
 * An area spans several modules (Pay holds payroll *and* benefits), so it can't
 * be gated by a single module key — it survives while at least one of its cards
 * does.  Non-area routes are none of this function's business and pass through.
 */
export function areaVisible(to: string, disabled: ReadonlySet<string>): boolean {
  const area = to.startsWith('/me/') ? findArea(to.slice('/me/'.length)) : undefined
  if (!area) return true
  return area.cards.some((c) => !c.needs || !disabled.has(c.needs))
}
