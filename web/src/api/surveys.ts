// M116 — Engagement surveys + eNPS API client.

import { api } from './client'

export type QuestionType =
  | 'RATING_1_5'
  | 'RATING_1_10'
  | 'BOOLEAN'
  | 'TEXT'
  | 'MULTIPLE_CHOICE'

export type CampaignStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'

export interface QuestionRequest {
  orderIndex: number
  prompt: string
  questionType: QuestionType
  metadata?: string
  required?: boolean
}

export interface QuestionResponse {
  id: string
  orderIndex: number
  prompt: string
  questionType: QuestionType
  metadata?: string | null
  required: boolean
}

export interface TemplateRequest {
  code: string
  name: string
  description?: string
  anonymous?: boolean
  active?: boolean
  questions: QuestionRequest[]
}

export interface TemplateResponse {
  id: string
  code: string
  name: string
  description?: string | null
  anonymous: boolean
  active: boolean
  questionCount: number
  questions: QuestionResponse[]
  createdAt: string
}

export interface CampaignRequest {
  templateId: string
  name: string
  description?: string
  opensOn: string
  closesOn: string
  targetAll?: boolean
}

export interface CampaignResponse {
  id: string
  templateId: string
  templateCode?: string | null
  templateName?: string | null
  anonymous: boolean
  name: string
  description?: string | null
  status: CampaignStatus
  opensOn: string
  closesOn: string
  targetAll: boolean
  responseCount: number
  createdAt: string
}

export interface AnswerRequest {
  questionId: string
  ratingValue?: number
  textValue?: string
  choiceValue?: string
}

export interface SubmitRequest {
  campaignId: string
  comment?: string
  answers: AnswerRequest[]
}

export interface QuestionAggregate {
  questionId: string
  prompt: string
  questionType: QuestionType
  answeredCount: number
  averageRating?: number | null
  netPromoterScore?: number | null
  distribution?: Record<string, number> | null
  choiceTallies?: Record<string, number> | null
  textSamples?: string[] | null
}

export interface CampaignResults {
  campaignId: string
  campaignName: string
  responseCount: number
  questions: QuestionAggregate[]
}

export const surveysAdminApi = {
  listTemplates: (activeOnly = false) =>
    api.get<TemplateResponse[]>('/engagement/templates', { params: { activeOnly } }).then((r) => r.data),
  getTemplate: (id: string) =>
    api.get<TemplateResponse>(`/engagement/templates/${id}`).then((r) => r.data),
  createTemplate: (req: TemplateRequest) =>
    api.post<TemplateResponse>('/engagement/templates', req).then((r) => r.data),
  updateTemplate: (id: string, req: TemplateRequest) =>
    api.put<TemplateResponse>(`/engagement/templates/${id}`, req).then((r) => r.data),

  listCampaigns: (status?: CampaignStatus) =>
    api
      .get<CampaignResponse[]>('/engagement/campaigns', { params: status ? { status } : {} })
      .then((r) => r.data),
  getCampaign: (id: string) =>
    api.get<CampaignResponse>(`/engagement/campaigns/${id}`).then((r) => r.data),
  createCampaign: (req: CampaignRequest) =>
    api.post<CampaignResponse>('/engagement/campaigns', req).then((r) => r.data),
  changeStatus: (id: string, status: CampaignStatus) =>
    api
      .post<CampaignResponse>(`/engagement/campaigns/${id}/status`, { status })
      .then((r) => r.data),
  results: (id: string) =>
    api.get<CampaignResults>(`/engagement/campaigns/${id}/results`).then((r) => r.data),
}

export const mySurveysApi = {
  openToday: () =>
    api.get<CampaignResponse[]>('/me/surveys').then((r) => r.data),
  template: (id: string) =>
    api.get<TemplateResponse>(`/me/surveys/templates/${id}`).then((r) => r.data),
  submit: (req: SubmitRequest) =>
    api.post<unknown>('/me/surveys/submit', req).then((r) => r.data),
}
