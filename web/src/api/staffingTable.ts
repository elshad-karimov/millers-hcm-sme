// M245 — Staffing table (ştat cədvəli) SPA client.

import { api } from './client'

export type StaffingTableStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'ACTIVE'
  | 'REJECTED'
  | 'ARCHIVED'

export interface StaffingTableLine {
  id: string
  staffingTableId: string
  lineNo: number
  orgUnitId?: string | null
  orgUnitLabel?: string | null
  positionId?: string | null
  positionCode?: string | null
  positionTitle: string
  grade?: string | null
  approvedHeadcount: number
  monthlySalary: number
  monthlySalaryFund: number
  currency: string
  notes?: string | null
}

export interface StaffingTable {
  id: string
  legalEntityId: string
  versionCode: string
  title?: string | null
  effectiveFrom: string
  effectiveTo?: string | null
  status: StaffingTableStatus
  notes?: string | null
  submittedBy?: string | null
  submittedAt?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  rejectedBy?: string | null
  rejectedAt?: string | null
  rejectReason?: string | null
  archivedAt?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
  totalLines: number
  totalHeadcount: number
  totalMonthlyFund?: number | null
}

export interface StaffingTableHeaderRequest {
  legalEntityId: string
  versionCode: string
  title?: string
  effectiveFrom: string
  effectiveTo?: string
  notes?: string
}

export interface StaffingTableLineRequest {
  lineNo?: number
  orgUnitId?: string
  orgUnitLabel?: string
  positionId?: string
  positionCode?: string
  positionTitle: string
  grade?: string
  approvedHeadcount: number
  monthlySalary?: number
  monthlySalaryFund?: number
  currency?: string
  notes?: string
}

export interface DiffRow {
  positionTitle: string
  grade?: string | null
  orgUnitLabel?: string | null
  countA: number
  countB: number
  countDelta: number
  fundA: number
  fundB: number
  fundDelta: number
}

export interface DiffResult {
  tableAId: string
  tableBId: string
  totalHeadcountA: number
  totalHeadcountB: number
  totalFundA: number
  totalFundB: number
  rows: DiffRow[]
}

export const STAFFING_TABLE_STATUS_COLOR: Record<StaffingTableStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  ACTIVE: 'green',
  REJECTED: 'red',
  ARCHIVED: 'default',
}

export const STAFFING_TABLE_STATUS_LABEL: Record<StaffingTableStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  ACTIVE: 'Active',
  REJECTED: 'Rejected',
  ARCHIVED: 'Archived',
}

export const staffingTableApi = {
  list: (legalEntityId?: string) =>
    api
      .get<StaffingTable[]>('/staffing-tables', {
        params: legalEntityId ? { legalEntityId } : {},
      })
      .then((r) => r.data),

  get: (id: string) =>
    api.get<StaffingTable>(`/staffing-tables/${id}`).then((r) => r.data),

  create: (body: StaffingTableHeaderRequest) =>
    api.post<StaffingTable>('/staffing-tables', body).then((r) => r.data),

  update: (id: string, body: StaffingTableHeaderRequest) =>
    api.put<StaffingTable>(`/staffing-tables/${id}`, body).then((r) => r.data),

  remove: (id: string) =>
    api.delete<void>(`/staffing-tables/${id}`).then(() => undefined),

  // Lines
  lines: (id: string) =>
    api.get<StaffingTableLine[]>(`/staffing-tables/${id}/lines`).then((r) => r.data),
  addLine: (id: string, body: StaffingTableLineRequest) =>
    api.post<StaffingTableLine>(`/staffing-tables/${id}/lines`, body).then((r) => r.data),
  updateLine: (lineId: string, body: StaffingTableLineRequest) =>
    api.put<StaffingTableLine>(`/staffing-tables/lines/${lineId}`, body).then((r) => r.data),
  removeLine: (lineId: string) =>
    api.delete<void>(`/staffing-tables/lines/${lineId}`).then(() => undefined),

  generateFromPositions: (id: string) =>
    api
      .post<StaffingTableLine[]>(`/staffing-tables/${id}/generate-from-positions`, {})
      .then((r) => r.data),

  // Lifecycle
  submit: (id: string) =>
    api.post<StaffingTable>(`/staffing-tables/${id}/submit`, {}).then((r) => r.data),
  approve: (id: string) =>
    api.post<StaffingTable>(`/staffing-tables/${id}/approve`, {}).then((r) => r.data),
  reject: (id: string, reason: string) =>
    api.post<StaffingTable>(`/staffing-tables/${id}/reject`, { reason }).then((r) => r.data),
  archive: (id: string) =>
    api.post<StaffingTable>(`/staffing-tables/${id}/archive`, {}).then((r) => r.data),

  compare: (idA: string, idB: string) =>
    api.get<DiffResult>(`/staffing-tables/${idA}/compare/${idB}`).then((r) => r.data),

  /**
   * Downloads the Excel export through axios so the auth bearer token
   * is attached (a plain <a href> link would hit the API anonymously).
   * Resolves once the blob is saved to the user's browser.
   */
  exportXlsx: async (id: string, filename: string) => {
    const res = await api.get<Blob>(`/staffing-tables/${id}/export/xlsx`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  },
}
