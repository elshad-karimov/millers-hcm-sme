import { api } from './client'
import type { PageResponse } from './employees'

export type VacancyState =
  | 'OCCUPIED'
  | 'VACANT'
  | 'PARTIALLY_OCCUPIED'
  | 'FROZEN'
  | 'PLANNED'
  | 'CANCELLED'

export type PositionStatus = 'ACTIVE' | 'CLOSED'

export interface Position {
  id: string
  code: string
  title: string
  orgUnitId?: string | null
  orgUnitLabel?: string | null
  grade?: string | null
  jobFamily?: string | null
  jobLevel?: string | null
  approvedHeadcount: number
  occupiedHeadcount: number
  vacantHeadcount: number
  salaryMin?: number | null
  salaryMax?: number | null
  currency: string
  employmentType?: string | null
  costCentre?: string | null
  budgetCode?: string | null
  location?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  vacancyState: VacancyState
  status: PositionStatus
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface PositionRequest {
  title: string
  orgUnitId?: string
  orgUnitLabel?: string
  grade?: string
  jobFamily?: string
  jobLevel?: string
  approvedHeadcount: number
  salaryMin?: number
  salaryMax?: number
  currency?: string
  employmentType?: string
  costCentre?: string
  budgetCode?: string
  location?: string
  effectiveFrom?: string
  effectiveTo?: string
}

export const positionsApi = {
  list: (params: {
    page?: number
    size?: number
    search?: string
    orgUnitId?: string
    vacancyState?: VacancyState
    status?: PositionStatus
  }) =>
    api.get<PageResponse<Position>>('/positions', { params }).then((r) => r.data),
  get: (id: string) => api.get<Position>(`/positions/${id}`).then((r) => r.data),
  create: (payload: PositionRequest) =>
    api.post<Position>('/positions', payload).then((r) => r.data),
  update: (id: string, payload: PositionRequest) =>
    api.put<Position>(`/positions/${id}`, payload).then((r) => r.data),
  changeVacancyState: (id: string, newState: VacancyState, reason?: string) =>
    api
      .post<Position>(`/positions/${id}/vacancy-state`, { newState, reason })
      .then((r) => r.data),
  close: (id: string, reason?: string) =>
    api.post<Position>(`/positions/${id}/close`, { reason }).then((r) => r.data),
}
