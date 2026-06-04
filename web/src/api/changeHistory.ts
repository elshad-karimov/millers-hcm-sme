// M117 — Per-employee change-history viewer API client.
// Distinct from M76 lifecycle timeline — this aggregates employment-history
// slices + audit-log JSON diffs for the Employee record.

import { api } from './client'

export type ChangeEventCategory = 'EMPLOYMENT_CHANGE' | 'STATUS_CHANGE' | 'AUDIT'

export interface ChangeEvent {
  category: ChangeEventCategory
  eventTime: string
  effectiveDate?: string | null
  action: string
  title: string
  summary?: string | null
  actor?: string | null
  sourceModule?: string | null
  sourceEntity?: string | null
  sourceId?: string | null
  oldValue?: string | null
  newValue?: string | null
  rowId: string
}

export interface EmployeeChangeHistory {
  employeeId: string
  employeeName: string
  eventCount: number
  events: ChangeEvent[]
}

export const changeHistoryApi = {
  forEmployee: (employeeId: string) =>
    api
      .get<EmployeeChangeHistory>(`/employees/${employeeId}/change-history`)
      .then((r) => r.data),
  mine: () =>
    api.get<EmployeeChangeHistory>('/me/change-history').then((r) => r.data),
}
