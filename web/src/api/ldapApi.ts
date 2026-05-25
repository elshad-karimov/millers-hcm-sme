import { api } from './client'

const BASE = '/admin/ldap'

export interface LdapStatus {
  setupStatus: 'configured' | 'not_configured'
  providerId: string | null
  providerName: string
  enabled: boolean
  connectionUrl: string
  usersDn: string
  lastFullSync: number
  lastChangedSync: number
  editMode: string
}

export interface LdapSyncResult {
  action: string
  added: number
  updated: number
  removed: number
  failed: number
}

export const ldapApi = {
  status: (): Promise<LdapStatus> =>
    api.get<LdapStatus>(`${BASE}/status`).then((r) => r.data),

  setup: (): Promise<void> =>
    api.post(`${BASE}/setup`).then(() => undefined),

  sync: (type: 'full' | 'changed' = 'full'): Promise<LdapSyncResult> =>
    api.post<LdapSyncResult>(`${BASE}/sync?type=${type}`).then((r) => r.data),
}
