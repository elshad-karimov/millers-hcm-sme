import { api } from './client'

/** M260 — Position transfer workflow API client (PRD §40). */

export type TransferStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'

export const TRANSFER_STATUS_COLOR: Record<TransferStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'blue',
  COMPLETED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

export interface PositionTransfer {
  id: string
  positionId: string
  fromOrgUnitId?: string | null
  fromOrgUnitLabel?: string | null
  fromCostCentre?: string | null
  fromLocation?: string | null
  toOrgUnitId?: string | null
  toOrgUnitLabel?: string | null
  toCostCentre?: string | null
  toLocation?: string | null
  transferReason?: string | null
  notes?: string | null
  effectiveDate: string
  status: TransferStatus
  requestedBy?: string | null
  requestedAt?: string | null
  submittedBy?: string | null
  submittedAt?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  rejectedBy?: string | null
  rejectedAt?: string | null
  rejectReason?: string | null
  completedBy?: string | null
  completedAt?: string | null
  cancelledBy?: string | null
  cancelledAt?: string | null
  cancelReason?: string | null
  createdAt: string
  updatedAt: string
}

export interface InitiateTransferRequest {
  positionId: string
  toOrgUnitId?: string
  toCostCentre?: string
  toLocation?: string
  transferReason?: string
  notes?: string
  effectiveDate: string
}

export interface ActionRequest {
  reason?: string
}

export const positionTransferApi = {
  list: (positionId: string) =>
    api
      .get<PositionTransfer[]>('/positions/transfers', { params: { positionId } })
      .then((r) => r.data),
  initiate: (req: InitiateTransferRequest) =>
    api.post<PositionTransfer>('/positions/transfers', req).then((r) => r.data),
  submit: (id: string) =>
    api.post<PositionTransfer>(`/positions/transfers/${id}/submit`).then((r) => r.data),
  approve: (id: string) =>
    api.post<PositionTransfer>(`/positions/transfers/${id}/approve`).then((r) => r.data),
  reject: (id: string, req?: ActionRequest) =>
    api
      .post<PositionTransfer>(`/positions/transfers/${id}/reject`, req ?? {})
      .then((r) => r.data),
  complete: (id: string) =>
    api.post<PositionTransfer>(`/positions/transfers/${id}/complete`).then((r) => r.data),
  cancel: (id: string, req?: ActionRequest) =>
    api
      .post<PositionTransfer>(`/positions/transfers/${id}/cancel`, req ?? {})
      .then((r) => r.data),
}
