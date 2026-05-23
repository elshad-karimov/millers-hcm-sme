import { api } from './client'
import type { PageResponse } from './employees'

export type PermissionRequestStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'

export interface PermissionType {
  id: string
  code: string
  name: string
  description?: string | null
  annualLimitHours?: number | null
  paid: boolean
  requiresAttachment: boolean
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface PermissionTypeRequest {
  code: string
  name: string
  description?: string
  annualLimitHours?: number
  paid?: boolean
  requiresAttachment?: boolean
  active?: boolean
}

export interface PermissionBalance {
  id: string
  employeeId: string
  permissionTypeId: string
  year: number
  limitHours: number
  adjustmentHours: number
  usedHours: number
  reservedHours: number
  remainingHours: number
  lastRecalculatedAt: string
}

export interface PermissionBalanceAdjustment {
  employeeId: string
  permissionTypeId: string
  year: number
  deltaHours: number
  reason: string
}

export interface PermissionRequest {
  id: string
  requestNo: string
  employeeId: string
  permissionTypeId: string
  permissionDate: string
  startTime?: string | null
  endTime?: string | null
  durationHours: number
  reason?: string | null
  attachmentUrl?: string | null
  status: PermissionRequestStatus
  workflowInstanceId?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
}

export interface PermissionSubmitRequest {
  employeeId: string
  permissionTypeId: string
  permissionDate: string
  startTime?: string
  endTime?: string
  durationHours?: number
  reason?: string
  attachmentUrl?: string
}

export const permissionApi = {
  // Types
  types: (activeOnly = false) =>
    api
      .get<PermissionType[]>('/permission/types', { params: { activeOnly } })
      .then((r) => r.data),
  getType: (id: string) =>
    api.get<PermissionType>(`/permission/types/${id}`).then((r) => r.data),
  createType: (payload: PermissionTypeRequest) =>
    api.post<PermissionType>('/permission/types', payload).then((r) => r.data),
  updateType: (id: string, payload: PermissionTypeRequest) =>
    api.put<PermissionType>(`/permission/types/${id}`, payload).then((r) => r.data),

  // Balances
  balances: (params: { employeeId?: string; year?: number }) =>
    api
      .get<PermissionBalance[]>('/permission/balances', { params })
      .then((r) => r.data),
  adjustBalance: (payload: PermissionBalanceAdjustment) =>
    api.post<PermissionBalance>('/permission/balances/adjust', payload).then((r) => r.data),

  // Requests
  requests: (params: {
    employeeId?: string
    status?: PermissionRequestStatus
    page?: number
    size?: number
  }) =>
    api
      .get<PageResponse<PermissionRequest>>('/permission/requests', { params })
      .then((r) => r.data),
  getRequest: (id: string) =>
    api.get<PermissionRequest>(`/permission/requests/${id}`).then((r) => r.data),
  submit: (payload: PermissionSubmitRequest) =>
    api.post<PermissionRequest>('/permission/requests/submit', payload).then((r) => r.data),
}
