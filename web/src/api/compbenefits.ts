import { api } from './client'
import type { PageResponse } from './employees'

export type AllowanceCategory =
  | 'TRANSPORT'
  | 'MEAL'
  | 'MOBILE'
  | 'HOUSING'
  | 'FUEL'
  | 'FAMILY'
  | 'EDUCATION'
  | 'MEDICAL'
  | 'OTHER'

export type AllowanceStatus = 'ACTIVE' | 'ENDED' | 'CANCELLED'
export type BonusRunStatus = 'DRAFT' | 'GENERATED' | 'PUSHED' | 'CANCELLED'
export type BonusItemSource = 'MATRIX_LOOKUP' | 'REVIEW_OVERRIDE' | 'MANUAL'

export interface BonusMatrixRule {
  id: string
  code: string
  description?: string | null
  matchRecommendation?: string | null
  minRating?: number | null
  maxRating?: number | null
  bonusPercent?: number | null
  flatAmount?: number | null
  currency: string
  maxAmount?: number | null
  priority: number
  effectiveFrom: string
  effectiveTo?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface BonusMatrixRuleRequest {
  code: string
  description?: string
  matchRecommendation?: string
  minRating?: number
  maxRating?: number
  bonusPercent?: number
  flatAmount?: number
  currency?: string
  maxAmount?: number
  priority?: number
  effectiveFrom: string
  effectiveTo?: string
  active?: boolean
}

export interface AllowanceType {
  id: string
  code: string
  name: string
  description?: string | null
  category: AllowanceCategory
  taxable: boolean
  recurring: boolean
  defaultAmount?: number | null
  currency: string
  active: boolean
  createdAt: string
}

export interface AllowanceTypeRequest {
  code: string
  name: string
  description?: string
  category: AllowanceCategory
  taxable?: boolean
  recurring?: boolean
  defaultAmount?: number
  currency?: string
  active?: boolean
}

export interface EmployeeAllowance {
  id: string
  allowanceNo: string
  employeeId: string
  allowanceTypeId: string
  amount: number
  currency: string
  effectiveFrom: string
  effectiveTo?: string | null
  status: AllowanceStatus
  note?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface EmployeeAllowanceRequest {
  employeeId: string
  allowanceTypeId: string
  amount: number
  currency?: string
  effectiveFrom: string
  effectiveTo?: string
  note?: string
}

export interface BonusRun {
  id: string
  runNo: string
  name: string
  cycleId?: string | null
  targetPayrollRunId?: string | null
  periodYear: number
  periodMonth: number
  currency: string
  status: BonusRunStatus
  totalAmount: number
  employeeCount: number
  generatedAt?: string | null
  generatedBy?: string | null
  pushedAt?: string | null
  pushedBy?: string | null
  note?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface BonusRunGenerateRequest {
  name: string
  cycleId: string
  periodYear: number
  periodMonth: number
  currency?: string
  note?: string
}

export interface BonusRunItem {
  id: string
  itemNo: string
  runId: string
  employeeId: string
  reviewId?: string | null
  recommendation?: string | null
  finalRating?: number | null
  baseSalary?: number | null
  bonusPercent?: number | null
  bonusAmount: number
  currency: string
  source: BonusItemSource
  matrixRuleId?: string | null
  pushedPayrollBonusId?: string | null
  note?: string | null
  createdAt: string
}

export const compBenefitsApi = {
  // Matrix
  matrixRules: (effectiveOn?: string) =>
    api
      .get<BonusMatrixRule[]>('/compbenefits/matrix-rules', { params: { effectiveOn } })
      .then((r) => r.data),
  matrixRule: (id: string) =>
    api.get<BonusMatrixRule>(`/compbenefits/matrix-rules/${id}`).then((r) => r.data),
  createMatrixRule: (payload: BonusMatrixRuleRequest) =>
    api.post<BonusMatrixRule>('/compbenefits/matrix-rules', payload).then((r) => r.data),
  updateMatrixRule: (id: string, payload: BonusMatrixRuleRequest) =>
    api.put<BonusMatrixRule>(`/compbenefits/matrix-rules/${id}`, payload).then((r) => r.data),

  // Allowance types
  allowanceTypes: (activeOnly = true) =>
    api
      .get<AllowanceType[]>('/compbenefits/allowance-types', { params: { activeOnly } })
      .then((r) => r.data),
  createAllowanceType: (payload: AllowanceTypeRequest) =>
    api.post<AllowanceType>('/compbenefits/allowance-types', payload).then((r) => r.data),
  updateAllowanceType: (id: string, payload: AllowanceTypeRequest) =>
    api.put<AllowanceType>(`/compbenefits/allowance-types/${id}`, payload).then((r) => r.data),

  // Employee allowances
  allowances: (employeeId: string, effectiveOn?: string) =>
    api
      .get<EmployeeAllowance[]>('/compbenefits/allowances', {
        params: { employeeId, effectiveOn },
      })
      .then((r) => r.data),
  createAllowance: (payload: EmployeeAllowanceRequest) =>
    api.post<EmployeeAllowance>('/compbenefits/allowances', payload).then((r) => r.data),
  endAllowance: (id: string, endDate: string) =>
    api
      .post<EmployeeAllowance>(`/compbenefits/allowances/${id}/end`, null, {
        params: { endDate },
      })
      .then((r) => r.data),
  cancelAllowance: (id: string, reason?: string) =>
    api
      .delete<EmployeeAllowance>(`/compbenefits/allowances/${id}`, { params: { reason } })
      .then((r) => r.data),

  // Bonus runs
  bonusRuns: (params: {
    cycleId?: string
    status?: BonusRunStatus
    page?: number
    size?: number
  }) =>
    api
      .get<PageResponse<BonusRun>>('/compbenefits/bonus-runs', { params })
      .then((r) => r.data),
  bonusRun: (id: string) =>
    api.get<BonusRun>(`/compbenefits/bonus-runs/${id}`).then((r) => r.data),
  bonusRunItems: (id: string) =>
    api.get<BonusRunItem[]>(`/compbenefits/bonus-runs/${id}/items`).then((r) => r.data),
  generateBonusRun: (payload: BonusRunGenerateRequest) =>
    api.post<BonusRun>('/compbenefits/bonus-runs/generate', payload).then((r) => r.data),
  pushBonusRun: (id: string, targetPayrollRunId: string) =>
    api
      .post<BonusRun>(`/compbenefits/bonus-runs/${id}/push`, { targetPayrollRunId })
      .then((r) => r.data),
  cancelBonusRun: (id: string, reason?: string) =>
    api
      .post<BonusRun>(`/compbenefits/bonus-runs/${id}/cancel`, null, { params: { reason } })
      .then((r) => r.data),
}
