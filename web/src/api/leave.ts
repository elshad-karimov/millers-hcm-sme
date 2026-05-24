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
}
