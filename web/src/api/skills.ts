// M127 — Skills/competency taxonomy client.

import { api } from './client'

export type GapSeverity = 'NONE' | 'MINOR' | 'MAJOR' | 'BLOCKER'

export interface PositionCompetencyResponse {
  id: string
  positionId: string
  competencyId: string
  competencyCode?: string | null
  competencyName?: string | null
  competencyCategory?: string | null
  requiredLevel: number
  mandatory: boolean
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface PositionCompetencyRequest {
  competencyId: string
  requiredLevel: number
  mandatory?: boolean | null
  notes?: string | null
}

export interface SkillGapRow {
  competencyId: string
  competencyCode?: string | null
  competencyName?: string | null
  requiredLevel: number
  currentLevel: number
  mandatory: boolean
  severity: GapSeverity
}

export interface EmployeeGapResponse {
  employeeId: string
  positionId: string
  fitScore: number
  totalRequirements: number
  blockers: number
  majorGaps: number
  minorGaps: number
  met: number
  gaps: SkillGapRow[]
}

export interface EmployeeFitRow {
  employeeId: string
  employeeNo: string
  employeeName: string
  fitScore: number
  blockers: number
  majorGaps: number
}

export interface PositionFitResponse {
  positionId: string
  totalCandidates: number
  ranked: EmployeeFitRow[]
}

export const skillsApi = {
  requirementsFor: (positionId: string) =>
    api.get<PositionCompetencyResponse[]>(`/skills/positions/${positionId}/requirements`).then((r) => r.data),
  upsertRequirement: (positionId: string, body: PositionCompetencyRequest) =>
    api.put<PositionCompetencyResponse>(`/skills/positions/${positionId}/requirements`, body).then((r) => r.data),
  removeRequirement: (positionId: string, competencyId: string) =>
    api.delete<void>(`/skills/positions/${positionId}/requirements/${competencyId}`).then((r) => r.data),
  employeeGap: (employeeId: string, positionId: string) =>
    api.get<EmployeeGapResponse>(`/skills/employees/${employeeId}/gap/${positionId}`).then((r) => r.data),
  positionFit: (positionId: string) =>
    api.get<PositionFitResponse>(`/skills/positions/${positionId}/fit`).then((r) => r.data),
}
