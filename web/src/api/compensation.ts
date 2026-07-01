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
}
