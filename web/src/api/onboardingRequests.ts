// M301 — Onboarding equipment/workspace provisioning requests (PRD §1 / §3 IT/Facilities).

import { api } from './client'
import type { AssetType } from './assetsNotesRewards'

export type ResourceRequestCategory =
  | 'EQUIPMENT' | 'WORKSPACE' | 'IT_ACCOUNT' | 'ACCESS_CARD' | 'OTHER'
export type ResourceRequestStatus =
  | 'REQUESTED' | 'IN_PROGRESS' | 'FULFILLED' | 'CANCELLED'

// M302 — IT/access provisioning sub-kind (request-tracking).
export const IT_PROVISIONING_KINDS = [
  'EMAIL_ACCOUNT', 'SYSTEM_ACCESS', 'SOFTWARE_LICENSE', 'ACCESS_CARD', 'NETWORK_VPN', 'OTHER',
] as const
export type ItProvisioningKind = (typeof IT_PROVISIONING_KINDS)[number]

export interface ResourceRequest {
  id: string
  requestNo: string
  employeeId: string
  employeeName?: string | null
  assignmentId?: string | null
  taskStatusId?: string | null
  category: ResourceRequestCategory
  title: string
  details?: string | null
  status: ResourceRequestStatus
  assetId?: string | null
  provisioningKind?: ItProvisioningKind | null
  provisionedRef?: string | null
  requestedAt: string
  fulfilledAt?: string | null
}

export interface FulfillRequest {
  createAsset: boolean
  assetType?: AssetType
  assetName?: string
  assetIdentifier?: string
  assignedAt?: string
  provisioningKind?: ItProvisioningKind
  provisionedRef?: string
  notes?: string
}

export const REQUEST_STATUS_COLOR: Record<ResourceRequestStatus, string> = {
  REQUESTED: 'gold',
  IN_PROGRESS: 'blue',
  FULFILLED: 'green',
  CANCELLED: 'default',
}

export const REQUEST_CATEGORY_COLOR: Record<ResourceRequestCategory, string> = {
  EQUIPMENT: 'geekblue',
  WORKSPACE: 'orange',
  IT_ACCOUNT: 'purple',
  ACCESS_CARD: 'volcano',
  OTHER: 'default',
}

export interface ProvisioningQueueSummary {
  totalPending: number
  totalInProgress: number
  byCategory: Partial<Record<ResourceRequestCategory, number>>
  itByKind: Partial<Record<ItProvisioningKind, number>>
}

export const onboardingRequestsApi = {
  open: () => api.get<ResourceRequest[]>('/onboarding/requests').then((r) => r.data),
  summary: () => api.get<ProvisioningQueueSummary>('/onboarding/requests/summary').then((r) => r.data),
  forEmployee: (employeeId: string) =>
    api.get<ResourceRequest[]>(`/onboarding/requests/employees/${employeeId}`).then((r) => r.data),
  updateStatus: (id: string, status: ResourceRequestStatus, details?: string, provisioningKind?: ItProvisioningKind) =>
    api.post<ResourceRequest>(`/onboarding/requests/${id}/status`, { status, details, provisioningKind }).then((r) => r.data),
  fulfill: (id: string, req: FulfillRequest) =>
    api.post<ResourceRequest>(`/onboarding/requests/${id}/fulfill`, req).then((r) => r.data),
}
