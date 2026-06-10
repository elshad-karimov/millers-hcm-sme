import { api } from './client'

/** M258 — Position budget-vs-actual variance API client. */

export type VarianceStatus = 'OVER' | 'UNDER' | 'ON_TRACK' | 'NO_BUDGET'

export interface PositionVarianceRow {
  positionId: string
  positionCode: string
  positionTitle: string
  orgUnitLabel?: string | null
  approvedHeadcount: number
  actualHeadcount: number
  budgeted: number | null
  actual: number | null
  variance: number | null
  variancePct: number | null
  currency: string | null
  status: VarianceStatus
}

export interface VarianceTotals {
  totalBudget: number | null
  totalActual: number | null
  totalVariance: number | null
  overCount: number
  underCount: number
  noBudgetCount: number
}

export interface VarianceReport {
  year: number
  month: number
  rowCount: number
  totals: VarianceTotals
  rows: PositionVarianceRow[]
}

export const positionVarianceApi = {
  fetch: (year: number, month: number) =>
    api
      .get<VarianceReport>('/positions/variance', { params: { year, month } })
      .then((r) => r.data),
}

export const VARIANCE_COLOR: Record<VarianceStatus, string> = {
  OVER: 'red',
  UNDER: 'blue',
  ON_TRACK: 'green',
  NO_BUDGET: 'gold',
}

export const VARIANCE_LABEL: Record<VarianceStatus, string> = {
  OVER: '🔴 Over budget',
  UNDER: '🔵 Under budget',
  ON_TRACK: '✅ On track',
  NO_BUDGET: '⚠ No budget',
}
