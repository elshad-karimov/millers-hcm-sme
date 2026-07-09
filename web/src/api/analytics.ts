// M473-M476 — Analytics & KPI API client.

import { api } from './client'

export type KpiCategory =
  | 'HEADCOUNT'
  | 'TURNOVER'
  | 'COST'
  | 'COMPLIANCE'
  | 'ENGAGEMENT'
  | 'LEARNING'

export interface KpiDefinition {
  id?: string
  code: string
  name: string
  category: KpiCategory
  description?: string
  unit?: string
  targetValue?: number
  active?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface DashboardLayout {
  id?: string
  ownerUsername?: string
  name: string
  shared: boolean
  widgets: string // JSON array of widget definitions
  createdAt?: string
  updatedAt?: string
}

export interface HeadcountTrendSummary {
  current: number
  previousMonth: number
  trend: string
}

export interface PayrollCostTrendSummary {
  currentMonth: number
  previousMonth: number
  trend: string
}

export interface ComplianceDeadlineItem {
  title: string
  dueDate: string
  status: string
}

export interface ExecutiveSummary {
  headcountTrend: HeadcountTrendSummary
  turnover12m: number
  payrollCostTrend: PayrollCostTrendSummary
  enps: number
  upcomingComplianceDeadlines: ComplianceDeadlineItem[]
  attritionHighRiskCount: number
}

export interface AttritionRisk {
  id: string
  employeeId: string
  score: number
  factors?: string
  computedAt: string
}

export const kpiDefinitionsApi = {
  listAll: (activeOnly = true) =>
    api.get<KpiDefinition[]>('/analytics/kpi-definitions', { params: { activeOnly } }).then((r) => r.data),
  get: (id: string) =>
    api.get<KpiDefinition>(`/analytics/kpi-definitions/${id}`).then((r) => r.data),
  create: (kpi: KpiDefinition) =>
    api.post<KpiDefinition>('/analytics/kpi-definitions', kpi).then((r) => r.data),
  update: (id: string, kpi: KpiDefinition) =>
    api.put<KpiDefinition>(`/analytics/kpi-definitions/${id}`, kpi).then((r) => r.data),
  delete: (id: string) =>
    api.delete(`/analytics/kpi-definitions/${id}`),
}

export const analyticsApi = {
  getKpiValues: (codes: string[]) =>
    api.get<Record<string, any>>('/analytics/kpi-values', { params: { codes: codes.join(',') } }).then((r) => r.data),
  listDashboards: (all = false) =>
    api.get<DashboardLayout[]>('/analytics/dashboards', { params: { all } }).then((r) => r.data),
  getDashboard: (id: string) =>
    api.get<DashboardLayout>(`/analytics/dashboards/${id}`).then((r) => r.data),
  createDashboard: (layout: DashboardLayout) =>
    api.post<DashboardLayout>('/analytics/dashboards', layout).then((r) => r.data),
  updateDashboard: (id: string, layout: DashboardLayout) =>
    api.put<DashboardLayout>(`/analytics/dashboards/${id}`, layout).then((r) => r.data),
  deleteDashboard: (id: string) =>
    api.delete(`/analytics/dashboards/${id}`),
  executiveSummary: () =>
    api.get<ExecutiveSummary>('/analytics/executive').then((r) => r.data),
}

export const attritionRiskApi = {
  listAll: () =>
    api.get<AttritionRisk[]>('/analytics/attrition-risk').then((r) => r.data),
  recompute: () =>
    api.post('/analytics/attrition-risk/recompute'),
}
