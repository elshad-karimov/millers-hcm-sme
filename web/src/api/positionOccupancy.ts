// M246 — Position occupancy + replacement workflow SPA client.

import { api } from './client'

// ── Occupancy ────────────────────────────────────────────────────────

export type OccupancyType =
  | 'PRIMARY'
  | 'SECONDARY'
  | 'ACTING'
  | 'TEMPORARY'
  | 'SECONDMENT'
  | 'INTERN'
  | 'CONTRACTOR'

export interface PositionOccupancy {
  id: string
  positionId: string
  employeeId: string
  occupancyType: OccupancyType
  fteAllocation: number
  startDate: string
  endDate?: string | null
  endReason?: string | null
  endNotes?: string | null
  homePositionId?: string | null
  actingAllowance?: number | null
  actingAllowanceCurrency?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface OccupancyRequest {
  positionId: string
  employeeId: string
  occupancyType?: OccupancyType
  fteAllocation?: number
  startDate: string
  endDate?: string
  endReason?: string
  endNotes?: string
  homePositionId?: string
  actingAllowance?: number
  actingAllowanceCurrency?: string
  notes?: string
}

export interface EndOccupancyRequest {
  endDate?: string
  reason?: string
  notes?: string
}

export const OCCUPANCY_TYPE_COLOR: Record<OccupancyType, string> = {
  PRIMARY: 'green',
  SECONDARY: 'blue',
  ACTING: 'gold',
  TEMPORARY: 'orange',
  SECONDMENT: 'purple',
  INTERN: 'cyan',
  CONTRACTOR: 'magenta',
}

export const OCCUPANCY_TYPE_LABEL: Record<OccupancyType, string> = {
  PRIMARY: 'Primary',
  SECONDARY: 'Secondary',
  ACTING: 'Acting',
  TEMPORARY: 'Temporary',
  SECONDMENT: 'Secondment',
  INTERN: 'Intern',
  CONTRACTOR: 'Contractor',
}

// ── Replacement ──────────────────────────────────────────────────────

export type ReplacementAction =
  | 'OPEN_RECRUITMENT'
  | 'INTERNAL_TRANSFER'
  | 'ACTING'
  | 'FREEZE'
  | 'CLOSE'

export type ReplacementStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'

export interface PositionReplacement {
  id: string
  positionId: string
  leavingEmployeeId: string
  leavingOccupancyId?: string | null
  reason: string
  lastWorkingDay: string
  action: ReplacementAction
  replacementEmployeeId?: string | null
  replacementStartDate?: string | null
  handoverOverlapDays?: number | null
  vacancyId?: string | null
  status: ReplacementStatus
  submittedBy?: string | null
  submittedAt?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  rejectedBy?: string | null
  rejectedAt?: string | null
  rejectReason?: string | null
  completedAt?: string | null
  cancelledAt?: string | null
  cancelReason?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface ReplacementRequest {
  positionId: string
  leavingEmployeeId: string
  leavingOccupancyId?: string
  reason: string
  lastWorkingDay: string
  action: ReplacementAction
  replacementEmployeeId?: string
  replacementStartDate?: string
  handoverOverlapDays?: number
  vacancyId?: string
  notes?: string
}

export const REPLACEMENT_STATUS_COLOR: Record<ReplacementStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  COMPLETED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

export const REPLACEMENT_STATUS_LABEL: Record<ReplacementStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  COMPLETED: 'Completed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
}

export const REPLACEMENT_ACTION_LABEL: Record<ReplacementAction, string> = {
  OPEN_RECRUITMENT: 'Open recruitment',
  INTERNAL_TRANSFER: 'Internal transfer',
  ACTING: 'Assign acting',
  FREEZE: 'Freeze position',
  CLOSE: 'Close position',
}

// ── Clients ──────────────────────────────────────────────────────────

export const positionOccupancyApi = {
  forPosition: (positionId: string) =>
    api
      .get<PositionOccupancy[]>(`/position-occupancies/by-position/${positionId}`)
      .then((r) => r.data),
  forEmployee: (employeeId: string) =>
    api
      .get<PositionOccupancy[]>(`/position-occupancies/by-employee/${employeeId}`)
      .then((r) => r.data),
  create: (body: OccupancyRequest) =>
    api.post<PositionOccupancy>('/position-occupancies', body).then((r) => r.data),
  update: (id: string, body: OccupancyRequest) =>
    api.put<PositionOccupancy>(`/position-occupancies/${id}`, body).then((r) => r.data),
  end: (id: string, body: EndOccupancyRequest) =>
    api
      .post<PositionOccupancy>(`/position-occupancies/${id}/end`, body)
      .then((r) => r.data),
  remove: (id: string) =>
    api.delete<void>(`/position-occupancies/${id}`).then(() => undefined),
}

export const positionReplacementApi = {
  forPosition: (positionId: string) =>
    api
      .get<PositionReplacement[]>(`/position-replacements/by-position/${positionId}`)
      .then((r) => r.data),
  get: (id: string) =>
    api.get<PositionReplacement>(`/position-replacements/${id}`).then((r) => r.data),
  listByStatus: (status?: ReplacementStatus) =>
    api
      .get<PositionReplacement[]>('/position-replacements', {
        params: status ? { status } : {},
      })
      .then((r) => r.data),
  create: (body: ReplacementRequest) =>
    api.post<PositionReplacement>('/position-replacements', body).then((r) => r.data),
  update: (id: string, body: ReplacementRequest) =>
    api.put<PositionReplacement>(`/position-replacements/${id}`, body).then((r) => r.data),
  submit: (id: string) =>
    api.post<PositionReplacement>(`/position-replacements/${id}/submit`, {}).then((r) => r.data),
  approve: (id: string) =>
    api.post<PositionReplacement>(`/position-replacements/${id}/approve`, {}).then((r) => r.data),
  reject: (id: string, reason: string) =>
    api
      .post<PositionReplacement>(`/position-replacements/${id}/reject`, { reason })
      .then((r) => r.data),
  complete: (id: string) =>
    api
      .post<PositionReplacement>(`/position-replacements/${id}/complete`, {})
      .then((r) => r.data),
  cancel: (id: string, reason?: string) =>
    api
      .post<PositionReplacement>(`/position-replacements/${id}/cancel`, { reason })
      .then((r) => r.data),
}
