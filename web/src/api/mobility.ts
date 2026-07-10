// M487 — International assignments (mobility) API client

import { api } from './client'

export interface InternationalAssignment {
  id: string
  tenantId: string
  assignmentNo: string
  employeeId: string
  hostCountry: string
  hostCity?: string
  hostEntity?: string
  purpose?: string
  startDate: string
  endDate?: string
  status: string
  visaType?: string
  visaExpiry?: string
  housingAllowance?: number
  colaAmount?: number
  hardshipAmount?: number
  notes?: string
  createdAt: string
  createdBy?: string
  updatedAt: string
  updatedBy?: string
}

export interface InternationalAssignmentRequest {
  employeeId: string
  hostCountry: string
  hostCity?: string
  hostEntity?: string
  purpose?: string
  startDate: string
  endDate?: string
  status?: string
  visaType?: string
  visaExpiry?: string
  housingAllowance?: number
  colaAmount?: number
  hardshipAmount?: number
  notes?: string
}

export const mobilityApi = {
  list: (status?: string) =>
    api.get<InternationalAssignment[]>('/api/mobility/assignments', { params: { status } })
      .then(r => r.data),
  get: (id: string) =>
    api.get<InternationalAssignment>(`/api/mobility/assignments/${id}`)
      .then(r => r.data),
  listMy: (employeeId: string) =>
    api.get<InternationalAssignment[]>(`/api/mobility/assignments/my/${employeeId}`)
      .then(r => r.data),
  create: (req: InternationalAssignmentRequest) =>
    api.post<InternationalAssignment>('/api/mobility/assignments', req)
      .then(r => r.data),
  update: (id: string, req: InternationalAssignmentRequest) =>
    api.put<InternationalAssignment>(`/api/mobility/assignments/${id}`, req)
      .then(r => r.data),
  expiringVisas: (days = 90) =>
    api.get<InternationalAssignment[]>('/api/mobility/assignments/expiring-visas', { params: { days } })
      .then(r => r.data),
}

export const ASSIGNMENT_STATUS_COLOR: Record<string, string> = {
  PLANNED: 'blue',
  ACTIVE: 'green',
  COMPLETED: 'default',
  CANCELLED: 'red',
}
