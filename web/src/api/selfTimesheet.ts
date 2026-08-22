import { api } from './client'

/**
 * The employee's own timesheet.
 *
 * Mirrors DailyEntryDtos on the server. Every call is scoped to the caller by
 * the backend — there is deliberately no employeeId parameter anywhere here.
 *
 * Quantities only: nothing in this module carries a rate or an amount.
 */

export type WorkType =
  | 'ONSHORE'
  | 'OFFSHORE'
  | 'QUAYSIDE'
  | 'BUSINESS_TRIP'
  | 'REMOTE'
  | 'LEAVE'
  | 'SICK'
  | 'NON_WORKING'

/** Work types an employee may pick when recording a day they worked. */
export const SELECTABLE_WORK_TYPES: { value: WorkType; label: string; hint: string }[] = [
  { value: 'OFFSHORE', label: 'Offshore', hint: 'Work at an offshore facility' },
  { value: 'ONSHORE', label: 'Onshore', hint: 'Work at an onshore site or office' },
  { value: 'QUAYSIDE', label: 'Quayside', hint: 'Work at the quayside' },
  { value: 'REMOTE', label: 'Remote', hint: 'Working away from a company site' },
  { value: 'BUSINESS_TRIP', label: 'Business trip', hint: 'Travelling on approved business' },
  { value: 'NON_WORKING', label: 'Non-working day', hint: 'Rest day — nothing worked' },
]

export const WORK_TYPE_LABELS: Record<WorkType, string> = {
  OFFSHORE: 'Offshore',
  ONSHORE: 'Onshore',
  QUAYSIDE: 'Quayside',
  REMOTE: 'Remote',
  BUSINESS_TRIP: 'Business trip',
  NON_WORKING: 'Non-working',
  LEAVE: 'Leave',
  SICK: 'Sick leave',
}

export interface CategoryOption {
  code: string
  name: string
  unit: 'HOURS' | 'DAYS' | 'MINUTES'
  /** True = the system calculates it; the form shows it read-only. */
  derived: boolean
  source: string
  maxPerDay: number
  displayOrder: number
  appliesTo: WorkType[]
}

export interface QuantityView {
  categoryCode: string
  categoryName: string
  unit: 'HOURS' | 'DAYS' | 'MINUTES'
  quantity: number
  derived: boolean
  derivedFrom?: string | null
}

export interface FindingView {
  code: string
  severity: 'BLOCKING' | 'WARNING'
  date?: string | null
  message: string
}

export interface DayView {
  id?: string | null
  date: string
  dayOfWeek: string
  workType?: WorkType | null
  entrySource?: string | null
  holiday: boolean
  scheduledWorkingDay: boolean
  readOnly: boolean
  readOnlyReason?: string | null
  leaveRequestId?: string | null
  attendanceHours?: number | null
  attendanceVarianceHours?: number | null
  varianceExplanation?: string | null
  note?: string | null
  workLocation?: string | null
  projectId?: string | null
  taskCode?: string | null
  quantities: QuantityView[]
  findings: FindingView[]
}

export interface MonthView {
  timesheetId: string
  year: number
  month: number
  status: 'DRAFT' | 'SUBMITTED' | 'PENDING_HR' | 'RETURNED' | 'APPROVED' | 'LOCKED' | 'REOPENED'
  editable: boolean
  submittedAt?: string | null
  submittedBy?: string | null
  employeeComment?: string | null
  days: DayView[]
  totals: Record<string, number>
  categories: CategoryOption[]
  findings: FindingView[]
  submittable: boolean
  /** V322 — permitted work locations; empty = free text. */
  workLocations: string[]
  /** V322 — selectable projects for the Cost Code column. */
  projects: ProjectOption[]
  /** V322 — minutes the payable overtime figure is rounded to; 0 = none. */
  overtimeRoundingMinutes: number
  /** Who the month is waiting on, by name. Null while it is still yours, or
      when it is with HR as a group rather than one person. */
  pendingWithName?: string | null
}

export interface QuantityInput {
  categoryCode: string
  quantity: number
  overrideReason?: string
}

export interface DayEntryRequest {
  workType: WorkType
  quantities: QuantityInput[]
  note?: string
  varianceExplanation?: string
  /** V322 — where the day was worked. */
  workLocation?: string | null
  /** V322 — cost attribution. */
  projectId?: string | null
  taskCode?: string | null
}

export interface ProjectOption {
  id: string
  code: string
  name: string
}

const base = '/self/timesheets'

export const selfTimesheetApi = {
  month: (year: number, month: number) =>
    api.get<MonthView>(`${base}/${year}/${month}`).then((r) => r.data),

  saveDay: (year: number, month: number, date: string, body: DayEntryRequest) =>
    api.put<MonthView>(`${base}/${year}/${month}/days/${date}`, body).then((r) => r.data),

  /**
   * Save many days at once — what the grid uses.
   *
   * One request, one transaction: a month of rotation lands whole or not at
   * all, instead of 20 requests that can fail halfway and leave the employee
   * reconciling by eye. `entry: null` clears that day.
   */
  saveDays: (year: number, month: number,
             days: { date: string; entry: DayEntryRequest | null }[]) =>
    api.put<MonthView>(`${base}/${year}/${month}/days`, { days }).then((r) => r.data),

  copyPrevious: (year: number, month: number, date: string) =>
    api.post<MonthView>(`${base}/${year}/${month}/days/${date}/copy-previous`).then((r) => r.data),

  clearDay: (year: number, month: number, date: string) =>
    api.delete<MonthView>(`${base}/${year}/${month}/days/${date}`).then((r) => r.data),

  submit: (year: number, month: number, confirmed: boolean, comment?: string) =>
    api.post<MonthView>(`${base}/${year}/${month}/submit`, { confirmed, comment })
      .then((r) => r.data),

  recall: (year: number, month: number) =>
    api.post<MonthView>(`${base}/${year}/${month}/recall`).then((r) => r.data),
}

/** Categories the employee may type for a work type, in display order. */
export function enterableCategories(all: CategoryOption[], workType?: WorkType | null): CategoryOption[] {
  if (!workType) return []
  return all
    .filter((c) => !c.derived)
    .filter((c) => c.appliesTo.length === 0 || c.appliesTo.includes(workType))
    .sort((a, b) => a.displayOrder - b.displayOrder)
}
