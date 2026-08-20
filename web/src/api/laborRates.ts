// M485 + M486 — Labor rates & cost reports API client

import { api } from './client'

export interface LaborRate {
  id: string
  tenantId: string
  gradeId?: string
  positionId?: string
  hourlyRate: number
  effectiveFrom: string
  effectiveTo?: string
  createdAt: string
  createdBy?: string
  updatedAt: string
  updatedBy?: string
}

export interface LaborRateRequest {
  gradeId?: string
  positionId?: string
  hourlyRate: number
  effectiveFrom: string
  effectiveTo?: string
}

export const laborRatesApi = {
  list: () =>
    api.get<LaborRate[]>('/payroll/labor-rates')
      .then(r => r.data),
  get: (id: string) =>
    api.get<LaborRate>(`/payroll/labor-rates/${id}`)
      .then(r => r.data),
  create: (req: LaborRateRequest) =>
    api.post<LaborRate>('/payroll/labor-rates', req)
      .then(r => r.data),
  update: (id: string, req: LaborRateRequest) =>
    api.put<LaborRate>(`/payroll/labor-rates/${id}`, req)
      .then(r => r.data),
}

export interface LaborCostRow {
  [key: string]: any
}

export const laborCostReportApi = {
  byProject: (year: number, month: number) =>
    api.get<LaborCostRow[]>('/reports/labor-cost/by-project', { params: { year, month } })
      .then(r => r.data),
  byDepartment: (year: number, month: number) =>
    api.get<LaborCostRow[]>('/reports/labor-cost/by-department', { params: { year, month } })
      .then(r => r.data),
  monthlySummary: (year: number, month: number) =>
    api.get<LaborCostRow[]>('/reports/labor-cost/monthly-summary', { params: { year, month } })
      .then(r => r.data),
}
