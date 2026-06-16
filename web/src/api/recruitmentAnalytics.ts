// M88 — Recruitment funnel + source + stale-outreach analytics.

import { api } from './client'

export interface FunnelRow {
  stage: string
  applications: number
  conversionPct?: number | null
}

export interface FunnelReport {
  from: string
  to: string
  totalCreated: number
  hired: number
  rejected: number
  withdrawn: number
  rows: FunnelRow[]
}

export interface TimeToHireRow {
  fromStage: string
  toStage: string
  transitions: number
  avgDays?: number | null
  medianDays?: number | null
}

export interface TimeToHireReport {
  from: string
  to: string
  avgDaysCreatedToHired?: number | null
  medianDaysCreatedToHired?: number | null
  stageTransitions: TimeToHireRow[]
}

export interface SourceRow {
  source: string
  applications: number
  hires: number
  hireRatePct: number
  avgTimeToHireDays?: number | null
}

export interface SourceReport {
  from: string
  to: string
  rows: SourceRow[]
}

export interface StaleCandidateRow {
  candidateId: string
  candidateNo: string
  fullName: string
  email?: string | null
  poolStatus: string
  lastContactedAt?: string | null
  daysSinceContact: number
}

export interface StaleReport {
  thresholdDays: number
  total: number
  rows: StaleCandidateRow[]
}

/** M89 — bucketed counts for the home-dashboard tile. */
export interface StaleSummary {
  thresholdDays: number
  total: number
  bucket30to59: number
  bucket60to89: number
  bucket90plus: number
  neverContacted: number
}

// M297 — cost-per-hire + recruitment finance (PRD Phase F finale)
export interface AgencySpendRow {
  agencyName: string
  invoices: number
  total: number
  paid: number
}

export interface CostReport {
  from: string
  to: string
  hires: number
  agencyHires: number
  referralHires: number
  directHires: number
  agencyTotal: number
  agencyPaid: number
  agencyOutstanding: number
  referralTotal: number
  referralPaid: number
  referralAccrued: number
  totalCommitted: number
  totalPaid: number
  costPerHire: number
  byAgency: AgencySpendRow[]
}

export const recruitmentAnalyticsApi = {
  funnel: (from?: string, to?: string) =>
    api
      .get<FunnelReport>('/reports/recruitment/funnel', { params: { from, to } })
      .then((r) => r.data),
  timeToHire: (from?: string, to?: string) =>
    api
      .get<TimeToHireReport>('/reports/recruitment/time-to-hire', {
        params: { from, to },
      })
      .then((r) => r.data),
  sources: (from?: string, to?: string) =>
    api
      .get<SourceReport>('/reports/recruitment/sources', { params: { from, to } })
      .then((r) => r.data),
  stale: (thresholdDays = 30) =>
    api
      .get<StaleReport>('/reports/recruitment/stale', { params: { thresholdDays } })
      .then((r) => r.data),
  staleSummary: (thresholdDays = 30) =>
    api
      .get<StaleSummary>('/reports/recruitment/stale/summary', {
        params: { thresholdDays },
      })
      .then((r) => r.data),
  cost: (from?: string, to?: string) =>
    api
      .get<CostReport>('/reports/recruitment/cost', { params: { from, to } })
      .then((r) => r.data),
}
