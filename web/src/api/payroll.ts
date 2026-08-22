import { api, tokenStore } from './client'

export type PayrollRunStatus =
  | 'DRAFT'
  | 'CALCULATED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'PAID'
  | 'CLOSED'
  | 'REOPENED'

export type BonusType =
  | 'FIXED'
  | 'PERCENTAGE'
  | 'PERFORMANCE'
  | 'ONE_TIME'
  | 'KPI'
  | 'DEPARTMENT'
  | 'MANUAL'
  | 'IMPORTED'

export type RunType = 'REGULAR' | 'OFF_CYCLE'

export type ComponentKind = 'EARNING' | 'DEDUCTION'

export type CalculationMethod = 'FIXED_AMOUNT' | 'PERCENTAGE_OF_BASE' | 'FLAT_RATE'

export type AdvanceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'DEDUCTED' | 'CANCELLED'

export type LoanStatus = 'PENDING' | 'ACTIVE' | 'FULLY_REPAID' | 'WRITTEN_OFF' | 'CANCELLED'

export type GLAccountType = 'DEBIT' | 'CREDIT'

export type GLJournalStatus = 'DRAFT' | 'APPROVED' | 'POSTED' | 'REVERSED'

export type CertificateStatus = 'DRAFT' | 'GENERATED' | 'DELIVERED'

