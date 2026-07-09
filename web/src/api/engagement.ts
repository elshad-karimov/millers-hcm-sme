// M477-M480 — Engagement module API client (pulse, recognition, action plans, participation).

import { api } from './client'

export type PulseFrequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY'

export interface PulseSchedule {
  id?: string
  surveyTemplateId: string
  frequency: PulseFrequency
  dayOfWeek?: number // 1=Mon..7=Sun
  dayOfMonth?: number // 1-28
  active?: boolean
  lastRunAt?: string
  createdAt?: string
  updatedAt?: string
}

export type RecognitionValueTag =
  | 'TEAMWORK'
  | 'INNOVATION'
  | 'EXCELLENCE'
  | 'CUSTOMER_FOCUS'
  | 'LEADERSHIP'

export type RecognitionVisibility = 'PUBLIC' | 'PRIVATE'
export type RecognitionStatus = 'ACTIVE' | 'HIDDEN'

export interface EmployeeRecognition {
  id: string
  fromEmployeeId: string
  toEmployeeId: string
  valueTag: RecognitionValueTag
  message: string
  visibility: RecognitionVisibility
  status: RecognitionStatus
  createdAt: string
  updatedAt: string
}

export interface RecognitionWallItem {
  id: string
  fromName: string
  toName: string
  valueTag: RecognitionValueTag
  message: string
  createdAt: string
}

export interface SendRecognitionRequest {
  toEmployeeId: string
  valueTag: RecognitionValueTag
  message: string
  visibility: RecognitionVisibility
}

export type EngagementActionPlanStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export interface EngagementActionPlan {
  id?: string
  campaignId?: string
  orgUnitId?: string
  ownerUsername?: string
  title: string
  description?: string
  status?: EngagementActionPlanStatus
  dueDate?: string
  createdAt?: string
  updatedAt?: string
}

export interface EngagementActionItem {
  id?: string
  planId?: string
  description: string
  responsibleUsername?: string
  done?: boolean
  doneAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ActionPlanWithProgress {
  plan: EngagementActionPlan
  totalItems: number
  completedItems: number
  progress: number
}

export interface DepartmentParticipation {
  departmentName: string
  invited: number
  responded: number
  rate: number
  suppressed: boolean
}

export interface ParticipationAnalytics {
  overallRate: number
  totalInvited: number
  totalResponded: number
  byDepartment: DepartmentParticipation[]
}

export interface SentimentAnalytics {
  positive: number
  neutral: number
  negative: number
}

export const pulseScheduleApi = {
  listAll: (activeOnly = true) =>
    api.get<PulseSchedule[]>('/engagement/pulse-schedules', { params: { activeOnly } }).then((r) => r.data),
  get: (id: string) =>
    api.get<PulseSchedule>(`/engagement/pulse-schedules/${id}`).then((r) => r.data),
  create: (schedule: PulseSchedule) =>
    api.post<PulseSchedule>('/engagement/pulse-schedules', schedule).then((r) => r.data),
  update: (id: string, schedule: PulseSchedule) =>
    api.put<PulseSchedule>(`/engagement/pulse-schedules/${id}`, schedule).then((r) => r.data),
  delete: (id: string) =>
    api.delete(`/engagement/pulse-schedules/${id}`),
}

export const recognitionApi = {
  publicWall: (limit = 50) =>
    api.get<RecognitionWallItem[]>('/engagement/recognition/wall', { params: { limit } }).then((r) => r.data),
  mySent: () =>
    api.get<EmployeeRecognition[]>('/engagement/recognition/my-sent').then((r) => r.data),
  myReceived: () =>
    api.get<EmployeeRecognition[]>('/engagement/recognition/my-received').then((r) => r.data),
  send: (req: SendRecognitionRequest) =>
    api.post<EmployeeRecognition>('/engagement/recognition', req).then((r) => r.data),
  hide: (id: string) =>
    api.post<EmployeeRecognition>(`/engagement/recognition/${id}/hide`).then((r) => r.data),
  unhide: (id: string) =>
    api.post<EmployeeRecognition>(`/engagement/recognition/${id}/unhide`).then((r) => r.data),
}

export const actionPlanApi = {
  listAll: () =>
    api.get<EngagementActionPlan[]>('/engagement/action-plans').then((r) => r.data),
  get: (id: string) =>
    api.get<ActionPlanWithProgress>(`/engagement/action-plans/${id}`).then((r) => r.data),
  create: (plan: EngagementActionPlan) =>
    api.post<EngagementActionPlan>('/engagement/action-plans', plan).then((r) => r.data),
  update: (id: string, plan: EngagementActionPlan) =>
    api.put<EngagementActionPlan>(`/engagement/action-plans/${id}`, plan).then((r) => r.data),
  delete: (id: string) =>
    api.delete(`/engagement/action-plans/${id}`),
  listItems: (planId: string) =>
    api.get<EngagementActionItem[]>(`/engagement/action-plans/${planId}/items`).then((r) => r.data),
  addItem: (planId: string, item: EngagementActionItem) =>
    api.post<EngagementActionItem>(`/engagement/action-plans/${planId}/items`, item).then((r) => r.data),
  toggleItem: (itemId: string) =>
    api.post<EngagementActionItem>(`/engagement/action-plans/items/${itemId}/toggle`).then((r) => r.data),
  deleteItem: (itemId: string) =>
    api.delete(`/engagement/action-plans/items/${itemId}`),
}

export const participationApi = {
  getParticipation: (campaignId: string) =>
    api.get<ParticipationAnalytics>(`/engagement/campaigns/${campaignId}/participation`).then((r) => r.data),
  getSentiment: (campaignId: string) =>
    api.get<SentimentAnalytics>(`/engagement/campaigns/${campaignId}/sentiment`).then((r) => r.data),
}
