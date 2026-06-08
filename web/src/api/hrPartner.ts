import { api } from './client'

export interface HrPartnerResponse {
  id: string
  orgUnitId: string
  employeeId: string
  backup: boolean
  effectiveFrom?: string | null
  effectiveTo?: string | null
  active: boolean
  notes?: string | null
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

export interface HrPartnerRequest {
  orgUnitId: string
  employeeId: string
  backup?: boolean
  effectiveFrom?: string | null
  effectiveTo?: string | null
  active?: boolean | null
  notes?: string | null
}

export const hrPartnerApi = {
  listForUnit: (orgUnitId: string) =>
    api.get<HrPartnerResponse[]>(`/hr-partners/by-unit/${orgUnitId}`).then((r) => r.data),

  listForEmployee: (employeeId: string) =>
    api.get<HrPartnerResponse[]>(`/hr-partners/by-employee/${employeeId}`).then((r) => r.data),

  get: (id: string) =>
    api.get<HrPartnerResponse>(`/hr-partners/${id}`).then((r) => r.data),

  create: (req: HrPartnerRequest) =>
    api.post<HrPartnerResponse>('/hr-partners', req).then((r) => r.data),

  update: (id: string, req: HrPartnerRequest) =>
    api.put<HrPartnerResponse>(`/hr-partners/${id}`, req).then((r) => r.data),

  activate: (id: string) =>
    api.post<HrPartnerResponse>(`/hr-partners/${id}/activate`).then((r) => r.data),

  deactivate: (id: string) =>
    api.post<HrPartnerResponse>(`/hr-partners/${id}/deactivate`).then((r) => r.data),
}
