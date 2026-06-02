import { api } from './client'

export type EmploymentStatus =
  | 'ACTIVE'
  | 'ON_PROBATION'
  | 'ON_LEAVE'
  | 'ON_BUSINESS_TRIP'
  | 'SUSPENDED'
  | 'TERMINATED'
  | 'RETIRED'
  | 'CONTRACTOR'
  | 'INTERN'

// M61 / P1-09 — employment type, separate from employment status
export type EmploymentType =
  | 'PERMANENT'
  | 'FIXED_TERM'
  | 'PART_TIME'
  | 'PROBATIONARY'
  | 'CONTRACTOR'
  | 'INTERN'

// M61 / P1-18 — marital status (PRD §8.1)
export type MaritalStatus =
  | 'SINGLE'
  | 'MARRIED'
  | 'DIVORCED'
  | 'WIDOWED'
  | 'CIVIL_PARTNERSHIP'
  | 'OTHER'

export interface Employee {
  id: string
  employeeNo: string
  firstName: string
  lastName: string
  middleName?: string | null
  birthDate?: string | null
  gender?: string | null
  /** M61 / P1-18 */
  maritalStatus?: MaritalStatus | null
  /** M61 / P1-18 — ISO 3166-1 alpha-2 country code */
  nationality?: string | null
  nationalId?: string | null
  email?: string | null
  phone?: string | null
  hireDate: string
  employmentStatus: EmploymentStatus
  /** M61 / P1-09 — drives payroll pro-rata */
  employmentType: EmploymentType
  /** M61 / P1-09 — FTE percentage (0..100); only applied for non-salaried types */
  ftePercent: number
  departmentName?: string | null
  positionTitle?: string | null
  costCentre?: string | null
  managerId?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface EmployeeRequest {
  firstName: string
  lastName: string
  middleName?: string
  birthDate?: string
  gender?: string
  /** M61 / P1-18 */
  maritalStatus?: MaritalStatus
  /** M61 / P1-18 — ISO 3166-1 alpha-2 country code */
  nationality?: string
  nationalId?: string
  email?: string
  phone?: string
  hireDate: string
  departmentName?: string
  positionTitle?: string
  costCentre?: string
  managerId?: string
  /** M61 / P1-09 — defaults to PERMANENT */
  employmentType?: EmploymentType
  /** M61 / P1-09 — defaults to 100 */
  ftePercent?: number
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditEntry {
  id: string
  actor: string
  module: string
  action: string
  oldValue?: string | null
  newValue?: string | null
  ipAddress?: string | null
  createdAt: string
}

export const employeesApi = {
  list: (params: { page?: number; size?: number; search?: string; status?: EmploymentStatus }) =>
    api.get<PageResponse<Employee>>('/employees', { params }).then((r) => r.data),
  get: (id: string) => api.get<Employee>(`/employees/${id}`).then((r) => r.data),
  create: (payload: EmployeeRequest) =>
    api.post<Employee>('/employees', payload).then((r) => r.data),
  update: (id: string, payload: EmployeeRequest) =>
    api.put<Employee>(`/employees/${id}`, payload).then((r) => r.data),
  changeStatus: (id: string, newStatus: EmploymentStatus, reason?: string) =>
    api.post<Employee>(`/employees/${id}/status`, { newStatus, reason }).then((r) => r.data),
  audit: (id: string) =>
    api.get<AuditEntry[]>(`/employees/${id}/audit`).then((r) => r.data),
}
