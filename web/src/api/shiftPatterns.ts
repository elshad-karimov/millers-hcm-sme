// M111 — Shift patterns + auto-roster generator.

import { api } from './client'

export interface PatternDayRequest {
  dayIndex: number
  shiftId?: string | null
  notes?: string
}

export interface PatternDayResponse {
  id: string
  dayIndex: number
  shiftId?: string | null
  shiftCode?: string | null
  shiftName?: string | null
  shiftColor?: string | null
  notes?: string | null
}

export interface PatternRequest {
  code: string
  name: string
  description?: string
  cycleDays: number
  active?: boolean
  days: PatternDayRequest[]
}

export interface PatternResponse {
  id: string
  code: string
  name: string
  description?: string | null
  cycleDays: number
  active: boolean
  assignmentCount: number
  days: PatternDayResponse[]
  createdAt: string
}

export interface AssignmentRequest {
  employeeId: string
  patternId: string
  startDate: string
  endDate?: string
  anchorDayIndex?: number
  notes?: string
}

export interface EndAssignmentRequest {
  endDate: string
  reason?: string
}

export interface AssignmentResponse {
  id: string
  employeeId: string
  employeeName?: string | null
  patternId: string
  patternCode?: string | null
  patternName?: string | null
  startDate: string
  endDate?: string | null
  anchorDayIndex: number
  notes?: string | null
  updatedAt: string
}

export interface GenerateRosterRequest {
  from: string
  to: string
  employeeIds?: string[]
  overwriteExisting?: boolean
}

export interface GenerateRosterResponse {
  employeesProcessed: number
  rosterRowsCreated: number
  rosterRowsUpdated: number
  rosterRowsSkippedLocked: number
  restDaysSkipped: number
}

export const shiftPatternsApi = {
  list: (activeOnly = false) =>
    api
      .get<PatternResponse[]>('/attendance/shift-patterns', { params: { activeOnly } })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<PatternResponse>(`/attendance/shift-patterns/${id}`).then((r) => r.data),
  create: (req: PatternRequest) =>
    api.post<PatternResponse>('/attendance/shift-patterns', req).then((r) => r.data),
  update: (id: string, req: PatternRequest) =>
    api.put<PatternResponse>(`/attendance/shift-patterns/${id}`, req).then((r) => r.data),

  assignmentsForEmployee: (employeeId: string) =>
    api
      .get<AssignmentResponse[]>(`/attendance/shift-patterns/assignments/employee/${employeeId}`)
      .then((r) => r.data),
  assignmentsForPattern: (id: string) =>
    api
      .get<AssignmentResponse[]>(`/attendance/shift-patterns/${id}/assignments`)
      .then((r) => r.data),
  assign: (req: AssignmentRequest) =>
    api
      .post<AssignmentResponse>('/attendance/shift-patterns/assignments', req)
      .then((r) => r.data),
  endAssignment: (id: string, req: EndAssignmentRequest) =>
    api
      .post<AssignmentResponse>(`/attendance/shift-patterns/assignments/${id}/end`, req)
      .then((r) => r.data),

  generateRoster: (req: GenerateRosterRequest) =>
    api
      .post<GenerateRosterResponse>('/attendance/shift-patterns/generate-roster', req)
      .then((r) => r.data),
}
