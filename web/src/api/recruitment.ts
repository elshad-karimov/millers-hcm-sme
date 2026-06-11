import { api } from './client'
import type { PageResponse } from './employees'

export type VacancyStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'OPEN'
  | 'PUBLISHED'
  | 'PAUSED'
  | 'ON_HOLD'
  | 'CLOSED'
  | 'FILLED'
  | 'CANCELLED'

// M274 — requisition taxonomy (Recruitment PRD §4)
export type RequisitionType =
  | 'NEW_HEADCOUNT'
  | 'REPLACEMENT'
  | 'TEMPORARY'
  | 'PROJECT'
  | 'SEASONAL'
  | 'INTERNSHIP'
  | 'CONTRACTOR'
  | 'MASS_HIRING'
  | 'EXECUTIVE'
  | 'INTERNAL'

export type HiringReason =
  | 'NEW_POSITION'
  | 'RESIGNATION'
  | 'TERMINATION'
  | 'RETIREMENT'
  | 'TRANSFER'
  | 'PROMOTION'
  | 'DEPARTMENT_EXPANSION'
  | 'NEW_BRANCH'
  | 'NEW_PROJECT'
  | 'SEASONAL_DEMAND'
  | 'BUSINESS_GROWTH'
  | 'COMPLIANCE_REQUIREMENT'
  | 'WORKLOAD_INCREASE'
  | 'OTHER'

export type CandidateSource =
  | 'LINKEDIN'
  | 'REFERRAL'
  | 'WEBSITE'
  | 'AGENCY'
  | 'JOB_BOARD'
  | 'INTERNAL'
  | 'OTHER'

export type ApplicationStage =
  | 'CV_SCREENING'
  | 'HR_INTERVIEW'
  | 'TECHNICAL_INTERVIEW'
  | 'FINAL_INTERVIEW'
  | 'OFFER'
  | 'HIRED'
  | 'REJECTED'
  | 'WITHDRAWN'

export type ApplicationStatus = 'IN_PROGRESS' | 'HIRED' | 'REJECTED' | 'WITHDRAWN'

export type OfferStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'SENT'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'RESCINDED'

export type Recommendation = 'STRONG_HIRE' | 'HIRE' | 'NO_HIRE' | 'STRONG_NO_HIRE'

