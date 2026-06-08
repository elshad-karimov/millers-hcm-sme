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
  create: (periodYear: number, periodMonth: number, jurisdiction = 'AZ', currency = 'AZN') =>
    api
      .post<PayrollRun>('/payroll/runs', { periodYear, periodMonth, jurisdiction, currency })
      .then((r) => r.data),
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
}

export async function downloadBankFile(id: string) {
  const response = await api.get(`/payroll/runs/${id}/bank-file`, { responseType: 'blob' })
  const blob = new Blob([response.data as BlobPart], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `payroll-bank-${id}.csv`
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
