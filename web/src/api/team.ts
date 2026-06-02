// M76 — Manager self-service team dashboard + profile timeline.

import { api } from './client'

export interface TeamMember {
  id: string
  employeeNo: string
  firstName: string
  lastName: string
  positionTitle?: string | null
  departmentName?: string | null
  employmentStatus?: string | null
  hireDate?: string | null
  onLeaveToday: boolean
}

export interface UpcomingItem {
  employeeId: string
  date: string
  label: string
}

export interface TeamSummary {
  managerId: string
  headcount: number
  onLeaveToday: number
  onProbation: number
  pendingLeaveRequests: number
  contractsEndingSoon: number
  upcomingProbationReviews: UpcomingItem[]
  upcomingContractEnds: UpcomingItem[]
}

export type TimelineKind =
  | 'HIRE'
  | 'STATUS_CHANGE'
  | 'CONTRACT_SIGNED'
  | 'CONTRACT_ENDED'
  | 'LEAVE_REQUEST'
  | 'BUSINESS_TRIP'
  | 'PERMISSION'
  | 'DISCIPLINARY'
  | 'PROBATION_REVIEW'
  | 'REWARD'
  | 'TERMINATION'
  | 'AUDIT_OTHER'

export interface TimelineEvent {
  at: string
  kind: TimelineKind
  title: string
  detail?: string | null
  actor?: string | null
  referenceId?: string | null
}

export const teamApi = {
  list: () => api.get<TeamMember[]>('/self/team').then((r) => r.data),
  summary: () => api.get<TeamSummary>('/self/team/summary').then((r) => r.data),
}

export const timelineApi = {
  forEmployee: (employeeId: string, limit = 200) =>
    api
      .get<TimelineEvent[]>(`/employees/${employeeId}/timeline`, { params: { limit } })
      .then((r) => r.data),
}
