import { api } from './client'
import type { PageResponse } from './employees'

export type ScheduleType =
  | 'FIVE_DAY'
  | 'SIX_DAY'
  | 'SHIFT'
  | 'FLEXIBLE'
  | 'ROTATIONAL'
  | 'NIGHT'
  | 'PART_TIME'

export type EventType = 'IN' | 'OUT'

export type SummaryStatus =
  | 'PRESENT'
  | 'PARTIAL'
  | 'ABSENT'
  | 'NON_WORKING_DAY'
  | 'NO_SCHEDULE'

export interface WorkSchedule {
  id: string
  code: string
  name: string
  scheduleType: ScheduleType
  workStart: string // HH:mm:ss
  workEnd: string
  breakMinutes: number
  gracePeriodMinutes: number
  workDays: string // 7-char bit string Mon..Sun
  overtimeThresholdMinutes?: number | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface WorkScheduleRequest {
  code: string
  name: string
  scheduleType: ScheduleType
  workStart: string
  workEnd: string
  breakMinutes?: number
  gracePeriodMinutes?: number
  workDays: string
  overtimeThresholdMinutes?: number
  active?: boolean
}

export interface ScheduleAssignment {
  id: string
  employeeId: string
  scheduleId: string
  effectiveFrom: string
  effectiveTo?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface AttendanceEvent {
  id: string
  employeeId: string
  deviceEmployeeCode?: string | null
  eventTime: string
  eventType: EventType
  deviceId?: string | null
  location?: string | null
  source: string
  importedAt: string
}

export interface AttendanceEventRequest {
  employeeId?: string
  employeeNo?: string
  deviceEmployeeCode?: string
  eventTime: string
  eventType: EventType
  deviceId?: string
  location?: string
}

export type SummarySource = 'SCHEDULE' | 'ROSTER' | 'NONE'

export interface DailySummary {
  id: string
  employeeId: string
  workDate: string
  scheduleId?: string | null
  /** M112 — populated when source === 'ROSTER'. */
  shiftId?: string | null
  /** M112 — where the scheduled window came from. */
  source: SummarySource
  scheduleStart?: string | null
  scheduleEnd?: string | null
  entryTime?: string | null
  exitTime?: string | null
  rawEventCount: number
  workedMinutes: number
  lateMinutes: number
  earlyMinutes: number
  breakMinutes: number
  overtimeMinutes: number
  status: SummaryStatus
  correctionReason?: string | null
  correctedBy?: string | null
  correctedAt?: string | null
  computedAt: string
}

export interface SummaryCorrectionRequest {
  reason: string
  workedMinutes?: number
  lateMinutes?: number
  earlyMinutes?: number
  breakMinutes?: number
  overtimeMinutes?: number
  status?: SummaryStatus
}

export interface CsvImportResult {
  imported: number
  errors: string[]
}

// ── Attendance Policy (M326) ──────────────────────────────────────────────

export type EarlyLeaveTreatment = 'SALARY_DEDUCTION' | 'LEAVE_BALANCE' | 'VIOLATION' | 'IGNORE'
export type AbsenceTreatment = 'UNPAID' | 'WARNING' | 'LEAVE_DEDUCTION'
export type RoundingDirection = 'NEAREST' | 'UP' | 'DOWN'

export interface AttendancePolicy {
  id: string
  code: string
  name: string
  description?: string
  departmentId?: string
  locationId?: string
  employmentType?: string
  clockInGraceMinutes: number
  clockOutGraceMinutes: number
  lateDeductionEnabled: boolean
  lateMaxBeforeHalfDayMinutes: number
  lateMonthlyThresholdMinutes: number
  earlyLeaveDeductionEnabled: boolean
  earlyLeaveTreatment: EarlyLeaveTreatment
  earlyLeaveToleranceMinutes: number
  absenceDeductionEnabled: boolean
  absenceDailyDivisor: number
  hoursPerDay: number
  unauthorizedAbsenceTreatment: AbsenceTreatment
  overtimeRequiresPreapproval: boolean
  overtimeDeptHeadThresholdMinutes: number
  compOffEnabled: boolean
  autoBreakDeductionEnabled: boolean
  breakMinutes: number
  minHoursForBreakDeduction: string
  roundingEnabled: boolean
  roundingMinutes: number
  roundingDirection: RoundingDirection
  active: boolean
  createdAt: string
  updatedAt: string
}

export type AttendancePolicyRequest = Omit<AttendancePolicy, 'id' | 'createdAt' | 'updatedAt'>

export const attendanceApi = {
  // Schedules
  schedules: () => api.get<WorkSchedule[]>('/attendance/schedules').then((r) => r.data),
  createSchedule: (payload: WorkScheduleRequest) =>
    api.post<WorkSchedule>('/attendance/schedules', payload).then((r) => r.data),
  updateSchedule: (id: string, payload: WorkScheduleRequest) =>
    api.put<WorkSchedule>(`/attendance/schedules/${id}`, payload).then((r) => r.data),

  // Assignments
  assignments: (employeeId: string) =>
    api
      .get<ScheduleAssignment[]>('/attendance/assignments', { params: { employeeId } })
      .then((r) => r.data),
  assign: (payload: {
    employeeId: string
    scheduleId: string
    effectiveFrom: string
    effectiveTo?: string
  }) => api.post<ScheduleAssignment>('/attendance/assignments', payload).then((r) => r.data),

  // Events
  events: (params: {
    fromDate: string
    toDate: string
    employeeId?: string
    page?: number
    size?: number
  }) =>
    api
      .get<PageResponse<AttendanceEvent>>('/attendance/events', { params })
      .then((r) => r.data),
  ingest: (payload: AttendanceEventRequest) =>
    api.post<AttendanceEvent>('/attendance/events', payload).then((r) => r.data),
  importCsv: (file: File) => {
    const data = new FormData()
    data.append('file', file)
    return api
      .post<CsvImportResult>('/attendance/events/csv', data, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },

  // Summary
  summary: (params: { fromDate: string; toDate: string; employeeId?: string }) =>
    api.get<DailySummary[]>('/attendance/summary', { params }).then((r) => r.data),
  runEngine: (payload: { fromDate: string; toDate: string; employeeId?: string }) =>
    api
      .post<{ employeesProcessed: number; summariesWritten: number }>(
        '/attendance/summary/run',
        payload,
      )
      .then((r) => r.data),
  correct: (id: string, payload: SummaryCorrectionRequest) =>
    api.post<DailySummary>(`/attendance/summary/${id}/correct`, payload).then((r) => r.data),

  // Attendance Policies (M326)
  policies: () => api.get<AttendancePolicy[]>('/attendance/policies').then((r) => r.data),
  policy: (id: string) => api.get<AttendancePolicy>(`/attendance/policies/${id}`).then((r) => r.data),
  resolvePolicy: (employeeId: string) =>
    api.get<AttendancePolicy | null>('/attendance/policies/resolve', { params: { employeeId } })
       .then((r) => r.data)
       .catch(() => null),
  createPolicy: (payload: AttendancePolicyRequest) =>
    api.post<AttendancePolicy>('/attendance/policies', payload).then((r) => r.data),
  updatePolicy: (id: string, payload: AttendancePolicyRequest) =>
    api.put<AttendancePolicy>(`/attendance/policies/${id}`, payload).then((r) => r.data),
  deactivatePolicy: (id: string) =>
    api.delete<void>(`/attendance/policies/${id}/deactivate`).then((r) => r.data),
  activatePolicy: (id: string) =>
    api.post<void>(`/attendance/policies/${id}/activate`).then((r) => r.data),
}
