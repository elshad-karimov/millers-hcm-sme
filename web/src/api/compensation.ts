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

// ── Merit Matrices ───────────────────────────────────────────────────────

export type PerformanceBand = 'EXCELLENT' | 'GOOD' | 'MEETS' | 'BELOW'

export type MeritRangePosition = 'LOW' | 'MID' | 'HIGH'

export interface MeritMatrixCellDto {
  id?: string
  performanceBand: PerformanceBand
  rangePosition: MeritRangePosition
  meritPct: number
}

export interface MeritMatrixDto {
  id: string
  code: string
  name: string
  isActive: boolean
  cells: MeritMatrixCellDto[]
}

export interface MeritMatrixRequest {
  code: string
  name: string
  isActive?: boolean
  cells: MeritMatrixCellDto[]
}

export interface MeritSuggestionDto {
  performanceBand: PerformanceBand
  rangePosition: MeritRangePosition
  meritPct: number
  currentSalary: number
  meritAmount: number
  newSalary: number
}

// ── Compensation Budgets ─────────────────────────────────────────────────

export type BudgetType = 'MERIT' | 'BONUS' | 'INCENTIVE' | 'PROMOTION'

export type BudgetScopeType = 'GLOBAL' | 'DEPARTMENT' | 'MANAGER' | 'GRADE' | 'LEGAL_ENTITY'

export interface CompensationBudgetDto {
  id: string
  cycleId: string | null
  scopeType: BudgetScopeType
  scopeRef: string | null
  budgetType: BudgetType
  amount: number
  currency: string
  consumedAmount: number
  effectiveFrom: string
  effectiveTo: string | null
  isActive: boolean
}

export interface CompensationBudgetRequest {
  cycleId?: string | null
  scopeType: BudgetScopeType
  scopeRef?: string | null
  budgetType: BudgetType
  amount: number
  currency: string
  effectiveFrom: string
  effectiveTo?: string | null
  isActive?: boolean
}

export interface BudgetStatusDto {
  budgetId: string
  amount: number
  consumedAmount: number
  remaining: number
  currency: string
}

// ── Incentive Plans ──────────────────────────────────────────────────────

export type IncentiveMeasure = 'KPI' | 'SALES' | 'PRODUCTION' | 'OTHER'

export type IncentivePayoutStatus = 'DRAFT' | 'APPROVED' | 'PAID' | 'CANCELLED'

export interface IncentivePlanDto {
  id: string
  code: string
  name: string
  measure: IncentiveMeasure
  targetPct: number
  thresholdAchievement: number
  targetAchievement: number
  capAchievement: number
  maxPayoutPct: number
  currency: string
  active: boolean
}

export interface IncentivePlanRequest {
  code: string
  name: string
  measure: IncentiveMeasure
  targetPct: number
  thresholdAchievement: number
  targetAchievement: number
  capAchievement: number
  maxPayoutPct: number
  currency: string
  active?: boolean
}

export interface IncentivePayoutDto {
  id: string
  planId: string
  employeeId: string
  employeeName?: string
  period: string
  eligibleSalary: number
  achievementPct: number
  payoutPct: number
  payoutAmount: number
  status: IncentivePayoutStatus
  approvedBy: string | null
  approvedAt: string | null
}

export interface CreateIncentivePayoutRequest {
  planId: string
  employeeId: string
  period: string
  achievementPct: number
}

// ── Commission Plans ─────────────────────────────────────────────────────

export type CommissionBasis = 'REVENUE' | 'GROSS_MARGIN' | 'COLLECTIONS'

export type CommissionPayoutStatus = 'DRAFT' | 'APPROVED' | 'PAID' | 'CANCELLED'

export interface CommissionTierDto {
  id?: string
  fromAmount: number
  toAmount: number | null
  ratePct: number
  sortOrder: number
}

export interface CommissionPlanDto {
  id: string
  code: string
  name: string
  basis: CommissionBasis
  flatRatePct: number | null
  tiered: boolean
  currency: string
  active: boolean
  tiers: CommissionTierDto[]
}

