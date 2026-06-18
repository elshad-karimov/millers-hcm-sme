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

// M304 — policy/contract/document acknowledgement (compliance audit).
export type AcknowledgementKind = 'POLICY' | 'CONTRACT' | 'DOCUMENT'

export interface Acknowledgement {
  id: string
  employeeId: string
  assignmentId?: string | null
  taskStatusId: string
  kind: AcknowledgementKind
  statement?: string | null
  reference?: string | null
  acknowledgedBy?: string | null
  acknowledgedAt: string
}

// M305 — buddy / mentor assignment.
export type BuddyRole = 'BUDDY' | 'MENTOR'
export type BuddyStatus = 'ACTIVE' | 'ENDED' | 'CANCELLED'

export interface Buddy {
  id: string
  employeeId: string
  employeeName?: string | null
  buddyEmployeeId: string
  buddyName?: string | null
  buddyNo?: string | null
  role: BuddyRole
  status: BuddyStatus
  notes?: string | null
  assignedBy?: string | null
  assignedAt: string
  endedAt?: string | null
  taskStatusId?: string | null
}

// M306 — first-day meeting / calendar invites (Phase C.2).
export type MeetingStatus = 'SCHEDULED' | 'CANCELLED'

export interface Meeting {
  id: string
  meetingNo: string
  employeeId: string
  taskStatusId?: string | null
  title: string
  description?: string | null
  location?: string | null
  startsAt: string
  durationMinutes: number
  attendeeEmails?: string | null
  calendarSequence: number
  inviteSentAt?: string | null
  status: MeetingStatus
  cancelledReason?: string | null
  scheduledBy?: string | null
  createdAt: string
}

export interface ScheduleMeetingRequest {
  employeeId: string
  taskStatusId?: string | null
  title: string
  description?: string | null
  location?: string | null
  startsAt: string
  durationMinutes: number
  attendeeEmails?: string | null
}

// M312 — onboarding analytics report (Phase E.3).
export interface DeptAnalytics {
  department: string
  started: number
  completed: number
  completionRatePct: number
  avgDaysToComplete: number
}

export interface TemplateAnalytics {
  templateName: string
  started: number
  completed: number
  completionRatePct: number
  avgDaysToComplete: number
}

export interface TaskTypeAnalytics {
  taskType: string
  total: number
  done: number
  completionRatePct: number
}

export interface OnboardingAnalyticsReport {
  from: string
  to: string
  totalStarted: number
  totalCompleted: number
  totalInProgress: number
  completionRatePct: number
  avgDaysToComplete: number
  byDepartment: DeptAnalytics[]
  byTemplate: TemplateAnalytics[]
  taskBottlenecks: TaskTypeAnalytics[]
}

// M311 — manager's direct-report onboarding view (scoped to the caller's team).
export interface ManagerOnboardingView {
  managerName: string
  directReportCount: number
  activeOnboardingCount: number
  overdueCount: number
  rows: OnboardingRow[]
}

export const onboardingApi = {
  overview: () => api.get<OnboardingOverview>('/onboarding/overview').then((r) => r.data),
  myTeam: () => api.get<ManagerOnboardingView>('/onboarding/my-team').then((r) => r.data),
  analytics: (from: string, to: string) =>
    api.get<OnboardingAnalyticsReport>('/onboarding/analytics', { params: { from, to } }).then((r) => r.data),
  journey: (employeeId: string) =>
    api.get<OnboardingJourney>(`/onboarding/journey/${employeeId}`).then((r) => r.data),
  acknowledgementsForEmployee: (employeeId: string) =>
    api.get<Acknowledgement[]>(`/onboarding/acknowledgements/employees/${employeeId}`).then((r) => r.data),
  acknowledge: (taskStatusId: string, statement?: string, reference?: string) =>
    api.post<Acknowledgement>('/onboarding/acknowledgements', { taskStatusId, statement, reference })
      .then((r) => r.data),
  buddiesForEmployee: (employeeId: string) =>
    api.get<Buddy[]>(`/onboarding/buddies/employees/${employeeId}`).then((r) => r.data),
  assignBuddy: (req: { employeeId: string; buddyEmployeeId: string; role: BuddyRole; taskStatusId?: string; notes?: string }) =>
    api.post<Buddy>('/onboarding/buddies', req).then((r) => r.data),
  endBuddy: (id: string, reason?: string) =>
    api.post<Buddy>(`/onboarding/buddies/${id}/end`, null, { params: reason ? { reason } : {} }).then((r) => r.data),
  meetingsForEmployee: (employeeId: string) =>
    api.get<Meeting[]>(`/onboarding/meetings/employees/${employeeId}`).then((r) => r.data),
  scheduleMeeting: (req: ScheduleMeetingRequest) =>
    api.post<Meeting>('/onboarding/meetings', req).then((r) => r.data),
  rescheduleMeeting: (id: string, req: ScheduleMeetingRequest) =>
    api.put<Meeting>(`/onboarding/meetings/${id}`, req).then((r) => r.data),
  cancelMeeting: (id: string, reason?: string) =>
    api.post<Meeting>(`/onboarding/meetings/${id}/cancel`, null, { params: reason ? { reason } : {} }).then((r) => r.data),
}
