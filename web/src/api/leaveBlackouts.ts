// M123 — Leave blackout windows admin client + leave-form preview hook.

import { api } from './client'

export type BlackoutScope = 'GLOBAL' | 'ORG_UNIT' | 'LEAVE_TYPE'
export type BlackoutSeverity = 'BLOCK' | 'REQUIRES_APPROVAL'

export interface BlackoutResponse {
  id: string
  name: string
  description?: string | null
  scope: BlackoutScope
  orgUnitId?: string | null
  orgUnitName?: string | null
  leaveTypeId?: string | null
  leaveTypeCode?: string | null
  startDate: string
  endDate: string
  severity: BlackoutSeverity
  reason?: string | null
  active: boolean
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy?: string | null
}

export interface BlackoutRequest {
  name: string
  description?: string | null
  scope: BlackoutScope
  orgUnitId?: string | null
  leaveTypeId?: string | null
  startDate: string
  endDate: string
  severity: BlackoutSeverity
  reason?: string | null
  active?: boolean | null
}

export interface PreviewRequest {
  employeeId: string
  leaveTypeId: string
  startDate: string
  endDate: string
}

export interface PreviewMatch {
  id: string
  name: string
  scope: BlackoutScope
  severity: BlackoutSeverity
  startDate: string
  endDate: string
  reason?: string | null
}

export interface PreviewResponse {
  worstSeverity?: BlackoutSeverity | null
  blockMessage?: string | null
  matches: PreviewMatch[]
}

export const leaveBlackoutsApi = {
  list: () => api.get<BlackoutResponse[]>('/leave/blackouts').then((r) => r.data),
  get: (id: string) =>
    api.get<BlackoutResponse>(`/leave/blackouts/${id}`).then((r) => r.data),
  create: (body: BlackoutRequest) =>
    api.post<BlackoutResponse>('/leave/blackouts', body).then((r) => r.data),
  update: (id: string, body: BlackoutRequest) =>
    api.put<BlackoutResponse>(`/leave/blackouts/${id}`, body).then((r) => r.data),
  delete: (id: string) =>
    api.delete<void>(`/leave/blackouts/${id}`).then((r) => r.data),
  preview: (body: PreviewRequest) =>
    api.post<PreviewResponse>('/leave/blackouts/preview', body).then((r) => r.data),
}
