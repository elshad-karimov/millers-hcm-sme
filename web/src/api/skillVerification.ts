import { api } from './client'

export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface SkillVerificationRequest {
  id: string
  employeeId: string
  competencyId: string
  requestedLevel: number
  evidenceNotes?: string | null
  status: VerificationStatus
  verifiedByEmployeeId?: string | null
  verifiedAt?: string | null
  verificationNotes?: string | null
  createdAt: string
}

export interface SubmitVerificationRequest {
  competencyId: string
  requestedLevel: number
  evidenceNotes?: string
}

export const skillVerificationApi = {
  submit: (req: SubmitVerificationRequest) =>
    api.post<SkillVerificationRequest>('/skills/verifications', req),

  pending: () =>
    api.get<SkillVerificationRequest[]>('/skills/verifications/pending'),

  myRequests: () =>
    api.get<SkillVerificationRequest[]>('/skills/verifications/my-requests'),

  approve: (id: string, notes?: string) =>
    api.post<SkillVerificationRequest>(`/skills/verifications/${id}/approve`, {
      notes,
    }),

  reject: (id: string, notes?: string) =>
    api.post<SkillVerificationRequest>(`/skills/verifications/${id}/reject`, {
      notes,
    }),
}
