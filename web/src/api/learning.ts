import { api } from './client'
import type { PageResponse } from './employees'

export type CourseStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type CourseCategory =
  | 'COMPLIANCE'
  | 'ONBOARDING'
  | 'TECHNICAL'
  | 'LEADERSHIP'
  | 'SOFT_SKILLS'
  | 'OTHER'

export type QuestionType = 'MULTIPLE_CHOICE' | 'MULTI_SELECT' | 'TRUE_FALSE'

export type EnrollmentStatus =
  | 'ENROLLED'
  | 'IN_PROGRESS'
  | 'PASSED'
  | 'FAILED'
  | 'WITHDRAWN'
  | 'EXPIRED'

export type EnrolledVia = 'SELF_ENROLLED' | 'ASSIGNED'

export type AttemptStatus = 'IN_PROGRESS' | 'PASSED' | 'FAILED'

export type CompetencyCategory =
  | 'TECHNICAL'
  | 'BEHAVIOURAL'
  | 'LEADERSHIP'
  | 'COMPLIANCE'
  | 'LANGUAGE'
  | 'OTHER'

export interface Course {
  id: string
  courseNo: string
  code: string
  title: string
  description?: string | null
  contentMarkdown?: string | null
  category: CourseCategory
  durationHours: number
  mandatory: boolean
  passingScore: number
  maxAttempts: number
  status: CourseStatus
  instructorId?: string | null
  validForMonths?: number | null
  coverUrl?: string | null
  publishedAt?: string | null
  archivedAt?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface CourseRequest {
  code: string
  title: string
  description?: string
  contentMarkdown?: string
  category: CourseCategory
  durationHours?: number
  mandatory: boolean
  passingScore?: number
  maxAttempts?: number
  instructorId?: string
  validForMonths?: number
  coverUrl?: string
}

export interface Choice {
  key: string
  text: string
}

export interface Question {
  id: string
  courseId: string
  questionNo: number
  questionType: QuestionType
  questionText: string
  choices: Choice[]
  correctKeys?: string[] | null
  explanation?: string | null
  points: number
}

export interface QuestionRequest {
  questionNo?: number
  questionType: QuestionType
  questionText: string
  choices: Choice[]
  correctKeys: string[]
  explanation?: string
  points?: number
}

export interface Enrollment {
  id: string
  enrollmentNo: string
  courseId: string
  employeeId: string
  status: EnrollmentStatus
  enrolledAt: string
  startedAt?: string | null
  completedAt?: string | null
  dueDate?: string | null
  attemptsUsed: number
  bestScorePercent?: number | null
  lastScorePercent?: number | null
  lastAttemptAt?: string | null
  enrolledVia: EnrolledVia
  assignedBy?: string | null
}

export interface EnrollRequest {
  courseId: string
  employeeId: string
  enrolledVia?: EnrolledVia
  dueDate?: string
}

export interface AttemptAnswerView {
  questionId: string
  selectedKeys: string[]
  correct: boolean
  pointsAwarded: number
}

export interface Attempt {
  id: string
  enrollmentId: string
  attemptNo: number
  startedAt: string
  submittedAt?: string | null
  status: AttemptStatus
  totalPoints?: number | null
  earnedPoints?: number | null
  scorePercent?: number | null
  passingScore?: number | null
  answers: AttemptAnswerView[]
}

export interface AttemptSubmitAnswer {
  questionId: string
  selectedKeys: string[]
}

export interface Certificate {
  id: string
  certificateNo: string
  enrollmentId: string
  courseId: string
  employeeId: string
  issuedAt: string
  validUntil?: string | null
  scorePercent: number
  revoked: boolean
  revokedAt?: string | null
  revokedBy?: string | null
  revokedReason?: string | null
}

export interface Competency {
  id: string
  code: string
  name: string
  description?: string | null
  category: CompetencyCategory
  active: boolean
  createdAt: string
}

export interface CompetencyRequest {
  code: string
  name: string
  description?: string
  category: CompetencyCategory
  active?: boolean
}

export interface EmployeeCompetency {
  id: string
  employeeId: string
  competencyId: string
  proficiency: number
  source: string
  sourceRef?: string | null
  awardedAt: string
  validUntil?: string | null
  // M136 — Section 16 closure
  yearsOfExperience?: number | null
  endorsedByEmployeeId?: string | null
  endorsedAt?: string | null
  endorsedLevel?: number | null
  endorsementNote?: string | null
  /** Derived: endorsedByEmployeeId != null. */
  endorsed: boolean
}

export interface EndorsementRequest {
  endorsedLevel: number
  note?: string
}

export interface CourseCompetencyMapping {
  competencyId: string
  awardedLevel: number
}

// M60: Gap analysis types
export interface PositionRequirement {
  positionId: string
  competencyId: string
  competencyCode: string
  competencyName: string
  competencyCategory: string
  requiredProficiency: number
  notes?: string | null
}

export interface CourseRecommendation {
  courseId: string
  courseCode: string
  courseTitle: string
  awardedLevel: number
}

export interface GapItem {
  competencyId: string
  competencyCode: string
  competencyName: string
  competencyCategory: string
  requiredProficiency: number
  currentProficiency: number | null  // null = never assessed
  gap: number                        // positive = deficit, 0 = met, negative = exceeds
  recommendedCourses: CourseRecommendation[]
}

export const learningApi = {
  // Courses
  courses: (params: {
    status?: CourseStatus
    category?: CourseCategory
    page?: number
    size?: number
  }) =>
    api.get<PageResponse<Course>>('/learning/courses', { params }).then((r) => r.data),
  course: (id: string) => api.get<Course>(`/learning/courses/${id}`).then((r) => r.data),
  createCourse: (payload: CourseRequest) =>
    api.post<Course>('/learning/courses', payload).then((r) => r.data),
  updateCourse: (id: string, payload: CourseRequest) =>
    api.put<Course>(`/learning/courses/${id}`, payload).then((r) => r.data),
  publishCourse: (id: string) =>
    api.post<Course>(`/learning/courses/${id}/publish`).then((r) => r.data),
  archiveCourse: (id: string) =>
    api.post<Course>(`/learning/courses/${id}/archive`).then((r) => r.data),
  questions: (id: string, includeAnswers = false) =>
    api
      .get<Question[]>(`/learning/courses/${id}/questions`, { params: { includeAnswers } })
      .then((r) => r.data),
  addQuestion: (id: string, payload: QuestionRequest) =>
    api.post<Question>(`/learning/courses/${id}/questions`, payload).then((r) => r.data),
  mapCompetency: (id: string, competencyId: string, awardedLevel: number) =>
    api
      .post(`/learning/courses/${id}/competencies`, { competencyId, awardedLevel })
      .then((r) => r.data),
  courseCompetencies: (id: string) =>
    api.get<CourseCompetencyMapping[]>(`/learning/courses/${id}/competencies`).then((r) => r.data),

  // Enrollments
  enrollments: (params: {
    courseId?: string
    employeeId?: string
    status?: EnrollmentStatus
    page?: number
    size?: number
  }) =>
    api.get<PageResponse<Enrollment>>('/learning/enrollments', { params }).then((r) => r.data),
  enrollment: (id: string) =>
    api.get<Enrollment>(`/learning/enrollments/${id}`).then((r) => r.data),
  enroll: (payload: EnrollRequest) =>
    api.post<Enrollment>('/learning/enrollments', payload).then((r) => r.data),
  attempts: (enrollmentId: string) =>
    api.get<Attempt[]>(`/learning/enrollments/${enrollmentId}/attempts`).then((r) => r.data),
  startAttempt: (enrollmentId: string) =>
    api.post<Attempt>(`/learning/enrollments/${enrollmentId}/attempts`).then((r) => r.data),
  submitAttempt: (attemptId: string, answers: AttemptSubmitAnswer[]) =>
    api
      .post<Attempt>(`/learning/enrollments/attempts/${attemptId}/submit`, { answers })
      .then((r) => r.data),
  getAttempt: (attemptId: string) =>
    api.get<Attempt>(`/learning/enrollments/attempts/${attemptId}`).then((r) => r.data),

  // Certificates
  certificates: (employeeId?: string) =>
    api.get<Certificate[]>('/learning/certificates', { params: { employeeId } }).then((r) => r.data),

  // Competencies
  competencies: (activeOnly = true) =>
    api
      .get<Competency[]>('/learning/competencies', { params: { activeOnly } })
      .then((r) => r.data),
  createCompetency: (payload: CompetencyRequest) =>
    api.post<Competency>('/learning/competencies', payload).then((r) => r.data),
  employeeCompetencies: (employeeId: string) =>
    api
      .get<EmployeeCompetency[]>(`/learning/competencies/employee/${employeeId}`)
      .then((r) => r.data),
  // M136 — years of experience + manager endorsement
  setCompetencyYears: (employeeCompetencyId: string, years: number | null) =>
    api
      .put<EmployeeCompetency>(
        `/learning/competencies/employees/${employeeCompetencyId}/years`,
        { years },
      )
      .then((r) => r.data),
  endorseCompetency: (employeeCompetencyId: string, payload: EndorsementRequest) =>
    api
      .post<EmployeeCompetency>(
        `/learning/competencies/employees/${employeeCompetencyId}/endorse`,
        payload,
      )
      .then((r) => r.data),

  // M60: Gap analysis — position requirements + per-employee report
  positionRequirements: (positionId: string) =>
    api.get<PositionRequirement[]>(`/learning/positions/${positionId}/requirements`).then((r) => r.data),
  addPositionRequirement: (positionId: string, payload: { competencyId: string; requiredProficiency: number; notes?: string }) =>
    api.post<PositionRequirement>(`/learning/positions/${positionId}/requirements`, payload).then((r) => r.data),
  removePositionRequirement: (positionId: string, competencyId: string) =>
    api.delete(`/learning/positions/${positionId}/requirements/${competencyId}`),
  gapAnalysis: (employeeId: string) =>
    api.get<GapItem[]>(`/learning/employees/${employeeId}/gap-analysis`).then((r) => r.data),
}

// ── M157: Training Plans (§8.14.2) ───────────────────────────────────────────

export type TrainingPlanType = 'DEPARTMENT' | 'ANNUAL' | 'COMPLIANCE' | 'CAREER_PATH'
export type TrainingPlanStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'

export interface TrainingPlanItemResponse {
  id: string
  courseId: string
  courseTitle?: string | null
  courseCode?: string | null
  dueDate?: string | null
  positionId?: string | null
  notes?: string | null
  sortOrder: number
}

export interface TrainingPlanResponse {
  id: string
  planNo: string
  name: string
  description?: string | null
  planType: TrainingPlanType
  status: TrainingPlanStatus
  orgUnitId?: string | null
  fiscalYear?: number | null
  deadline?: string | null
  ownerId?: string | null
  createdBy?: string | null
  createdAt: string
  activatedAt?: string | null
  completedAt?: string | null
  enrolledCount: number
  completedCount: number
  items: TrainingPlanItemResponse[]
}

export interface TrainingPlanRequest {
  name: string
  description?: string
  planType: TrainingPlanType
  orgUnitId?: string
  fiscalYear?: number
  deadline?: string
  ownerId?: string
}

export interface TrainingPlanItemRequest {
  courseId: string
  dueDate?: string
  positionId?: string
  notes?: string
  sortOrder?: number
}

export interface EnrollAllResult {
  enrolled: number
  skipped: number
}

export const trainingPlanApi = {
  list: (status?: string) =>
    api.get<TrainingPlanResponse[]>('/learning/training-plans', { params: status ? { status } : {} }).then((r) => r.data),
  get: (id: string) =>
    api.get<TrainingPlanResponse>(`/learning/training-plans/${id}`).then((r) => r.data),
  create: (body: TrainingPlanRequest) =>
    api.post<TrainingPlanResponse>('/learning/training-plans', body).then((r) => r.data),
  update: (id: string, body: TrainingPlanRequest) =>
    api.put<TrainingPlanResponse>(`/learning/training-plans/${id}`, body).then((r) => r.data),
  addItem: (planId: string, body: TrainingPlanItemRequest) =>
    api.post<TrainingPlanResponse>(`/learning/training-plans/${planId}/items`, body).then((r) => r.data),
  removeItem: (planId: string, itemId: string) =>
    api.delete(`/learning/training-plans/${planId}/items/${itemId}`),
  activate: (id: string) =>
    api.post<TrainingPlanResponse>(`/learning/training-plans/${id}/activate`).then((r) => r.data),
  complete: (id: string) =>
    api.post<TrainingPlanResponse>(`/learning/training-plans/${id}/complete`).then((r) => r.data),
  archive: (id: string) =>
    api.post<TrainingPlanResponse>(`/learning/training-plans/${id}/archive`).then((r) => r.data),
  enrollAll: (id: string) =>
    api.post<EnrollAllResult>(`/learning/training-plans/${id}/enroll-all`).then((r) => r.data),
}
