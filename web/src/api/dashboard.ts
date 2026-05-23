import { api } from './client'

// ── Shared primitives ──────────────────────────────────────────────────────────
export interface LabeledValue { label: string; value: number }
export interface MonthPoint   { month: string; value: number }

// ── Headcount trend ────────────────────────────────────────────────────────────
export interface HeadcountTrend {
  headcount:    MonthPoint[]
  hires:        MonthPoint[]
  leavers:      MonthPoint[]
  byDepartment: LabeledValue[]
  byStatus:     LabeledValue[]
}

// ── Payroll trend ──────────────────────────────────────────────────────────────
export interface PayrollMonthPoint {
  month:         string
  gross:         number
  net:           number
  tax:           number
  employeeCount: number
}
export interface PayrollTrend {
  monthly:       PayrollMonthPoint[]
  totalGrossYtd: number
  totalNetYtd:   number
  totalTaxYtd:   number
}

// ── Turnover dashboard ─────────────────────────────────────────────────────────
export interface TurnoverDashboard {
  year:                 number
  terminationsYtd:      number
  attritionRatePercent: number
  avgTenureMonths:      number
  monthlyAttritionRate: MonthPoint[]
  byReason:             LabeledValue[]
}

// ── Manager team dashboard ─────────────────────────────────────────────────────
export interface TeamMemberRow {
  employeeNo:       string
  firstName:        string
  lastName:         string
  positionTitle:    string
  employmentStatus: string
}
export interface UpcomingLeaveRow {
  employeeName:  string
  leaveTypeCode: string
  startDate:     string
  endDate:       string
  totalDays:     number
}
export interface ManagerTeamDashboard {
  teamSize:        number
  pendingApprovals: number
  statusBreakdown: LabeledValue[]
  members:         TeamMemberRow[]
  upcomingLeave:   UpcomingLeaveRow[]
}

// ── Executive dashboard ────────────────────────────────────────────────────────
export interface ExecutiveDashboard {
  totalHeadcount:           number
  currentMonthPayrollCost:  number
  attritionRateYtd:         number
  trainingCompliancePercent: number
  openVacancies:            number
  pendingApprovals:         number
  headcountTrend:           MonthPoint[]
  payrollTrend:             PayrollMonthPoint[]
}

// ── API calls ──────────────────────────────────────────────────────────────────
export const dashboardApi = {
  headcountTrend: (months = 12) =>
    api.get<HeadcountTrend>('/dashboards/headcount-trend', { params: { months } }).then(r => r.data),

  payrollTrend: (year = 0) =>
    api.get<PayrollTrend>('/dashboards/payroll-trend', { params: { year } }).then(r => r.data),

  turnover: (year = 0) =>
    api.get<TurnoverDashboard>('/dashboards/turnover', { params: { year } }).then(r => r.data),

  managerTeam: () =>
    api.get<ManagerTeamDashboard>('/dashboards/manager-team').then(r => r.data),

  executive: () =>
    api.get<ExecutiveDashboard>('/dashboards/executive').then(r => r.data),
}
