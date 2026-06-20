import { api } from './client'

export type ResignationStatus =
  | 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CANCELLED'

export type OffboardingCaseStatus =
  | 'DRAFT' | 'SUBMITTED' | 'PENDING_APPROVAL' | 'APPROVED' | 'IN_PROGRESS'
  | 'UNDER_NOTICE' | 'CLEARANCE_PENDING' | 'SETTLEMENT_PENDING'
  | 'COMPLETED' | 'CANCELLED' | 'REVERSED'

export type OffboardingSource = 'RESIGNATION' | 'TERMINATION' | 'OTHER'

export interface ResignationResponse {
  id: string
  resignationNo: string
  employeeId: string
  resignationDate: string
  proposedLastWorkingDate: string
  calculatedLastWorkingDate?: string
  approvedLastWorkingDate?: string
  noticeDaysCalculated?: number
  reasonText?: string
  comments?: string
  status: ResignationStatus
  workflowInstanceId?: string
  createdAt: string
  createdBy?: string
  updatedAt: string
}

export interface OffboardingCaseResponse {
  id: string
  caseNo: string
  employeeId: string
  source: OffboardingSource
  resignationId?: string
  terminationId?: string
  exitReason?: string
  caseStatus: OffboardingCaseStatus
  caseOwner?: string
  lastWorkingDate?: string
  accessRemovalDate?: string
  settlementDate?: string
  notes?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface OffboardingOverviewResponse {
  activeCases: number
  leavingThisWeek: number
  leavingThisMonth: number
  totalCases: number
  recentCases: OffboardingCaseResponse[]
}

export interface ResignationSubmitRequest {
  employeeId: string
  resignationDate: string
  proposedLastWorkingDate: string
  reasonText?: string
  comments?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── Resignations ──────────────────────────────────────────────────────────────

export async function listResignations(params?: {
  employeeId?: string
  status?: ResignationStatus
  page?: number
  size?: number
}): Promise<PageResponse<ResignationResponse>> {
  const res = await api.get('/lifecycle/offboarding/resignations', { params })
  return res.data
}

export async function getResignation(id: string): Promise<ResignationResponse> {
  const res = await api.get(`/lifecycle/offboarding/resignations/${id}`)
  return res.data
}

export async function submitResignation(req: ResignationSubmitRequest): Promise<ResignationResponse> {
  const res = await api.post('/lifecycle/offboarding/resignations/submit', req)
  return res.data
}

export async function withdrawResignation(id: string): Promise<ResignationResponse> {
  const res = await api.post(`/lifecycle/offboarding/resignations/${id}/withdraw`)
  return res.data
}

// ── Offboarding Cases ─────────────────────────────────────────────────────────

export async function getOffboardingOverview(): Promise<OffboardingOverviewResponse> {
  const res = await api.get('/lifecycle/offboarding/cases/overview')
  return res.data
}

export async function listOffboardingCases(params?: {
  employeeId?: string
  status?: OffboardingCaseStatus
  page?: number
  size?: number
}): Promise<PageResponse<OffboardingCaseResponse>> {
  const res = await api.get('/lifecycle/offboarding/cases', { params })
  return res.data
}

export async function getOffboardingCase(id: string): Promise<OffboardingCaseResponse> {
  const res = await api.get(`/lifecycle/offboarding/cases/${id}`)
  return res.data
}

export async function updateOffboardingCaseStatus(
  id: string,
  status: OffboardingCaseStatus,
  notes?: string
): Promise<OffboardingCaseResponse> {
  const res = await api.patch(`/lifecycle/offboarding/cases/${id}/status`, null, {
    params: { status, notes }
  })
  return res.data
}

export async function updateResignationLwd(id: string, approvedLastWorkingDate: string): Promise<ResignationResponse> {
  const res = await api.patch(`/lifecycle/offboarding/resignations/${id}/lwd`, null, {
    params: { approvedLastWorkingDate }
  })
  return res.data
}

// ── Notice Period Rules ───────────────────────────────────────────────────────

export type EmploymentType =
  | 'PERMANENT' | 'FIXED_TERM' | 'PART_TIME' | 'PROBATIONARY' | 'CONTRACTOR' | 'INTERN'

export interface NoticePeriodRule {
  id: string
  employmentType?: EmploymentType
  onProbation: boolean
  noticeDays: number
  description?: string
  active: boolean
}

export interface NoticePeriodRuleRequest {
  employmentType?: EmploymentType
  onProbation?: boolean
  noticeDays: number
  description?: string
}

export async function listNoticePeriodRules(): Promise<NoticePeriodRule[]> {
  const res = await api.get('/lifecycle/offboarding/notice-period-rules')
  return res.data
}

export async function createNoticePeriodRule(req: NoticePeriodRuleRequest): Promise<NoticePeriodRule> {
  const res = await api.post('/lifecycle/offboarding/notice-period-rules', req)
  return res.data
}

export async function updateNoticePeriodRule(id: string, req: NoticePeriodRuleRequest): Promise<NoticePeriodRule> {
  const res = await api.put(`/lifecycle/offboarding/notice-period-rules/${id}`, req)
  return res.data
}

export async function deleteNoticePeriodRule(id: string): Promise<void> {
  await api.delete(`/lifecycle/offboarding/notice-period-rules/${id}`)
}
