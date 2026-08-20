import { api } from './client'

/**
 * Read-only pricing of approved timesheets, for payroll only.
 *
 * Nothing here writes: no payroll run, no result, no payslip. It exists so the
 * numbers can be checked against the January 2026 workbook before the engine is
 * allowed to pay anyone.
 */

export interface PayLine {
  categoryCode: string
  label?: string | null
  quantity?: number | null
  rate?: number | null
  amount: number
}

export interface PayResult {
  hourlyRate: number
  overtimeRate: number
  earnings: PayLine[]
  gross: number
  contributionExemptAmount: number
  incomeTax: number
  spf: number
  unemploymentFund: number
  compulsoryInsurance: number
  lifeInsurance: number
  azercell: number
  advance: number
  totalDeductions: number
  netPay: number
  warnings: string[]
}

export interface EmployeePreview {
  employeeId: string
  employeeNo?: string | null
  employeeName?: string | null
  positionTitle?: string | null
  timesheetStatus: string
  baseSalary: number
  normHours: number
  quantities: Record<string, number>
  result: PayResult
  blockers: string[]
}

export interface PeriodPreview {
  year: number
  month: number
  normHours: number
  priceable: number
  notPriceable: number
  totalGross: number
  totalNet: number
  withBlockers: number
  employees: EmployeePreview[]
}

export const timePayInputsApi = {
  period: (year: number, month: number) =>
    api.get<PeriodPreview>(`/payroll/time-inputs/${year}/${month}`).then((r) => r.data),

  employee: (year: number, month: number, employeeId: string) =>
    api.get<EmployeePreview>(`/payroll/time-inputs/${year}/${month}/${employeeId}`)
      .then((r) => r.data),
}

/** Workbook column order, so the screen reads like the spreadsheet it replaces. */
export const CATEGORY_LABELS: Record<string, string> = {
  OFFSHORE_HOURS: 'Offshore hours',
  ONSHORE_HOURS: 'Onshore working hours',
  ONSHORE_OVERTIME_HOURS: 'Onshore overtime hours',
  QUAYSIDE_HOURS: 'Quayside hours',
  EXCESS_HOURS: 'Excess hours',
  MEAL_ALLOWANCE_DAYS: 'Meal allowance (days)',
  TRANSPORT_ALLOWANCE_DAYS: 'Transport allowance (days)',
  HOTEL_QUARANTINE_HOURS: 'Hotel quarantine hours',
  OFFSHORE_NIGHT_HOURS: 'Offshore nightshift hours',
  QUAYSIDE_NIGHT_HOURS: 'Quayside nightshift hours',
  OFFSHORE_HOLIDAY_HOURS: 'Offshore rota on public holidays',
  QUAYSIDE_HOLIDAY_HOURS: 'Quayside rota on public holidays',
  VACATION_HOURS: 'Vacation hours',
  SICK_LEAVE_HOURS: 'Sick leave hours',
}

export const categoryLabel = (code: string) =>
  CATEGORY_LABELS[code] ?? code.replaceAll('_', ' ').toLowerCase()
