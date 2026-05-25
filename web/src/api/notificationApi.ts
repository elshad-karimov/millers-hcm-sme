import { api } from './client'

const BASE = '/notifications'

export interface NotificationItem {
  id: string
  title: string
  body: string
  module: string | null
  entityType: string | null
  entityId: string | null
  readAt: string | null
  createdAt: string
  channel: 'EMAIL' | 'PUSH' | 'IN_APP'
}

export interface NotificationPage {
  content: NotificationItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const listNotifications = (params: { page?: number; size?: number }) =>
  api.get<NotificationPage>(BASE, { params })

export const getUnreadCount = () =>
  api.get<{ count: number }>(`${BASE}/unread-count`)

export const markRead = (id: string) =>
  api.patch(`${BASE}/${id}/read`, {})

export const markAllRead = () =>
  api.post(`${BASE}/read-all`, {})

export const registerDeviceToken = (fcmToken: string, platform = 'FLUTTER') =>
  api.post(`${BASE}/device-token`, { fcmToken, platform })

export const deregisterDeviceToken = (fcmToken: string) =>
  api.delete(`${BASE}/device-token`, { data: { fcmToken } })
