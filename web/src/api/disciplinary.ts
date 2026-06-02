// M67 — Disciplinary actions + approval workflow.

import { api } from './client'

export type DisciplinaryActionType =
  | 'VERBAL_WARNING'
  | 'WRITTEN_WARNING'
  | 'FINAL_WARNING'
  | 'PENALTY'
  | 'SUSPENSION'
  | 'INVESTIGATION'
  | 'DISMISSAL'

export type DisciplinaryStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'ISSUED'
  | 'APPEALED'
  | 'CLOSED'
  | 'REJECTED'

export interface DisciplinaryAction {
  id: string
  actionNo: string
  employeeId: string
  actionType: DisciplinaryActionType
  incidentDate: string
  actionDate: string
  issuedBy?: string | null
  description?: string | null
  status: DisciplinaryStatus
  workflowInstanceId?: string | null
  linkedCaseId?: string | null
  appealFlag: boolean
  appealReason?: string | null
  appealOutcome?: string | null
  appealedAt?: string | null
  issuedAt?: string | null
  closedAt?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface DisciplinaryActionRequest {
  employeeId: string
  actionType: DisciplinaryActionType
  incidentDate: string
  actionDate: string
  description?: string
  linkedCaseId?: string
}

interface DisciplinaryPage {
  content: DisciplinaryAction[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const disciplinaryApi = {
  list: (params: {
    status?: DisciplinaryStatus
    page?: number
    size?: number
  } = {}) =>
    api.get<DisciplinaryPage>('/disciplinary', { params }).then((r) => r.data),

  get: (id: string) =>
    api.get<DisciplinaryAction>(`/disciplinary/${id}`).then((r) => r.data),

  listForEmployee: (employeeId: string) =>
    api
      .get<DisciplinaryAction[]>(`/disciplinary/by-employee/${employeeId}`)
      .then((r) => r.data),

  create: (payload: DisciplinaryActionRequest) =>
    api.post<DisciplinaryAction>('/disciplinary', payload).then((r) => r.data),

  submit: (id: string) =>
    api.post<DisciplinaryAction>(`/disciplinary/${id}/submit`).then((r) => r.data),

  issue: (id: string) =>
    api.post<DisciplinaryAction>(`/disciplinary/${id}/issue`).then((r) => r.data),

  appeal: (id: string, reason: string) =>
    api
      .post<DisciplinaryAction>(`/disciplinary/${id}/appeal`, { reason })
      .then((r) => r.data),

  close: (id: string, appealOutcome?: string) =>
    api
      .post<DisciplinaryAction>(`/disciplinary/${id}/close`, null, {
        params: { appealOutcome },
      })
      .then((r) => r.data),
}
