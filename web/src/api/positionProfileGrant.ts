// M250 — Phase F.2 — Profile grant SPA client.

import { api } from './client'
import type { ProfileItemType } from './positionProfile'

export type GrantStatus = 'PENDING' | 'ACTIVE' | 'REVOKED' | 'FAILED'

export interface ProfileGrant {
  id: string
  occupancyId: string
  profileItemId?: string | null
  employeeId: string
  positionId: string
  itemType: ProfileItemType
  label: string
  valueAmount?: number | null
  currency?: string | null
  referenceCode?: string | null
  notes?: string | null
  status: GrantStatus
  grantedAt?: string | null
  grantedBy?: string | null
  revokedAt?: string | null
  revokedBy?: string | null
  revokeReason?: string | null
  failureReason?: string | null
  // M251 — Phase F.3: when an ALLOWANCE grant fires successfully it
  // stashes the resulting employee_allowance row id here. Future
  // phases will populate this for TRAINING / EQUIPMENT etc. too.
  downstreamEntityId?: string | null
  downstreamEntityType?: string | null
  createdAt: string
  updatedAt: string
}

export const GRANT_STATUS_COLOR: Record<GrantStatus, string> = {
  PENDING: 'gold',
  ACTIVE: 'green',
  REVOKED: 'default',
  FAILED: 'red',
}

export const GRANT_STATUS_LABEL: Record<GrantStatus, string> = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  REVOKED: 'Revoked',
  FAILED: 'Failed',
}

export const positionProfileGrantApi = {
  forOccupancy: (occupancyId: string) =>
    api
      .get<ProfileGrant[]>(`/position-profile-grants/by-occupancy/${occupancyId}`)
      .then((r) => r.data),
  pendingForEmployee: (employeeId: string) =>
    api
      .get<ProfileGrant[]>(`/position-profile-grants/pending/${employeeId}`)
      .then((r) => r.data),
  markActive: (id: string) =>
    api
      .post<ProfileGrant>(`/position-profile-grants/${id}/mark-active`, {})
      .then((r) => r.data),
  revoke: (id: string, reason?: string) =>
    api
      .post<ProfileGrant>(`/position-profile-grants/${id}/revoke`, { reason })
      .then((r) => r.data),
  markFailed: (id: string, reason: string) =>
    api
      .post<ProfileGrant>(`/position-profile-grants/${id}/mark-failed`, { reason })
      .then((r) => r.data),
}
