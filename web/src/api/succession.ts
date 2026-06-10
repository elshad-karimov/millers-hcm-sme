// M92 — 9-box succession grid API.

import { api } from './client'

export type Band = 'LOW' | 'MID' | 'HIGH'

export interface GridEmployee {
  reviewId: string
  employeeId: string
  employeeName: string
  department?: string | null
  performanceRating: number
  potentialRating: number
  recommendation?: string | null
}

export interface GridCell {
  performance: Band
  potential: Band
  label: string
  count: number
  employees: GridEmployee[]
}

export interface SuccessionGrid {
  cycleId: string
  cycleName: string
  totalReviews: number
  placedReviews: number
  missingPerformance: number
  missingPotential: number
  cells: GridCell[]
}

export interface PotentialRatingRequest {
  potentialRating: number
  potentialNotes?: string
}

// M94 — bench depth report (per-manager readiness counts).

export interface BenchRow {
  managerId: string
  managerName: string
  totalReports: number
  placedReports: number
  readyNow: number
  readySoon: number
  readyLongTerm: number
  underDevelopment: number
}

export interface BenchReport {
  cycleId: string
  cycleName: string
  totalManagers: number
  rows: BenchRow[]
}

// M96 — Development drill: who's in the UNDER_DEVELOPMENT cell, and what
// learning paths are they on right now.

export interface AssignmentSummary {
  assignmentId: string
  pathId: string
  pathName: string
  status: string
  progressPercent: number
}

export interface DevelopmentEmployee {
  reviewId: string
  employeeId: string
  employeeName: string
  department?: string | null
  performanceRating: number
  potentialRating: number
  recommendation?: string | null
  activeAssignments: AssignmentSummary[]
}

export interface DevelopmentList {
  cycleId: string
  cycleName: string
  managerId?: string | null
  managerName?: string | null
  total: number
  employees: DevelopmentEmployee[]
}

// M257 — Critical Roles at Risk (PRD §31 wired into M103).
export interface CriticalRoleRow {
  positionId: string
  positionCode: string
  positionTitle: string
  orgUnitLabel?: string | null
  businessImpactScore?: number | null
  riskCategory?: string | null
  keySkillConcentration: boolean
  successorRequired: boolean
  totalNominations: number
  readyNowCount: number
  readySoonCount: number
  readyLongTermCount: number
  atRisk: boolean
  atRiskReason?: string | null
}

export interface CriticalRolesReport {
  totalCritical: number
  atRiskCount: number
  rows: CriticalRoleRow[]
}

export const successionApi = {
  grid: (cycleId: string) =>
    api.get<SuccessionGrid>(`/performance/succession/grid/${cycleId}`).then((r) => r.data),
  setPotential: (reviewId: string, req: PotentialRatingRequest) =>
    api.put<void>(`/performance/succession/reviews/${reviewId}/potential`, req).then((r) => r.data),
  bench: (cycleId: string) =>
    api.get<BenchReport>(`/performance/succession/bench/${cycleId}`).then((r) => r.data),
  development: (cycleId: string, managerId?: string) =>
    api
      .get<DevelopmentList>(`/performance/succession/development/${cycleId}`, {
        params: managerId ? { managerId } : undefined,
      })
      .then((r) => r.data),
  // M257 — snapshot of critical positions + their nomination depth.
  criticalRoles: () =>
    api.get<CriticalRolesReport>('/performance/succession/critical-roles').then((r) => r.data),
}
