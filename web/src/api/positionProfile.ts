// M248 — Position profile SPA client.
// Single client + shared color/label maps used by the panel and by any
// other future consumer (employee onboarding tab, manager dashboard).

import { api } from './client'

export type ProfileItemType =
  | 'ALLOWANCE'
  | 'REQUIRED_DOCUMENT'
  | 'TRAINING'
  | 'EQUIPMENT'
  | 'ACCESS_ROLE'
  | 'CHECKLIST_ITEM'
  | 'APPROVAL_LIMIT'

export interface PositionProfileItem {
  id: string
  positionId: string
  itemType: ProfileItemType
  label: string
  valueAmount?: number | null
  currency?: string | null
  mandatory: boolean
  referenceCode?: string | null
  notes?: string | null
  sortOrder: number
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface ProfileItemRequest {
  itemType: ProfileItemType
  label: string
  valueAmount?: number
  currency?: string
  mandatory: boolean
  referenceCode?: string
  notes?: string
  sortOrder: number
}

export interface GrantPreview {
  profileItemId: string
  positionId: string
  employeeId: string
  itemType: ProfileItemType
  label: string
  valueAmount?: number | null
  currency?: string | null
  referenceCode?: string | null
  notes?: string | null
}

export const PROFILE_ITEM_TYPE_LABEL: Record<ProfileItemType, string> = {
  ALLOWANCE: 'Allowance',
  REQUIRED_DOCUMENT: 'Required document',
  TRAINING: 'Mandatory training',
  EQUIPMENT: 'Equipment',
  ACCESS_ROLE: 'Access role',
  CHECKLIST_ITEM: 'Onboarding item',
  APPROVAL_LIMIT: 'Approval limit',
}

export const PROFILE_ITEM_TYPE_COLOR: Record<ProfileItemType, string> = {
  ALLOWANCE: 'green',
  REQUIRED_DOCUMENT: 'blue',
  TRAINING: 'purple',
  EQUIPMENT: 'cyan',
  ACCESS_ROLE: 'magenta',
  CHECKLIST_ITEM: 'gold',
  APPROVAL_LIMIT: 'orange',
}

export const PROFILE_ITEM_TYPE_ICON: Record<ProfileItemType, string> = {
  ALLOWANCE: '💰',
  REQUIRED_DOCUMENT: '📄',
  TRAINING: '🎓',
  EQUIPMENT: '💻',
  ACCESS_ROLE: '🔑',
  CHECKLIST_ITEM: '✅',
  APPROVAL_LIMIT: '🖊️',
}

/** True if this item type has a meaningful {@code valueAmount}. */
export const profileItemHasAmount = (t: ProfileItemType) =>
  t === 'ALLOWANCE' || t === 'APPROVAL_LIMIT'

export const positionProfileApi = {
  list: (positionId: string) =>
    api
      .get<PositionProfileItem[]>(`/positions/${positionId}/profile`)
      .then((r) => r.data),
  create: (positionId: string, body: ProfileItemRequest) =>
    api
      .post<PositionProfileItem>(`/positions/${positionId}/profile`, body)
      .then((r) => r.data),
  update: (positionId: string, itemId: string, body: ProfileItemRequest) =>
    api
      .put<PositionProfileItem>(`/positions/${positionId}/profile/${itemId}`, body)
      .then((r) => r.data),
  remove: (positionId: string, itemId: string) =>
    api
      .delete<void>(`/positions/${positionId}/profile/${itemId}`)
      .then(() => undefined),
  cloneFrom: (positionId: string, sourcePositionId: string) =>
    api
      .post<PositionProfileItem[]>(`/positions/${positionId}/profile/clone-from`, {
        sourcePositionId,
      })
      .then((r) => r.data),
  grantPreview: (positionId: string, employeeId: string) =>
    api
      .get<GrantPreview[]>(
        `/positions/${positionId}/profile/grant-preview/${employeeId}`,
      )
      .then((r) => r.data),
}
