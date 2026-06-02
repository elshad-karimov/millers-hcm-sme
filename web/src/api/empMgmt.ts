// M80 — Employee-Management reports + global activity feed.

import { api } from './client'

export interface EmpMgmtSummary {
  headcount: number
  onProbation: number
  onLeaveToday: number
  probationDueIn60d: number
  contractsExpiringIn60d: number
  certsExpiringIn60d: number
  unverifiedIdentifications: number
  pendingPersonalInfoChanges: number
}

export interface ProbationDueRow {
  employeeId: string
  employeeNo?: string | null
  fullName?: string | null
  reviewId: string
  scheduledDate: string
  reviewType: string
  status: string
  daysUntil: number
}

export interface ContractExpiringRow {
  employeeId: string
  employeeNo?: string | null
  fullName?: string | null
  contractId: string
  contractNo: string
  endDate: string
  contractType?: string | null
  daysUntil: number
}

export interface CertificationExpiringRow {
  employeeId: string
  employeeNo?: string | null
  fullName?: string | null
  certificationId: string
  certificationName: string
  expiryDate: string
  daysUntil: number
}

export interface RehireRow {
  employeeId: string
  employeeNo?: string | null
  fullName?: string | null
  previousEmployeeId?: string | null
  hireDate?: string | null
  rehireReason?: string | null
}

export interface ActivityRow {
  at: string
  actor: string
  module: string
  entityName: string
  entityId?: string | null
  action: string
  summary: string
}

export const empMgmtApi = {
  summary: () =>
    api.get<EmpMgmtSummary>('/reports/emp-mgmt/summary').then((r) => r.data),

  probationDue: (lookaheadDays = 60) =>
    api
      .get<{ asOf: string; rows: ProbationDueRow[] }>(
        '/reports/emp-mgmt/probation-due',
        { params: { lookaheadDays } },
      )
      .then((r) => r.data),

  contractsExpiring: (lookaheadDays = 60) =>
    api
      .get<{ asOf: string; lookaheadDays: number; rows: ContractExpiringRow[] }>(
        '/reports/emp-mgmt/contracts-expiring',
        { params: { lookaheadDays } },
      )
      .then((r) => r.data),

  certsExpiring: (lookaheadDays = 60) =>
    api
      .get<{ asOf: string; lookaheadDays: number; rows: CertificationExpiringRow[] }>(
        '/reports/emp-mgmt/certifications-expiring',
        { params: { lookaheadDays } },
      )
      .then((r) => r.data),

  rehires: (limit = 100) =>
    api
      .get<{ limit: number; rows: RehireRow[] }>('/reports/emp-mgmt/recent-rehires', {
        params: { limit },
      })
      .then((r) => r.data),

  activity: (params: {
    module?: string
    entityName?: string
    actor?: string
    limit?: number
  } = {}) =>
    api
      .get<{ limit: number; rows: ActivityRow[] }>('/reports/emp-mgmt/activity', {
        params,
      })
      .then((r) => r.data),
}
