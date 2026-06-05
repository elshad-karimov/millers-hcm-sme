// M122 — Pre-boarding portal API clients.
//
// Two clients:
//   - preboardingApi:        HR-side, authenticated, /api/preboarding/invites/*
//   - publicPreboardingApi:  candidate-side, NO auth required, /api/public/preboarding/{token}/*
//
// The public client deliberately uses a bare axios instance (not the
// app's `api`) because the app's instance sends a Bearer token. The
// candidate has no Keycloak session — only the magic-link URL.

import axios from 'axios'
import { api } from './client'

export type PreboardingStatus =
  | 'DRAFT'
  | 'SENT'
  | 'OPENED'
  | 'SUBMITTED'
  | 'COMPLETED'
  | 'EXPIRED'
  | 'REVOKED'

export interface InviteSummary {
  id: string
  employeeId: string
  employeeName: string
  employeeNo: string
  status: PreboardingStatus
  expiresAt: string
  sentAt?: string | null
  openedAt?: string | null
  submittedAt?: string | null
  completedAt?: string | null
  completedBy?: string | null
  revokedAt?: string | null
  revokedBy?: string | null
  revokeReason?: string | null
  createdAt: string
  createdBy: string
  note?: string | null
}

export interface InviteDetail {
  summary: InviteSummary
  payload?: Record<string, unknown> | null
}

export interface IssueRequest {
  employeeId: string
  expiresInDays?: number | null
  note?: string | null
}

export interface IssueResponse {
  summary: InviteSummary
  plaintextToken: string
}

export interface CompleteRequest {
  personalInfo?: boolean | null
  emergencyContacts?: boolean | null
  dependents?: boolean | null
  note?: string | null
}

// ── Public DTOs ─────────────────────────────────────────────────────────────

export interface FormField {
  key: string
  label: string
  type: 'string' | 'email' | 'date' | 'boolean' | 'enum' | 'text'
  required: boolean
  options?: string[]
}

export interface FormSection {
  key: string
  title: string
  multiple?: boolean
  fields: FormField[]
}

export interface PublicSchema {
  version: number
  sections: FormSection[]
}

export interface PublicInfo {
  employeeName: string
  employeeNo: string
  status: PreboardingStatus
  expiresAt: string
  schema: PublicSchema
}

export interface SubmitResponse {
  status: PreboardingStatus
  submittedAt: string
}

// ── HR-side client ──────────────────────────────────────────────────────────

export const preboardingApi = {
  list: () => api.get<InviteSummary[]>('/preboarding/invites').then((r) => r.data),
  get: (id: string) => api.get<InviteDetail>(`/preboarding/invites/${id}`).then((r) => r.data),
  issue: (body: IssueRequest) =>
    api.post<IssueResponse>('/preboarding/invites', body).then((r) => r.data),
  complete: (id: string, body?: CompleteRequest) =>
    api.post<InviteSummary>(`/preboarding/invites/${id}/complete`, body ?? {}).then((r) => r.data),
  revoke: (id: string, reason?: string) =>
    api.delete<InviteSummary>(`/preboarding/invites/${id}`, { data: { reason } }).then((r) => r.data),
}

// ── Public client (no auth) ─────────────────────────────────────────────────

const publicAxios = axios.create({ baseURL: '/api' })

export const publicPreboardingApi = {
  info: (token: string) =>
    publicAxios.get<PublicInfo>(`/public/preboarding/${token}/info`).then((r) => r.data),
  submit: (token: string, payload: Record<string, unknown>) =>
    publicAxios
      .post<SubmitResponse>(`/public/preboarding/${token}/submit`, { payload })
      .then((r) => r.data),
}
