// M75 — Master catalogues for staffing normalisation:
// Grade, JobFamily, JobFunction. Plus EmployeeAssignment under /employees/:id.

import { api } from './client'

// ── Grade ────────────────────────────────────────────────────────────────────

export interface Grade {
  id: string
  code: string
  name: string
  description?: string | null
  level?: number | null
  minSalary?: number | null
  maxSalary?: number | null
  currency: string
  active: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface GradeRequest {
  code: string
  name: string
  description?: string
  level?: number
  minSalary?: number
  maxSalary?: number
  currency?: string
  active?: boolean
}

// ── Job Family ───────────────────────────────────────────────────────────────

export interface JobFamily {
  id: string
  code: string
  name: string
  description?: string | null
  active: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface JobFamilyRequest {
  code: string
  name: string
  description?: string
  active?: boolean
}

// ── Job Function ─────────────────────────────────────────────────────────────

export interface JobFunction {
  id: string
  code: string
  name: string
  description?: string | null
  jobFamilyId?: string | null
  active: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface JobFunctionRequest {
  code: string
  name: string
  description?: string
  jobFamilyId?: string
  active?: boolean
}

// ── EmployeeAssignment ───────────────────────────────────────────────────────

export type AssignmentType =
  | 'PRIMARY'
  | 'ACTING'
  | 'SECONDMENT'
  | 'MATRIX'
  | 'PROJECT'

export interface EmployeeAssignment {
  id: string
  employeeId: string
  positionId: string
  assignmentType: AssignmentType
  allocationPercent: number
  matrixManagerId?: string | null
  effectiveFrom: string
  effectiveTo?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface EmployeeAssignmentRequest {
  employeeId: string
  positionId: string
  assignmentType: AssignmentType
  allocationPercent?: number
  matrixManagerId?: string
  effectiveFrom: string
  effectiveTo?: string
  notes?: string
}

// ── API ──────────────────────────────────────────────────────────────────────

export const gradeApi = {
  list: (activeOnly = true) =>
    api.get<Grade[]>('/grades', { params: { activeOnly } }).then((r) => r.data),
  get: (id: string) => api.get<Grade>(`/grades/${id}`).then((r) => r.data),
  create: (req: GradeRequest) => api.post<Grade>('/grades', req).then((r) => r.data),
  update: (id: string, req: GradeRequest) =>
    api.put<Grade>(`/grades/${id}`, req).then((r) => r.data),
  delete: (id: string) => api.delete(`/grades/${id}`),
}

export const jobFamilyApi = {
  list: (activeOnly = true) =>
    api.get<JobFamily[]>('/job-families', { params: { activeOnly } }).then((r) => r.data),
  get: (id: string) => api.get<JobFamily>(`/job-families/${id}`).then((r) => r.data),
  create: (req: JobFamilyRequest) =>
    api.post<JobFamily>('/job-families', req).then((r) => r.data),
  update: (id: string, req: JobFamilyRequest) =>
    api.put<JobFamily>(`/job-families/${id}`, req).then((r) => r.data),
  delete: (id: string) => api.delete(`/job-families/${id}`),
}

export const jobFunctionApi = {
  list: (jobFamilyId?: string, activeOnly = true) =>
    api
      .get<JobFunction[]>('/job-functions', { params: { jobFamilyId, activeOnly } })
      .then((r) => r.data),
  get: (id: string) => api.get<JobFunction>(`/job-functions/${id}`).then((r) => r.data),
  create: (req: JobFunctionRequest) =>
    api.post<JobFunction>('/job-functions', req).then((r) => r.data),
  update: (id: string, req: JobFunctionRequest) =>
    api.put<JobFunction>(`/job-functions/${id}`, req).then((r) => r.data),
  delete: (id: string) => api.delete(`/job-functions/${id}`),
}

export const assignmentApi = {
  list: (employeeId: string, openOnly = false) =>
    api
      .get<EmployeeAssignment[]>(`/employees/${employeeId}/assignments`, {
        params: { openOnly },
      })
      .then((r) => r.data),

  create: (employeeId: string, req: EmployeeAssignmentRequest) =>
    api
      .post<EmployeeAssignment>(`/employees/${employeeId}/assignments`, req)
      .then((r) => r.data),

  update: (employeeId: string, id: string, req: EmployeeAssignmentRequest) =>
    api
      .put<EmployeeAssignment>(`/employees/${employeeId}/assignments/${id}`, req)
      .then((r) => r.data),

  close: (employeeId: string, id: string, closeOn?: string) =>
    api
      .post<EmployeeAssignment>(
        `/employees/${employeeId}/assignments/${id}/close`,
        null,
        { params: { closeOn } },
      )
      .then((r) => r.data),

  delete: (employeeId: string, id: string) =>
    api.delete(`/employees/${employeeId}/assignments/${id}`),
}
