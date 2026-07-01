import { api } from './client'

// ── Types ────────────────────────────────────────────────────────────────────

export type BudgetExceededPolicy = 'WARNING' | 'HARD_STOP' | 'EXCEPTION_APPROVAL'

export type ChangeReasonCategory = 'MERIT' | 'PROMOTION' | 'MARKET' | 'ADJUSTMENT' | 'OTHER'

export type RangePosition = 'BELOW_MIN' | 'LOW' | 'MID' | 'HIGH' | 'ABOVE_MAX'

// ── Config ───────────────────────────────────────────────────────────────────

export interface CompConfig {
  maxIncreasePctWithoutApproval: number
  budgetExceededPolicy: BudgetExceededPolicy
  defaultCurrency: string
}

// ── Pay Bands ────────────────────────────────────────────────────────────────

export interface PayBandResponse {
  id: string
  code: string
  name: string
  gradeId: string | null
  currency: string
  minSalary: number
  midSalary: number
  maxSalary: number
  q1Salary: number | null
  q3Salary: number | null
  effectiveFrom: string
  effectiveTo: string | null
  isActive: boolean
}

export interface PayBandRequest {
  code: string
  name: string
  gradeId?: string | null
  currency: string
  minSalary: number
  midSalary: number
  maxSalary: number
  q1Salary?: number | null
  q3Salary?: number | null
  effectiveFrom: string
  effectiveTo?: string | null
  isActive?: boolean
}

// ── Change Reasons ───────────────────────────────────────────────────────────

export interface ChangeReasonResponse {
  id: string
  code: string
  name: string
  affectsWorkflow: boolean
  category: ChangeReasonCategory
  isActive: boolean
}

export interface ChangeReasonRequest {
  code: string
  name: string
  affectsWorkflow: boolean
  category: ChangeReasonCategory
  isActive?: boolean
}

// ── Compensation Profile ─────────────────────────────────────────────────────

export interface CurrentSalaryInfo {
  monthlyBaseSalary: number
  currency: string
  effectiveFrom: string
}

export interface GradeInfo {
  code: string
  name: string
  minSalary: number | null
  maxSalary: number | null
}

export interface BandInfo {
  code: string
  name: string
  minSalary: number
  midSalary: number
  maxSalary: number
  q1Salary: number | null
  q3Salary: number | null
}

export interface SalaryHistoryItem {
  effectiveFrom: string
  effectiveTo: string | null
  monthlyBaseSalary: number
  currency: string
  reason: string | null
}

export interface CompensationProfileDto {
  currentSalary: CurrentSalaryInfo | null
  grade: GradeInfo | null
  band: BandInfo | null
  compaRatio: number | null
  rangePenetration: number | null
  rangePositionLabel: string | null
  salaryHistory: SalaryHistoryItem[]
}

// ── Salary Change Requests ───────────────────────────────────────────────────

export type SalaryChangeStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'APPLIED'
  | 'CANCELLED'

export interface SalaryChangeRequestDto {
  id: string
  employeeId: string
  currentSalary: number
  proposedSalary: number
  currency: string
  changeReasonId: string
  effectiveDate: string
  increasePct: number
  outsideBand: boolean
  aboveThreshold: boolean
  status: SalaryChangeStatus
  workflowInstanceId: string | null
  requestedBy: string
  createdAt: string
  decidedAt: string | null
  appliedAt: string | null
  note: string | null
}

export interface CreateSalaryChangeRequest {
  employeeId: string
  proposedSalary: number
  currency: string
  changeReasonId: string
  effectiveDate: string
  note?: string
}

// ── Compensation Exceptions ──────────────────────────────────────────────────

export type CompensationExceptionSource = 'SALARY_CHANGE' | 'PROFILE_REVIEW' | 'BULK_IMPORT'

export type CompensationExceptionType =
  | 'BELOW_BAND'
  | 'ABOVE_BAND'
  | 'ABOVE_THRESHOLD'
  | 'OVER_BUDGET'

export type CompensationExceptionSeverity = 'LOW' | 'MEDIUM' | 'HIGH'

export type CompensationExceptionStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'WAIVED'

