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

// ── M328 — Corrections ────────────────────────────────────────────────────
export interface AttendanceCorrection {
  id: string
  employeeId: string
  workDate: string
  summaryId: string
  requestedClockIn?: string
  requestedClockOut?: string
  requestedStatus?: string
  reason: string
  correctionType: string
  absenceStatusChanged: boolean
  overtimeDeltaMinutes: number
  workflowStatus: string
  decision?: string
  decisionComment?: string
  decidedAt?: string
  decidedBy?: string
  createdAt: string
  createdBy?: string
}

export interface CorrectionRequest {
  employeeId: string
  workDate: string
  summaryId: string
  requestedClockIn?: string
  requestedClockOut?: string
  requestedStatus?: string
  reason: string
}

// ── M329 — Overtime Requests ──────────────────────────────────────────────
export interface OvertimeRequest {
  id: string
  employeeId: string
  workDate: string
  requestedMinutes: number
  reason: string
  type: string
  workflowStatus: string
  decision?: string
  decisionComment?: string
  decidedAt?: string
  decidedBy?: string
  createdAt: string
}

export interface OvertimeRequestPayload {
  employeeId: string
  workDate: string
  requestedMinutes: number
  reason: string
  type?: string
}

// ── M330 — Periods ────────────────────────────────────────────────────────
export interface AttendancePeriod {
  id: string
  year: number
  month: number
  status: 'OPEN' | 'LOCKED' | 'CLOSED'
  lockedAt?: string
  lockedBy?: string
  employeeCountAtLock?: number
  notes?: string
  unlockedAt?: string
  unlockedBy?: string
}

// ── M332 — Exceptions ─────────────────────────────────────────────────────
export interface AttendanceException {
  id: string
  employeeId: string
  workDate: string
  exceptionType: string
  severity: string
  thresholdMinutes: number
  actualMinutes: number
  status: string
  acknowledgedBy?: string
  resolvedBy?: string
  notes?: string
}

export interface ExceptionConfig {
  id: string
  exceptionType: string
  severity: string
  thresholdMinutes: number
  enabled: boolean
  autoNotify: boolean
}

// ── M333 — Devices ────────────────────────────────────────────────────────
export interface AttendanceDevice {
  id: string
  code: string
  name: string
  deviceType: string
  locationId?: string
  ipAddress?: string
  serialNumber?: string
  active: boolean
  lastSeenAt?: string
}

export interface DeviceRequest {
  code: string
  name: string
  deviceType: string
  locationId?: string
  ipAddress?: string
  serialNumber?: string
}

// ── M335 — Workspace ──────────────────────────────────────────────────────
export interface AttendanceWorkspace {
  date: string
  totalEmployees: number
  presentCount: number
  absentCount: number
  lateCount: number
  missingClockOutCount: number
  pendingCorrections: number
  pendingOvertimeRequests: number
  openExceptions: number
  lateEmployees: any[]
  absentEmployees: any[]
}

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

  // M328 — Corrections
  corrections: () => api.get<AttendanceCorrection[]>('/attendance/corrections').then((r) => r.data),
  submitCorrection: (payload: CorrectionRequest) =>
    api.post<AttendanceCorrection>('/attendance/corrections', payload).then((r) => r.data),
  approveCorrection: (id: string, comment?: string) =>
    api.post<void>(`/attendance/corrections/${id}/approve`, { comment }).then((r) => r.data),
  rejectCorrection: (id: string, comment: string) =>
    api.post<void>(`/attendance/corrections/${id}/reject`, { comment }).then((r) => r.data),

  // M329 — Overtime Requests
  overtimeRequests: () =>
    api.get<OvertimeRequest[]>('/attendance/overtime-requests').then((r) => r.data),
  submitOvertimeRequest: (payload: OvertimeRequestPayload) =>
    api.post<OvertimeRequest>('/attendance/overtime-requests', payload).then((r) => r.data),
  approveOvertimeRequest: (id: string, comment?: string) =>
    api.post<void>(`/attendance/overtime-requests/${id}/approve`, { comment }).then((r) => r.data),
  rejectOvertimeRequest: (id: string, comment: string) =>
    api.post<void>(`/attendance/overtime-requests/${id}/reject`, { comment }).then((r) => r.data),

  // M330 — Periods
  periods: () => api.get<AttendancePeriod[]>('/attendance/periods').then((r) => r.data),
  period: (year: number, month: number) =>
    api.get<AttendancePeriod>(`/attendance/periods/${year}/${month}`).then((r) => r.data),
  lockPeriod: (year: number, month: number, notes?: string) =>
    api.post<void>(`/attendance/periods/${year}/${month}/lock`, { notes }).then((r) => r.data),
  unlockPeriod: (year: number, month: number) =>
    api.post<void>(`/attendance/periods/${year}/${month}/unlock`).then((r) => r.data),

  // M332 — Exceptions
  exceptions: (status?: string) =>
    api.get<AttendanceException[]>('/attendance/exceptions', { params: { status } }).then((r) => r.data),
  exceptionConfigs: () =>
    api.get<ExceptionConfig[]>('/attendance/exceptions/configs').then((r) => r.data),
  acknowledgeException: (id: string) =>
    api.post<void>(`/attendance/exceptions/${id}/acknowledge`).then((r) => r.data),
  resolveException: (id: string, notes?: string) =>
    api.post<void>(`/attendance/exceptions/${id}/resolve`, { notes }).then((r) => r.data),
  updateExceptionConfig: (id: string, payload: Partial<ExceptionConfig>) =>
    api.put<ExceptionConfig>(`/attendance/exceptions/configs/${id}`, payload).then((r) => r.data),

  // M333 — Devices
  devices: () => api.get<AttendanceDevice[]>('/attendance/devices').then((r) => r.data),
  device: (id: string) => api.get<AttendanceDevice>(`/attendance/devices/${id}`).then((r) => r.data),
  createDevice: (payload: DeviceRequest) =>
    api.post<AttendanceDevice>('/attendance/devices', payload).then((r) => r.data),
  updateDevice: (id: string, payload: DeviceRequest) =>
    api.put<AttendanceDevice>(`/attendance/devices/${id}`, payload).then((r) => r.data),
  deactivateDevice: (id: string) =>
    api.delete<void>(`/attendance/devices/${id}/deactivate`).then((r) => r.data),
  activateDevice: (id: string) =>
    api.post<void>(`/attendance/devices/${id}/activate`).then((r) => r.data),

  // M335 — Workspace
  workspace: (date: string, departmentId?: string) =>
    api.get<AttendanceWorkspace>('/attendance/workspace', { params: { date, departmentId } })
       .then((r) => r.data),

  // M336 — Reports
  attendanceReport: (type: string, from: string, to: string, departmentId?: string) =>
    api.get<any[]>(`/reports/attendance/${type}`, { params: { from, to, departmentId } })
       .then((r) => r.data),
}
