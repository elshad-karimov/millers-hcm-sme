import { api } from './client'
import type { AssignmentResponse } from './checklists'

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
  checklistAssignmentId?: string
  notes?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export { type AssignmentResponse }

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

export async function getOffboardingCaseChecklist(id: string): Promise<AssignmentResponse> {
  const res = await api.get(`/lifecycle/offboarding/cases/${id}/checklist`)
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

// ── Clearance ─────────────────────────────────────────────────────────────────

export type ClearanceDepartment =
  | 'HR' | 'PAYROLL' | 'IT' | 'FINANCE' | 'ASSET' | 'FACILITIES' | 'LEGAL' | 'SECURITY' | 'MANAGER'

export type ClearanceStatus =
  | 'NOT_STARTED' | 'IN_PROGRESS' | 'CLEARED' | 'CLEARED_WITH_DEDUCTION' | 'NOT_CLEARED' | 'WAIVED'

export interface ClearanceResponse {
  id: string
  caseId: string
  department: ClearanceDepartment
  status: ClearanceStatus
  clearedBy?: string
  clearedAt?: string
  deductionAmount?: number
  notes?: string
  createdAt: string
  updatedAt: string
}

export interface ClearanceSignOffRequest {
  status: ClearanceStatus
  deductionAmount?: number
  notes?: string
}

export async function listClearances(caseId: string): Promise<ClearanceResponse[]> {
  const res = await api.get(`/lifecycle/offboarding/cases/${caseId}/clearance`)
  return res.data
}

export async function signOffClearance(
  caseId: string,
  department: ClearanceDepartment,
  req: ClearanceSignOffRequest
): Promise<ClearanceResponse> {
  const res = await api.patch(`/lifecycle/offboarding/cases/${caseId}/clearance/${department}`, req)
  return res.data
}

// ── Asset Return ──────────────────────────────────────────────────────────────

export type AssetReturnStatus =
  | 'PENDING_RETURN' | 'RETURNED' | 'RETURNED_DAMAGED'
  | 'MISSING' | 'DEDUCTION_APPROVED' | 'WRITTEN_OFF' | 'WAIVED'

export interface AssetReturnResponse {
  id: string; caseId: string; employeeAssetId?: string
  assetType: string; assetName: string; assetIdentifier?: string
  returnStatus: AssetReturnStatus; returnDate?: string
  conditionAtReturn?: string; deductionAmount?: number
  notes?: string; createdAt: string; updatedAt: string
}

export interface AssetReturnUpdateRequest {
  returnStatus?: AssetReturnStatus; returnDate?: string
  conditionAtReturn?: string; deductionAmount?: number; notes?: string
}

export async function listAssetReturns(caseId: string): Promise<AssetReturnResponse[]> {
  const res = await api.get(`/lifecycle/offboarding/cases/${caseId}/assets`)
  return res.data
}

export async function updateAssetReturn(
  caseId: string, returnId: string, req: AssetReturnUpdateRequest
): Promise<AssetReturnResponse> {
  const res = await api.patch(`/lifecycle/offboarding/cases/${caseId}/assets/${returnId}`, req)
  return res.data
}
