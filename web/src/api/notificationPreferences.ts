// M115 — Notification preferences API client.

import { api } from './client'

export type NotificationCategory =
  | 'WORKFLOW_APPROVAL'
  | 'WORKFLOW_OUTCOME'
  | 'EXPIRY_ALERT'
  | 'PROBATION_REVIEW'
  | 'LEARNING_REMINDER'
  | 'STALE_POOL_REMINDER'
  | 'REPORT_DELIVERY'
  | 'ANNOUNCEMENT'
  | 'TRANSACTIONAL'

export type NotificationChannel = 'EMAIL' | 'PUSH' | 'IN_APP'

export interface CategoryRow {
  category: NotificationCategory
  displayName: string
  description: string
  mutable: boolean
  channels: Partial<Record<NotificationChannel, boolean>>
}

export interface PreferenceGrid {
  username: string
  categories: CategoryRow[]
}

export const notificationPreferencesApi = {
  mine: () =>
    api.get<PreferenceGrid>('/me/notification-preferences').then((r) => r.data),
  toggle: (category: NotificationCategory, channel: NotificationChannel, enabled: boolean) =>
    api
      .post<PreferenceGrid>('/me/notification-preferences', { category, channel, enabled })
      .then((r) => r.data),
}
