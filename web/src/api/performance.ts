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
  /** M392 — NOT_SUBMITTED | PENDING_APPROVAL | APPROVED | REJECTED. */
  approvalStatus: GoalApprovalStatus
  workflowInstanceId?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

// M392 — goal-plan approval + §6.4 progress trail
export type GoalApprovalStatus = 'NOT_SUBMITTED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'

export interface GoalProgressUpdate {
  id: string
  oldProgress?: number | null
  newProgress: number
  oldStatus?: string | null
  newStatus?: string | null
  note?: string | null
  recordedBy?: string | null
  recordedAt: string
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

// ─── KPI library + assignments (HCM_12 M390) ─────────────────────────────────

export type KpiFrequency = 'MONTHLY' | 'QUARTERLY' | 'SEMI_ANNUAL' | 'ANNUAL'
export type KpiScoringModel = 'LINEAR' | 'THRESHOLD'
export type KpiDataSource = 'MANUAL' | 'MANAGER' | 'EMPLOYEE' | 'IMPORT' | 'EXTERNAL'
export type KpiAssignmentStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'MEASURED' | 'CANCELLED'

export const KPI_FREQUENCY_LABEL: Record<KpiFrequency, string> = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  SEMI_ANNUAL: 'Semi-annual',
  ANNUAL: 'Annual',
}

export const KPI_SCORING_LABEL: Record<KpiScoringModel, string> = {
  LINEAR: 'Linear (achievement ÷ 20)',
  THRESHOLD: 'Threshold bands (110/100/80/60)',
}

export const KPI_DATA_SOURCE_LABEL: Record<KpiDataSource, string> = {
  MANUAL: 'Manual entry',
  MANAGER: 'Manager reported',
  EMPLOYEE: 'Employee reported',
  IMPORT: 'File import',
  EXTERNAL: 'External system',
}

export interface KpiRequest {
  kpiCode: string
  kpiName: string
  category?: string | null
  description?: string | null
  measurementUnit?: string | null
  defaultTarget?: number | null
  minThreshold?: number | null
  maxThreshold?: number | null
  frequency?: KpiFrequency
  scoringModel?: KpiScoringModel
  dataSource?: KpiDataSource
  active?: boolean
}

export interface KpiResponse {
  id: string
  kpiCode: string
  kpiName: string
  category?: string | null
  description?: string | null
  measurementUnit?: string | null
  defaultTarget?: number | null
  minThreshold?: number | null
  maxThreshold?: number | null
  frequency: KpiFrequency
  scoringModel: KpiScoringModel
  dataSource: KpiDataSource
  active: boolean
  createdAt: string
}

export interface KpiAssignRequest {
  kpiId: string
  cycleId: string
  employeeId: string
  assignedTarget: number
  weightPercent?: number
}

export interface KpiMeasureRequest {
  actualValue: number
  periodLabel?: string
  note?: string
}

export interface KpiAssignmentResponse {
  id: string
  kpiId: string
  kpiCode?: string | null
  kpiName?: string | null
  measurementUnit?: string | null
  scoringModel?: KpiScoringModel | null
  cycleId: string
  employeeId: string
  employeeName?: string | null
  assignedTarget: number
  weightPercent?: number | null
  actualValue?: number | null
  achievementPercent?: number | null
  rating?: number | null
  status: KpiAssignmentStatus
  createdAt: string
}

export interface KpiResultResponse {
  id: string
  periodLabel?: string | null
  actualValue: number
  achievementPercent?: number | null
  rating?: number | null
  note?: string | null
  recordedBy?: string | null
  recordedAt: string
}

export const kpisApi = {
  list: (activeOnly = false) =>
    api.get<KpiResponse[]>('/performance/kpis', { params: { activeOnly } }).then((r) => r.data),
  create: (req: KpiRequest) =>
    api.post<KpiResponse>('/performance/kpis', req).then((r) => r.data),
  update: (id: string, req: KpiRequest) =>
    api.put<KpiResponse>(`/performance/kpis/${id}`, req).then((r) => r.data),
  assignments: (params: { cycleId?: string; employeeId?: string }) =>
    api
      .get<KpiAssignmentResponse[]>('/performance/kpis/assignments', { params })
      .then((r) => r.data),
  assign: (req: KpiAssignRequest) =>
    api.post<KpiAssignmentResponse>('/performance/kpis/assignments', req).then((r) => r.data),
  measure: (id: string, req: KpiMeasureRequest) =>
    api
      .post<KpiAssignmentResponse>(`/performance/kpis/assignments/${id}/measure`, req)
      .then((r) => r.data),
  cancelAssignment: (id: string) =>
    api.post<KpiAssignmentResponse>(`/performance/kpis/assignments/${id}/cancel`).then((r) => r.data),
  history: (id: string) =>
    api.get<KpiResultResponse[]>(`/performance/kpis/assignments/${id}/history`).then((r) => r.data),
}

// ─── OKR (HCM_12 M391) ────────────────────────────────────────────────────────

