// M482 — Open shifts + shift swap API client

import { api } from './client'

export interface OpenShift {
  id: string
  tenantId: string
  shiftId: string
  shiftDate: string
  orgUnitId?: string
  slots: number
  filled: number
  status: string
  notes?: string
  createdAt: string
  createdBy?: string
  updatedAt: string
  updatedBy?: string
}

export interface ShiftSwapRequest {
  id: string
  tenantId: string
  requestNo: string
  rosterEntryId: string
  fromEmployeeId: string
  toEmployeeId: string
  status: string
  requestedAt: string
  approvedAt?: string
  approvedBy?: string
  rejectionReason?: string
  notes?: string
}

export interface OpenShiftRequest {
  shiftId: string
  shiftDate: string
  orgUnitId?: string
  slots: number
  notes?: string
}

export interface SwapRequestDto {
  rosterEntryId: string
  fromEmployeeId: string
  toEmployeeId: string
  notes?: string
}

export const openShiftsApi = {
  list: (from?: string, to?: string) =>
    api.get<OpenShift[]>('/api/attendance/scheduling/open-shifts', { params: { from, to } })
      .then(r => r.data),
  get: (id: string) =>
    api.get<OpenShift>(`/api/attendance/scheduling/open-shifts/${id}`)
      .then(r => r.data),
  create: (req: OpenShiftRequest) =>
    api.post<OpenShift>('/api/attendance/scheduling/open-shifts', req)
      .then(r => r.data),
  update: (id: string, req: Partial<OpenShiftRequest>) =>
    api.put<OpenShift>(`/api/attendance/scheduling/open-shifts/${id}`, req)
      .then(r => r.data),
  claim: (id: string, employeeId: string) =>
    api.post(`/api/attendance/scheduling/open-shifts/${id}/claim`, null, { params: { employeeId } }),
}

export const swapRequestsApi = {
  list: (status?: string) =>
    api.get<ShiftSwapRequest[]>('/api/attendance/scheduling/swap-requests', { params: { status } })
      .then(r => r.data),
  get: (id: string) =>
    api.get<ShiftSwapRequest>(`/api/attendance/scheduling/swap-requests/${id}`)
      .then(r => r.data),
  create: (req: SwapRequestDto) =>
    api.post<ShiftSwapRequest>('/api/attendance/scheduling/swap-requests', req)
      .then(r => r.data),
  approve: (id: string) =>
    api.post(`/api/attendance/scheduling/swap-requests/${id}/approve`),
  reject: (id: string, reason: string) =>
    api.post(`/api/attendance/scheduling/swap-requests/${id}/reject`, null, { params: { reason } }),
}

export const SWAP_STATUS_COLOR: Record<string, string> = {
  PENDING: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}
