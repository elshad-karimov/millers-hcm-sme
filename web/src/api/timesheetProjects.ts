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
    api.get<TimesheetProject[]>('/timesheet/projects', { params: { activeOnly } })
      .then(r => r.data),
  get: (id: string) =>
    api.get<TimesheetProject>(`/timesheet/projects/${id}`)
      .then(r => r.data),
  create: (req: TimesheetProjectRequest) =>
    api.post<TimesheetProject>('/timesheet/projects', req)
      .then(r => r.data),
  update: (id: string, req: TimesheetProjectRequest) =>
    api.put<TimesheetProject>(`/timesheet/projects/${id}`, req)
      .then(r => r.data),
}
