// M109 — Position control gate dashboard.

import { api } from './client'

export type VacancyState = 'PLANNED' | 'VACANT' | 'PARTIALLY_OCCUPIED' | 'OCCUPIED' | 'FROZEN' | 'CANCELLED'

export interface PositionHeadcountRow {
  positionId: string
  code: string
  title: string
  orgUnitId?: string | null
  orgUnitLabel?: string | null
  approvedHeadcount: number
  occupiedHeadcount: number
  actualOccupied: number
  openVacancyOpenings: number
  remainingCapacity: number
  overBudget: boolean
  driftDetected: boolean
  vacancyState: VacancyState
}

export interface OrgUnitRoll {
  orgUnitId?: string | null
  orgUnitLabel?: string | null
  positionCount: number
  approvedHeadcount: number
  actualOccupied: number
  openVacancyOpenings: number
  remainingCapacity: number
}

export interface HeadcountSummary {
  totalApproved: number
  totalActualOccupied: number
  totalOpenVacancies: number
  totalRemainingCapacity: number
  positionsOverBudget: number
  positionsWithDrift: number
  positionCount: number
  rows: PositionHeadcountRow[]
  byOrgUnit: OrgUnitRoll[]
}

export interface ReconcileResponse {
  driftedCount: number
  drifted: {
    id: string
    code: string
    title: string
    occupiedHeadcount: number
  }[]
}

export const headcountApi = {
  summary: () => api.get<HeadcountSummary>('/staffing/headcount').then((r) => r.data),
  reconcile: () => api.post<ReconcileResponse>('/staffing/headcount/reconcile', {}).then((r) => r.data),
}

// ── M156: Headcount change requests ─────────────────────────────────────────

export type HcrStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'

export interface HeadcountChangeResponse {
  id: string
  positionId: string
  requestedDelta: number
  reason?: string | null
  status: HcrStatus
  workflowInstanceId?: string | null
  requestedBy?: string | null
  requestedAt?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  rejectedBy?: string | null
  rejectedAt?: string | null
  rejectReason?: string | null
  createdAt?: string | null
}

export interface HeadcountChangeSubmitRequest {
  requestedDelta: number
  reason?: string
}

export const hcrApi = {
  submit: (positionId: string, body: HeadcountChangeSubmitRequest) =>
    api.post<HeadcountChangeResponse>(`/staffing/positions/${positionId}/headcount-requests`, body).then((r) => r.data),
  listByPosition: (positionId: string) =>
    api.get<HeadcountChangeResponse[]>(`/staffing/positions/${positionId}/headcount-requests`).then((r) => r.data),
  listPending: () =>
    api.get<HeadcountChangeResponse[]>('/staffing/headcount-requests/pending').then((r) => r.data),
}
