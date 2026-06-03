// M87 — Talent pool / candidate CRM API client.

import { api } from './client'

export type CandidatePoolStatus =
  | 'ACTIVE'
  | 'PASSIVE'
  | 'ARCHIVED'
  | 'DO_NOT_CONTACT'

export type CandidateNoteKind =
  | 'NOTE'
  | 'CALL'
  | 'EMAIL'
  | 'MEETING'
  | 'EVENT'
  | 'REFERRAL'
  | 'OTHER'

export interface PoolCandidateRow {
  id: string
  candidateNo: string
  firstName: string
  lastName: string
  email?: string | null
  phone?: string | null
  source?: string | null
  experienceYears?: number | null
  expectedSalary?: number | null
  currency?: string | null
  poolStatus: CandidatePoolStatus
  lastContactedAt?: string | null
  createdAt: string
  tags: string[]
}

export interface PoolSearchResponse {
  page: number
  size: number
  totalElements: number
  totalPages: number
  content: PoolCandidateRow[]
}

export interface CandidateTag {
  id: string
  candidateId: string
  tag: string
  createdAt: string
  createdBy?: string | null
}

export interface CandidateNote {
  id: string
  candidateId: string
  kind: CandidateNoteKind
  body: string
  contactDate?: string | null
  createdAt: string
  createdBy?: string | null
}

export const talentPoolApi = {
  search: (params: {
    status?: CandidatePoolStatus
    tag?: string[]
    q?: string
    page?: number
    size?: number
  }) =>
    api
      .get<PoolSearchResponse>('/recruitment/talent-pool/search', { params })
      .then((r) => r.data),

  knownTags: () =>
    api.get<string[]>('/recruitment/talent-pool/tags').then((r) => r.data),

  // Per-candidate tag management
  tagsOf: (candidateId: string) =>
    api
      .get<CandidateTag[]>(`/recruitment/candidates/${candidateId}/tags`)
      .then((r) => r.data),
  addTag: (candidateId: string, tag: string) =>
    api
      .post<CandidateTag>(`/recruitment/candidates/${candidateId}/tags`, { tag })
      .then((r) => r.data),
  removeTag: (candidateId: string, tagId: string) =>
    api.delete(`/recruitment/candidates/${candidateId}/tags/${tagId}`),

  // Per-candidate notes
  notesOf: (candidateId: string) =>
    api
      .get<CandidateNote[]>(`/recruitment/candidates/${candidateId}/notes`)
      .then((r) => r.data),
  addNote: (
    candidateId: string,
    req: { kind: CandidateNoteKind; body: string; contactDate?: string },
  ) =>
    api
      .post<CandidateNote>(`/recruitment/candidates/${candidateId}/notes`, req)
      .then((r) => r.data),

  // Pool status
  changePoolStatus: (
    candidateId: string,
    req: { newStatus: CandidatePoolStatus; reason?: string },
  ) =>
    api
      .post(`/recruitment/candidates/${candidateId}/pool-status`, req)
      .then((r) => r.data),
}
