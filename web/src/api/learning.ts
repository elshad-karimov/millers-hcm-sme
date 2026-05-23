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
}

export interface CourseCompetencyMapping {
  competencyId: string
  awardedLevel: number
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
}
