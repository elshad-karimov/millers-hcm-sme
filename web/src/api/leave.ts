import { api } from './client'
import type { PageResponse } from './employees'

export type LeaveRequestStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'

/** One tier in a seniority-based leave entitlement schedule (M47). */
export interface SeniorityBracket {
  yearsMin: number
  yearsMax?: number | null
  annualDays: number
}

export interface LeaveType {
  id: string
  code: string
  name: string
  description?: string | null
  categoryId?: string | null
  paid: boolean
  requiresAttachment: boolean
  requiresReplacement: boolean
  defaultAnnualEntitlementDays?: number | null
  carryForwardLimitDays?: number | null
  maxConsecutiveDays?: number | null
  excludeWeekends: boolean
  excludeHolidays: boolean
  active: boolean
  accruesMonthly: boolean
  monthlyAccrualDays?: number | null
  /** Seniority bracket schedule; empty array when none configured (M47). */
  seniorityBrackets: SeniorityBracket[]
  createdAt: string
  updatedAt: string
}

export interface LeaveTypeRequest {
  code: string
  name: string
  description?: string
  paid?: boolean
  requiresAttachment?: boolean
  requiresReplacement?: boolean
  defaultAnnualEntitlementDays?: number
  carryForwardLimitDays?: number
  maxConsecutiveDays?: number
  excludeWeekends?: boolean
  excludeHolidays?: boolean
  active?: boolean
  accruesMonthly?: boolean
  monthlyAccrualDays?: number
  /** Optional seniority bracket schedule (M47). */
  seniorityBrackets?: SeniorityBracket[]
}

export interface LeaveBalance {
  id: string
  employeeId: string
  leaveTypeId: string
  year: number
  entitlementDays: number
  carriedForwardDays: number
  adjustmentDays: number
  usedDays: number
  reservedDays: number
  remainingDays: number
  lastRecalculatedAt: string
}

export interface LeaveBalanceAdjustment {
  employeeId: string
  leaveTypeId: string
  year: number
  deltaDays: number
  reason: string
}

export interface LeaveRequest {
  id: string
  requestNo: string
  employeeId: string
  leaveTypeId: string
  startDate: string
  endDate: string
  halfDay: boolean
  totalDays: number
  reason?: string | null
  attachmentUrl?: string | null
  replacementEmployeeId?: string | null
  status: LeaveRequestStatus
  workflowInstanceId?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
}

export interface LeaveSubmitRequest {
  employeeId: string
  leaveTypeId: string
  startDate: string
  endDate: string
  halfDay?: boolean
  reason?: string
  replacementEmployeeId?: string
  attachmentUrl?: string
}

