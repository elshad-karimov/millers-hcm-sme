import { api } from './client'
import type { OrgUnitResponse } from './org'

export type OrgUnitLifecycleState = 'PLANNED' | 'ACTIVE' | 'CLOSING' | 'CLOSED'

export interface ClosureRequest {
  effectiveDate?: string | null
  reason?: string | null
}

export interface ReopenRequest {
  reason?: string | null
}

export const orgUnitLifecycleApi = {
  open: (unitId: string) =>
    api.post<OrgUnitResponse>(`/org/units/${unitId}/open`).then((r) => r.data),

  announceClosure: (unitId: string, req: ClosureRequest = {}) =>
    api.post<OrgUnitResponse>(`/org/units/${unitId}/announce-closure`, req).then((r) => r.data),

  cancelClosure: (unitId: string) =>
    api.post<OrgUnitResponse>(`/org/units/${unitId}/cancel-closure`).then((r) => r.data),

  close: (unitId: string, req: ClosureRequest = {}) =>
    api.post<OrgUnitResponse>(`/org/units/${unitId}/close`, req).then((r) => r.data),

  reopen: (unitId: string, req: ReopenRequest = {}) =>
    api.post<OrgUnitResponse>(`/org/units/${unitId}/reopen`, req).then((r) => r.data),
}

export const LIFECYCLE_COLOR: Record<OrgUnitLifecycleState, string> = {
  PLANNED: 'gold',
  ACTIVE: 'green',
  CLOSING: 'orange',
  CLOSED: 'default',
}
