import { api } from './client'

export type AbsenceConversionStatus = 'PENDING' | 'CONVERTED' | 'DISMISSED'

export interface AbsenceScanResult {
  id: string
  employeeId: string
  employeeName: string
  employeeNo: string
  absenceDate: string
  status: AbsenceConversionStatus
  leaveTypeId?: string | null
  leaveTypeName?: string | null
  leaveRequestId?: string | null
  notes?: string | null
  resolvedBy?: string | null
  resolvedAt?: string | null
  createdAt: string
}

export interface AbsenceConvertRequest {
  employeeId: string
  absenceDates: string[]
  leaveTypeId: string
  notes?: string
}

export interface AbsenceDismissRequest {
  employeeId: string
  absenceDates: string[]
  notes?: string
}

export const unauthorizedAbsenceApi = {
  listPending: () =>
    api.get<AbsenceScanResult[]>('/leave/unauthorized-absences').then((r) => r.data),

  scan: (employeeId: string, from: string, to: string) =>
    api
      .get<AbsenceScanResult[]>('/leave/unauthorized-absences/scan', {
        params: { employeeId, from, to },
      })
      .then((r) => r.data),

  convert: (payload: AbsenceConvertRequest) =>
    api.post<AbsenceScanResult[]>('/leave/unauthorized-absences/convert', payload).then((r) => r.data),

  dismiss: (payload: AbsenceDismissRequest) =>
    api.post<AbsenceScanResult[]>('/leave/unauthorized-absences/dismiss', payload).then((r) => r.data),
}
