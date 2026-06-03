// M85 — Recruitment interview kits + structured scoring.

import { api } from './client'

export type InterviewStatus =
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'

export type InterviewRecommendation =
  | 'STRONG_HIRE'
  | 'HIRE'
  | 'MAYBE'
  | 'NO_HIRE'
  | 'STRONG_NO_HIRE'

// ── Kits ─────────────────────────────────────────────────────────────────────

export interface InterviewKit {
  id: string
  code: string
  name: string
  description?: string | null
  jobFamilyId?: string | null
  active: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface InterviewKitRequest {
  code: string
  name: string
  description?: string
  jobFamilyId?: string
  active?: boolean
}

// ── Questions ────────────────────────────────────────────────────────────────

export interface InterviewQuestion {
  id: string
  kitId: string
  questionText: string
  weight: number
  sortOrder: number
  required: boolean
  active: boolean
}

export interface InterviewQuestionRequest {
  questionText: string
  weight?: number
  sortOrder?: number
  required?: boolean
  active?: boolean
}

// ── Interview ────────────────────────────────────────────────────────────────

export interface Interview {
  id: string
  interviewNo: string
  applicationId: string
  kitId: string
  interviewerEmployeeId: string
  scheduledAt: string
  status: InterviewStatus
  overallScore?: number | null
  recommendation?: InterviewRecommendation | null
  overallComment?: string | null
  completedAt?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface InterviewSchedule {
  applicationId: string
  kitId: string
  interviewerEmployeeId: string
  scheduledAt: string
}

export interface InterviewFinalize {
  recommendation: InterviewRecommendation
  overallComment?: string
}

export interface ScoreUpsert {
  questionId: string
  score: number // 1..5
  comment?: string
}

export interface InterviewScore {
  id: string
  interviewId: string
  questionId: string
  score: number
  comment?: string | null
  createdAt: string
  updatedAt: string
}

export interface InterviewDetail {
  interview: Interview
  kit: InterviewKit
  questions: InterviewQuestion[]
  scores: InterviewScore[]
}

// ── API ──────────────────────────────────────────────────────────────────────

export const interviewKitsApi = {
  list: (jobFamilyId?: string, activeOnly = true) =>
    api
      .get<InterviewKit[]>('/recruitment/interview-kits', {
        params: { jobFamilyId, activeOnly },
      })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<InterviewKit>(`/recruitment/interview-kits/${id}`).then((r) => r.data),
  create: (req: InterviewKitRequest) =>
    api.post<InterviewKit>('/recruitment/interview-kits', req).then((r) => r.data),
  update: (id: string, req: InterviewKitRequest) =>
    api.put<InterviewKit>(`/recruitment/interview-kits/${id}`, req).then((r) => r.data),
  deactivate: (id: string) => api.delete(`/recruitment/interview-kits/${id}`),

  listQuestions: (kitId: string, activeOnly = false) =>
    api
      .get<InterviewQuestion[]>(`/recruitment/interview-kits/${kitId}/questions`, {
        params: { activeOnly },
      })
      .then((r) => r.data),
  addQuestion: (kitId: string, req: InterviewQuestionRequest) =>
    api
      .post<InterviewQuestion>(`/recruitment/interview-kits/${kitId}/questions`, req)
      .then((r) => r.data),
  updateQuestion: (kitId: string, questionId: string, req: InterviewQuestionRequest) =>
    api
      .put<InterviewQuestion>(
        `/recruitment/interview-kits/${kitId}/questions/${questionId}`,
        req,
      )
      .then((r) => r.data),
  deleteQuestion: (kitId: string, questionId: string) =>
    api.delete(`/recruitment/interview-kits/${kitId}/questions/${questionId}`),
}

export const interviewsApi = {
  listForApplication: (applicationId: string) =>
    api
      .get<Interview[]>('/recruitment/interviews', { params: { applicationId } })
      .then((r) => r.data),
  listForInterviewer: (interviewerEmployeeId: string, status?: InterviewStatus) =>
    api
      .get<Interview[]>('/recruitment/interviews', {
        params: { interviewerEmployeeId, status },
      })
      .then((r) => r.data),
  detail: (id: string) =>
    api.get<InterviewDetail>(`/recruitment/interviews/${id}`).then((r) => r.data),
  schedule: (req: InterviewSchedule) =>
    api.post<Interview>('/recruitment/interviews', req).then((r) => r.data),
  upsertScore: (id: string, req: ScoreUpsert) =>
    api
      .post<InterviewScore>(`/recruitment/interviews/${id}/scores`, req)
      .then((r) => r.data),
  finalize: (id: string, req: InterviewFinalize) =>
    api
      .post<Interview>(`/recruitment/interviews/${id}/finalize`, req)
      .then((r) => r.data),
  cancel: (id: string, reason?: string) =>
    api
      .post<Interview>(`/recruitment/interviews/${id}/cancel`, null, {
        params: { reason },
      })
      .then((r) => r.data),
  noShow: (id: string, reason?: string) =>
    api
      .post<Interview>(`/recruitment/interviews/${id}/no-show`, null, {
        params: { reason },
      })
      .then((r) => r.data),
}