export interface CompensationExceptionDto {
  id: string
  employeeId: string
  sourceType: CompensationExceptionSource
  sourceId: string
  exceptionType: CompensationExceptionType
  severity: CompensationExceptionSeverity
  status: CompensationExceptionStatus
  reason: string
  raisedAt: string
  resolvedBy: string | null
  resolvedAt: string | null
}

export interface ResolveExceptionRequest {
  status: CompensationExceptionStatus
  note: string
}

// ── API ──────────────────────────────────────────────────────────────────────

export const compensationApi = {
  // Config
  getConfig: () => api.get<CompConfig>('/api/compensation/config').then((r) => r.data),
  updateConfig: (payload: CompConfig) =>
    api.put<CompConfig>('/api/compensation/config', payload).then((r) => r.data),

  // Pay Bands
  listPayBands: () =>
    api.get<PayBandResponse[]>('/api/compensation/pay-bands').then((r) => r.data),
  createPayBand: (payload: PayBandRequest) =>
    api.post<PayBandResponse>('/api/compensation/pay-bands', payload).then((r) => r.data),
  updatePayBand: (id: string, payload: PayBandRequest) =>
    api.put<PayBandResponse>(`/api/compensation/pay-bands/${id}`, payload).then((r) => r.data),
  deactivatePayBand: (id: string) =>
    api.delete(`/api/compensation/pay-bands/${id}`).then((r) => r.data),

  // Change Reasons
  listChangeReasons: () =>
    api.get<ChangeReasonResponse[]>('/api/compensation/change-reasons').then((r) => r.data),
  createChangeReason: (payload: ChangeReasonRequest) =>
    api
      .post<ChangeReasonResponse>('/api/compensation/change-reasons', payload)
      .then((r) => r.data),
  updateChangeReason: (id: string, payload: ChangeReasonRequest) =>
    api
      .put<ChangeReasonResponse>(`/api/compensation/change-reasons/${id}`, payload)
      .then((r) => r.data),
  deactivateChangeReason: (id: string) =>
    api.delete(`/api/compensation/change-reasons/${id}`).then((r) => r.data),

  // Profile
  getProfile: (employeeId: string) =>
    api
      .get<CompensationProfileDto>(`/api/compensation/employees/${employeeId}/profile`)
      .then((r) => r.data),

  // Salary Change Requests
  listSalaryChanges: (filters?: { employeeId?: string; status?: SalaryChangeStatus }) => {
    const params = new URLSearchParams()
    if (filters?.employeeId) params.set('employeeId', filters.employeeId)
    if (filters?.status) params.set('status', filters.status)
    const query = params.toString()
    return api
      .get<SalaryChangeRequestDto[]>(`/api/compensation/salary-changes${query ? `?${query}` : ''}`)
      .then((r) => r.data)
  },
  getSalaryChange: (id: string) =>
    api.get<SalaryChangeRequestDto>(`/api/compensation/salary-changes/${id}`).then((r) => r.data),
  createSalaryChange: (payload: CreateSalaryChangeRequest) =>
    api
      .post<SalaryChangeRequestDto>('/api/compensation/salary-changes', payload)
      .then((r) => r.data),
  submitSalaryChange: (id: string) =>
    api.post(`/api/compensation/salary-changes/${id}/submit`).then((r) => r.data),
  cancelSalaryChange: (id: string) =>
    api.post(`/api/compensation/salary-changes/${id}/cancel`).then((r) => r.data),

  // Exceptions
  listExceptions: (filters?: { status?: CompensationExceptionStatus; exceptionType?: CompensationExceptionType }) => {
    const params = new URLSearchParams()
    if (filters?.status) params.set('status', filters.status)
    if (filters?.exceptionType) params.set('exceptionType', filters.exceptionType)
    const query = params.toString()
    return api
      .get<CompensationExceptionDto[]>(`/api/compensation/exceptions${query ? `?${query}` : ''}`)
      .then((r) => r.data)
  },
  resolveException: (id: string, payload: ResolveExceptionRequest) =>
    api.post(`/api/compensation/exceptions/${id}/resolve`, payload).then((r) => r.data),
}
