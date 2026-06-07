// M77 — HR letter template engine + self-service letter requests.

import { api } from './client'

export type LetterStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'ISSUED'
  | 'REJECTED'
  | 'CANCELLED'

export type LetterOutputFormat = 'TEXT' | 'HTML' | 'PDF'

export interface LetterTemplate {
  id: string
  code: string
  name: string
  description?: string | null
  body: string
  placeholdersJson?: Record<string, string> | null
  outputFormat: LetterOutputFormat
  requiresApproval: boolean
  active: boolean
  /** M139 — ISO 639-1 alpha-2 lowercase. */
  language?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface LetterTemplateRequest {
  code: string
  name: string
  description?: string
  body: string
  placeholdersJson?: Record<string, string>
  outputFormat?: LetterOutputFormat
  requiresApproval?: boolean
  active?: boolean
  language?: string
}

export interface LetterRequest {
  id: string
  requestNo: string
  templateId: string
  employeeId: string
  purpose?: string | null
  customFieldsJson?: Record<string, string> | null
  status: LetterStatus
  workflowInstanceId?: string | null
  renderedBody?: string | null
  attachmentId?: string | null
  requestedAt: string
  issuedAt?: string | null
  decidedBy?: string | null
  decisionComment?: string | null
  // M139 — Phase 2
  renderedPdfUrl?: string | null
  verificationToken?: string | null
  verifiedAt?: string | null
  signedBy?: string | null
  signedAt?: string | null
  language?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface LetterSubmitRequest {
  employeeId: string
  templateId: string
  purpose?: string
  customFields?: Record<string, string>
}

export interface LetterPageResponse {
  content: LetterRequest[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const letterTemplatesApi = {
  list: (activeOnly = true) =>
    api
      .get<LetterTemplate[]>('/letter-templates', { params: { activeOnly } })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<LetterTemplate>(`/letter-templates/${id}`).then((r) => r.data),
  create: (req: LetterTemplateRequest) =>
    api.post<LetterTemplate>('/letter-templates', req).then((r) => r.data),
  update: (id: string, req: LetterTemplateRequest) =>
    api.put<LetterTemplate>(`/letter-templates/${id}`, req).then((r) => r.data),
  deactivate: (id: string) => api.delete(`/letter-templates/${id}`),
}

export const letterRequestsApi = {
  list: (status?: LetterStatus, page = 0, size = 20) =>
    api
      .get<LetterPageResponse>('/letter-requests', { params: { status, page, size } })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<LetterRequest>(`/letter-requests/${id}`).then((r) => r.data),
  create: (req: LetterSubmitRequest) =>
    api.post<LetterRequest>('/letter-requests', req).then((r) => r.data),

  /** Returns the rendered body as text/plain or text/html — caller decides what to do. */
  bodyUrl: (id: string) => `/api/letter-requests/${id}/body`,
  /** M139 — PDF with signature line + QR verification code. */
  pdfUrl: (id: string) => `/api/letter-requests/${id}/pdf`,
}

// M139 — public, anonymous-accessible verify endpoint
export interface LetterVerifyResponse {
  requestNo: string
  status: string
  issuedDate?: string | null
  signedBy?: string | null
  language?: string | null
}
export const publicLetterApi = {
  verify: (token: string) =>
    api
      .get<LetterVerifyResponse>(`/public/letters/verify/${token}`)
      .then((r) => r.data),
}

export const selfLettersApi = {
  list: () => api.get<LetterRequest[]>('/self/letters').then((r) => r.data),
  submit: (req: Omit<LetterSubmitRequest, 'employeeId'>) =>
    api
      .post<LetterRequest>('/self/letters/submit', { employeeId: '', ...req })
      .then((r) => r.data),
}
