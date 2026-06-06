// M125 — Presence snapshot client.

import { api } from './client'

export type PresenceState =
  | 'ON_LEAVE'
  | 'ON_TRIP'
  | 'IN_OFFICE'
  | 'OFFLINE'
  | 'NOT_SCHEDULED'
  | 'UNKNOWN'

export interface PresenceRow {
  employeeId: string
  employeeNo: string
  employeeName: string
  department?: string | null
  managerId?: string | null
  state: PresenceState
  since?: string | null
  note?: string | null
}

export interface PresenceSnapshot {
  generatedFor: string
  generatedAt: string
  total: number
  counts: Record<PresenceState, number>
  rows: PresenceRow[]
}

export const presenceApi = {
  snapshot: () =>
    api.get<PresenceSnapshot>('/presence/snapshot').then((r) => r.data),
}
