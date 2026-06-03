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

export const successionApi = {
  grid: (cycleId: string) =>
    api.get<SuccessionGrid>(`/performance/succession/grid/${cycleId}`).then((r) => r.data),
  setPotential: (reviewId: string, req: PotentialRatingRequest) =>
    api.put<void>(`/performance/succession/reviews/${reviewId}/potential`, req).then((r) => r.data),
}
