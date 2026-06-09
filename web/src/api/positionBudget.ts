// M244 — Position budget + funding SPA clients.
// One file owns both surfaces so consumers have a single import. The
// shared color + label maps live here too — every page that renders
// a funding pill or budget total reads from the same source of truth
// (per the "develop once, use everywhere" rule).

import { api } from './client'

// ── Funding ──────────────────────────────────────────────────────────

export type FundingStatus =
  | 'UNFUNDED'
  | 'PENDING'
  | 'PARTIALLY_FUNDED'
  | 'FUNDED'
  | 'EXPIRED'

export interface PositionFunding {
  positionId: string
  status: FundingStatus
  fundingSource?: string | null
  fundingOwner?: string | null
  fundingExpiry?: string | null
  notes?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface FundingRequest {
  status: FundingStatus
  fundingSource?: string
  fundingOwner?: string
  fundingExpiry?: string
  notes?: string
}

export const FUNDING_STATUS_COLOR: Record<FundingStatus, string> = {
  UNFUNDED: 'default',
  PENDING: 'gold',
  PARTIALLY_FUNDED: 'orange',
  FUNDED: 'green',
  EXPIRED: 'red',
}

export const FUNDING_STATUS_LABEL: Record<FundingStatus, string> = {
  UNFUNDED: 'Unfunded',
  PENDING: 'Pending funding',
  PARTIALLY_FUNDED: 'Partially funded',
  FUNDED: 'Funded',
  EXPIRED: 'Expired',
}

// ── Budget ────────────────────────────────────────────────────────────

export interface PositionBudget {
  id: string
  positionId: string
  effectiveFrom: string
  effectiveTo?: string | null
  budgetedBasicSalary: number
  budgetedAllowances: number
  budgetedEmployerTax: number
  budgetedBonus: number
  budgetedOvertime: number
  budgetedBenefits: number
  totalMonthly: number
  totalAnnual: number
  currency: string
  budgetOwner?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface BudgetRequest {
  effectiveFrom: string
  effectiveTo?: string
  budgetedBasicSalary?: number
  budgetedAllowances?: number
  budgetedEmployerTax?: number
  budgetedBonus?: number
  budgetedOvertime?: number
  budgetedBenefits?: number
  currency?: string
  budgetOwner?: string
  notes?: string
}

// ── Combined client ──────────────────────────────────────────────────

export const positionBudgetApi = {
  // Budget — versioned per position
  list: (positionId: string) =>
    api.get<PositionBudget[]>(`/positions/${positionId}/budgets`).then((r) => r.data),
  current: (positionId: string) =>
    api.get<PositionBudget | null>(`/positions/${positionId}/budgets/current`).then((r) => r.data),
  create: (positionId: string, body: BudgetRequest) =>
    api.post<PositionBudget>(`/positions/${positionId}/budgets`, body).then((r) => r.data),
  update: (positionId: string, budgetId: string, body: BudgetRequest) =>
    api.put<PositionBudget>(`/positions/${positionId}/budgets/${budgetId}`, body).then((r) => r.data),
  remove: (positionId: string, budgetId: string) =>
    api.delete<void>(`/positions/${positionId}/budgets/${budgetId}`).then(() => undefined),
}

export const positionFundingApi = {
  /** Singleton get for one position. */
  get: (positionId: string) =>
    api.get<PositionFunding>(`/positions/${positionId}/funding`).then((r) => r.data),
  upsert: (positionId: string, body: FundingRequest) =>
    api.put<PositionFunding>(`/positions/${positionId}/funding`, body).then((r) => r.data),
  /** Bulk: one map for the whole tenant — used by PositionsPage to
   *  avoid an N+1 fetch when rendering the funding column. */
  allMap: () =>
    api.get<Record<string, FundingStatus>>('/positions/funding/map').then((r) => r.data),
}