export interface Vacancy {
  id: string
  vacancyNo: string
  title: string
  positionId?: string | null
  department?: string | null
  location?: string | null
  openings: number
  description?: string | null
  requirements?: string | null
  salaryMin?: number | null
  salaryMax?: number | null
  currency: string
  hiringManagerId?: string | null
  recruiterId?: string | null
  status: VacancyStatus
  // M274 — requisition fields
  requisitionType: RequisitionType
  hiringReason?: HiringReason | null
  targetStartDate?: string | null
  costCentre?: string | null
  employmentType?: string | null
  replacedEmployeeId?: string | null
  // M275 — approval workflow instance (null until first submit)
  workflowInstanceId?: string | null
  // M277 — confidential requisition flag
  confidential: boolean
  openingDate?: string | null
  closingDate?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface VacancyRequest {
  title: string
  positionId?: string
  department?: string
  location?: string
  openings: number
  description?: string
  requirements?: string
  salaryMin?: number
  salaryMax?: number
  currency?: string
  hiringManagerId?: string
  recruiterId?: string
  openingDate?: string
  closingDate?: string
  // M274 — requisition fields
  requisitionType?: RequisitionType
  hiringReason?: HiringReason
  targetStartDate?: string
  costCentre?: string
  employmentType?: string
  replacedEmployeeId?: string
  // M277 — confidential requisition
  confidential?: boolean
}

export interface Candidate {
  id: string
  candidateNo: string
  firstName: string
  lastName: string
  middleName?: string | null
  email?: string | null
  phone?: string | null
  source?: CandidateSource | null
  cvUrl?: string | null
  experienceYears?: number | null
  expectedSalary?: number | null
  currency: string
  skills?: string | null
  notes?: string | null
  createdAt: string
  updatedAt: string
}

export interface CandidateRequest {
  firstName: string
  lastName: string
  middleName?: string
  email?: string
  phone?: string
  source?: CandidateSource
  cvUrl?: string
  experienceYears?: number
  expectedSalary?: number
  currency?: string
  skills?: string
  notes?: string
}

export interface Application {
  id: string
  applicationNo: string
  vacancyId: string
  candidateId: string
  currentStage: ApplicationStage
  status: ApplicationStatus
  createdEmployeeId?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
}

export interface ApplicationEvent {
  id: string
  applicationId: string
  eventType: 'STAGE_CHANGE' | 'EVALUATION' | 'NOTE'
  fromStage?: ApplicationStage | null
  toStage?: ApplicationStage | null
  rating?: number | null
  recommendation?: Recommendation | null
  comment?: string | null
  actor: string
  createdAt: string
}

export interface StageTransitionRequest {
  toStage: ApplicationStage
  rating?: number
  recommendation?: Recommendation
  comment?: string
}

export interface Offer {
  id: string
  offerNo: string
  applicationId: string
  proposedSalary: number
  currency: string
  proposedStartDate: string
  benefits?: string | null
  status: OfferStatus
  // M276 — approval workflow + salary exception flag
  workflowInstanceId?: string | null
  salaryException: boolean
  sentAt?: string | null
  sentBy?: string | null
  responseAt?: string | null
  notes?: string | null
  createdAt: string
  updatedAt: string
}

export interface OfferRequest {
  proposedSalary: number
  currency?: string
  proposedStartDate: string
  benefits?: string
  notes?: string
}

export const recruitmentApi = {
  // Vacancies
  vacancies: (params: { status?: VacancyStatus; page?: number; size?: number }) =>
    api
      .get<PageResponse<Vacancy>>('/recruitment/vacancies', { params })
      .then((r) => r.data),
  vacancy: (id: string) =>
    api.get<Vacancy>(`/recruitment/vacancies/${id}`).then((r) => r.data),
  createVacancy: (payload: VacancyRequest) =>
    api.post<Vacancy>('/recruitment/vacancies', payload).then((r) => r.data),
  updateVacancy: (id: string, payload: VacancyRequest) =>
    api.put<Vacancy>(`/recruitment/vacancies/${id}`, payload).then((r) => r.data),
  changeVacancyStatus: (id: string, status: VacancyStatus, reason?: string) =>
    api
      .post<Vacancy>(`/recruitment/vacancies/${id}/status/${status}`, null, {
        params: { reason },
      })
      .then((r) => r.data),
  // M275 — kick off the requisition approval workflow
  submitVacancyApproval: (id: string) =>
    api.post<Vacancy>(`/recruitment/vacancies/${id}/submit-approval`).then((r) => r.data),

  // Candidates
  candidates: (params: { search?: string; page?: number; size?: number }) =>
    api
      .get<PageResponse<Candidate>>('/recruitment/candidates', { params })
      .then((r) => r.data),
  candidate: (id: string) =>
    api.get<Candidate>(`/recruitment/candidates/${id}`).then((r) => r.data),
  createCandidate: (payload: CandidateRequest) =>
    api.post<Candidate>('/recruitment/candidates', payload).then((r) => r.data),
  updateCandidate: (id: string, payload: CandidateRequest) =>
    api.put<Candidate>(`/recruitment/candidates/${id}`, payload).then((r) => r.data),

  // Applications
  applicationsByVacancy: (vacancyId: string) =>
    api
      .get<Application[]>('/recruitment/applications', { params: { vacancyId } })
      .then((r) => r.data),
  applicationsByCandidate: (candidateId: string) =>
    api
      .get<Application[]>('/recruitment/applications', { params: { candidateId } })
      .then((r) => r.data),
  application: (id: string) =>
    api.get<Application>(`/recruitment/applications/${id}`).then((r) => r.data),
  applicationHistory: (id: string) =>
    api
      .get<ApplicationEvent[]>(`/recruitment/applications/${id}/history`)
      .then((r) => r.data),
  apply: (vacancyId: string, candidateId: string) =>
    api
      .post<Application>('/recruitment/applications', { vacancyId, candidateId })
      .then((r) => r.data),
  transition: (id: string, payload: StageTransitionRequest) =>
    api
      .post<Application>(`/recruitment/applications/${id}/transition`, payload)
      .then((r) => r.data),

  // Offers
  offerForApplication: (applicationId: string) =>
    api
      .get<Offer | null>('/recruitment/offers', { params: { applicationId } })
      .then((r) => r.data ?? null),
  upsertOffer: (applicationId: string, payload: OfferRequest) =>
    api
      .put<Offer>('/recruitment/offers', payload, { params: { applicationId } })
      .then((r) => r.data),
  // M276 — kick off the offer approval workflow (salary-exception routed)
  submitOfferApproval: (id: string) =>
    api.post<Offer>(`/recruitment/offers/${id}/submit-approval`).then((r) => r.data),
  transitionOffer: (id: string, status: OfferStatus, notes?: string) =>
    api
      .post<Offer>(`/recruitment/offers/${id}/status/${status}`, null, {
        params: { notes },
      })
      .then((r) => r.data),
}
