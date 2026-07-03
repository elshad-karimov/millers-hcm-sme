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
  /** Non-null when linked to an LMS course for auto-rating (M49). */
  sourceCourseId?: string | null
  /** M130 — set when the goal was created by the cascade action. */
  cascadedBy?: string | null
  cascadedAt?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

/** M130 — flat tree node for the OKR view on GoalsPage. */
export interface GoalTreeNode {
  id: string
  parentGoalId?: string | null
  employeeId: string
  employeeName?: string | null
  goalNo: string
  title: string
  weightPercent: number
  progressPercent: number
  depth: number
  descendantCount: number
  alignmentPercent: number
}

/** M130 — body for the cascade action. */
export interface GoalCascadeRequest {
  employeeId: string
  weightPercent?: number | null
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
  /** Optional: link this DEVELOPMENT goal to an LMS course (M49). */
  sourceCourseId?: string
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

// ─── Rating scales (HCM_12 M388) ─────────────────────────────────────────────

export type RatingScaleType =
  | 'NUMERIC_1_5'
  | 'NUMERIC_1_10'
  | 'PERCENTAGE'
  | 'DESCRIPTIVE'
  | 'LETTER'
  | 'PASS_FAIL'

export const RATING_SCALE_TYPE_LABEL: Record<RatingScaleType, string> = {
  NUMERIC_1_5: '1–5 numeric',
  NUMERIC_1_10: '1–10 numeric',
  PERCENTAGE: 'Percentage',
  DESCRIPTIVE: 'Descriptive',
  LETTER: 'Letter grades',
  PASS_FAIL: 'Pass / Fail',
}

export interface RatingScaleValue {
  id?: string
  valueOrder?: number
  ratingValue: number
  ratingLabel: string
  description?: string | null
  minPercentage?: number | null
  maxPercentage?: number | null
  colorCode?: string | null
}

export interface RatingScaleRequest {
  scaleCode: string
  scaleName: string
  scaleType: RatingScaleType
  description?: string
  active?: boolean
  isDefault?: boolean
  values: RatingScaleValue[]
}

export interface RatingScaleResponse {
  id: string
  scaleCode: string
  scaleName: string
  scaleType: RatingScaleType
  description?: string | null
  active: boolean
  isDefault: boolean
  values: RatingScaleValue[]
  createdAt: string
}

export const ratingScalesApi = {
  list: (activeOnly = false) =>
    api
      .get<RatingScaleResponse[]>('/performance/rating-scales', { params: { activeOnly } })
      .then((r) => r.data),
  create: (req: RatingScaleRequest) =>
    api.post<RatingScaleResponse>('/performance/rating-scales', req).then((r) => r.data),
  update: (id: string, req: RatingScaleRequest) =>
    api.put<RatingScaleResponse>(`/performance/rating-scales/${id}`, req).then((r) => r.data),
}

// ─── Review templates (HCM_12 M389) ──────────────────────────────────────────

export type PerfSectionType =
  | 'GOALS'
  | 'KPI'
  | 'OKR'
  | 'COMPETENCY'
  | 'VALUES'
  | 'BEHAVIORAL'
  | 'MANAGER_COMMENTS'
  | 'EMPLOYEE_COMMENTS'
  | 'DEVELOPMENT_PLAN'
  | 'FINAL_RATING'
  | 'PROMOTION_RECOMMENDATION'
  | 'COMPENSATION_RECOMMENDATION'
  | 'SUMMARY'
  | 'SIGNATURE'

export const SECTION_TYPE_LABEL: Record<PerfSectionType, string> = {
  GOALS: 'Goals',
  KPI: 'KPIs',
  OKR: 'OKRs',
  COMPETENCY: 'Competencies',
  VALUES: 'Company Values',
  BEHAVIORAL: 'Behavioral',
  MANAGER_COMMENTS: 'Manager Comments',
  EMPLOYEE_COMMENTS: 'Employee Comments',
  DEVELOPMENT_PLAN: 'Development Plan',
  FINAL_RATING: 'Final Rating',
  PROMOTION_RECOMMENDATION: 'Promotion Recommendation',
  COMPENSATION_RECOMMENDATION: 'Compensation Recommendation',
  SUMMARY: 'Summary',
  SIGNATURE: 'Signature',
}

export const SCORING_SECTIONS: PerfSectionType[] = [
  'GOALS', 'KPI', 'OKR', 'COMPETENCY', 'VALUES', 'BEHAVIORAL',
]

export interface TemplateSection {
  id?: string
  sectionType: PerfSectionType
  sectionOrder?: number
  title?: string | null
  weightPercent?: number
  required?: boolean
  scoring?: boolean
}

export interface PerfTemplateRequest {
  templateCode: string
  templateName: string
  description?: string
  legalEntityId?: string | null
  departmentId?: string | null
  gradeId?: string | null
  employeeType?: string | null
  active?: boolean
  sections: TemplateSection[]
}

export interface PerfTemplateResponse {
  id: string
  templateCode: string
  templateName: string
  description?: string | null
  legalEntityId?: string | null
  departmentId?: string | null
  gradeId?: string | null
  employeeType?: string | null
  active: boolean
  sections: TemplateSection[]
  scoringWeightTotal: number
  createdAt: string
}

export const perfTemplatesApi = {
  list: (activeOnly = false) =>
    api
      .get<PerfTemplateResponse[]>('/performance/templates', { params: { activeOnly } })
      .then((r) => r.data),
  create: (req: PerfTemplateRequest) =>
    api.post<PerfTemplateResponse>('/performance/templates', req).then((r) => r.data),
  update: (id: string, req: PerfTemplateRequest) =>
    api.put<PerfTemplateResponse>(`/performance/templates/${id}`, req).then((r) => r.data),
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

  // M130 — OKR cascade
  goalTree: (cycleId: string) =>
    api.get<GoalTreeNode[]>('/performance/goals/tree', { params: { cycleId } }).then((r) => r.data),
  cascadeGoal: (parentGoalId: string, payload: GoalCascadeRequest) =>
    api.post<Goal>(`/performance/goals/${parentGoalId}/cascade`, payload).then((r) => r.data),

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
