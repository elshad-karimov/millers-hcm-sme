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

// M291 — structured candidate profile (Recruitment PRD §11)
export type SkillProficiency = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'

export interface CandidateEducation {
  id: string
  ordinal: number
  institution: string
  degree?: string | null
  fieldOfStudy?: string | null
  startYear?: number | null
  endYear?: number | null
  grade?: string | null
}
export type CandidateEducationRequest = Omit<CandidateEducation, 'id'>

export interface CandidateExperience {
  id: string
  ordinal: number
  company: string
  title: string
  location?: string | null
  startDate?: string | null
  endDate?: string | null
  current: boolean
  description?: string | null
}
export type CandidateExperienceRequest = Omit<CandidateExperience, 'id'>

export interface CandidateSkillRow {
  id: string
  ordinal: number
  name: string
  proficiency?: SkillProficiency | null
  yearsExperience?: number | null
}
export type CandidateSkillRequest = Omit<CandidateSkillRow, 'id'>

export interface CandidateProfile {
  education: CandidateEducation[]
  experience: CandidateExperience[]
  skills: CandidateSkillRow[]
}

// M292 — candidate document checklist (Recruitment PRD §12)
export type DocumentStatus = 'PENDING' | 'RECEIVED' | 'VERIFIED' | 'REJECTED' | 'WAIVED'

export interface DocumentType {
  id: string
  code: string
  name: string
  description?: string | null
  defaultRequired: boolean
  ordinal: number
  active: boolean
}

export interface CandidateDocumentItem {
  id: string
  documentTypeId: string
  typeCode: string
  typeName: string
  required: boolean
  status: DocumentStatus
  notes?: string | null
  receivedAt?: string | null
  verifiedAt?: string | null
  verifiedBy?: string | null
}

export interface CandidateChecklist {
  items: CandidateDocumentItem[]
  total: number
  requiredCount: number
  verifiedCount: number
  outstandingRequired: number
}

// M293 — duplicate detection + merge (Recruitment PRD §12)
export interface DuplicateCandidate {
  id: string
  candidateNo: string
  fullName: string
  email?: string | null
  phone?: string | null
  source?: string | null
  applicationCount: number
  createdAt: string
}

export interface DuplicateGroup {
  matchType: 'EMAIL' | 'PHONE' | 'NAME'
  matchValue: string
  candidates: DuplicateCandidate[]
}

export interface MergeResult {
  primaryId: string
  mergedId: string
  applicationsMoved: number
  applicationsConflicted: number
  notesMoved: number
  tagsMoved: number
  educationMoved: number
  experienceMoved: number
  skillsMoved: number
  documentsMoved: number
}

// M294 — consent retention + anonymization (Recruitment PRD §46/§47)
export interface RetentionRow {
  id: string
  candidateNo: string
  fullName: string
  email?: string | null
  anchorDate: string
  retentionDate: string
  daysUntilDue: number
  overdue: boolean
}

export interface RetentionReport {
  retentionMonths: number
  dueCount: number
  upcomingCount: number
  due: RetentionRow[]
  upcoming: RetentionRow[]
}

export interface SweepResult {
  dryRun: boolean
  count: number
  candidateNos: string[]
}

// M295 — employee referral program (Recruitment PRD Phase F)
export type ReferralStatus = 'SUBMITTED' | 'HIRED' | 'QUALIFIED' | 'PAID' | 'REJECTED'

export interface Referral {
  id: string
  referralNo: string
  referrerEmployeeId: string
  referrerName: string
  candidateId: string
  candidateName: string
  vacancyId?: string | null
  vacancyTitle?: string | null
  status: ReferralStatus
  bonusAmount: number
  currency: string
  qualifyingDays: number
  hiredAt?: string | null
  qualifiedAt?: string | null
  paidAt?: string | null
  payrollRunId?: string | null
  payrollBonusId?: string | null
  notes?: string | null
  createdAt: string
}