export type OkrLevel =
  | 'COMPANY'
  | 'LEGAL_ENTITY'
  | 'BUSINESS_UNIT'
  | 'DEPARTMENT'
  | 'TEAM'
  | 'INDIVIDUAL'
export type OkrStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'
export type OkrConfidence = 'HIGH' | 'MEDIUM' | 'LOW'
export type OkrMeasurementType = 'NUMBER' | 'PERCENT' | 'CURRENCY' | 'BOOLEAN'
export type OkrKrStatus = 'ACTIVE' | 'DONE' | 'AT_RISK' | 'CANCELLED'

export const OKR_LEVEL_LABEL: Record<OkrLevel, string> = {
  COMPANY: 'Company',
  LEGAL_ENTITY: 'Legal entity',
  BUSINESS_UNIT: 'Business unit',
  DEPARTMENT: 'Department',
  TEAM: 'Team',
  INDIVIDUAL: 'Individual',
}

export interface OkrObjectiveRequest {
  title: string
  description?: string
  okrLevel: OkrLevel
  parentId?: string | null
  ownerEmployeeId?: string | null
  orgUnitId?: string | null
  legalEntityId?: string | null
  cycleId?: string | null
  periodStart?: string | null
  periodEnd?: string | null
  dueDate?: string | null
  confidence?: OkrConfidence | null
}

export interface OkrKeyResultRequest {
  title: string
  measurementType?: OkrMeasurementType
  baselineValue?: number
  targetValue: number
  weightPercent?: number
  confidence?: OkrConfidence | null
  ownerEmployeeId?: string | null
  dueDate?: string | null
}

export interface OkrCheckInRequest {
  keyResultId?: string
  currentValue?: number
  confidence?: OkrConfidence
  status?: OkrKrStatus
  comment?: string
}

export interface OkrKeyResultResponse {
  id: string
  title: string
  measurementType: OkrMeasurementType
  baselineValue: number
  targetValue: number
  currentValue?: number | null
  progressPercent: number
  weightPercent: number
  confidence?: OkrConfidence | null
  ownerEmployeeId?: string | null
  dueDate?: string | null
  status: OkrKrStatus
}

export interface OkrObjectiveResponse {
  id: string
  title: string
  description?: string | null
  okrLevel: OkrLevel
  parentId?: string | null
  parentTitle?: string | null
  ownerEmployeeId?: string | null
  ownerName?: string | null
  orgUnitId?: string | null
  legalEntityId?: string | null
  cycleId?: string | null
  periodStart?: string | null
  periodEnd?: string | null
  dueDate?: string | null
  status: OkrStatus
  progressPercent: number
  confidence?: OkrConfidence | null
  keyResults: OkrKeyResultResponse[]
  createdAt: string
}

export interface OkrCheckInResponse {
  id: string
  keyResultId?: string | null
  oldValue?: number | null
  newValue?: number | null
  confidence?: OkrConfidence | null
  comment?: string | null
  recordedBy?: string | null
  recordedAt: string
}

export const okrsApi = {
  list: (params: { cycleId?: string; level?: OkrLevel; ownerEmployeeId?: string } = {}) =>
    api.get<OkrObjectiveResponse[]>('/performance/okrs', { params }).then((r) => r.data),
  get: (id: string) => api.get<OkrObjectiveResponse>(`/performance/okrs/${id}`).then((r) => r.data),
  create: (req: OkrObjectiveRequest) =>
    api.post<OkrObjectiveResponse>('/performance/okrs', req).then((r) => r.data),
  update: (id: string, req: OkrObjectiveRequest) =>
    api.put<OkrObjectiveResponse>(`/performance/okrs/${id}`, req).then((r) => r.data),
  changeStatus: (id: string, status: OkrStatus) =>
    api.post<OkrObjectiveResponse>(`/performance/okrs/${id}/status/${status}`).then((r) => r.data),
  addKeyResult: (id: string, req: OkrKeyResultRequest) =>
    api.post<OkrKeyResultResponse>(`/performance/okrs/${id}/key-results`, req).then((r) => r.data),
  updateKeyResult: (krId: string, req: OkrKeyResultRequest) =>
    api.put<OkrKeyResultResponse>(`/performance/okrs/key-results/${krId}`, req).then((r) => r.data),
  checkIn: (id: string, req: OkrCheckInRequest) =>
    api.post<OkrObjectiveResponse>(`/performance/okrs/${id}/check-ins`, req).then((r) => r.data),
  checkIns: (id: string) =>
    api.get<OkrCheckInResponse[]>(`/performance/okrs/${id}/check-ins`).then((r) => r.data),
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
  // M392 — submit the employee's DRAFT goal plan for manager approval (§6.2/§37.5)
  submitGoalPlan: (cycleId: string, employeeId: string) =>
    api.post<Goal[]>('/performance/goals/submit', { cycleId, employeeId }).then((r) => r.data),
  // M392 — §6.4 progress-update trail
  goalProgressHistory: (id: string) =>
    api
      .get<GoalProgressUpdate[]>(`/performance/goals/${id}/progress-history`)
      .then((r) => r.data),
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
