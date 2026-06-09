// M247 — Workforce plan + scenarios SPA client.

import { api } from './client'

export type ScenarioType =
  | 'BASELINE'
  | 'EXPANSION'
  | 'REDUCTION'
  | 'RESTRUCTURE'
  | 'SEASONAL'
  | 'WHAT_IF'

export type WorkforcePlanStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'ACTIVE'
  | 'REJECTED'
  | 'ARCHIVED'

export interface WorkforcePlan {
  id: string
  legalEntityId: string
  versionCode: string
  title?: string | null
  scenarioType: ScenarioType
  effectiveFrom: string
  effectiveTo?: string | null
  status: WorkforcePlanStatus
  parentPlanId?: string | null
  notes?: string | null
  submittedBy?: string | null
  submittedAt?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  rejectedBy?: string | null
  rejectedAt?: string | null
  rejectReason?: string | null
  archivedAt?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
  totalLines: number
  totalHeadcount: number
  totalMonthlyCost?: number | null
}

export interface WorkforcePlanLine {
  id: string
  workforcePlanId: string
  lineNo: number
  orgUnitId?: string | null
  orgUnitLabel?: string | null
  jobFamily?: string | null
  grade?: string | null
  positionTitle?: string | null
  plannedHeadcount: number
  plannedFte: number
  plannedMonthlyCost: number
  plannedAnnualCost: number
  currency: string
  changeType?: string | null
  targetStartDate?: string | null
  justification?: string | null
  notes?: string | null
}

export interface WorkforcePlanHeaderRequest {
  legalEntityId: string
  versionCode: string
  title?: string
  scenarioType?: ScenarioType
  effectiveFrom: string
  effectiveTo?: string
  notes?: string
}

export interface WorkforcePlanLineRequest {
  lineNo?: number
  orgUnitId?: string
  orgUnitLabel?: string
  jobFamily?: string
  grade?: string
  positionTitle?: string
  plannedHeadcount: number
  plannedFte?: number
  plannedMonthlyCost?: number
  currency?: string
  changeType?: string
  targetStartDate?: string
  justification?: string
  notes?: string
}

export interface CloneRequest {
  newVersionCode: string
  newTitle?: string
  scenarioType?: ScenarioType
}

export interface DiffRow {
  key: string
  positionTitle?: string | null
  grade?: string | null
  orgUnitLabel?: string | null
  hcA: number
  hcB: number
  hcDelta: number
  costA: number
  costB: number
  costDelta: number
}

export interface DiffResult {
  planAId: string
  planBId: string
  totalHcA: number
  totalHcB: number
  totalCostA: number
  totalCostB: number
  rows: DiffRow[]
}

export interface VarianceResult {
  planId: string
  plannedHeadcount: number
  actualHeadcount: number
  headcountGap: number
  plannedMonthlyCost: number
  actualMonthlyCost: number
  monthlyCostGap: number
}

export const WORKFORCE_PLAN_STATUS_COLOR: Record<WorkforcePlanStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  ACTIVE: 'green',
  REJECTED: 'red',
  ARCHIVED: 'default',
}

export const WORKFORCE_PLAN_STATUS_LABEL: Record<WorkforcePlanStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  ACTIVE: 'Active',
  REJECTED: 'Rejected',
  ARCHIVED: 'Archived',
}

export const SCENARIO_TYPE_COLOR: Record<ScenarioType, string> = {
  BASELINE: 'blue',
  EXPANSION: 'green',
  REDUCTION: 'orange',
  RESTRUCTURE: 'purple',
  SEASONAL: 'cyan',
  WHAT_IF: 'magenta',
}

export const SCENARIO_TYPE_LABEL: Record<ScenarioType, string> = {
  BASELINE: 'Baseline',
  EXPANSION: 'Expansion',
  REDUCTION: 'Reduction',
  RESTRUCTURE: 'Restructure',
  SEASONAL: 'Seasonal',
  WHAT_IF: 'What-if',
}

export const workforcePlanApi = {
  list: (legalEntityId?: string) =>
    api
      .get<WorkforcePlan[]>('/workforce-plans', {
        params: legalEntityId ? { legalEntityId } : {},
      })
      .then((r) => r.data),

  get: (id: string) =>
    api.get<WorkforcePlan>(`/workforce-plans/${id}`).then((r) => r.data),

  create: (body: WorkforcePlanHeaderRequest) =>
    api.post<WorkforcePlan>('/workforce-plans', body).then((r) => r.data),

  update: (id: string, body: WorkforcePlanHeaderRequest) =>
    api.put<WorkforcePlan>(`/workforce-plans/${id}`, body).then((r) => r.data),

  remove: (id: string) =>
    api.delete<void>(`/workforce-plans/${id}`).then(() => undefined),

  clone: (id: string, body: CloneRequest) =>
    api.post<WorkforcePlan>(`/workforce-plans/${id}/clone`, body).then((r) => r.data),

  lines: (id: string) =>
    api.get<WorkforcePlanLine[]>(`/workforce-plans/${id}/lines`).then((r) => r.data),
  addLine: (id: string, body: WorkforcePlanLineRequest) =>
    api.post<WorkforcePlanLine>(`/workforce-plans/${id}/lines`, body).then((r) => r.data),
  updateLine: (lineId: string, body: WorkforcePlanLineRequest) =>
    api.put<WorkforcePlanLine>(`/workforce-plans/lines/${lineId}`, body).then((r) => r.data),
  removeLine: (lineId: string) =>
    api.delete<void>(`/workforce-plans/lines/${lineId}`).then(() => undefined),

  submit: (id: string) =>
    api.post<WorkforcePlan>(`/workforce-plans/${id}/submit`, {}).then((r) => r.data),
  approve: (id: string) =>
    api.post<WorkforcePlan>(`/workforce-plans/${id}/approve`, {}).then((r) => r.data),
  reject: (id: string, reason: string) =>
    api.post<WorkforcePlan>(`/workforce-plans/${id}/reject`, { reason }).then((r) => r.data),
  archive: (id: string) =>
    api.post<WorkforcePlan>(`/workforce-plans/${id}/archive`, {}).then((r) => r.data),

  compare: (idA: string, idB: string) =>
    api.get<DiffResult>(`/workforce-plans/${idA}/compare/${idB}`).then((r) => r.data),
  variance: (id: string) =>
    api
      .get<VarianceResult>(`/workforce-plans/${id}/variance-vs-actual`)
      .then((r) => r.data),
}
