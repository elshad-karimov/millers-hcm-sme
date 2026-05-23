import { api } from './client'
import type { PageResponse } from './employees'

export type CycleType =
  | 'ANNUAL'
  | 'MID_YEAR'
  | 'QUARTERLY'
  | 'PROBATION'
  | 'PROJECT'
  | 'ADHOC'

export type CycleStatus =
  | 'DRAFT'
  | 'OPEN'
  | 'CLOSED'
  | 'CALIBRATING'
  | 'COMPLETED'

export type GoalCategory =
  | 'COMPANY'
  | 'DEPARTMENT'
  | 'TEAM'
  | 'INDIVIDUAL'
  | 'DEVELOPMENT'

export type GoalStatus =
  | 'DRAFT'
  | 'ACTIVE'
  | 'ON_TRACK'
  | 'AT_RISK'
  | 'BLOCKED'
  | 'ACHIEVED'
  | 'MISSED'
  | 'CANCELLED'

export type ReviewStatus =
  | 'DRAFT'
  | 'SELF_IN_PROGRESS'
  | 'SELF_SUBMITTED'
  | 'MANAGER_IN_PROGRESS'
  | 'MANAGER_SUBMITTED'
  | 'PENDING_APPROVAL'
  | 'CALIBRATING'
  | 'APPROVED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'

export type FeedbackRelationship =
  | 'PEER'
  | 'DIRECT_REPORT'
  | 'MANAGER'
  | 'SKIP_LEVEL'
  | 'CROSS_FUNCTIONAL'
  | 'EXTERNAL'
  | 'SELF'

export type FeedbackVisibility = 'NORMAL' | 'ANONYMOUS'
export type FeedbackStatus = 'DRAFT' | 'SUBMITTED'

