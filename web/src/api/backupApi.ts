import { api } from './client'

const BASE = '/admin/backups'

export interface BackupEntry {
  id: string
  startedAt: string
  finishedAt: string | null
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'VERIFIED'
  objectKey: string | null
  sizeBytes: number | null
  checksumSha256: string | null
  errorMessage: string | null
  triggeredBy: string
}

export const listBackups = (): Promise<BackupEntry[]> =>
  api.get<BackupEntry[]>(BASE).then((r) => r.data)

export const triggerBackup = (): Promise<BackupEntry> =>
  api.post<BackupEntry>(`${BASE}/trigger`, {}).then((r) => r.data)

export const verifyBackup = (id: string): Promise<BackupEntry> =>
  api.post<BackupEntry>(`${BASE}/${id}/verify`, {}).then((r) => r.data)
