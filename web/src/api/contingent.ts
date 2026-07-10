// M488 + M489 — Contingent workforce (contractor engagements) API client

import { api } from './client'

export interface ContractorEngagement {
  id: string
  tenantId: string
  employeeId: string
  vendorAgencyId?: string
  contractStart: string
  contractEnd?: string
  rate?: number
  rateUnit?: string
  poNumber?: string
  tenureAlertDays: number
  status: string
  conversionDate?: string
  notes?: string
  createdAt: string
  createdBy?: string
  updatedAt: string
  updatedBy?: string
}

export interface ContractorEngagementRequest {
  employeeId: string
  vendorAgencyId?: string
  contractStart: string
  contractEnd?: string
  rate?: number
  rateUnit?: string
  poNumber?: string
  tenureAlertDays?: number
  status?: string
  notes?: string
}

export interface ConvertToFTERequest {
  newEmploymentType: string
  effectiveDate: string
}

export const contingentApi = {
  list: (status?: string) =>
    api.get<ContractorEngagement[]>('/api/contingent/contractors', { params: { status } })
      .then(r => r.data),
  get: (id: string) =>
    api.get<ContractorEngagement>(`/api/contingent/contractors/${id}`)
      .then(r => r.data),
  create: (req: ContractorEngagementRequest) =>
    api.post<ContractorEngagement>('/api/contingent/contractors', req)
      .then(r => r.data),
  update: (id: string, req: ContractorEngagementRequest) =>
    api.put<ContractorEngagement>(`/api/contingent/contractors/${id}`, req)
      .then(r => r.data),
  convertToFTE: (id: string, req: ConvertToFTERequest) =>
    api.post(`/api/contingent/contractors/${id}/convert`, req),
}

export const ENGAGEMENT_STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  PLANNED: 'blue',
  COMPLETED: 'default',
  TERMINATED: 'red',
  CONVERTED: 'purple',
}
