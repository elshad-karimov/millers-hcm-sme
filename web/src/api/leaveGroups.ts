// M66 — Leave groups + per-(group, type) entitlement overrides.

import { api } from './client'

export interface SeniorityBracket {
  yearsMin: number
  yearsMax?: number | null
  annualDays: number
}

export interface LeaveGroup {
  id: string
  code: string
  name: string
  description?: string | null
  defaultGroup: boolean
  active: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface LeaveGroupRequest {
  code: string
  name: string
  description?: string
  defaultGroup?: boolean
  active?: boolean
}

export interface LeaveGroupEntitlement {
  id: string
  leaveGroupId: string
  leaveTypeId: string
  annualEntitlementDays?: number | null
  monthlyAccrualDays?: number | null
  seniorityBrackets?: SeniorityBracket[] | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface LeaveGroupEntitlementRequest {
  leaveTypeId: string
  annualEntitlementDays?: number
  monthlyAccrualDays?: number
  seniorityBrackets?: SeniorityBracket[]
  notes?: string
}

export const leaveGroupsApi = {
  list: () => api.get<LeaveGroup[]>('/leave/groups').then((r) => r.data),

  get: (id: string) => api.get<LeaveGroup>(`/leave/groups/${id}`).then((r) => r.data),

  create: (payload: LeaveGroupRequest) =>
    api.post<LeaveGroup>('/leave/groups', payload).then((r) => r.data),

  update: (id: string, payload: LeaveGroupRequest) =>
    api.put<LeaveGroup>(`/leave/groups/${id}`, payload).then((r) => r.data),

  listEntitlements: (id: string) =>
    api
      .get<LeaveGroupEntitlement[]>(`/leave/groups/${id}/entitlements`)
      .then((r) => r.data),

  upsertEntitlement: (id: string, payload: LeaveGroupEntitlementRequest) =>
    api
      .put<LeaveGroupEntitlement>(`/leave/groups/${id}/entitlements`, payload)
      .then((r) => r.data),

  deleteEntitlement: (id: string, leaveTypeId: string) =>
    api.delete(`/leave/groups/${id}/entitlements/${leaveTypeId}`),
}