export interface CommissionPlanRequest {
  code: string
  name: string
  basis: CommissionBasis
  flatRatePct?: number | null
  tiered: boolean
  currency: string
  active?: boolean
  tiers?: CommissionTierDto[]
}

export interface CommissionPayoutDto {
  id: string
  planId: string
  employeeId: string
  employeeName?: string
  period: string
  salesAmount: number
  commissionAmount: number
  status: CommissionPayoutStatus
  approvedBy: string | null
  approvedAt: string | null
}

export interface CreateCommissionPayoutRequest {
  planId: string
  employeeId: string
  period: string
  salesAmount: number
}

// ── M367: Market Data ────────────────────────────────────────────────────

export interface MarketSalarySurveyDto {
  id: string
  provider: string
  surveyYear: number
  country?: string
  currency?: string
  notes?: string
}

export interface CreateMarketSurveyRequest {
  provider: string
  surveyYear: number
  country?: string
  currency?: string
  notes?: string
}

export interface MarketSalaryDataDto {
  id: string
  surveyId: string
  jobCode?: string
  gradeCode?: string
  location?: string
  p25?: number
  p50?: number
  p75?: number
  p90?: number
  currency?: string
}

export interface AddMarketDataRequest {
  jobCode?: string
  gradeCode?: string
  location?: string
  p25?: number
  p50?: number
  p75?: number
  p90?: number
  currency?: string
}

export interface MarketComparisonDto {
  currentSalary: number
  gradeCode?: string
  surveyProvider?: string
  surveyYear?: number
  p25?: number
  p50?: number
  p75?: number
  p90?: number
  marketRatio?: number
  positionVsMarket?: string
}

// ── M368: Total Comp Statements ──────────────────────────────────────────

export type TotalCompStatementStatus = 'GENERATED' | 'RELEASED'

export interface TotalCompStatementDto {
  id: string
  employeeId: string
  employeeName?: string
  year: number
  baseSalary: number
  allowancesTotal: number
  bonusTotal: number
  incentivesTotal: number
  commissionTotal: number
  employerBenefitsTotal: number
  employerContributionsTotal: number
  totalComp: number
  currency: string
  status: TotalCompStatementStatus
  generatedAt?: string
  releasedAt?: string
}

// ── M369: Comp → Payroll Transfer ────────────────────────────────────────

export interface PayrollCompTransferDto {
  id: string
  sourceType: string
  sourceId: string
  employeeId: string
  employeeName?: string
  targetRunId: string
  amount: number
  payrollBonusId?: string
  status: string
  transferredAt?: string
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

  // Merit Matrices
  listMeritMatrices: () =>
    api.get<MeritMatrixDto[]>('/api/compensation/merit-matrices').then((r) => r.data),
  getMeritMatrix: (id: string) =>
    api.get<MeritMatrixDto>(`/api/compensation/merit-matrices/${id}`).then((r) => r.data),
  createMeritMatrix: (payload: MeritMatrixRequest) =>
    api.post<MeritMatrixDto>('/api/compensation/merit-matrices', payload).then((r) => r.data),
  updateMeritMatrix: (id: string, payload: MeritMatrixRequest) =>
    api.put<MeritMatrixDto>(`/api/compensation/merit-matrices/${id}`, payload).then((r) => r.data),
  deactivateMeritMatrix: (id: string) =>
    api.delete(`/api/compensation/merit-matrices/${id}`).then((r) => r.data),
  suggestMerit: (employeeId: string, matrixId: string) =>
    api
      .get<MeritSuggestionDto>(
        `/api/compensation/merit-matrices/suggest?employeeId=${employeeId}&matrixId=${matrixId}`,
      )
      .then((r) => r.data),

