import { api } from './client'

export type VersionStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'ACTIVE'
  | 'REJECTED'
  | 'ARCHIVED'

/** M143 — org unit types are now DB-driven; this alias keeps existing code compiling. */
export type OrgUnitType = string

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
  // M141 — structured location FK
  locationId?: string | null
  // M142 — primary HRBP employee id
  hrbpId?: string | null
  // M81 — finance / facilities attributes
  costCentreCode?: string | null
  location?: string | null
  contactEmail?: string | null
  glAccount?: string | null
  headcountBudget?: number | null
  active: boolean
}

export interface OrgUnitRequest {
  code: string
  name: string
  unitType: OrgUnitType
  parentId?: string | null
  headEmployeeId?: string | null
  sortOrder?: number
  // M141 — structured location FK
  locationId?: string | null
  // M142 — primary HRBP employee id
  hrbpId?: string | null
  // M81 — extended attributes; all optional
  costCentreCode?: string
  location?: string
  contactEmail?: string
  glAccount?: string
  headcountBudget?: number
  active?: boolean
}

// M81 — per-unit policy override
export interface OrgUnitPolicy {
  id: string
  orgUnitId: string
  versionId: string
  leaveGroupId?: string | null
  payrollGroupId?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

// M81 — per-unit history row
export type OrgChangeKind =
  | 'CREATE'
  | 'UPDATE'
  | 'REMOVE'
  | 'MOVE'
  | 'RENAME'
  | 'HEAD_CHANGE'
  | 'POLICY_CHANGE'

export interface OrgUnitHistory {
  id: string
  orgUnitId: string
  versionId: string
  changeKind: OrgChangeKind
  beforeValue?: unknown
  afterValue?: unknown
  changeReason?: string | null
  changedAt: string
  changedBy?: string | null
}

// M84 — bulk reorg manifest
export type BulkReorgKind = 'ADD' | 'UPDATE' | 'MOVE' | 'REMOVE'

export interface BulkReorgOperation {
  kind: BulkReorgKind
  code: string
  name?: string
  unitType?: OrgUnitType
  parentCode?: string
  headEmployeeId?: string
  sortOrder?: number
  locationId?: string
  costCentreCode?: string
  location?: string
  contactEmail?: string
  glAccount?: string
  headcountBudget?: number
  active?: boolean
  newParentCode?: string
  reason?: string
}

export interface BulkReorgManifest {
  dryRun: boolean
  operations: BulkReorgOperation[]
}

export interface BulkReorgRowResult {
  index: number
  kind: BulkReorgKind
  code: string
  applied: boolean
  message: string
}

export interface BulkReorgResult {
  dryRun: boolean
  operationsTotal: number
  operationsApplied: number
  rows: BulkReorgRowResult[]
}

// M81 — span-of-control
export interface SpanOfControlRow {
  managerId: string
  employeeNo?: string | null
  fullName?: string | null
  positionTitle?: string | null
  departmentName?: string | null
  directReports: number
  transitiveReports: number
  depth: number
  flag: 'OK' | 'OVERSPAN' | 'UNDERSPAN'
}

export interface SpanOfControlReport {
  managersCount: number
  overspanCount: number
  underspanCount: number
  overspanThreshold: number
  underspanThreshold: number
  rows: SpanOfControlRow[]
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

  // M81 — policy + history
  getPolicy: (unitId: string, versionId: string) =>
    api
      .get<OrgUnitPolicy | null>(`/org/units/${unitId}/policy/${versionId}`)
      .then((r) => r.data),
  upsertPolicy: (
    unitId: string,
    versionId: string,
    payload: { leaveGroupId?: string; payrollGroupId?: string; notes?: string },
  ) =>
    api
      .put<OrgUnitPolicy>(`/org/units/${unitId}/policy/${versionId}`, payload)
      .then((r) => r.data),
  deletePolicy: (unitId: string, versionId: string) =>
    api.delete(`/org/units/${unitId}/policy/${versionId}`),
  unitHistory: (unitId: string) =>
    api.get<OrgUnitHistory[]>(`/org/units/${unitId}/history`).then((r) => r.data),
  recentVersionHistory: (versionId: string) =>
    api
      .get<OrgUnitHistory[]>(`/org/versions/${versionId}/recent-history`)
      .then((r) => r.data),

  // M84 — bulk reorg
  bulkReorg: (versionId: string, manifest: BulkReorgManifest) =>
    api
      .post<BulkReorgResult>(`/org/versions/${versionId}/bulk-reorg`, manifest)
      .then((r) => r.data),
}

// M81 — span-of-control lives under the existing emp-mgmt report family
export const orgReportApi = {
  spanOfControl: () =>
    api
      .get<SpanOfControlReport>('/reports/emp-mgmt/span-of-control')
      .then((r) => r.data),
}