export interface ReferralRequest {
  referrerEmployeeId: string
  candidateId: string
  vacancyId?: string
  bonusAmount?: number
  qualifyingDays?: number
  currency?: string
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

// M288 — stage SLA + overdue tracking (Recruitment PRD §14/§43)
export interface SlaConfig {
  id: string
  stage: ApplicationStage
  slaDays: number
  ownerRole?: string | null
  active: boolean
}

export interface SlaBreachRow {
  applicationId: string
  applicationNo: string
  candidateId: string
  candidateName: string
  vacancyTitle: string
  stage: ApplicationStage
  ownerRole?: string | null
  daysInStage: number
  slaDays: number
  daysOver: number
  severity: 'OVERDUE' | 'DUE_SOON'
}

export interface SlaBreachReport {
  overdueCount: number
  dueSoonCount: number
  rows: SlaBreachRow[]
}

// M287 — assessments (Recruitment PRD §22)
export type AssessmentType =
  | 'TECHNICAL' | 'LANGUAGE' | 'COGNITIVE' | 'PERSONALITY' | 'JOB_SIMULATION'
  | 'PRACTICAL' | 'CASE_STUDY' | 'TYPING' | 'OTHER'
export type AssessmentStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED'
export type AssessmentResult = 'PASS' | 'FAIL'

export interface Assessment {
  id: string
  assessmentNo: string
  applicationId: string
  assessmentType: AssessmentType
  name: string
  provider?: string | null
  status: AssessmentStatus
  score?: number | null
  maxScore?: number | null
  passingScore?: number | null
  result?: AssessmentResult | null
  notes?: string | null
  attachmentId?: string | null
  blocksHire: boolean
  assignedAt?: string | null
  completedAt?: string | null
  validUntil?: string | null
  createdAt: string
  updatedAt: string
}

export interface AssessmentRequest {
  assessmentType: AssessmentType
  name: string
  provider?: string
  maxScore?: number
  passingScore?: number
  validUntil?: string
  blocksHire?: boolean
}

export interface AssessmentUpdate {
  status: AssessmentStatus
  score?: number
  result?: AssessmentResult
  notes?: string
  attachmentId?: string
}

// M286 — pre-hire checks (Recruitment PRD §25-§27)
export type CheckType =
  | 'BACKGROUND' | 'IDENTITY' | 'EDUCATION' | 'EMPLOYMENT' | 'REFERENCE'
  | 'CRIMINAL' | 'CREDIT' | 'LICENSE' | 'WORK_AUTHORIZATION' | 'MEDICAL'
export type CheckStatus =
  | 'NOT_REQUIRED' | 'REQUIRED' | 'REQUESTED' | 'IN_PROGRESS'
  | 'COMPLETED' | 'PASSED' | 'FAILED' | 'REQUIRES_REVIEW' | 'CANCELLED'
export type CheckResult = 'PASS' | 'FAIL' | 'CONDITIONAL'

export interface PreHireCheck {
  id: string
  checkNo: string
  applicationId: string
  checkType: CheckType
  status: CheckStatus
  provider?: string | null
  subjectName?: string | null
  subjectContact?: string | null
  result?: CheckResult | null
  resultNotes?: string | null
  resultRedacted: boolean
  attachmentId?: string | null
  blocksHire: boolean
  requestedAt?: string | null
  completedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface PreHireCheckRequest {
  checkType: CheckType
  provider?: string
  subjectName?: string
  subjectContact?: string
  blocksHire?: boolean
}

export interface PreHireCheckUpdate {
  status: CheckStatus
  result?: CheckResult
  resultNotes?: string
  attachmentId?: string
}

// M278 — job postings (Recruitment PRD §8)
export type PostingChannel = 'INTERNAL' | 'EXTERNAL' | 'JOB_BOARD' | 'AGENCY' | 'SOCIAL'
export type PostingStatus = 'DRAFT' | 'PUBLISHED' | 'PAUSED' | 'EXPIRED' | 'CLOSED'

export interface JobPosting {
  id: string
  postingNo: string
  vacancyId: string
  channel: PostingChannel
  language: string
  title: string
  description?: string | null
  requirements?: string | null
  benefitsDescription?: string | null
  salaryVisible: boolean
  applicationDeadline?: string | null
  status: PostingStatus
  publishedAt?: string | null
  publishedBy?: string | null
  createdAt: string
  updatedAt: string
}

export interface JobPostingRequest {
  channel: PostingChannel
  language?: string
  title?: string
  description?: string
  requirements?: string
  benefitsDescription?: string
  salaryVisible?: boolean
  applicationDeadline?: string
}

// M289 — knockout / screening questions (Recruitment PRD §15)
export type ScreeningQuestionType = 'BOOLEAN' | 'SINGLE_CHOICE' | 'NUMERIC'

export interface ScreeningQuestion {
  id: string
  postingId: string
  ordinal: number
  questionText: string
  questionType: ScreeningQuestionType
  options?: string | null
  knockout: boolean
  required: boolean
  expectedBoolean?: boolean | null
  acceptableOptions?: string | null
  minValue?: number | null
  maxValue?: number | null
  active: boolean
}

export interface ScreeningQuestionRequest {
  ordinal: number
  questionText: string
  questionType: ScreeningQuestionType
  options?: string | null
  knockout: boolean
  required: boolean
  expectedBoolean?: boolean | null
  acceptableOptions?: string | null
  minValue?: number | null
  maxValue?: number | null
  active?: boolean
}

export interface ScreeningAnswer {
  questionId: string
  questionText: string
  answerText?: string | null
  passed: boolean
  knockout: boolean
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

// M284 — counteroffer / revision (PRD §33)
export interface OfferReviseRequest {
  proposedSalary: number
  currency?: string
  proposedStartDate?: string
  benefits?: string
  reason: 'CANDIDATE_COUNTER' | 'HR_REVISION'
  notes?: string
}

export interface OfferRevision {
  id: string
  offerId: string
  revisionNo: number
  prevSalary?: number | null
  prevCurrency?: string | null
  prevStartDate?: string | null
  prevBenefits?: string | null
  prevStatus: string
  reason: 'CANDIDATE_COUNTER' | 'HR_REVISION'
  notes?: string | null
  createdAt: string
  createdBy?: string | null
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

