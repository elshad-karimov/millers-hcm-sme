// M138 — company policy library client.

import { api } from './client'

export type PolicyStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type PolicyBodyFormat = 'MARKDOWN' | 'HTML' | 'PDF' | 'URL'

export interface PolicyRequest {
  code: string
  title: string
  summary?: string
  category: string
  bodyFormat: PolicyBodyFormat
  bodyText?: string
  attachmentUrl?: string
  effectiveFrom?: string
  effectiveTo?: string
  requiresAck: boolean
}

export interface PolicyResponse {
  id: string
  code: string
  title: string
  summary?: string | null
  category: string
  version: number
  bodyFormat: PolicyBodyFormat
  bodyText?: string | null
  attachmentUrl?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status: PolicyStatus
  requiresAck: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface SelfPolicyView {
  policy: PolicyResponse
  acknowledged: boolean
  acknowledgedAt?: string | null
}

export interface AcknowledgementResponse {
  id: string
  policyId: string
  employeeId: string
  versionAcknowledged: number
  acknowledgedAt: string
  acknowledgedIp?: string | null
}

export const policiesApi = {
  list: (status?: PolicyStatus) =>
    api.get<PolicyResponse[]>('/policies', { params: status ? { status } : {} })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<PolicyResponse>(`/policies/${id}`).then((r) => r.data),
  create: (payload: PolicyRequest) =>
    api.post<PolicyResponse>('/policies', payload).then((r) => r.data),
  update: (id: string, payload: PolicyRequest) =>
    api.put<PolicyResponse>(`/policies/${id}`, payload).then((r) => r.data),
  changeStatus: (id: string, target: PolicyStatus) =>
    api.post<PolicyResponse>(`/policies/${id}/status/${target}`).then((r) => r.data),
  acknowledgements: (id: string) =>
    api.get<AcknowledgementResponse[]>(`/policies/${id}/acknowledgements`).then((r) => r.data),

  // Self-service
  myPolicies: () =>
    api.get<SelfPolicyView[]>('/self/policies').then((r) => r.data),
  acknowledge: (id: string) =>
    api.post<AcknowledgementResponse>(`/self/policies/${id}/acknowledge`).then((r) => r.data),
}
