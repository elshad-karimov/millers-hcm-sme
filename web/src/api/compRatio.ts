// M102 — Comp-ratio / salary-planning analytics.

import { api } from './client'

export type CompRiskLevel =
  | 'BELOW_RANGE'
  | 'LOW_IN_RANGE'
  | 'AT_MIDPOINT'
  | 'HIGH_IN_RANGE'
  | 'ABOVE_RANGE'
  | 'NO_BAND'

export interface EmployeeCompRatioRow {
  employeeId: string
  employeeNo: string
  fullName: string
  department?: string | null
  gradeCode?: string | null
  gradeName?: string | null
  minSalary?: number | null
  midpointSalary?: number | null
  maxSalary?: number | null
  actualSalary?: number | null
  compRatio?: number | null
  salaryVsMidpoint?: number | null
  riskLevel: CompRiskLevel
}

export interface GradeBandRow {
  gradeCode: string
  gradeName: string
  employeeCount: number
  minSalary?: number | null
  midpointSalary?: number | null
  maxSalary?: number | null
  avgActualSalary?: number | null
  avgCompRatio?: number | null
  belowRange: number
  atMidpoint: number
  aboveRange: number
}

export interface CompRatioReport {
  totalEmployees: number
  noGradeCount: number
  noBandCount: number
  overallAvgCompRatio?: number | null
  flightRiskCount: number
  employees: EmployeeCompRatioRow[]
  gradeBands: GradeBandRow[]
}

export const compRatioApi = {
  report: () =>
    api.get<CompRatioReport>('/compbenefits/comp-ratio').then((r) => r.data),
}
