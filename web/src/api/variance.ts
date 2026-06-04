// M113 — Roster variance dashboard.

import { api } from './client'

export type VarianceCategory =
  | 'NO_SHOW'
  | 'LATE'
  | 'EARLY_LEAVE'
  | 'UNPLANNED_OT'
  | 'ON_TIME'
  | 'NOT_APPLICABLE'

export interface VarianceCell {
  employeeId: string
  workDate: string
  category: VarianceCategory
  lateMinutes: number
  earlyMinutes: number
  overtimeMinutes: number
}

export interface EmployeeRoll {
  employeeId: string
  employeeName?: string | null
  orgUnitLabel?: string | null
  rosteredDays: number
  onTime: number
  late: number
  earlyLeave: number
  unplannedOt: number
  noShow: number
  totalLateMinutes: number
  totalEarlyMinutes: number
  totalOvertimeMinutes: number
}

export interface VarianceReport {
  from: string
  to: string
  rosteredRowsScanned: number
  totals: Partial<Record<VarianceCategory, number>>
  byEmployee: EmployeeRoll[]
  cells: VarianceCell[]
}

export const VARIANCE_COLOR: Record<VarianceCategory, string> = {
  NO_SHOW: '#ff4d4f',
  LATE: '#fa8c16',
  EARLY_LEAVE: '#faad14',
  UNPLANNED_OT: '#722ed1',
  ON_TIME: '#52c41a',
  NOT_APPLICABLE: '#d9d9d9',
}

export const VARIANCE_LABEL: Record<VarianceCategory, string> = {
  NO_SHOW: 'No-show',
  LATE: 'Late',
  EARLY_LEAVE: 'Left early',
  UNPLANNED_OT: 'Unplanned OT',
  ON_TIME: 'On time',
  NOT_APPLICABLE: 'N/A',
}

export const varianceApi = {
  report: (from: string, to: string) =>
    api.get<VarianceReport>('/attendance/variance', { params: { from, to } }).then((r) => r.data),
}
