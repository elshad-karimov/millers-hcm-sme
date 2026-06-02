// M78 — Concurrent status overlays + rehire.

import { api } from './client'
import type { Employee, EmploymentStatus } from './employees'

export type OverlaySource =
  | 'MANUAL'
  | 'LEAVE_REQUEST'
  | 'BUSINESS_TRIP'
  | 'PERMISSION'
  | 'DISCIPLINARY'
  | 'LIFECYCLE'

export interface StatusOverlay {
  id: string
  employeeId: string
  status: EmploymentStatus
  source: OverlaySource
  sourceId?: string | null
  effectiveFrom: string
  effectiveTo?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface StatusOverlayRequest {
  status: EmploymentStatus
  source?: OverlaySource
  sourceId?: string
  effectiveFrom: string
  effectiveTo?: string
  notes?: string
}

export interface RehireRequest {
  previousEmployeeId: string
  newHireDate: string
  reason?: string
  managerId?: string
  departmentName?: string
  positionTitle?: string
  orgUnitId?: string
  positionId?: string
}

export const statusOverlayApi = {
  list: (employeeId: string, openOnly = false) =>
    api
      .get<StatusOverlay[]>(`/employees/${employeeId}/status-overlays`, { params: { openOnly } })
      .then((r) => r.data),

  apply: (employeeId: string, req: StatusOverlayRequest) =>
    api
      .post<StatusOverlay>(`/employees/${employeeId}/status-overlays`, req)
      .then((r) => r.data),

  close: (employeeId: string, overlayId: string, closeOn?: string) =>
    api
      .post<StatusOverlay>(
        `/employees/${employeeId}/status-overlays/${overlayId}/close`,
        null,
        { params: { closeOn } },
      )
      .then((r) => r.data),

  delete: (employeeId: string, overlayId: string) =>
    api.delete(`/employees/${employeeId}/status-overlays/${overlayId}`),
}

export const rehireApi = {
  rehire: (req: RehireRequest) =>
    api.post<Employee>('/employees/rehire', req).then((r) => r.data),
}
