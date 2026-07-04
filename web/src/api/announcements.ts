import { api } from './client'

export type AnnouncementAudience = 'ALL' | 'DEPARTMENT' | 'LOCATION'

export interface Announcement {
  id: string
  title: string
  body?: string
  publishFrom: string
  publishTo?: string
  audience: AnnouncementAudience
  audienceRef?: string
  active: boolean
}

export interface CreateAnnouncement {
  title: string
  body?: string
  publishFrom: string
  publishTo?: string
  audience: AnnouncementAudience
  audienceRef?: string
}

export const announcementsApi = {
  active: () => api.get<Announcement[]>('/self/announcements'),
  list: () => api.get<Announcement[]>('/announcements'),
  get: (id: string) => api.get<Announcement>(`/announcements/${id}`),
  create: (dto: CreateAnnouncement) => api.post<Announcement>('/announcements', dto),
  update: (id: string, dto: Announcement) => api.put<Announcement>(`/announcements/${id}`, dto),
  delete: (id: string) => api.delete(`/announcements/${id}`),
}
