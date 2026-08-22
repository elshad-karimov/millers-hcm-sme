import { api } from './client'
import type { FindingView, QuantityView } from './selfTimesheet'

/**
 * Manager approval and HR period control.
 *
 * Hours and quantities only — no endpoint behind this module returns pay.
 * Hierarchy scoping happens server-side: a manager's request simply comes back
 * without employees outside their line.
 */

export type TimesheetStatus =
  | 'DRAFT' | 'SUBMITTED'
  /** V324 — manager approved, awaiting HR sign-off. */
  | 'PENDING_HR'
  | 'RETURNED' | 'APPROVED' | 'LOCKED' | 'REOPENED'
export type DayApprovalState = 'PENDING' | 'APPROVED' | 'RETURNED'

export interface QueueRow {
  timesheetId: string
  employeeId: string
  employeeNo?: string | null
  employeeName?: string | null
  positionTitle?: string | null
  year: number
  month: number
  status: TimesheetStatus
  totalHours: number
  overtimeHours: number
  daysEntered: number
  daysReturned: number
  warnings: number
  blockingIssues: number
  cleanForBulkApproval: boolean
  submittedAt?: string | null
}

export interface ReviewDay {
  dayId: string
  date: string
  dayOfWeek: string
  workType?: string | null
  entrySource?: string | null
  approvalState: DayApprovalState
  returnReason?: string | null
  holiday: boolean
  enteredHours: number
  attendanceHours?: number | null
  varianceHours?: number | null
  varianceExplanation?: string | null
  employeeNote?: string | null
  /** Where the day was worked — drives the offshore/quayside rate. */
  workLocation?: string | null
  /** Project the day is booked to, already resolved to "CODE — Name". */
  project?: string | null
  quantities: QuantityView[]
  findings: FindingView[]
}

export interface CorrectionView {
  id: string
  timesheetId: string
  employeeId: string
  employeeName?: string | null
  workDate: string
  currentValue?: string | null
  requestedValue: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  requestedBy: string
  requestedAt: string
  decidedBy?: string | null
  decidedAt?: string | null
  decisionNote?: string | null
}

export interface ReviewView {
  timesheetId: string
  employeeId: string
  employeeNo?: string | null
  employeeName?: string | null
  positionTitle?: string | null
  year: number
  month: number
  status: TimesheetStatus
  actionable: boolean
  notActionableReason?: string | null
  submittedAt?: string | null
  employeeComment?: string | null
  totalEnteredHours: number
  totalAttendanceHours: number
  totalVarianceHours: number
  totals: Record<string, number>
  days: ReviewDay[]
  findings: FindingView[]
  corrections: CorrectionView[]
}

export interface BulkApproveResult {
  approved: string[]
  skipped: Record<string, string>
}

export interface ControlRow {
  timesheetId: string
  employeeId: string
  employeeNo?: string | null
  employeeName?: string | null
  status: TimesheetStatus
  totalHours: number
  warnings: number
  blockingIssues: number
  exception?: string | null
  payrollReady: boolean
}

export interface ControlBoard {
  year: number
  month: number
  periodStatus: 'OPEN' | 'LOCKED'
  lockedAt?: string | null
  lockedBy?: string | null
  employees: number
  draft: number
  submitted: number
  returned: number
  approved: number
  locked: number
  payrollReady: number
  lockable: boolean
  lockBlockedReason?: string | null
  rows: ControlRow[]
}

export const timesheetApprovalApi = {
  queue: (year: number, month: number, status?: TimesheetStatus) =>
    api.get<QueueRow[]>('/manager/timesheets', { params: { year, month, status } })
      .then((r) => r.data),

  review: (id: string) =>
    api.get<ReviewView>(`/manager/timesheets/${id}`).then((r) => r.data),

  approve: (id: string, comment?: string) =>
    api.post<ReviewView>(`/manager/timesheets/${id}/approve`, { comment }).then((r) => r.data),

  /** Named days go back; every other day stays approved. */
  returnDays: (id: string, dates: string[], reason: string) =>
    api.post<ReviewView>(`/manager/timesheets/${id}/return`, { dates, reason }).then((r) => r.data),

  reject: (id: string, reason: string) =>
    api.post<ReviewView>(`/manager/timesheets/${id}/reject`, { reason }).then((r) => r.data),

  bulkApprove: (timesheetIds: string[], comment?: string) =>
    api.post<BulkApproveResult>('/manager/timesheets/bulk-approve', { timesheetIds, comment })
      .then((r) => r.data),

  pendingCorrections: () =>
    api.get<CorrectionView[]>('/manager/timesheets/corrections/pending').then((r) => r.data),

  decideCorrection: (id: string, approve: boolean, note?: string) =>
    api.post<CorrectionView>(`/manager/timesheets/corrections/${id}/decide`, { approve, note })
      .then((r) => r.data),
}

export const timesheetControlApi = {
  board: (year: number, month: number) =>
    api.get<ControlBoard>(`/timesheets/control/${year}/${month}`).then((r) => r.data),

  lock: (year: number, month: number, reason?: string) =>
    api.post<ControlBoard>(`/timesheets/control/${year}/${month}/lock`, { reason })
      .then((r) => r.data),

  unlock: (year: number, month: number, reason: string) =>
    api.post<ControlBoard>(`/timesheets/control/${year}/${month}/unlock`, { reason })
      .then((r) => r.data),
}
