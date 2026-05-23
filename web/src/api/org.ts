import { api } from './client'

export type VersionStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'ACTIVE'
  | 'REJECTED'
  | 'ARCHIVED'

export type OrgUnitType =
  | 'COMPANY'
  | 'BRANCH'
  | 'DIVISION'
  | 'DEPARTMENT'
  | 'SECTION'
  | 'UNIT'
  | 'TEAM'

export interface StructureVersion {
  id: string
  versionNumber: number
  effectiveDate: string
  status: VersionStatus
  changeReason?: string | null
  previousVersionId?: string | null
  createdBy: string
  approvedBy?: string | null
  activatedAt?: string | null
  archivedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface OrgUnitResponse {
  id: string
  versionId: string
  code: string
  name: string
  unitType: OrgUnitType
  parentId?: string | null
  headEmployeeId?: string | null
  sortOrder: number
}

export interface OrgUnitRequest {
  code: string
  name: string
  unitType: OrgUnitType
  parentId?: string | null
  headEmployeeId?: string | null
  sortOrder?: number
}

export interface OrgTreeNode extends OrgUnitResponse {
  children: OrgTreeNode[]
}

export const orgApi = {
  versions: () => api.get<StructureVersion[]>('/org/versions').then((r) => r.data),
  active: () => api.get<StructureVersion | null>('/org/versions/active').then((r) => r.data),
  version: (id: string) =>
    api.get<StructureVersion>(`/org/versions/${id}`).then((r) => r.data),
  units: (id: string) =>
    api.get<OrgUnitResponse[]>(`/org/versions/${id}/units`).then((r) => r.data),
  tree: (id: string) =>
    api.get<OrgTreeNode | null>(`/org/versions/${id}/tree`).then((r) => r.data),
  createDraft: (effectiveDate: string, changeReason?: string) =>
    api
      .post<StructureVersion>('/org/versions/draft', { effectiveDate, changeReason })
      .then((r) => r.data),
  submit: (id: string) =>
    api.post<StructureVersion>(`/org/versions/${id}/submit`).then((r) => r.data),
  approve: (id: string, reason?: string) =>
    api.post<StructureVersion>(`/org/versions/${id}/approve`, { reason }).then((r) => r.data),
  reject: (id: string, reason?: string) =>
    api.post<StructureVersion>(`/org/versions/${id}/reject`, { reason }).then((r) => r.data),
  activate: (id: string) =>
    api.post<StructureVersion>(`/org/versions/${id}/activate`).then((r) => r.data),
  rollback: (sourceVersionId: string, effectiveDate: string, reason?: string) =>
    api
      .post<StructureVersion>('/org/versions/rollback', {
        sourceVersionId,
        effectiveDate,
        reason,
      })
      .then((r) => r.data),

  addUnit: (versionId: string, payload: OrgUnitRequest) =>
    api.post<OrgUnitResponse>(`/org/versions/${versionId}/units`, payload).then((r) => r.data),
  updateUnit: (unitId: string, payload: OrgUnitRequest) =>
    api.put<OrgUnitResponse>(`/org/units/${unitId}`, payload).then((r) => r.data),
  removeUnit: (unitId: string) => api.delete(`/org/units/${unitId}`).then((r) => r.data),
}
