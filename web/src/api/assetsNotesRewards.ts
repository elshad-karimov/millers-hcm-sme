// M72 — Assets + Notes + Rewards sub-entities on the employee profile.

import { api } from './client'

// ── Assets ──────────────────────────────────────────────────────────────────

export type AssetType =
  | 'LAPTOP'
  | 'MOBILE_PHONE'
  | 'SIM_CARD'
  | 'VEHICLE'
  | 'ACCESS_CARD'
  | 'UNIFORM'
  | 'TOOLS'
  | 'EQUIPMENT'
  | 'LOCKER'
  | 'FUEL_CARD'
  | 'CREDIT_CARD'
  | 'TABLET'
  | 'HEADSET'
  | 'OTHER'

export type AssetStatus = 'ASSIGNED' | 'RETURNED' | 'LOST' | 'DAMAGED' | 'WRITTEN_OFF'

export interface Asset {
  id: string
  employeeId: string
  assetType: AssetType
  assetIdentifier?: string | null
  assetName: string
  description?: string | null
  status: AssetStatus
  assignedAt: string
  expectedReturnDate?: string | null
  returnedAt?: string | null
  conditionAtAssignment?: string | null
  conditionAtReturn?: string | null
  returnAcceptedBy?: string | null
  custodyFormUrl?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface AssetRequest {
  assetType: AssetType
  assetName: string
  assetIdentifier?: string
  description?: string
  assignedAt: string
  expectedReturnDate?: string
  conditionAtAssignment?: string
  custodyFormUrl?: string
  notes?: string
}

export interface AssetReturnRequest {
  status: Exclude<AssetStatus, 'ASSIGNED'>
  returnedAt: string
  conditionAtReturn?: string
  notes?: string
}

// ── Notes ───────────────────────────────────────────────────────────────────

export type NoteType =
  | 'GENERAL'
  | 'CONFIDENTIAL'
  | 'MANAGER'
  | 'HR'
  | 'PERFORMANCE'
  | 'PAYROLL'
  | 'SYSTEM'

export type NoteVisibility =
  | 'ALL_HR'
  | 'MANAGER_ONLY'
  | 'HR_ONLY'
  | 'SYSTEM_ADMIN_ONLY'

export interface Note {
  id: string
  employeeId: string
  noteType: NoteType
  noteBody: string
  visibilityLevel: NoteVisibility
  pinned: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface NoteRequest {
  noteType?: NoteType
  noteBody: string
  visibilityLevel?: NoteVisibility
  pinned?: boolean
}

// ── Rewards ─────────────────────────────────────────────────────────────────

export type RewardType =
  | 'RECOGNITION'
  | 'MONETARY_AWARD'
  | 'CERTIFICATE'
  | 'APPRECIATION_LETTER'
  | 'PROMOTION_FAST_TRACK'
  | 'BONUS_RECOMMENDATION'
  | 'ACHIEVEMENT'
  | 'SPOT_AWARD'
  | 'OTHER'

export interface Reward {
  id: string
  employeeId: string
  rewardType: RewardType
  title: string
  description?: string | null
  awardValue?: number | null
  currency?: string | null
  awardedBy?: string | null
  awardedAt: string
  certificateUrl?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface RewardRequest {
  rewardType: RewardType
  title: string
  description?: string
  awardValue?: number
  currency?: string
  awardedAt: string
  certificateUrl?: string
  notes?: string
}

// ── API ─────────────────────────────────────────────────────────────────────

export const assetsNotesRewardsApi = {
  // assets
  listAssets: (employeeId: string, assignedOnly = false) =>
    api
      .get<Asset[]>(`/employees/${employeeId}/assets`, { params: { assignedOnly } })
      .then((r) => r.data),

  assignAsset: (employeeId: string, payload: AssetRequest) =>
    api.post<Asset>(`/employees/${employeeId}/assets`, payload).then((r) => r.data),

  updateAsset: (employeeId: string, assetId: string, payload: AssetRequest) =>
    api
      .put<Asset>(`/employees/${employeeId}/assets/${assetId}`, payload)
      .then((r) => r.data),

  closeAsset: (employeeId: string, assetId: string, payload: AssetReturnRequest) =>
    api
      .post<Asset>(`/employees/${employeeId}/assets/${assetId}/close`, payload)
      .then((r) => r.data),

  deleteAsset: (employeeId: string, assetId: string) =>
    api.delete(`/employees/${employeeId}/assets/${assetId}`),

  // notes
  listNotes: (employeeId: string) =>
    api.get<Note[]>(`/employees/${employeeId}/notes`).then((r) => r.data),

  createNote: (employeeId: string, payload: NoteRequest) =>
    api.post<Note>(`/employees/${employeeId}/notes`, payload).then((r) => r.data),

  updateNote: (employeeId: string, noteId: string, payload: NoteRequest) =>
    api
      .put<Note>(`/employees/${employeeId}/notes/${noteId}`, payload)
      .then((r) => r.data),

  deleteNote: (employeeId: string, noteId: string) =>
    api.delete(`/employees/${employeeId}/notes/${noteId}`),

  // rewards
  listRewards: (employeeId: string) =>
    api.get<Reward[]>(`/employees/${employeeId}/rewards`).then((r) => r.data),

  createReward: (employeeId: string, payload: RewardRequest) =>
    api.post<Reward>(`/employees/${employeeId}/rewards`, payload).then((r) => r.data),

  updateReward: (employeeId: string, rewardId: string, payload: RewardRequest) =>
    api
      .put<Reward>(`/employees/${employeeId}/rewards/${rewardId}`, payload)
      .then((r) => r.data),

  deleteReward: (employeeId: string, rewardId: string) =>
    api.delete(`/employees/${employeeId}/rewards/${rewardId}`),
}
