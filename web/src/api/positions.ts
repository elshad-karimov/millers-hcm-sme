import { api } from './client'
import type { PageResponse } from './employees'

export type VacancyState =
  | 'OCCUPIED'
  | 'VACANT'
  | 'PARTIALLY_OCCUPIED'
  | 'FROZEN'
  | 'PLANNED'
  | 'CANCELLED'

// M243 — Phase A lifecycle. ACTIVE + CLOSED kept for backward compat with
// pre-M243 rows; new positions go through the full DRAFT → ACTIVE chain.
export type PositionStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'ACTIVE'
  | 'FROZEN'
  | 'UNDER_REVIEW'
  | 'CLOSED'
  | 'ARCHIVED'

/**
 * One row of the position lifecycle journal. Each transition writes
 * exactly one of these so the audit trail is complete and ordered.
 */
export interface PositionLifecycleEvent {
  id: string
  positionId: string
  fromStatus?: PositionStatus | null
  toStatus: PositionStatus
  reason?: string | null
  comments?: string | null
  scheduledUnfreezeDate?: string | null
  actor?: string | null
  occurredAt: string
}

/** Body sent to every lifecycle action endpoint. */
export interface LifecycleActionRequest {
  reason?: string
  comments?: string
  scheduledUnfreezeDate?: string
}

export interface Position {
  id: string
  code: string
  title: string
  /** M146 / §8 — parent position; null for root-level positions */
  parentPositionId?: string | null
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
  // M254 — compliance / regulatory (PRD §44)
  establishmentNumber?: string | null
  civilServiceGrade?: string | null
  unionCategory?: string | null
  exemptStatus?: string | null
  occupationalCategory?: string | null
  laborClassification?: string | null
  legalBasisReference?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  vacancyState: VacancyState
  status: PositionStatus
  // M243 — denormalised breadcrumbs from the most recent lifecycle event.
  freezeReason?: string | null
  frozenAt?: string | null
  frozenBy?: string | null
  scheduledUnfreezeDate?: string | null
  closureReason?: string | null
  closedAt?: string | null
  closedBy?: string | null
  approvedAt?: string | null
  approvedBy?: string | null
  reviewReason?: string | null
  underReviewAt?: string | null
  underReviewBy?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface PositionRequest {
  title: string
  /** M146 / §8 — optional parent position */
  parentPositionId?: string
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
  // M254 — compliance / regulatory (PRD §44)
  establishmentNumber?: string
  civilServiceGrade?: string
  unionCategory?: string
  exemptStatus?: string
  occupationalCategory?: string
  laborClassification?: string
  legalBasisReference?: string
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

  // ── M243 — lifecycle transitions ─────────────────────────────────────
  // Single helper for the 9 actions: thin wrapper around POST
  // /positions/{id}/lifecycle/<action>. The SPA never speaks to the
  // legacy /close endpoint for new code paths — everything routes here.
  lifecycle: {
    history: (id: string) =>
      api.get<PositionLifecycleEvent[]>(`/positions/${id}/lifecycle`).then((r) => r.data),
    act: (
      id: string,
      action:
        | 'submit'
        | 'approve'
        | 'reject'
        | 'activate'
        | 'freeze'
        | 'unfreeze'
        | 'under-review'
        | 'finish-review'
        | 'close'
        | 'archive',
      body?: LifecycleActionRequest,
    ) =>
      api
        .post<Position>(`/positions/${id}/lifecycle/${action}`, body ?? {})
        .then((r) => r.data),
  },
}

/**
 * M243 — Tag colour per lifecycle state. Centralised so PositionsPage,
 * PositionFormPage, PositionControlPage all render the pill the same.
 */
export const POSITION_STATUS_COLOR: Record<PositionStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  ACTIVE: 'green',
  FROZEN: 'blue',
  UNDER_REVIEW: 'purple',
  CLOSED: 'red',
  ARCHIVED: 'default',
}

/**
 * Legal next-states per current state. Mirrors PositionLifecycleService.ALLOWED.
 * The SPA uses this to grey out impossible actions in the menu.
 */
export const POSITION_STATUS_NEXT: Record<PositionStatus, PositionStatus[]> = {
  DRAFT: ['PENDING_APPROVAL', 'CLOSED'],
  PENDING_APPROVAL: ['APPROVED', 'DRAFT', 'CLOSED'],
  APPROVED: ['ACTIVE', 'CLOSED'],
  ACTIVE: ['FROZEN', 'UNDER_REVIEW', 'CLOSED'],
  FROZEN: ['ACTIVE', 'CLOSED'],
  UNDER_REVIEW: ['ACTIVE', 'CLOSED'],
  CLOSED: ['ARCHIVED'],
  ARCHIVED: [],
}
