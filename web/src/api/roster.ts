// M110 — Shift catalog + roster.

import { api } from './client'

export interface Shift {
  id: string
  code: string
  name: string
  description?: string | null
  startTime: string  // HH:mm:ss or HH:mm
  endTime: string
  breakMinutes: number
  crossesMidnight: boolean
  durationMinutes: number
  color?: string | null
  active: boolean
  createdAt: string
}

export interface ShiftRequest {
  code: string
  name: string
  description?: string
  startTime: string  // HH:mm
  endTime: string
  breakMinutes?: number
  color?: string
  active?: boolean
}

export interface RosterEntryResponse {
  id: string
  employeeId: string
  employeeName?: string | null
  shiftId: string
  shiftCode?: string | null
  shiftName?: string | null
  shiftColor?: string | null
  shiftStart?: string | null
  shiftEnd?: string | null
  shiftCrossesMidnight: boolean
  rosterDate: string
  notes?: string | null
  locked: boolean
  updatedAt: string
}

export interface RosterGridRow {
  employeeId: string
  employeeName?: string | null
  entries: RosterEntryResponse[]
}

export interface RosterGrid {
  from: string
  to: string
  employees: RosterGridRow[]
}

export interface AssignRequest {
  employeeId: string
  shiftId: string
  rosterDate: string
  notes?: string
}

export interface BulkAssignRequest {
  entries: AssignRequest[]
}

export interface SwapRequest {
  entryAId: string
  entryBId: string
  reason?: string
}

export const shiftsApi = {
  list: (activeOnly = false) =>
    api.get<Shift[]>('/attendance/shifts', { params: { activeOnly } }).then((r) => r.data),
  get: (id: string) => api.get<Shift>(`/attendance/shifts/${id}`).then((r) => r.data),
  create: (req: ShiftRequest) =>
    api.post<Shift>('/attendance/shifts', req).then((r) => r.data),
  update: (id: string, req: ShiftRequest) =>
    api.put<Shift>(`/attendance/shifts/${id}`, req).then((r) => r.data),
  archive: (id: string) =>
    api.delete<Shift>(`/attendance/shifts/${id}`).then((r) => r.data),
}

export const rosterApi = {
  grid: (employeeIds: string[], from: string, to: string) =>
    api
      .get<RosterGrid>('/attendance/roster/grid', {
        params: { employeeIds: employeeIds.join(','), from, to },
        // axios serializes string-list joined with comma by default for repeat params,
        // backend's @RequestParam List<UUID> accepts comma-joined or repeated.
      })
      .then((r) => r.data),
  forEmployee: (employeeId: string, from: string, to: string) =>
    api
      .get<RosterEntryResponse[]>(`/attendance/roster/employee/${employeeId}`, {
        params: { from, to },
      })
      .then((r) => r.data),
  mine: (from: string, to: string) =>
    api
      .get<RosterEntryResponse[]>('/attendance/roster/me', { params: { from, to } })
      .then((r) => r.data),
  assign: (req: AssignRequest) =>
    api.post<RosterEntryResponse>('/attendance/roster/assign', req).then((r) => r.data),
  bulkAssign: (req: BulkAssignRequest) =>
    api
      .post<RosterEntryResponse[]>('/attendance/roster/bulk-assign', req)
      .then((r) => r.data),
  lock: (id: string) =>
    api.post<RosterEntryResponse>(`/attendance/roster/${id}/lock`, {}).then((r) => r.data),
  swap: (req: SwapRequest) =>
    api.post<RosterEntryResponse[]>('/attendance/roster/swap', req).then((r) => r.data),
  remove: (id: string) =>
    api.delete<void>(`/attendance/roster/${id}`).then(() => undefined),
}