  // M291 — structured candidate profile (Recruitment PRD §11)
  candidateProfile: (candidateId: string) =>
    api.get<CandidateProfile>(`/recruitment/candidates/${candidateId}/profile`).then((r) => r.data),
  addCandidateEducation: (candidateId: string, p: CandidateEducationRequest) =>
    api
      .post<CandidateEducation>(`/recruitment/candidates/${candidateId}/education`, p)
      .then((r) => r.data),
  updateCandidateEducation: (id: string, p: CandidateEducationRequest) =>
    api.put<CandidateEducation>(`/recruitment/candidates/education/${id}`, p).then((r) => r.data),
  deleteCandidateEducation: (id: string) =>
    api.delete(`/recruitment/candidates/education/${id}`).then((r) => r.data),
  addCandidateExperience: (candidateId: string, p: CandidateExperienceRequest) =>
    api
      .post<CandidateExperience>(`/recruitment/candidates/${candidateId}/experience`, p)
      .then((r) => r.data),
  updateCandidateExperience: (id: string, p: CandidateExperienceRequest) =>
    api.put<CandidateExperience>(`/recruitment/candidates/experience/${id}`, p).then((r) => r.data),
  deleteCandidateExperience: (id: string) =>
    api.delete(`/recruitment/candidates/experience/${id}`).then((r) => r.data),
  addCandidateSkill: (candidateId: string, p: CandidateSkillRequest) =>
    api.post<CandidateSkillRow>(`/recruitment/candidates/${candidateId}/skills`, p).then((r) => r.data),
  updateCandidateSkill: (id: string, p: CandidateSkillRequest) =>
    api.put<CandidateSkillRow>(`/recruitment/candidates/skills/${id}`, p).then((r) => r.data),
  deleteCandidateSkill: (id: string) =>
    api.delete(`/recruitment/candidates/skills/${id}`).then((r) => r.data),

