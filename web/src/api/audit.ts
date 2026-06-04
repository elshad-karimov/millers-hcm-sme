// M114 — Audit-log browser API client.

import { api } from './client'
import type { PageResponse } from './employees'

export interface AuditLogRow {
  id: string
  createdAt: string
  actor: string
  module: string
  entityName: string
  entityId?: string | null
  action: string
  ipAddress?: string | null
  hasOldValue: boolean
  hasNewValue: boolean
}

export interface AuditLogDetail {
  id: string
  createdAt: string
  actor: string
  module: string
  entityName: string
  entityId?: string | null
  action: string
  ipAddress?: string | null
  oldValue?: string | null
  newValue?: string | null
}

export interface AuditSearchParams {
  from?: string
  to?: string
  module?: string
  entityName?: string
  entityId?: string
  action?: string
  actor?: string
  page?: number
  size?: number
}

export const auditApi = {
  search: (params: AuditSearchParams) =>
    api
      .get<PageResponse<AuditLogRow>>('/audit', { params })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<AuditLogDetail>(`/audit/${id}`).then((r) => r.data),
  forEntity: (entityName: string, entityId: string) =>
    api
      .get<AuditLogRow[]>('/audit/entity', { params: { entityName, entityId } })
      .then((r) => r.data),
  modules: () => api.get<string[]>('/audit/modules').then((r) => r.data),
  entities: (module: string) =>
    api.get<string[]>('/audit/entities', { params: { module } }).then((r) => r.data),
  actions: (module: string) =>
    api.get<string[]>('/audit/actions', { params: { module } }).then((r) => r.data),
}
