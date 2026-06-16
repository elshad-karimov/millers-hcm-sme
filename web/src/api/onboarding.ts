// M300 — Onboarding journey hub + HR console (Onboarding PRD §1 / §3).
// Read-only projections over the M105 checklist engine, scoped to ONBOARDING.

import { api } from './client'
import type { AssignmentResponse, ChecklistAssignmentStatus } from './checklists'

export interface OnboardingRow {
  assignmentId: string
  employeeId: string
  employeeNo?: string | null
  employeeName?: string | null
  department: string
  templateName?: string | null
  joinDate?: string | null
  daysToJoin?: number | null
  status: ChecklistAssignmentStatus
  totalTasks: number
  completedTasks: number
  requiredTotal: number
  requiredCompleted: number
  progressPercent: number
  overdueTaskCount: number
  nextDueDate?: string | null
}

export interface TypeCount {
  taskType: string
  pending: number
}

export interface DeptCount {
  department: string
  onboardings: number
  overdueTasks: number
}

export interface OnboardingOverview {
  active: number
  joiningThisWeek: number
  joiningThisMonth: number
  assignmentsWithOverdue: number
  totalOverdueTasks: number
  rows: OnboardingRow[]
  pendingByType: TypeCount[]
  byDepartment: DeptCount[]
}

export interface OnboardingJourney {
  employeeId: string
  employeeNo?: string | null
  employeeName?: string | null
  department?: string | null
  joinDate?: string | null
  daysToJoin?: number | null
  hasActiveOnboarding: boolean
  assignment?: AssignmentResponse | null
}

export const onboardingApi = {
  overview: () => api.get<OnboardingOverview>('/onboarding/overview').then((r) => r.data),
  journey: (employeeId: string) =>
    api.get<OnboardingJourney>(`/onboarding/journey/${employeeId}`).then((r) => r.data),
}
