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

export interface Employee {
  id: string
  employeeNo: string
  firstName: string
  lastName: string
  middleName?: string | null
  birthDate?: string | null
  gender?: string | null
  nationalId?: string | null
  email?: string | null
  phone?: string | null
  hireDate: string
  employmentStatus: EmploymentStatus
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
  nationalId?: string
  email?: string
  phone?: string
  hireDate: string
  departmentName?: string
  positionTitle?: string
  costCentre?: string
  managerId?: string
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
