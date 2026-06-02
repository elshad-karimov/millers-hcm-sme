// M64 — Employment contracts.

import { api } from './client'
import type { EmploymentType } from './employees'

export type ContractStatus =
  | 'DRAFT'
  | 'ACTIVE'
  | 'EXPIRED'
  | 'RENEWED'
  | 'TERMINATED'

export interface Contract {
  id: string
  contractNo: string
  employeeId: string
  contractType: EmploymentType
  startDate: string
  endDate?: string | null
  probationEndDate?: string | null
  noticePeriodDays: number
  status: ContractStatus
  signedByEmployeeAt?: string | null
  signedByHrAt?: string | null
  hasConfidentiality: boolean
  nonCompeteEndDate?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface ContractRequest {
  contractType: EmploymentType
  startDate: string
  endDate?: string
  probationEndDate?: string
  noticePeriodDays?: number
  hasConfidentiality?: boolean
  nonCompeteEndDate?: string
  notes?: string
}

export const contractsApi = {
  listForEmployee: (employeeId: string) =>
    api.get<Contract[]>(`/employees/${employeeId}/contracts`).then((r) => r.data),

  currentForEmployee: (employeeId: string) =>
    api.get<Contract>(`/employees/${employeeId}/contracts/current`).then((r) => r.data),

  create: (employeeId: string, payload: ContractRequest) =>
    api
      .post<Contract>(`/employees/${employeeId}/contracts`, payload)
      .then((r) => r.data),

  get: (id: string) => api.get<Contract>(`/contracts/${id}`).then((r) => r.data),

  update: (id: string, payload: ContractRequest) =>
    api.put<Contract>(`/contracts/${id}`, payload).then((r) => r.data),

  activate: (id: string) =>
    api.post<Contract>(`/contracts/${id}/activate`).then((r) => r.data),

  signByEmployee: (id: string) =>
    api.post<Contract>(`/contracts/${id}/sign-employee`).then((r) => r.data),

  signByHr: (id: string) =>
    api.post<Contract>(`/contracts/${id}/sign-hr`).then((r) => r.data),

  terminate: (id: string, reason?: string) =>
    api
      .post<Contract>(`/contracts/${id}/terminate`, null, { params: { reason } })
      .then((r) => r.data),
}