export const leaveApi = {
  // Types
  types: (activeOnly = false) =>
    api
      .get<LeaveType[]>('/leave/types', { params: { activeOnly } })
      .then((r) => r.data),
  getType: (id: string) => api.get<LeaveType>(`/leave/types/${id}`).then((r) => r.data),
  createType: (payload: LeaveTypeRequest) =>
    api.post<LeaveType>('/leave/types', payload).then((r) => r.data),
  updateType: (id: string, payload: LeaveTypeRequest) =>
    api.put<LeaveType>(`/leave/types/${id}`, payload).then((r) => r.data),

  // Balances
  balances: (params: { employeeId?: string; year?: number }) =>
    api.get<LeaveBalance[]>('/leave/balances', { params }).then((r) => r.data),
  adjustBalance: (payload: LeaveBalanceAdjustment) =>
    api.post<LeaveBalance>('/leave/balances/adjust', payload).then((r) => r.data),

  // Requests
  requests: (params: {
    employeeId?: string
    status?: LeaveRequestStatus
    page?: number
    size?: number
  }) =>
    api
      .get<PageResponse<LeaveRequest>>('/leave/requests', { params })
      .then((r) => r.data),
  getRequest: (id: string) =>
    api.get<LeaveRequest>(`/leave/requests/${id}`).then((r) => r.data),
  submit: (payload: LeaveSubmitRequest) =>
    api.post<LeaveRequest>('/leave/requests/submit', payload).then((r) => r.data),

  // M131 — team time-off calendar
  teamCalendar: (params: {
    orgUnitId?: string
    windowStart: string
    windowEnd: string
    thresholdPercent?: number
  }) =>
    api.get<TeamCalendarResponse>('/leave/team-calendar', { params }).then((r) => r.data),

  // M338 — Leave Categories
  categories: () => api.get<LeaveCategory[]>('/leave/categories').then((r) => r.data),
  createCategory: (payload: LeaveCategoryRequest) =>
    api.post<LeaveCategory>('/leave/categories', payload).then((r) => r.data),
  updateCategory: (id: string, payload: LeaveCategoryRequest) =>
    api.put<LeaveCategory>(`/leave/categories/${id}`, payload).then((r) => r.data),
  deactivateCategory: (id: string) =>
    api.delete<void>(`/leave/categories/${id}`).then((r) => r.data),

  // M338 — Balance Ledger
  balanceLedger: (employeeId: string, year: number, leaveTypeId?: string) =>
    api
      .get<LedgerEntry[]>('/leave/balances/ledger', {
        params: { employeeId, year, leaveTypeId },
      })
      .then((r) => r.data),

  // M339 — Entitlement rules
  entitlementRules: (typeId: string) =>
    api.get<LeaveEntitlementRule[]>(`/leave/types/${typeId}/entitlement-rules`).then((r) => r.data),
  createEntitlementRule: (typeId: string, payload: LeaveEntitlementRuleRequest) =>
    api.post<LeaveEntitlementRule>(`/leave/types/${typeId}/entitlement-rules`, payload).then((r) => r.data),
  updateEntitlementRule: (typeId: string, id: string, payload: LeaveEntitlementRuleRequest) =>
    api.put<LeaveEntitlementRule>(`/leave/types/${typeId}/entitlement-rules/${id}`, payload).then((r) => r.data),
  toggleEntitlementRule: (typeId: string, id: string) =>
    api.delete<void>(`/leave/types/${typeId}/entitlement-rules/${id}`).then((r) => r.data),
  resolveEntitlementRule: (typeId: string, employmentType: string, tenureMonths: number) =>
    api
      .get<EntitlementResolveResult>(`/leave/types/${typeId}/entitlement-rules/resolve`, {
        params: { employmentType, tenureMonths },
      })
      .then((r) => r.data),
}

// ── M131 — Team time-off calendar wire types ───────────────────────────

export interface TeamLeaveEntry {
  requestId: string
  employeeId: string
  employeeNo?: string | null
  employeeName?: string | null
  leaveTypeId: string
  leaveTypeName?: string | null
  leaveTypeColor?: string | null
  startDate: string
  endDate: string
  totalDays: number
  halfDay: boolean
  status: LeaveRequestStatus
}

export interface DailyRollup {
  date: string
  outCount: number
  percentOff: number
  flagged: boolean
}

export interface TeamCalendarResponse {
  windowStart: string
  windowEnd: string
  orgUnitId?: string | null
  teamSize: number
  thresholdPercent: number
  entries: TeamLeaveEntry[]
  days: DailyRollup[]
}

// ── M338 — Leave Categories ─────────────────────────────────────────────

export interface LeaveCategory {
  id: string
  code: string
  name: string
  description?: string | null
  paidDefault: boolean
  reportingGroup?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface LeaveCategoryRequest {
  code: string
  name: string
  description?: string
  paidDefault: boolean
  reportingGroup?: string
  active?: boolean
}

// ── M338 — Balance Ledger ───────────────────────────────────────────────

export interface LedgerEntry {
  id: string
  employeeId: string
  leaveTypeId: string
  year: number
  txType: string
  amount: number
  effectiveDate: string
  sourceType?: string | null
  sourceId?: string | null
  balanceAfter: number
  notes?: string | null
  createdBy?: string | null
  createdAt: string
}

// ── M339 — Entitlement rules ────────────────────────────────────────────

export interface LeaveEntitlementRule {
  id: string
  leaveTypeId: string
  employmentType?: string | null
  minTenureMonths?: number | null
  maxTenureMonths?: number | null
  annualEntitlementDays: number
  priority: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface LeaveEntitlementRuleRequest {
  employmentType?: string | null
  minTenureMonths?: number | null
  maxTenureMonths?: number | null
  annualEntitlementDays: number
  priority?: number
  active?: boolean
}

export interface EntitlementResolveResult {
  matched: boolean
  annualEntitlementDays?: number
  monthlyAccrualDays?: number
}

export const EMPLOYMENT_TYPES = [
  'PERMANENT',
  'FIXED_TERM',
  'PART_TIME',
  'PROBATIONARY',
  'CONTRACTOR',
  'INTERN',
] as const
