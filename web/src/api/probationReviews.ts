// M73 — Probation review milestones.

import { api } from './client'

export type ProbationReviewType = 'MID_POINT' | 'FINAL'

export type ProbationReviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'

export type ProbationOutcome = 'PASSED' | 'FAILED' | 'EXTENDED'

export interface ProbationReview {
  id: string
  employeeId: string
  contractId: string
  reviewType: ProbationReviewType
  scheduledDate: string
  completedDate?: string | null
  managerEmployeeId?: string | null
  reviewerEmployeeId?: string | null
  managerFeedback?: string | null
  managerRating?: number | null
  hrFeedback?: string | null
  hrRating?: number | null
  status: ProbationReviewStatus
  outcome?: ProbationOutcome | null
  effectiveDate?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface ScheduleProbationReviewRequest {
  contractId: string
  reviewType: ProbationReviewType
  scheduledDate: string
  managerEmployeeId?: string
  reviewerEmployeeId?: string
  notes?: string
}

export interface CompleteProbationReviewRequest {
  outcome: ProbationOutcome
  completedDate: string
  effectiveDate?: string
  managerFeedback?: string
  managerRating?: number
  hrFeedback?: string
  hrRating?: number
  notes?: string
}

export const probationReviewsApi = {
  listForEmployee: (employeeId: string) =>
    api
      .get<ProbationReview[]>(`/employees/${employeeId}/probation-reviews`)
      .then((r) => r.data),

  get: (id: string) =>
    api.get<ProbationReview>(`/probation-reviews/${id}`).then((r) => r.data),

  schedule: (payload: ScheduleProbationReviewRequest) =>
    api.post<ProbationReview>('/probation-reviews', payload).then((r) => r.data),

  complete: (id: string, payload: CompleteProbationReviewRequest) =>
    api
      .post<ProbationReview>(`/probation-reviews/${id}/complete`, payload)
      .then((r) => r.data),
}
