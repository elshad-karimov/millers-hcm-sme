// M120 — API keys admin client.
//
// Mirrors ApiKeyDtos.java. plaintextKey is returned exactly once from
// issue() — the SPA shows it in a one-time modal and then forgets it.

import { api } from './client'

export interface ApiKeySummary {
  id: string
  label: string
  description?: string | null
  ownerUser: string
  last4: string
  scopes: string[]
  rateLimitPerMin: number
  active: boolean
  expiresAt?: string | null
  lastUsedAt?: string | null
  lastUsedIp?: string | null
  usageCount: number
  createdAt: string
  revokedAt?: string | null
  revokeReason?: string | null
}

export interface IssueRequest {
  label: string
  description?: string | null
  scopes: string[]
  rateLimitPerMin?: number | null
  expiresAt?: string | null
}

export interface IssueResponse {
  summary: ApiKeySummary
  plaintextKey: string
}

export interface UsageBucket {
  minuteBucket: string
  requestCount: number
  rejectedCount: number
}

export interface UsageResponse {
  apiKeyId: string
  buckets: UsageBucket[]
  totalRequests: number
  totalRejected: number
}

export const apiKeysApi = {
  list: () => api.get<ApiKeySummary[]>('/api-keys').then((r) => r.data),
  get: (id: string) => api.get<ApiKeySummary>(`/api-keys/${id}`).then((r) => r.data),
  usage: (id: string, hours = 24) =>
    api.get<UsageResponse>(`/api-keys/${id}/usage`, { params: { hours } }).then((r) => r.data),
  issue: (body: IssueRequest) =>
    api.post<IssueResponse>('/api-keys', body).then((r) => r.data),
  revoke: (id: string, reason?: string) =>
    api.delete<ApiKeySummary>(`/api-keys/${id}`, { data: { reason } }).then((r) => r.data),
}
