// M124 — Asset admin client (cross-employee).
//
// Lives alongside the existing assetsNotesRewardsApi (M72), which is
// employee-scoped. This client backs the new HR-admin page:
// search across all employees, read the event-log timeline,
// reissue from one holder to another atomically.

import { api } from './client'
import type { Asset, AssetStatus, AssetType } from './assetsNotesRewards'

export type AssetEventType =
  | 'ASSIGN'
  | 'RETURN'
  | 'MARK_LOST'
  | 'MARK_DAMAGED'
  | 'WRITE_OFF'
  | 'REASSIGN'
  | 'UPDATE_CONDITION'

export interface AssetEventResponse {
  id: string
  assetId: string
  eventType: AssetEventType
  occurredAt: string
  actor: string
  previousStatus?: AssetStatus | null
  newStatus?: AssetStatus | null
  previousEmployeeId?: string | null
  newEmployeeId?: string | null
  condition?: string | null
  notes?: string | null
}

export interface AssetReissueRequest {
  newEmployeeId: string
  effectiveAt: string
  conditionAtReturn?: string | null
  conditionAtAssignment?: string | null
  notes?: string | null
}

export interface SearchFilters {
  status?: AssetStatus
  type?: AssetType
  employeeId?: string
}

// ── M128 — depreciation ─────────────────────────────────────────────────────

export type DepreciationMethod = 'STRAIGHT_LINE' | 'DECLINING_BALANCE' | 'NONE'

export interface DepreciationPeriodRow {
  period: number
  periodStart: string
  openingValue: number
  depreciation: number
  closingValue: number
}

export interface AssetDepreciation {
  assetId: string
  assetName: string
  method: DepreciationMethod
  purchaseCost?: number | null
  purchaseDate?: string | null
  usefulLifeMonths?: number | null
  salvageValue?: number | null
  decliningRatePercent?: number | null
  totalDepreciation: number
  bookValueToday?: number | null
  schedule: DepreciationPeriodRow[]
}

export const assetsAdminApi = {
  search: (filters: SearchFilters = {}) =>
    api.get<Asset[]>('/assets', { params: filters }).then((r) => r.data),
  counts: () =>
    api.get<Record<AssetStatus, number>>('/assets/counts').then((r) => r.data),
  history: (id: string) =>
    api.get<AssetEventResponse[]>(`/assets/${id}/events`).then((r) => r.data),
  reissue: (id: string, body: AssetReissueRequest) =>
    api.post<Asset>(`/assets/${id}/reissue`, body).then((r) => r.data),
  depreciation: (id: string) =>
    api.get<AssetDepreciation>(`/assets/${id}/depreciation`).then((r) => r.data),
}
