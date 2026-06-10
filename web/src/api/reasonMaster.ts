import { api } from './client'

/** M259 — Reason master client (PRD §22). */

export type ReasonCategory = 'VACANCY' | 'FREEZE' | 'CLOSURE' | 'REPLACEMENT'

export interface Reason {
  id: string
  category: ReasonCategory
  code: string
  label: string
  description?: string | null
  active: boolean
  sortOrder: number
}

export interface ReasonRequest {
  category: ReasonCategory
  code: string
  label: string
  description?: string
  active?: boolean
  sortOrder?: number
}

export const reasonMasterApi = {
  list: (category: ReasonCategory, includeInactive = false) =>
    api
      .get<Reason[]>('/staffing/reasons', { params: { category, includeInactive } })
      .then((r) => r.data),
  create: (req: ReasonRequest) =>
    api.post<Reason>('/staffing/reasons', req).then((r) => r.data),
  update: (id: string, req: ReasonRequest) =>
    api.put<Reason>(`/staffing/reasons/${id}`, req).then((r) => r.data),
  deactivate: (id: string) =>
    api.delete(`/staffing/reasons/${id}`).then((r) => r.data),
}
