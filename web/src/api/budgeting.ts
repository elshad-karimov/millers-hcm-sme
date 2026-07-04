// HCM_20 M425+ — Budgeting API client.

import { api } from './client'

export type CycleType = 'ANNUAL' | 'QUARTERLY' | 'ROLLING'
export type BudgetCycleStatus = 'DRAFT' | 'OPEN' | 'LOCKED' | 'CLOSED'
export type DepartmentBudgetStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export interface BudgetCycle {
  id: string
  code: string
  name: string
  cycleType: CycleType
  periodStart: string
  periodEnd: string
  status: BudgetCycleStatus
  submissionDeadline?: string | null
}

export interface DepartmentBudget {
  id: string
  cycleId: string
  orgUnitId: string
  salaryBudget: number
  headcountBudget: number
  benefitsBudget: number
  trainingBudget: number
  recruitmentBudget: number
  overtimeBudget: number
  totalBudget: number
  consumedAmount: number
  status: DepartmentBudgetStatus
  workflowInstanceId?: string | null
  approvedBy?: string | null
}

export const budgetApi = {
  // Cycles
  listCycles: (status?: BudgetCycleStatus) =>
    api
      .get<BudgetCycle[]>('/budgets/cycles', { params: status ? { status } : {} })
      .then((r) => r.data),
  getCycle: (id: string) => api.get<BudgetCycle>(`/budgets/cycles/${id}`).then((r) => r.data),
  createCycle: (body: Partial<BudgetCycle>) =>
    api.post<BudgetCycle>('/budgets/cycles', body).then((r) => r.data),
  updateCycle: (id: string, body: Partial<BudgetCycle>) =>
    api.put<BudgetCycle>(`/budgets/cycles/${id}`, body).then((r) => r.data),
  updateCycleStatus: (id: string, status: BudgetCycleStatus) =>
    api.put<BudgetCycle>(`/budgets/cycles/${id}/status`, { status }).then((r) => r.data),
  deleteCycle: (id: string) => api.delete<void>(`/budgets/cycles/${id}`).then(() => undefined),

  // Department budgets
  listDepartmentBudgets: (cycleId: string) =>
    api.get<DepartmentBudget[]>('/budgets/departments', { params: { cycleId } }).then((r) => r.data),
  getDepartmentBudget: (id: string) =>
    api.get<DepartmentBudget>(`/budgets/departments/${id}`).then((r) => r.data),
  upsertDepartmentBudget: (cycleId: string, body: Partial<DepartmentBudget>) =>
    api.post<DepartmentBudget>('/budgets/departments', body, { params: { cycleId } }).then((r) => r.data),
  submitDepartmentBudget: (id: string) =>
    api.post<DepartmentBudget>(`/budgets/departments/${id}/submit`, {}).then((r) => r.data),
}
