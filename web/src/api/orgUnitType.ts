import { api } from './client'

export interface OrgUnitTypeConfigResponse {
  code: string
  label: string
  color?: string | null
  sortOrder: number
  canHaveChildren: boolean
  rootLevel: boolean
  allowedParentTypes?: string | null
  active: boolean
  notes?: string | null
  createdAt: string
  updatedAt: string
}

export interface OrgUnitTypeConfigRequest {
  code: string
  label: string
  color?: string | null
  sortOrder?: number
  canHaveChildren?: boolean
  rootLevel?: boolean
  allowedParentTypes?: string | null
  active?: boolean | null
  notes?: string | null
}

export const orgUnitTypeApi = {
  list: (activeOnly = false) =>
    api.get<OrgUnitTypeConfigResponse[]>('/org-unit-types', { params: { activeOnly } }).then((r) => r.data),

  get: (code: string) =>
    api.get<OrgUnitTypeConfigResponse>(`/org-unit-types/${code}`).then((r) => r.data),

  create: (req: OrgUnitTypeConfigRequest) =>
    api.post<OrgUnitTypeConfigResponse>('/org-unit-types', req).then((r) => r.data),

  update: (code: string, req: OrgUnitTypeConfigRequest) =>
    api.put<OrgUnitTypeConfigResponse>(`/org-unit-types/${code}`, req).then((r) => r.data),

  activate: (code: string) =>
    api.post<OrgUnitTypeConfigResponse>(`/org-unit-types/${code}/activate`).then((r) => r.data),

  deactivate: (code: string) =>
    api.post<OrgUnitTypeConfigResponse>(`/org-unit-types/${code}/deactivate`).then((r) => r.data),
}