  // Compensation Budgets
  listBudgets: (filters?: { budgetType?: BudgetType; scopeType?: BudgetScopeType }) => {
    const params = new URLSearchParams()
    if (filters?.budgetType) params.set('budgetType', filters.budgetType)
    if (filters?.scopeType) params.set('scopeType', filters.scopeType)
    const query = params.toString()
    return api
      .get<CompensationBudgetDto[]>(`/api/compensation/budgets${query ? `?${query}` : ''}`)
      .then((r) => r.data)
  },
  getBudget: (id: string) =>
    api.get<CompensationBudgetDto>(`/api/compensation/budgets/${id}`).then((r) => r.data),
  createBudget: (payload: CompensationBudgetRequest) =>
    api.post<CompensationBudgetDto>('/api/compensation/budgets', payload).then((r) => r.data),
  updateBudget: (id: string, payload: CompensationBudgetRequest) =>
    api.put<CompensationBudgetDto>(`/api/compensation/budgets/${id}`, payload).then((r) => r.data),
  deactivateBudget: (id: string) =>
    api.delete(`/api/compensation/budgets/${id}`).then((r) => r.data),
  getBudgetStatus: (id: string) =>
    api.get<BudgetStatusDto>(`/api/compensation/budgets/${id}/status`).then((r) => r.data),

  // Incentive Plans
  listIncentivePlans: () =>
    api.get<IncentivePlanDto[]>('/api/compensation/incentive-plans').then((r) => r.data),
  getIncentivePlan: (id: string) =>
    api.get<IncentivePlanDto>(`/api/compensation/incentive-plans/${id}`).then((r) => r.data),
  createIncentivePlan: (payload: IncentivePlanRequest) =>
    api.post<IncentivePlanDto>('/api/compensation/incentive-plans', payload).then((r) => r.data),
  updateIncentivePlan: (id: string, payload: IncentivePlanRequest) =>
    api.put<IncentivePlanDto>(`/api/compensation/incentive-plans/${id}`, payload).then((r) => r.data),
  deactivateIncentivePlan: (id: string) =>
    api.delete(`/api/compensation/incentive-plans/${id}`).then((r) => r.data),

  // Incentive Payouts
  listIncentivePayouts: (filters?: { planId?: string; employeeId?: string; status?: IncentivePayoutStatus }) => {
    const params = new URLSearchParams()
    if (filters?.planId) params.set('planId', filters.planId)
    if (filters?.employeeId) params.set('employeeId', filters.employeeId)
    if (filters?.status) params.set('status', filters.status)
    const query = params.toString()
    return api
      .get<IncentivePayoutDto[]>(`/api/compensation/incentive-payouts${query ? `?${query}` : ''}`)
      .then((r) => r.data)
  },
  createIncentivePayout: (payload: CreateIncentivePayoutRequest) =>
    api.post<IncentivePayoutDto>('/api/compensation/incentive-payouts', payload).then((r) => r.data),
  approveIncentivePayout: (id: string) =>
    api.post(`/api/compensation/incentive-payouts/${id}/approve`).then((r) => r.data),
  cancelIncentivePayout: (id: string) =>
    api.post(`/api/compensation/incentive-payouts/${id}/cancel`).then((r) => r.data),

  // Commission Plans
  listCommissionPlans: () =>
    api.get<CommissionPlanDto[]>('/api/compensation/commission-plans').then((r) => r.data),
  getCommissionPlan: (id: string) =>
    api.get<CommissionPlanDto>(`/api/compensation/commission-plans/${id}`).then((r) => r.data),
  createCommissionPlan: (payload: CommissionPlanRequest) =>
    api.post<CommissionPlanDto>('/api/compensation/commission-plans', payload).then((r) => r.data),
  updateCommissionPlan: (id: string, payload: CommissionPlanRequest) =>
    api.put<CommissionPlanDto>(`/api/compensation/commission-plans/${id}`, payload).then((r) => r.data),
  deactivateCommissionPlan: (id: string) =>
    api.delete(`/api/compensation/commission-plans/${id}`).then((r) => r.data),