export interface PayrollRun {
  id: string
  runNo: string
  periodYear: number
  periodMonth: number
  jurisdiction: string
  currency: string
  status: PayrollRunStatus
  workflowInstanceId?: string | null
  totalGross: number
  totalIncomeTax: number
  totalDsmfEmployee: number
  totalDsmfEmployer: number
  totalMmiEmployee: number
  totalMmiEmployer: number
  totalUnemplEmployee: number
  totalUnemplEmployer: number
  totalNet: number
  /** M41: combined taxable + non-taxable allowance spend across the run. */
  totalAllowance: number
  employeeCount: number
  calculatedAt?: string | null
  calculatedBy?: string | null
  approvedAt?: string | null
  approvedBy?: string | null
  paidAt?: string | null
  closedAt?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface PayrollResult {
  id: string
  runId: string
  employeeId: string
  payslipNo: string
  timesheetId?: string | null
  baseSalary: number
  workedHours: number
  expectedMonthlyHours: number
  proRationFactor: number
  overtimeHours: number
  overtimePay: number
  bonusAmount: number
  allowanceAmount: number
  deductionAmount: number
  grossAmount: number
  incomeTax: number
  dsmfEmployee: number
  dsmfEmployer: number
  mmiEmployee: number
  mmiEmployer: number
  unemplEmployee: number
  unemplEmployer: number
  netAmount: number
  calculationDetails?: string | null
  createdAt: string
}

export interface CompensationResponse {
  id: string
  employeeId: string
  monthlyBaseSalary: number
  currency: string
  effectiveFrom: string
  effectiveTo?: string | null
  reason?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface CompensationRequest {
  employeeId: string
  monthlyBaseSalary: number
  currency?: string
  effectiveFrom: string
  effectiveTo?: string
  reason?: string
}

export interface BankAccountResponse {
  id: string
  employeeId: string
  bankCode?: string | null
  bankName?: string | null
  /** Last-4 mask — always populated. */
  ibanMasked?: string | null
  /** Plaintext — only for cleared roles (SYSTEM_ADMIN / HR_ADMIN / AUDITOR). */
  iban?: string | null
  accountNumberMasked?: string | null
  accountNumber?: string | null
  /** M74 — ISO 9362 SWIFT/BIC code. */
  swiftBic?: string | null
  currency: string
  /** M74 — fraction of net salary routed to this account. Sums to 100 across active accounts. */
  salarySplitPercent: number
  /** M74 — true for the primary account. At most one active primary per employee. */
  primary: boolean
  active: boolean
  notes?: string | null
  lastChangeWorkflowId?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface BankAccountRequest {
  /** Non-null on update; omitted on create. */
  id?: string
  employeeId: string
  bankCode?: string
  bankName?: string
  iban?: string
  accountNumber?: string
  /** Uppercase 8 or 11 chars (ISO 9362). */
  swiftBic?: string
  currency?: string
  /** Defaults to 100 when omitted (single-account case). */
  salarySplitPercent?: number
  primary?: boolean
  active?: boolean
  notes?: string
}

export interface AddBonusRequest {
  employeeId: string
  bonusType: BonusType
  amount: number
  note?: string
}

/**
 * M41: one allowance line attached to a payroll run for a given
 * employee. Snapshotted at calc time so historical payslips stay
 * stable across catalogue edits.
 */
export interface PayrollAllowance {
  id: string
  runId: string
  employeeId: string
  employeeAllowanceId?: string | null
  allowanceTypeId?: string | null
  allowanceTypeCode?: string | null
  allowanceTypeName?: string | null
  amount: number
  currency: string
  taxable: boolean
  note?: string | null
  createdAt: string
}

// ── M349: Salary Components ───────────────────────────────────────────────────

export interface SalaryComponent {
  id: string
  code: string
  name: string
  kind: ComponentKind
  calculationMethod: CalculationMethod
  defaultAmount?: number | null
  percentage?: number | null
  isTaxable: boolean
  contributionExempt: boolean
  isStatutory: boolean
  isActive: boolean
  createdAt: string
}

export interface SalaryComponentRequest {
  code: string
  name: string
  kind: ComponentKind
  calculationMethod: CalculationMethod
  percentage?: number
  defaultAmount?: number
  isTaxable: boolean
  contributionExempt?: boolean
}

export interface ComponentAssignment {
  id: string
  employeeId: string
  componentId: string
  componentCode: string
  componentName: string
  componentKind: ComponentKind
  amountOverride?: number | null
  effectiveFrom: string
  effectiveTo?: string | null
}

export interface ComponentAssignmentRequest {
  componentId: string
  effectiveFrom: string
  amountOverride?: number
  reason?: string
}

// ── M350: Run enhancements ────────────────────────────────────────────────────

export interface CreateRunRequest {
  periodYear: number
  periodMonth: number
  jurisdiction?: string
  currency?: string
  runType?: RunType
  description?: string
  employeeIds?: string[]
}

export interface HoldEmployeeRequest {
  employeeId: string
  reason: string
}

export interface SetHoldRequest {
  holds: HoldEmployeeRequest[]
}

/**
 * Mirrors PayrollPreFlightResponse on the server: the lists sit at the TOP
 * LEVEL, not under a `checklist` object. The type used to claim otherwise, and
 * because it was only a type nothing complained until the screen ran and threw
 * on `checklist.noCompensation` of undefined.
 */
export interface PreFlightEmployeeIssue {
  employeeId: string
  employeeNo: string
  name: string
}

export interface PayrollPreFlightResponse {
  summary: {
    totalIssues: number
    employeesInScope: number
  }
  noCompensation: PreFlightEmployeeIssue[]
  noTimesheet: PreFlightEmployeeIssue[]
  onHold: PreFlightEmployeeIssue[]
  pendingAdvances: PreFlightEmployeeIssue[]
  retroactiveSalaryChange: PreFlightEmployeeIssue[]
}

// ── M351: Salary Advances ─────────────────────────────────────────────────────

export interface SalaryAdvance {
  id: string
  employeeId: string
  requestedAmount: number
  approvedAmount?: number | null
  repaymentYear?: number | null
  repaymentMonth?: number | null
  status: AdvanceStatus
  reason?: string | null
  requestedBy?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  createdAt: string
}

export interface CreateAdvanceRequest {
  employeeId: string
  requestedAmount: number
  reason?: string
}

export interface ApproveAdvanceRequest {
  approvedAmount: number
  repaymentYear: number
  repaymentMonth: number
}

export interface RejectAdvanceRequest {
  reason: string
}

// ── M352: Payroll Loans ───────────────────────────────────────────────────────

export interface PayrollLoan {
  id: string
  employeeId: string
  principalAmount: number
  monthlyInstallment: number
  outstandingBalance: number
  amountRepaid: number
  startDeductionYear: number
  startDeductionMonth: number
  status: LoanStatus
  description?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  createdAt: string
}

export interface CreateLoanRequest {
  employeeId: string
  principalAmount: number
  monthlyInstallment: number
  startDeductionYear: number
  startDeductionMonth: number
  description?: string
}

export interface WriteOffLoanRequest {
  reason: string
}

// ── M353: Variance Report ─────────────────────────────────────────────────────

export interface PayrollVarianceResponse {
  currentRunId: string
  priorRunId: string
  summary: {
    totalGrossChange: number
    pctChange: number
    highVarianceCount: number
    newEmployeeCount: number
    absentEmployeeCount: number
  }
  employees: Array<{
    employeeId: string
    employeeNo: string
    name: string
    priorGross: number
    currentGross: number
    grossDelta: number
    grossDeltaPct: number
    netDelta: number
    flags: string[]
  }>
}

// ── M353: YTD Summary ─────────────────────────────────────────────────────────

export interface YTDSummaryResponse {
  year: number
  employees: Array<{
    employeeId: string
    employeeNo: string
    name: string
    totalGross: number
    totalIncomeTax: number
    totalDsmf: number
    totalMmi: number
    totalUnemployment: number
    totalBonuses: number
    totalNet: number
    monthsCount: number
  }>
}

// ── M354: Year-End + Tax Certificates ────────────────────────────────────────

export interface AnnualTaxCertificate {
  id: string
  employeeId: string
  employeeNo: string
  employeeName: string
  maskedNationalId?: string | null
  year: number
  annualGross: number
  exemptAmount: number
  taxableGross: number
  totalTaxWithheld: number
  status: CertificateStatus
  generatedAt?: string | null
  generatedBy?: string | null
}

// ── M355: Cost Allocations + GL ───────────────────────────────────────────────

export interface CostAllocation {
  id: string
  employeeId: string
  costCenterCode: string
  allocationPct: number
  effectiveFrom: string
  effectiveTo?: string | null
}

export interface CostAllocationRequest {
  allocations: Array<{
    costCenterCode: string
    allocationPct: number
  }>
  effectiveFrom: string
}

export interface GLAccountMapping {
  id: string
  componentKind: string
  componentCode?: string | null
  accountType: GLAccountType
  glAccountCode: string
  glAccountName: string
  isActive: boolean
}

export interface GLAccountMappingRequest {
  componentKind: string
  componentCode?: string
  accountType: GLAccountType
  glAccountCode: string
  glAccountName: string
}

export interface GLJournalResponse {
  journal: {
    id: string
    runId: string
    journalDate: string
    status: GLJournalStatus
    totalDebit: number
    totalCredit: number
    createdAt: string
    createdBy?: string | null
    approvedBy?: string | null
    approvedAt?: string | null
    postedBy?: string | null
    postedAt?: string | null
    reversedJournalId?: string | null
  }
  lines: Array<{
    id: string
    lineNo: number
    costCenterCode?: string | null
    componentKind?: string | null
    accountType: GLAccountType
    glAccountCode: string
    glAccountName: string
    amount: number
  }>
}

// ── M356: PDF Payslips ────────────────────────────────────────────────────────

export interface PayslipInfo {
  runId: string
  periodYear: number
  periodMonth: number
  netAmount: number
  generatedAt?: string | null
}

export interface GeneratePayslipsResponse {
  generated: number
}

export interface SendPayslipsResponse {
  sent: number
  failed: number
}

// ── M357: Control Board ───────────────────────────────────────────────────────

export interface PayrollControlBoardResponse {
  currentRun?: {
    id: string
    periodYear: number
    periodMonth: number
    status: PayrollRunStatus
    runType: RunType
  } | null
  headcount: number
  totalGross: number
  totalNet: number
  totalTax: number
  momGrossVariancePct: number
  outstandingLoanBalance: number
  pendingAdvanceCount: number
}

// ── M358: Reports Suite ───────────────────────────────────────────────────────

export interface PeriodSummaryResponse {
  runId: string
  totalGross: number
  totalTax: number
  totalNet: number
  employees: Array<{
    employeeId: string
    employeeNo: string
    name: string
    gross: number
    tax: number
    net: number
  }>
}

export interface EmployerCostResponse {
  runId: string
  employees: Array<{
    employeeId: string
    employeeNo: string
    name: string
    gross: number
    dsmfEmployer: number
    mmiEmployer: number
    unemploymentEmployer: number
    totalEmployerCost: number
  }>
}

export interface LoanAdvanceStatusResponse {
  loans: Array<PayrollLoan>
  advances: Array<SalaryAdvance>
}

export interface BankReconciliationResponse {
  runId: string
  payrollTotal: number
  bankFileTotal: number
  delta: number
  isBalanced: boolean
}

// ── M467: GL Reconciliation ───────────────────────────────────────────────────

export type ReconciliationStatus = 'MATCHED' | 'DISCREPANCY'

export interface ReconciliationRow {
  description: string
  componentKind: string
  accountType: string
  payrollTotal: number
  glTotal: number
  difference: number
  status: ReconciliationStatus
}

export interface ReconciliationReport {
  year: number
  month: number
  rows: ReconciliationRow[]
}

export const payrollApi = {
  runs: () => api.get<PayrollRun[]>('/payroll/runs').then((r) => r.data),
  run: (id: string) => api.get<PayrollRun>(`/payroll/runs/${id}`).then((r) => r.data),
  results: (id: string) =>
    api.get<PayrollResult[]>(`/payroll/runs/${id}/results`).then((r) => r.data),
  /** M41: per-run / per-employee allowance lines snapshot. */
  allowances: (id: string, employeeId: string) =>
    api
      .get<PayrollAllowance[]>(`/payroll/runs/${id}/results/${employeeId}/allowances`)
      .then((r) => r.data),
  runAllowances: (id: string) =>
    api.get<PayrollAllowance[]>(`/payroll/runs/${id}/allowances`).then((r) => r.data),
  create: (req: CreateRunRequest) =>
    api.post<PayrollRun>('/payroll/runs', req).then((r) => r.data),
  calculate: (id: string) =>
    api.post<PayrollRun>(`/payroll/runs/${id}/calculate`).then((r) => r.data),
  addBonus: (id: string, payload: AddBonusRequest) =>
    api.post(`/payroll/runs/${id}/bonuses`, payload).then((r) => r.data),
  submit: (id: string) =>
    api.post<PayrollRun>(`/payroll/runs/${id}/submit`).then((r) => r.data),
  markPaid: (id: string) =>
    api.post<PayrollRun>(`/payroll/runs/${id}/mark-paid`).then((r) => r.data),
  close: (id: string) =>
    api.post<PayrollRun>(`/payroll/runs/${id}/close`).then((r) => r.data),
  reopen: (id: string) =>
    api.post<PayrollRun>(`/payroll/runs/${id}/reopen`).then((r) => r.data),
  bankFileUrl: (id: string) => {
    // Use the token in a query string is not desirable; we'll fetch via axios + blob
    const token = tokenStore.get()
    return { id, token }
  },

  // M350: Pre-flight + holds
  preFlight: (id: string) =>
    api.get<PayrollPreFlightResponse>(`/payroll/runs/${id}/pre-flight`).then((r) => r.data),
  setHolds: (id: string, payload: SetHoldRequest) =>
    api.post(`/payroll/runs/${id}/hold-employees`, payload),
  releaseHold: (id: string, employeeId: string) =>
    api.post(`/payroll/runs/${id}/hold-employees/${employeeId}/release`),

  // Compensation
  compensationHistory: (employeeId: string) =>
    api
      .get<CompensationResponse[]>('/payroll/compensation', { params: { employeeId } })
      .then((r) => r.data),
  setCompensation: (payload: CompensationRequest) =>
    api.post<CompensationResponse>('/payroll/compensation', payload).then((r) => r.data),

  // Bank accounts (M74 — multi-row with salary split)
  bankAccounts: (employeeId: string, activeOnly = false) =>
    api
      .get<BankAccountResponse[]>('/payroll/bank-accounts', {
        params: { employeeId, activeOnly },
      })
      .then((r) => r.data),
  /**
   * Legacy single-account accessor — returns the primary active account.
   * Pre-M74 frontend callers calling .bankAccount() keep working; new code
   * should prefer .bankAccounts() for the full list.
   */
  bankAccount: (employeeId: string) =>
    api
      .get<BankAccountResponse[]>('/payroll/bank-accounts', {
        params: { employeeId, activeOnly: true },
      })
      .then((r) => r.data.find((a) => a.primary) ?? r.data[0] ?? null),
  createBankAccount: (payload: BankAccountRequest) =>
    api.post<BankAccountResponse>('/payroll/bank-accounts', payload).then((r) => r.data),
  updateBankAccount: (id: string, payload: BankAccountRequest) =>
    api
      .put<BankAccountResponse>(`/payroll/bank-accounts/${id}`, payload)
      .then((r) => r.data),
  deleteBankAccount: (id: string) => api.delete(`/payroll/bank-accounts/${id}`),
  /** Backward compatibility — pre-M74 callers used setBankAccount as upsert. */
  setBankAccount: (payload: BankAccountRequest) =>
    api.post<BankAccountResponse>('/payroll/bank-accounts', payload).then((r) => r.data),

  // M349: Salary components.
  //
  // GET /payroll/components is the one payroll list endpoint that returns a
  // Spring Page ({content, totalElements, …}) rather than a bare array — every
  // sibling (runs, advances, loans, groups) returns a List. Callers reasonably
  // assumed an array and called .map() on it, which threw
  // "components.map is not a function" and, with no error boundary, blanked
  // the whole employee profile.
  //
  // Unwrap here rather than at each call site, and accept either shape so this
  // keeps working if the endpoint is ever aligned with its siblings.
  //
  // The explicit size is deliberate: Spring defaults to 20 rows per page, so
  // without it a tenant with more than 20 components would silently lose the
  // rest — a wrong dropdown rather than a visible error.
  components: (params?: { kind?: ComponentKind; isActive?: boolean; isStatutory?: boolean }) =>
    api
      .get<SalaryComponent[] | { content?: SalaryComponent[] }>('/payroll/components', {
        params: { size: 500, ...params },
      })
      .then((r) => {
        const body = r.data
        if (Array.isArray(body)) return body
        return Array.isArray(body?.content) ? body.content : []
      }),
  component: (id: string) =>
    api.get<SalaryComponent>(`/payroll/components/${id}`).then((r) => r.data),
  createComponent: (payload: SalaryComponentRequest) =>
    api.post<SalaryComponent>('/payroll/components', payload).then((r) => r.data),
  updateComponent: (id: string, payload: SalaryComponentRequest) =>
    api.put<SalaryComponent>(`/payroll/components/${id}`, payload).then((r) => r.data),
  deleteComponent: (id: string) => api.delete(`/payroll/components/${id}`),

  // Component assignments
  componentAssignments: (employeeId: string, activeOnly = true) =>
    api
      .get<ComponentAssignment[]>(`/payroll/employees/${employeeId}/component-assignments`, {
        params: { activeOnly },
      })
      .then((r) => r.data),
  createComponentAssignment: (employeeId: string, payload: ComponentAssignmentRequest) =>
    api
      .post<ComponentAssignment>(
        `/payroll/employees/${employeeId}/component-assignments`,
        payload,
      )
      .then((r) => r.data),
  deleteComponentAssignment: (employeeId: string, assignmentId: string) =>
    api.delete(`/payroll/employees/${employeeId}/component-assignments/${assignmentId}`),

  // M351: Salary advances
  advances: (params?: { employeeId?: string; status?: AdvanceStatus }) =>
    api.get<SalaryAdvance[]>('/payroll/advances', { params }).then((r) => r.data),
  advance: (id: string) =>
    api.get<SalaryAdvance>(`/payroll/advances/${id}`).then((r) => r.data),
  createAdvance: (payload: CreateAdvanceRequest) =>
    api.post<SalaryAdvance>('/payroll/advances', payload).then((r) => r.data),
  approveAdvance: (id: string, payload: ApproveAdvanceRequest) =>
    api.post<SalaryAdvance>(`/payroll/advances/${id}/approve`, payload).then((r) => r.data),
  rejectAdvance: (id: string, payload: RejectAdvanceRequest) =>
    api.post<SalaryAdvance>(`/payroll/advances/${id}/reject`, payload).then((r) => r.data),
  cancelAdvance: (id: string) =>
    api.post<SalaryAdvance>(`/payroll/advances/${id}/cancel`).then((r) => r.data),

  // M352: Payroll loans
  loans: (params?: { employeeId?: string; status?: LoanStatus }) =>
    api.get<PayrollLoan[]>('/payroll/loans', { params }).then((r) => r.data),
  loan: (id: string) =>
    api.get<PayrollLoan>(`/payroll/loans/${id}`).then((r) => r.data),
  createLoan: (payload: CreateLoanRequest) =>
    api.post<PayrollLoan>('/payroll/loans', payload).then((r) => r.data),
  cancelLoan: (id: string) =>
    api.post<PayrollLoan>(`/payroll/loans/${id}/cancel`).then((r) => r.data),
  writeOffLoan: (id: string, payload: WriteOffLoanRequest) =>
    api.post<PayrollLoan>(`/payroll/loans/${id}/write-off`, payload).then((r) => r.data),

  // M353: Variance & YTD reports
  varianceReport: (currentRunId: string, priorRunId: string) =>
    api
      .get<PayrollVarianceResponse>('/payroll/reports/variance', {
        params: { currentRunId, priorRunId },
      })
      .then((r) => r.data),
  ytdReport: (year: number, employeeId?: string) =>
    api
      .get<YTDSummaryResponse>('/payroll/reports/ytd', { params: { year, employeeId } })
      .then((r) => r.data),

  // M354: Year-end + tax certificates
  generateYearEndSummary: (year: number) =>
    api.post(`/payroll/year-end/generate-summary`, null, { params: { year } }),
  taxCertificates: (year: number) =>
    api
      .get<AnnualTaxCertificate[]>('/payroll/year-end/certificates', { params: { year } })
      .then((r) => r.data),
  generateTaxCertificates: (year: number) =>
    api.post(`/payroll/year-end/certificates/generate`, null, { params: { year } }),
  downloadTaxCertificate: async (id: string, employeeNo: string, year: number) => {
    const response = await api.get(`/payroll/year-end/certificates/${id}/download`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `tax-certificate-${employeeNo}-${year}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  },
  myTaxCertificate: (year: number) =>
    api
      .get<AnnualTaxCertificate>('/payroll/employees/me/year-end/certificate', {
        params: { year },
      })
      .then((r) => r.data),
  downloadMyTaxCertificate: async (year: number) => {
    const response = await api.get(`/payroll/employees/me/year-end/certificate`, {
      responseType: 'blob',
      params: { year },
    })
    const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `my-tax-certificate-${year}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  },

  // M355: Cost allocations
  costAllocations: (employeeId: string) =>
    api
      .get<CostAllocation[]>(`/payroll/employees/${employeeId}/cost-allocations`)
      .then((r) => r.data),
  setCostAllocations: (employeeId: string, payload: CostAllocationRequest) =>
    api
      .post<CostAllocation[]>(`/payroll/employees/${employeeId}/cost-allocations`, payload)
      .then((r) => r.data),

  // M355: GL account mappings
  glMappings: () =>
    api.get<GLAccountMapping[]>('/payroll/gl/account-mappings').then((r) => r.data),
  createGLMapping: (payload: GLAccountMappingRequest) =>
    api.post<GLAccountMapping>('/payroll/gl/account-mappings', payload).then((r) => r.data),

  // M355: GL journal
  generateGLJournal: (runId: string) =>
    api.post<GLJournalResponse>(`/payroll/runs/${runId}/gl-journal/generate`).then((r) => r.data),
  glJournal: (runId: string) =>
    api.get<GLJournalResponse>(`/payroll/runs/${runId}/gl-journal`).then((r) => r.data),
  downloadGLJournal: async (runId: string, journalNo: string) => {
    const response = await api.get(`/payroll/runs/${runId}/gl-journal/export`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data as BlobPart], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${journalNo}.csv`
    a.click()
    URL.revokeObjectURL(url)
  },
  // M465: Approve GL journal (DRAFT → APPROVED)
  approveGLJournal: (runId: string) =>
    api.post<GLJournalResponse['journal']>(`/payroll/runs/${runId}/gl-journal/approve`).then((r) => r.data),
  // M465: Post GL journal (APPROVED → POSTED)
  postGLJournal: (runId: string) =>
    api.post<GLJournalResponse['journal']>(`/payroll/runs/${runId}/gl-journal/post`).then((r) => r.data),
  // M466: Reverse a posted GL journal
  reverseGLJournal: (runId: string, journalId: string) =>
    api.post<GLJournalResponse['journal']>(`/payroll/runs/${runId}/gl-journal/${journalId}/reverse`).then((r) => r.data),

  // M467: GL Reconciliation
  glReconciliation: (year: number, month: number) =>
    api.get<ReconciliationReport>('/reports/gl-reconciliation', {
      params: { year, month },
    }).then((r) => r.data),

  // M356: Payslips
  generatePayslips: (runId: string) =>
    api
      .post<GeneratePayslipsResponse>(`/payroll/runs/${runId}/generate-payslips`)
      .then((r) => r.data),
  downloadPayslip: async (runId: string, employeeId: string, employeeNo: string) => {
    const response = await api.get(`/payroll/runs/${runId}/payslips/${employeeId}/download`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `payslip-${employeeNo}-${runId}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  },
  myPayslips: () =>
    api.get<PayslipInfo[]>('/payroll/employees/me/payslips').then((r) => r.data),
  downloadMyPayslip: async (runId: string, year: number, month: number) => {
    const response = await api.get(`/payroll/employees/me/payslips/${runId}/download`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `payslip-${year}-${String(month).padStart(2, '0')}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  },
  sendPayslips: (runId: string) =>
    api
      .post<SendPayslipsResponse>(`/payroll/runs/${runId}/send-payslips`)
      .then((r) => r.data),

  // M357: Control board
  controlBoard: () =>
    api.get<PayrollControlBoardResponse>('/payroll/control-board').then((r) => r.data),

  // M358: Reports suite
  periodSummary: (runId: string) =>
    api
      .get<PeriodSummaryResponse>('/payroll/reports/period-summary', { params: { runId } })
      .then((r) => r.data),
  employerCostReport: (runId: string) =>
    api
      .get<EmployerCostResponse>('/payroll/reports/employer-cost', { params: { runId } })
      .then((r) => r.data),
  loanAdvanceStatus: () =>
    api.get<LoanAdvanceStatusResponse>('/payroll/reports/loan-advance-status').then((r) => r.data),
  bankReconciliation: (runId: string) =>
    api
      .get<BankReconciliationResponse>('/payroll/reports/bank-reconciliation', {
        params: { runId },
      })
      .then((r) => r.data),
}

export type BankFileFormat = 'CSV' | 'ABB' | 'KAPITAL' | 'PASHA' | 'RESPUBLIKA' | 'SWIFT_MT103' | 'SEPA' | 'CUSTOM'

export async function downloadBankFile(id: string, format: BankFileFormat = 'CSV') {
  const response = await api.get(`/payroll/runs/${id}/bank-file`, {
    responseType: 'blob',
    params: { format },
  })
  const ext = format === 'RESPUBLIKA' ? 'txt' : 'csv'
  const mime = format === 'RESPUBLIKA' ? 'text/plain' : 'text/csv'
  const blob = new Blob([response.data as BlobPart], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `payroll-bank-${format.toLowerCase()}-${id}.${ext}`
  a.click()
  URL.revokeObjectURL(url)
}

// ── M158: ERP / Accounting journal export ─────────────────────────────────────

export type ErpExportFormat = 'CSV_GENERIC' | 'CSV_1C' | 'CSV_DYNAMICS365' | 'JSON'
export type ErpExportStatus = 'PENDING' | 'GENERATING' | 'READY' | 'FAILED'

export interface ErpExportLineResponse {
  id: string
  lineNo: number
  accountCode: string
  accountName?: string | null
  costCentre?: string | null
  description?: string | null
  debit: number
  credit: number
  currency: string
  employeeCount: number
}

export interface ErpExportResponse {
  id: string
  exportNo: string
  runId: string
  format: ErpExportFormat
  status: ErpExportStatus
  postingDate: string
  referenceNo?: string | null
  journalType?: string | null
  lineCount: number
  totalDebit?: number | null
  totalCredit?: number | null
  errorMessage?: string | null
  fileSizeBytes?: number | null
  createdBy?: string | null
  createdAt: string
  generatedAt?: string | null
  lines: ErpExportLineResponse[]
}

export interface ErpGenerateRequest {
  format: ErpExportFormat
  postingDate: string
  referenceNo?: string
  journalType?: string
}

export const erpExportApi = {
  listAll: () =>
    api.get<ErpExportResponse[]>('/payroll/erp-exports').then((r) => r.data),
  listByRun: (runId: string) =>
    api.get<ErpExportResponse[]>(`/payroll/runs/${runId}/erp-exports`).then((r) => r.data),
  get: (id: string) =>
    api.get<ErpExportResponse>(`/payroll/erp-exports/${id}`).then((r) => r.data),
  generate: (runId: string, body: ErpGenerateRequest) =>
    api.post<ErpExportResponse>(`/payroll/runs/${runId}/erp-exports`, body).then((r) => r.data),
}

export async function downloadErpExport(id: string, exportNo: string, format: ErpExportFormat) {
  const ext = format === 'JSON' ? 'json' : 'csv'
  const response = await api.get(`/payroll/erp-exports/${id}/download`, { responseType: 'blob' })
  const blob = new Blob([response.data as BlobPart],
    { type: format === 'JSON' ? 'application/json' : 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${exportNo}.${ext}`
  a.click()
  URL.revokeObjectURL(url)
}