  // M292 — document checklist (Recruitment PRD §12)
  documentTypes: (activeOnly = true) =>
    api
      .get<DocumentType[]>('/recruitment/document-types', { params: { activeOnly } })
      .then((r) => r.data),
  candidateDocuments: (candidateId: string) =>
    api
      .get<CandidateChecklist>(`/recruitment/candidates/${candidateId}/documents`)
      .then((r) => r.data),
  addCandidateDocument: (candidateId: string, p: { documentTypeId: string; required?: boolean }) =>
    api
      .post<CandidateDocumentItem>(`/recruitment/candidates/${candidateId}/documents`, p)
      .then((r) => r.data),
  seedCandidateDocuments: (candidateId: string) =>
    api
      .post<CandidateChecklist>(`/recruitment/candidates/${candidateId}/documents/seed-defaults`)
      .then((r) => r.data),
  updateCandidateDocument: (
    id: string,
    p: { status: DocumentStatus; notes?: string; required?: boolean },
  ) => api.put<CandidateDocumentItem>(`/recruitment/candidate-documents/${id}`, p).then((r) => r.data),
  deleteCandidateDocument: (id: string) =>
    api.delete(`/recruitment/candidate-documents/${id}`).then((r) => r.data),

  // M293 — duplicate detection + merge (Recruitment PRD §12)
  candidateDuplicates: () =>
    api.get<DuplicateGroup[]>('/recruitment/candidates/duplicates').then((r) => r.data),
  mergeCandidates: (primaryId: string, duplicateId: string) =>
    api
      .post<MergeResult>('/recruitment/candidates/merge', { primaryId, duplicateId })
      .then((r) => r.data),

  // M294 — consent retention + anonymization (Recruitment PRD §46/§47)
  retentionReport: () =>
    api.get<RetentionReport>('/recruitment/retention/report').then((r) => r.data),
  retentionSweep: (dryRun: boolean) =>
    api
      .post<SweepResult>('/recruitment/retention/sweep', null, { params: { dryRun } })
      .then((r) => r.data),
  anonymizeCandidate: (id: string, reason?: string) =>
    api
      .post(`/recruitment/candidates/${id}/anonymize`, null, { params: { reason } })
      .then((r) => r.data),

  // M295 — employee referral program (Recruitment PRD Phase F)
  referrals: (status?: ReferralStatus) =>
    api.get<Referral[]>('/recruitment/referrals', { params: { status } }).then((r) => r.data),
  createReferral: (payload: ReferralRequest) =>
    api.post<Referral>('/recruitment/referrals', payload).then((r) => r.data),
  rejectReferral: (id: string, reason?: string) =>
    api.post<Referral>(`/recruitment/referrals/${id}/reject`, null, { params: { reason } }).then((r) => r.data),
  payReferral: (id: string, payrollRunId: string) =>
    api
      .post<Referral>(`/recruitment/referrals/${id}/pay`, null, { params: { payrollRunId } })
      .then((r) => r.data),

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
  // M284 — counteroffer / revision (PRD §33)
  reviseOffer: (id: string, payload: OfferReviseRequest) =>
    api.post<Offer>(`/recruitment/offers/${id}/revise`, payload).then((r) => r.data),
  offerRevisions: (id: string) =>
    api.get<OfferRevision[]>(`/recruitment/offers/${id}/revisions`).then((r) => r.data),
  // M283 — render the offer letter PDF (lang: az | en)
  downloadOfferLetter: (id: string, lang: string) =>
    api
      .get<Blob>(`/recruitment/offers/${id}/letter`, {
        params: { lang },
        responseType: 'blob',
      })
      .then((r) => r.data),

