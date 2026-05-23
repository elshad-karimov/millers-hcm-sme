import { api } from './client'

export interface LabeledCount { label: string; count: number }

export interface HeadcountReport {
  totalEmployees: number
  activeEmployees: number
  onProbation: number
  onLeave: number
  terminatedYearToDate: number
  attritionRatePercent: number
  byStatus: LabeledCount[]
  byDepartment: LabeledCount[]
  hiresLast12Months: LabeledCount[]
  leaversLast12Months: LabeledCount[]
}

export interface AttritionReport {
  year: number
  terminationsTotal: number
  attritionRatePercent: number
  byReason: LabeledCount[]
  byMonth: LabeledCount[]
  totalSettlementPayout: number
  currency: string
}

export interface PayrollPeriodRow {
  runId: string
  runNo: string
  year: number
  month: number
  status: string
  totalGross: number
  totalNet: number
  totalTax: number
  totalDeductions: number
  employeeCount: number
  currency: string
}

export interface PayrollSummaryReport {
  year: number
  totalGrossYtd: number
  totalNetYtd: number
  totalTaxYtd: number
  totalDeductionsYtd: number
  runsClosed: number
  runsInProgress: number
  runs: PayrollPeriodRow[]
}

export interface LeaveTypeUsage {
  leaveTypeCode: string
  leaveTypeName: string
  entitlementSum: number
  usedSum: number
  reservedSum: number
  remainingSum: number
  employeeCount: number
}

export interface LeaveAtRiskRow {
  employeeId: string
  employeeNo: string
  fullName: string
  leaveTypeCode: string
  remaining: number
  entitlement: number
}

export interface LeaveReport {
  year: number
  byType: LeaveTypeUsage[]
  overdrawn: LeaveAtRiskRow[]
  highBalanceAtRisk: LeaveAtRiskRow[]
}

export interface AttendanceEmployeeRow {
  employeeId: string
  employeeNo: string
  fullName: string
  workedHours: number
  lateMinutes: number
  earlyMinutes: number
  overtimeMinutes: number
  workDays: number
  absentDays: number
}

export interface AttendanceReport {
  from: string
  to: string
  totalWorkedHours: number
  totalLateMinutes: number
  totalEarlyMinutes: number
  totalOvertimeMinutes: number
  totalAbsentDays: number
  byEmployee: AttendanceEmployeeRow[]
}

export interface MandatoryCourseRow {
  courseId: string
  courseNo: string
  code: string
  title: string
  enrolled: number
  passed: number
  failed: number
  inProgress: number
  completionRatePercent: number
}

export interface NonCompliantEmployee {
  employeeId: string
  employeeNo: string
  fullName: string
  courseId: string
  courseTitle: string
  reason: string
}

export interface TrainingReport {
  mandatoryCourses: number
  employeesActive: number
  overallCompliancePercent: number
  perCourse: MandatoryCourseRow[]
  nonCompliant: NonCompliantEmployee[]
}

export interface RatingBucketRow { bucket: number; count: number }

export interface PerformanceCycleSummary {
  cycleId: string
  cycleCode: string
  cycleName: string
  reviewsTotal: number
  reviewsCompleted: number
  averageFinalRating: number | null
  distribution: RatingBucketRow[]
  recommendationBreakdown: LabeledCount[]
}

export interface PerformanceReport {
  cycles: PerformanceCycleSummary[]
}

export interface VacancyFunnelRow {
  vacancyId: string
  vacancyNo: string
  title: string
  status: string
  openings: number
  applicationsTotal: number
  hires: number
  averageTimeToHireDays: number | null
}

export interface RecruitmentReport {
  vacanciesOpen: number
  vacanciesFilled: number
  candidatesPool: number
  applicationsInProgress: number
  averageTimeToHireDays: number
  applicationsByStage: LabeledCount[]
  byVacancy: VacancyFunnelRow[]
}

export const reportsApi = {
  headcount: () => api.get<HeadcountReport>('/reports/headcount').then((r) => r.data),
  attrition: (year?: number) =>
    api.get<AttritionReport>('/reports/attrition', { params: { year } }).then((r) => r.data),
  payrollSummary: (year?: number) =>
    api
      .get<PayrollSummaryReport>('/reports/payroll-summary', { params: { year } })
      .then((r) => r.data),
  leave: (year?: number) =>
    api.get<LeaveReport>('/reports/leave', { params: { year } }).then((r) => r.data),
  attendance: (from?: string, to?: string) =>
    api
      .get<AttendanceReport>('/reports/attendance', { params: { from, to } })
      .then((r) => r.data),
  training: () => api.get<TrainingReport>('/reports/training').then((r) => r.data),
  performance: (cycleId?: string) =>
    api
      .get<PerformanceReport>('/reports/performance', { params: { cycleId } })
      .then((r) => r.data),
  recruitment: () => api.get<RecruitmentReport>('/reports/recruitment').then((r) => r.data),
}
