import { api } from './client'

export interface ByDepartmentRow {
  department: string
  competencyName: string
  employeeCount: number
  avgLevel: number
}

export interface CriticalSkillRow {
  competencyName: string
  requiredLevel: number
  coveredEmployees: number
}

export interface CertificationRow {
  certificationName: string
  totalCount: number
  expiredCount: number
  expiringSoonCount: number
}

export const skillInventoryApi = {
  byDepartment: () => api.get<ByDepartmentRow[]>('/api/reports/skills/inventory'),

  critical: () => api.get<CriticalSkillRow[]>('/api/reports/skills/critical'),

  certifications: () => api.get<CertificationRow[]>('/api/reports/skills/certifications'),
}
