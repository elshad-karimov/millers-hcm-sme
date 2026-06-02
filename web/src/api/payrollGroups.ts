// M75 — Payroll groups: shared pay calendar / bank file format per cohort.

import { api } from './client'

export type PayCycle = 'MONTHLY' | 'BI_WEEKLY' | 'WEEKLY' | 'SEMI_MONTHLY'
export type BankFileFormat = 'CSV' | 'SWIFT_MT103' | 'SEPA' | 'CUSTOM'

export interface PayrollGroup {
  id: string
  code: string
  name: string
  description?: string | null
  payCycle: PayCycle
  bankFileFormat: BankFileFormat
  defaultCurrency: string
  rulesJson?: unknown
  active: boolean
  defaultGroup: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface PayrollGroupRequest {
  code: string
  name: string
  description?: string
  payCycle?: PayCycle
  bankFileFormat?: BankFileFormat
  defaultCurrency?: string
  rulesJson?: unknown
  active?: boolean
  defaultGroup?: boolean
}

export const payrollGroupsApi = {
  list: (activeOnly = true) =>
    api
      .get<PayrollGroup[]>('/payroll-groups', { params: { activeOnly } })
      .then((r) => r.data),

  get: (id: string) => api.get<PayrollGroup>(`/payroll-groups/${id}`).then((r) => r.data),

  create: (req: PayrollGroupRequest) =>
    api.post<PayrollGroup>('/payroll-groups', req).then((r) => r.data),

  update: (id: string, req: PayrollGroupRequest) =>
    api.put<PayrollGroup>(`/payroll-groups/${id}`, req).then((r) => r.data),

  delete: (id: string) => api.delete(`/payroll-groups/${id}`),
}
