import { api } from './client'
import type { OrgUnitLifecycleState } from './orgUnitLifecycle'

export interface HeadcountRow {
  unitId: string
  code: string
  name: string
  unitType: string
  lifecycleState?: OrgUnitLifecycleState | null
  headcountBudget?: number | null
  actualHeadcount: number
  variance?: number | null
}

export interface HeadcountReport {
  totalBudget: number
  totalActual: number
  totalVariance?: number | null
  rows: HeadcountRow[]
}

export interface HrbpCoverageRow {
  unitId: string
  code: string
  name: string
  unitType: string
  hrbpId?: string | null
  hrbpName?: string | null
  hasHrbp: boolean
}

export interface HrbpCoverageReport {
  totalUnits: number
  unitsWithHrbp: number
  unitsWithoutHrbp: number
  coveragePct: number
  rows: HrbpCoverageRow[]
}

export interface OrgDistributionReport {
  byLifecycleState: Record<string, number>
  byUnitType: Record<string, number>
}

export interface OrgUnitFlatRow {
  unitId: string
  code: string
  name: string
  unitType: string
  parentCode?: string | null
  lifecycleState?: string | null
  legalEntityCode?: string | null
  locationCode?: string | null
  hrbpEmployeeNo?: string | null
  costCentreCode?: string | null
  contactEmail?: string | null
  headcountBudget?: number | null
  actualHeadcount: number
  closureAnnouncedDate?: string | null
  closedDate?: string | null
}

export interface OrgFlatReport {
  rows: OrgUnitFlatRow[]
}

export const orgNativeReportsApi = {
  headcount: () =>
    api.get<HeadcountReport>('/reports/org/headcount').then((r) => r.data),

  hrbpCoverage: () =>
    api.get<HrbpCoverageReport>('/reports/org/hrbp-coverage').then((r) => r.data),

  distribution: () =>
    api.get<OrgDistributionReport>('/reports/org/distribution').then((r) => r.data),

  flat: () =>
    api.get<OrgFlatReport>('/reports/org/flat').then((r) => r.data),
}