  // M278 — job postings (Recruitment PRD §8)
  postingsForVacancy: (vacancyId: string) =>
    api
      .get<JobPosting[]>(`/recruitment/vacancies/${vacancyId}/postings`)
      .then((r) => r.data),
  createPosting: (vacancyId: string, payload: JobPostingRequest) =>
    api
      .post<JobPosting>(`/recruitment/vacancies/${vacancyId}/postings`, payload)
      .then((r) => r.data),
  updatePosting: (id: string, payload: JobPostingRequest) =>
    api.put<JobPosting>(`/recruitment/postings/${id}`, payload).then((r) => r.data),
  publishPosting: (id: string) =>
    api.post<JobPosting>(`/recruitment/postings/${id}/publish`).then((r) => r.data),
  pausePosting: (id: string) =>
    api.post<JobPosting>(`/recruitment/postings/${id}/pause`).then((r) => r.data),
  closePosting: (id: string) =>
    api.post<JobPosting>(`/recruitment/postings/${id}/close`).then((r) => r.data),

  // M289 — knockout / screening questions (Recruitment PRD §15)
  screeningQuestions: (postingId: string) =>
    api
      .get<ScreeningQuestion[]>(`/recruitment/postings/${postingId}/screening-questions`)
      .then((r) => r.data),
  createScreeningQuestion: (postingId: string, payload: ScreeningQuestionRequest) =>
    api
      .post<ScreeningQuestion>(`/recruitment/postings/${postingId}/screening-questions`, payload)
      .then((r) => r.data),
  updateScreeningQuestion: (id: string, payload: ScreeningQuestionRequest) =>
    api.put<ScreeningQuestion>(`/recruitment/screening-questions/${id}`, payload).then((r) => r.data),
  deleteScreeningQuestion: (id: string) =>
    api.delete(`/recruitment/screening-questions/${id}`).then((r) => r.data),
  screeningAnswers: (applicationId: string) =>
    api
      .get<ScreeningAnswer[]>(`/recruitment/applications/${applicationId}/screening-answers`)
      .then((r) => r.data),

  // M286 — pre-hire checks (Recruitment PRD §25-§27)
  checksForApplication: (applicationId: string) =>
    api
      .get<PreHireCheck[]>(`/recruitment/applications/${applicationId}/checks`)
      .then((r) => r.data),
  createCheck: (applicationId: string, payload: PreHireCheckRequest) =>
    api
      .post<PreHireCheck>(`/recruitment/applications/${applicationId}/checks`, payload)
      .then((r) => r.data),
  updateCheck: (id: string, payload: PreHireCheckUpdate) =>
    api.put<PreHireCheck>(`/recruitment/checks/${id}`, payload).then((r) => r.data),

  // M287 — assessments (Recruitment PRD §22)
  assessmentsForApplication: (applicationId: string) =>
    api
      .get<Assessment[]>(`/recruitment/applications/${applicationId}/assessments`)
      .then((r) => r.data),
  createAssessment: (applicationId: string, payload: AssessmentRequest) =>
    api
      .post<Assessment>(`/recruitment/applications/${applicationId}/assessments`, payload)
      .then((r) => r.data),
  updateAssessment: (id: string, payload: AssessmentUpdate) =>
    api.put<Assessment>(`/recruitment/assessments/${id}`, payload).then((r) => r.data),

  // M288 — stage SLA + overdue tracking (Recruitment PRD §14/§43)
  slaConfig: () => api.get<SlaConfig[]>('/recruitment/sla/config').then((r) => r.data),
  updateSlaConfig: (id: string, payload: { slaDays: number; ownerRole?: string; active?: boolean }) =>
    api.put<SlaConfig>(`/recruitment/sla/config/${id}`, payload).then((r) => r.data),
  slaBreaches: () =>
    api.get<SlaBreachReport>('/recruitment/sla/breaches').then((r) => r.data),
  transitionOffer: (id: string, status: OfferStatus, notes?: string) =>
    api
      .post<Offer>(`/recruitment/offers/${id}/status/${status}`, null, {
        params: { notes },
      })
      .then((r) => r.data),
}