export interface ReviewCycle {
  id: string
  code: string
  name: string
  cycleType: CycleType
  periodStart: string
  periodEnd: string
  selfReviewDue?: string | null
  managerReviewDue?: string | null
  finalDue?: string | null
  status: CycleStatus
  description?: string | null
  ratingScale?: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface ReviewCycleRequest {
  code: string
  name: string
  cycleType: CycleType
  periodStart: string
  periodEnd: string
  selfReviewDue?: string
  managerReviewDue?: string
  finalDue?: string
  description?: string
  ratingScale?: Record<string, unknown>
}

export interface Goal {
  id: string
  goalNo: string
  cycleId: string
  employeeId: string
  parentGoalId?: string | null
  title: string
  description?: string | null
  category: GoalCategory
  targetMetric?: string | null
  weightPercent: number
  progressPercent: number
  status: GoalStatus
  dueDate?: string | null
  rating?: number | null
  ratingNote?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface GoalRequest {
  cycleId: string
  employeeId: string
  parentGoalId?: string
  title: string
  description?: string
  category: GoalCategory
  targetMetric?: string
  weightPercent?: number
  progressPercent?: number
  status?: GoalStatus
  dueDate?: string
}

export interface PerformanceReview {
  id: string
  reviewNo: string
  cycleId: string
  employeeId: string
  managerId?: string | null
  status: ReviewStatus
  workflowInstanceId?: string | null
  selfRating?: number | null
  selfComments?: string | null
  selfSubmittedAt?: string | null
  managerRating?: number | null
  managerComments?: string | null
  managerSubmittedAt?: string | null
  finalRating?: number | null
  finalBand?: string | null
  calibrationNotes?: string | null
  goalScore?: number | null
  recommendation?: string | null
  bonusPercent?: number | null
  note?: string | null
  createdAt: string
  createdBy?: string | null
  closedAt?: string | null
}

export interface ReviewStartRequest {
  cycleId: string
  employeeId: string
  managerId?: string
}

export interface SelfReviewRequest {
  rating: number
  comments?: string
}

export interface ManagerReviewRequest {
  rating: number
  comments?: string
}

export interface CalibrationRequest {
  finalRating?: number
  finalBand?: string
  recommendation?: string
  bonusPercent?: number
  calibrationNotes?: string
}

export interface Feedback {
  id: string
  cycleId: string
  subjectEmployeeId: string
  authorEmployeeId?: string | null
  relationship: FeedbackRelationship
  visibility: FeedbackVisibility
  overallRating?: number | null
  strengths?: string | null
  improvements?: string | null
  comments?: string | null
  competencies?: Record<string, unknown> | null
  status: FeedbackStatus
  submittedAt?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface FeedbackRequest {
  cycleId: string
  subjectEmployeeId: string
  authorEmployeeId?: string
  relationship: FeedbackRelationship
  visibility?: FeedbackVisibility
  overallRating?: number
  strengths?: string
  improvements?: string
  comments?: string
  competencies?: Record<string, unknown>
  submit: boolean
}

export const performanceApi = {
  cycles: (status?: CycleStatus) =>
    api.get<ReviewCycle[]>('/performance/cycles', { params: { status } }).then((r) => r.data),
  cycle: (id: string) => api.get<ReviewCycle>(`/performance/cycles/${id}`).then((r) => r.data),
  createCycle: (payload: ReviewCycleRequest) =>
    api.post<ReviewCycle>('/performance/cycles', payload).then((r) => r.data),
  updateCycle: (id: string, payload: ReviewCycleRequest) =>
    api.put<ReviewCycle>(`/performance/cycles/${id}`, payload).then((r) => r.data),
  changeCycleStatus: (id: string, status: CycleStatus, reason?: string) =>
    api
      .post<ReviewCycle>(`/performance/cycles/${id}/status/${status}`, null, {
        params: { reason },
      })
      .then((r) => r.data),

  goals: (cycleId: string, employeeId?: string) =>
    api.get<Goal[]>('/performance/goals', { params: { cycleId, employeeId } }).then((r) => r.data),
  goal: (id: string) => api.get<Goal>(`/performance/goals/${id}`).then((r) => r.data),
  createGoal: (payload: GoalRequest) =>
    api.post<Goal>('/performance/goals', payload).then((r) => r.data),
  updateGoal: (id: string, payload: GoalRequest) =>
    api.put<Goal>(`/performance/goals/${id}`, payload).then((r) => r.data),
  updateGoalProgress: (id: string, progressPercent: number, status?: GoalStatus, note?: string) =>
    api
      .post<Goal>(`/performance/goals/${id}/progress`, { progressPercent, status, note })
      .then((r) => r.data),
  rateGoal: (id: string, rating: number, finalStatus?: GoalStatus, note?: string) =>
    api
      .post<Goal>(`/performance/goals/${id}/rate`, { rating, finalStatus, note })
      .then((r) => r.data),

  reviews: (params: {
    cycleId?: string
    employeeId?: string
    status?: ReviewStatus
    page?: number
    size?: number
  }) =>
    api
      .get<PageResponse<PerformanceReview>>('/performance/reviews', { params })
      .then((r) => r.data),
  review: (id: string) =>
    api.get<PerformanceReview>(`/performance/reviews/${id}`).then((r) => r.data),
  startReview: (payload: ReviewStartRequest) =>
    api.post<PerformanceReview>('/performance/reviews/start', payload).then((r) => r.data),
  submitSelf: (id: string, payload: SelfReviewRequest) =>
    api.post<PerformanceReview>(`/performance/reviews/${id}/self`, payload).then((r) => r.data),
  submitManager: (id: string, payload: ManagerReviewRequest) =>
    api.post<PerformanceReview>(`/performance/reviews/${id}/manager`, payload).then((r) => r.data),
  submitForApproval: (id: string) =>
    api.post<PerformanceReview>(`/performance/reviews/${id}/submit`).then((r) => r.data),
  calibrate: (id: string, payload: CalibrationRequest) =>
    api.post<PerformanceReview>(`/performance/reviews/${id}/calibrate`, payload).then((r) => r.data),
  closeReview: (id: string) =>
    api.post<PerformanceReview>(`/performance/reviews/${id}/close`).then((r) => r.data),

  feedback: (cycleId: string, subjectEmployeeId?: string) =>
    api
      .get<Feedback[]>('/performance/feedback', { params: { cycleId, subjectEmployeeId } })
      .then((r) => r.data),
  submitFeedback: (payload: FeedbackRequest) =>
    api.post<Feedback>('/performance/feedback', payload).then((r) => r.data),
  submitDraft: (id: string) =>
    api.post<Feedback>(`/performance/feedback/${id}/submit`).then((r) => r.data),
}
