import { api } from './client'

export type TimesheetStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'LOCKED'
  | 'REOPENED'

export type TimesheetCode = 'W' | 'L' | 'S' | 'BT' | 'P' | 'O' | 'H' | 'A'

export interface TimesheetDay {
  id: string
  workDate: string
  primaryCode: TimesheetCode
  workedHours: number
  overtimeHours: number
  breakHours: number
  lateMinutes: number
  earlyMinutes: number
  sourceSummaryId?: string | null
  leaveRequestId?: string | null
  btRequestId?: string | null
  permissionRequestId?: string | null
  anomalies?: string | null
  correctionReason?: string | null
  correctedBy?: string | null
  correctedAt?: string | null
  projectId?: string | null
  taskCode?: string | null
  billable?: boolean | null
}

export interface Timesheet {
  id: string
  employeeId: string
  periodYear: number
  periodMonth: number
  status: TimesheetStatus
  workflowInstanceId?: string | null
  totalWorkedHours: number
  totalOvertimeHours: number
  totalLeaveDays: number
  totalSickDays: number
  totalBtDays: number
  totalPermissionHrs: number
  totalAbsentDays: number
  generatedAt: string
  generatedBy?: string | null
  submittedAt?: string | null
  submittedBy?: string | null
  approvedAt?: string | null
  approvedBy?: string | null
  lockedAt?: string | null
  days: TimesheetDay[]
}

export interface DayCorrectionRequest {
  primaryCode?: TimesheetCode
  workedHours?: number
  overtimeHours?: number
  projectId?: string
  taskCode?: string
  billable?: boolean
  reason: string
}

export const timesheetApi = {
  listForPeriod: (year: number, month: number) =>
    api.get<Timesheet[]>('/timesheets', { params: { year, month } }).then((r) => r.data),
  get: (id: string) => api.get<Timesheet>(`/timesheets/${id}`).then((r) => r.data),
  generate: (employeeId: string, periodYear: number, periodMonth: number) =>
    api
      .post<Timesheet>('/timesheets/generate', { employeeId, periodYear, periodMonth })
      .then((r) => r.data),
  submit: (id: string) =>
    api.post<Timesheet>(`/timesheets/${id}/submit`).then((r) => r.data),
  correctDay: (id: string, dayId: string, payload: DayCorrectionRequest) =>
    api
      .post<TimesheetDay>(`/timesheets/${id}/days/${dayId}/correct`, payload)
      .then((r) => r.data),
}