  // Commission Payouts
  listCommissionPayouts: (filters?: { planId?: string; employeeId?: string; status?: CommissionPayoutStatus }) => {
    const params = new URLSearchParams()
    if (filters?.planId) params.set('planId', filters.planId)
    if (filters?.employeeId) params.set('employeeId', filters.employeeId)
    if (filters?.status) params.set('status', filters.status)
    const query = params.toString()
    return api
      .get<CommissionPayoutDto[]>(`/api/compensation/commission-payouts${query ? `?${query}` : ''}`)
      .then((r) => r.data)
  },
  createCommissionPayout: (payload: CreateCommissionPayoutRequest) =>
    api.post<CommissionPayoutDto>('/api/compensation/commission-payouts', payload).then((r) => r.data),
  approveCommissionPayout: (id: string) =>
    api.post(`/api/compensation/commission-payouts/${id}/approve`).then((r) => r.data),
  cancelCommissionPayout: (id: string) =>
    api.post(`/api/compensation/commission-payouts/${id}/cancel`).then((r) => r.data),

  // ── M367: Market Data ──────────────────────────────────────────────────────

  listMarketSurveys: () =>
    api.get<MarketSalarySurveyDto[]>('/api/compensation/market/surveys').then((r) => r.data),
  createMarketSurvey: (payload: CreateMarketSurveyRequest) =>
    api.post<MarketSalarySurveyDto>('/api/compensation/market/surveys', payload).then((r) => r.data),
  deleteMarketSurvey: (id: string) =>
    api.delete(`/api/compensation/market/surveys/${id}`).then((r) => r.data),
  listMarketData: (surveyId: string) =>
    api.get<MarketSalaryDataDto[]>(`/api/compensation/market/surveys/${surveyId}/data`).then((r) => r.data),
  addMarketData: (surveyId: string, payload: AddMarketDataRequest) =>
    api.post<MarketSalaryDataDto>(`/api/compensation/market/surveys/${surveyId}/data`, payload).then((r) => r.data),
  deleteMarketData: (id: string) =>
    api.delete(`/api/compensation/market/data/${id}`).then((r) => r.data),
  compareMarket: (employeeId: string, surveyId?: string) => {
    const params = new URLSearchParams({ employeeId })
    if (surveyId) params.set('surveyId', surveyId)
    return api.get<MarketComparisonDto>(`/api/compensation/market/compare?${params.toString()}`).then((r) => r.data)
  },

  // ── M368: Total Comp Statements ─────────────────────────────────────────────

  generateStatement: (employeeId: string, year: number) =>
    api.post<TotalCompStatementDto>(`/api/compensation/total-comp-statements/generate?employeeId=${employeeId}&year=${year}`).then((r) => r.data),
  generateAllStatements: (year: number) =>
    api.post<{ generated: number }>(`/api/compensation/total-comp-statements/generate-all?year=${year}`).then((r) => r.data),
  listStatements: (year: number) =>
    api.get<TotalCompStatementDto[]>(`/api/compensation/total-comp-statements?year=${year}`).then((r) => r.data),
  releaseStatement: (id: string) =>
    api.post<TotalCompStatementDto>(`/api/compensation/total-comp-statements/${id}/release`).then((r) => r.data),
  releaseAllStatements: (year: number) =>
    api.post<{ released: number }>(`/api/compensation/total-comp-statements/release-all?year=${year}`).then((r) => r.data),
  downloadStatement: async (id: string, employeeName: string, year: number) => {
    const response = await api.get(`/api/compensation/total-comp-statements/${id}/download`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `total-comp-statement-${employeeName.replace(/\s/g, '-')}-${year}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  },
  myStatement: (year: number) =>
    api.get<TotalCompStatementDto>(`/api/compensation/total-comp-statements/employees/me?year=${year}`).then((r) => r.data),
  downloadMyStatement: async (year: number) => {
    const response = await api.get(`/api/compensation/total-comp-statements/employees/me/download?year=${year}`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `my-total-comp-statement-${year}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  },

  // ── M369: Comp → Payroll Transfer ───────────────────────────────────────────

  transferToPayroll: (payrollRunId: string) =>
    api.post<{ transferredCount: number; skippedCount: number }>(`/api/compensation/transfers/to-run/${payrollRunId}`).then((r) => r.data),
  listTransfers: (status?: string) => {
    const params = new URLSearchParams()
    if (status) params.set('status', status)
    const query = params.toString()
    return api.get<PayrollCompTransferDto[]>(`/api/compensation/transfers${query ? `?${query}` : ''}`).then((r) => r.data)
  },
}
