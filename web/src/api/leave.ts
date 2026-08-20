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

export type LeaveUnit = 'DAYS' | 'HALF_DAY' | 'HOURS'

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
  carryForwardExpiryMonths?: number | null
  maxConsecutiveDays?: number | null
  excludeWeekends: boolean
  excludeHolidays: boolean
  active: boolean
  accruesMonthly: boolean
  monthlyAccrualDays?: number | null
  /** Seniority bracket schedule; empty array when none configured (M47). */
  seniorityBrackets: SeniorityBracket[]
  /**
   * M151 — when true this type's annual entitlement is resolved from itemised
   * components (base + statutory uplifts) and the monthly accrual chain is
   * skipped for it. Only these types have an entitlement breakdown.
   */
  entitlementComponentsEnabled?: boolean
  negativeBalanceAllowed: boolean
  maxNegativeDays?: number | null
  /** M341: How this leave type is measured — DAYS (default), HALF_DAY, or HOURS. */
  leaveUnit: LeaveUnit
  /** M341: Working hours per day for HOURS-unit types. */
  hoursPerDay?: number | null
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
  carryForwardExpiryMonths?: number | null
  maxConsecutiveDays?: number
  excludeWeekends?: boolean
  excludeHolidays?: boolean
  active?: boolean
  accruesMonthly?: boolean
  monthlyAccrualDays?: number
  /** Optional seniority bracket schedule (M47). */
  seniorityBrackets?: SeniorityBracket[]
  negativeBalanceAllowed?: boolean
  maxNegativeDays?: number | null
  /** M341: Leave unit — DAYS (default), HALF_DAY, or HOURS. */
  leaveUnit?: LeaveUnit
  /** M341: Working hours per day for HOURS-unit types. */
  hoursPerDay?: number | null
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
  carryForwardExpiresAt?: string | null
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
  /** M341: For HOURS-unit types — start time (HH:mm:ss). */
  startTime?: string | null
  /** M341: For HOURS-unit types — end time (HH:mm:ss). */
  endTime?: string | null
  /** M341: For HOURS-unit types — gross hours requested. */
  durationHours?: number | null
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
  /** M341: For HOURS-unit leave types — start time (HH:mm). */
  startTime?: string
  /** M341: For HOURS-unit leave types — end time (HH:mm). */
  endTime?: string
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

  // M342 — Period Locks
  periodLocks: () => api.get<LeavePeriodLock[]>('/leave/period-locks').then((r) => r.data),
  createPeriodLock: (payload: LeavePeriodLockRequest) =>
    api.post<LeavePeriodLock>('/leave/period-locks', payload).then((r) => r.data),
  updatePeriodLock: (id: string, payload: LeavePeriodLockRequest) =>
    api.put<LeavePeriodLock>(`/leave/period-locks/${id}`, payload).then((r) => r.data),
  deletePeriodLock: (id: string) =>
    api.delete<void>(`/leave/period-locks/${id}`).then((r) => r.data),

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

  // M347 — Workspace analytics
  workspaceStats: (year: number) =>
    api.get('/leave/workspace/stats', { params: { year } }).then((r) => r.data),

  // M348 — Liability report
  liabilityReport: (year: number, workingDaysPerMonth = 22) =>
    api.get('/leave/reports/liability', { params: { year, workingDaysPerMonth } }).then((r) => r.data),

  // M346 — Delegation
  delegations: (requestId: string) =>
    api.get<LeaveDelegation[]>(`/leave/requests/${requestId}/delegations`).then((r) => r.data),
  pendingDelegations: (delegateId: string) =>
    api.get<LeaveDelegation[]>('/leave/delegations/pending', { params: { delegateId } }).then((r) => r.data),
  createDelegation: (requestId: string, payload: LeaveDelegationRequest) =>
    api.post<LeaveDelegation>(`/leave/requests/${requestId}/delegations`, payload).then((r) => r.data),
  acceptDelegation: (id: string, notes?: string) =>
    api.post<LeaveDelegation>(`/leave/delegations/${id}/accept`, null, { params: { notes } }).then((r) => r.data),
  declineDelegation: (id: string, notes?: string) =>
    api.post<LeaveDelegation>(`/leave/delegations/${id}/decline`, null, { params: { notes } }).then((r) => r.data),
  revokeDelegation: (id: string) =>
    api.delete<LeaveDelegation>(`/leave/delegations/${id}`).then((r) => r.data),

  // M343 — Unpaid Leave → Payroll Bridge
  syncUnpaidDeductions: (year: number, month: number, workingDaysPerMonth = 22) =>
    api.post('/leave/unpaid-deductions/sync', null, { params: { year, month, workingDaysPerMonth } }).then((r) => r.data),
  listUnpaidDeductions: (employeeId: string) =>
    api.get('/leave/unpaid-deductions', { params: { employeeId } }).then((r) => r.data),

  // M344 — Leave Encashment
  listEncashments: (employeeId: string) =>
    api.get('/leave/encashments', { params: { employeeId } }).then((r) => r.data),
  createEncashment: (params: {
    employeeId: string; leaveTypeId: string; year: number; days: number;
    payrollYear: number; payrollMonth: number; notes?: string
  }) => api.post('/leave/encashments', null, { params }).then((r) => r.data),
  reverseEncashment: (id: string) =>
    api.post(`/leave/encashments/${id}/reverse`).then((r) => r.data),
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

// ── M342 — Leave Period Locks ───────────────────────────────────────────

export interface LeavePeriodLock {
  id: string
  leaveTypeId?: string | null
  periodStart: string
  periodEnd: string
  reason?: string | null
  lockedBy?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface LeavePeriodLockRequest {
  periodStart: string
  periodEnd: string
  leaveTypeId?: string | null
  reason?: string
  active?: boolean
}

// ── M346 — Leave Delegation ─────────────────────────────────────────────

export type DelegationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED'

export interface LeaveDelegation {
  id: string
  leaveRequestId: string
  delegatorId: string
  delegatorName?: string | null
  delegateId: string
  delegateName?: string | null
  delegationScope?: string | null
  status: DelegationStatus
  delegateNotes?: string | null
  respondedAt?: string | null
  createdAt: string
}

export interface LeaveDelegationRequest {
  delegateId: string
  delegationScope?: string
}

export const EMPLOYMENT_TYPES = [
  'PERMANENT',
  'FIXED_TERM',
  'PART_TIME',
  'PROBATIONARY',
  'CONTRACTOR',
  'INTERN',
] as const
