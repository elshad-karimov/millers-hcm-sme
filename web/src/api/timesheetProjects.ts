// M484 — Timesheet projects API client

import { api } from './client'

export interface TimesheetProject {
  id: string
  tenantId: string
  code: string
  name: string
  description?: string
  billingRate?: number
  active: boolean
  createdAt: string
  createdBy?: string
  updatedAt: string
  updatedBy?: string
}

export interface TimesheetProjectRequest {
  code: string
  name: string
  description?: string
  billingRate?: number
  active?: boolean
}

export const timesheetProjectsApi = {
  list: (activeOnly = true) =>
    api.get<TimesheetProject[]>('/api/timesheet/projects', { params: { activeOnly } })
      .then(r => r.data),
  get: (id: string) =>
    api.get<TimesheetProject>(`/api/timesheet/projects/${id}`)
      .then(r => r.data),
  create: (req: TimesheetProjectRequest) =>
    api.post<TimesheetProject>('/api/timesheet/projects', req)
      .then(r => r.data),
  update: (id: string, req: TimesheetProjectRequest) =>
    api.put<TimesheetProject>(`/api/timesheet/projects/${id}`, req)
      .then(r => r.data),
}
