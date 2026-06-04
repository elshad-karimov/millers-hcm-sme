// M118 — Compensation planning workbench API client.

import { api } from './client'

export type CompCycleStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'CANCELLED'
export type CompProposalStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export interface CycleRequest {
  code: string
  name: string
  description?: string
  opensOn: string
  closesOn: string
  poolTotal?: number
  currency?: string
}

export interface CycleResponse {
  id: string
  code: string
  name: string
  description?: string | null
  status: CompCycleStatus
  opensOn: string
  closesOn: string
  poolTotal: number
  poolCommitted: number
  poolRemaining: number
  currency: string
  proposalCount: number
  createdAt: string
}

export interface ProposalRequest {
  cycleId: string
  employeeId: string
  proposedSalary: number
  rationale?: string
  effectiveOn?: string
}

export interface DecisionRequest {
  decision: 'APPROVED' | 'REJECTED'
  note?: string
  effectiveOn?: string
}

export interface ProposalResponse {
  id: string
  cycleId: string
  employeeId: string
  employeeName?: string | null
  employeeNo?: string | null
  currentSalary: number
  proposedSalary: number
  deltaAmount: number
  increasePercent?: number | null
  currency: string
  rationale?: string | null
  status: CompProposalStatus
  proposedBy?: string | null
  proposedAt: string
  decidedBy?: string | null
  decidedAt?: string | null
  decisionNote?: string | null
  effectiveOn?: string | null
}

export interface TeamRow {
  employeeId: string
  employeeNo?: string | null
  employeeName: string
  orgUnitLabel?: string | null
  currentSalary: number
  currency: string
  proposal?: ProposalResponse | null
}

export interface TeamGrid {
  cycleId: string
  cycleName: string
  cycleStatus: CompCycleStatus
  poolTotal: number
  poolCommitted: number
  poolRemaining: number
  currency: string
  rows: TeamRow[]
}

export const compPlanningApi = {
  listCycles: (status?: CompCycleStatus) =>
    api
      .get<CycleResponse[]>('/compbenefits/comp-planning/cycles', { params: status ? { status } : {} })
      .then((r) => r.data),
  getCycle: (id: string) =>
    api.get<CycleResponse>(`/compbenefits/comp-planning/cycles/${id}`).then((r) => r.data),
  createCycle: (req: CycleRequest) =>
    api.post<CycleResponse>('/compbenefits/comp-planning/cycles', req).then((r) => r.data),
  updateCycle: (id: string, req: CycleRequest) =>
    api.put<CycleResponse>(`/compbenefits/comp-planning/cycles/${id}`, req).then((r) => r.data),
  changeStatus: (id: string, status: CompCycleStatus) =>
    api
      .post<CycleResponse>(`/compbenefits/comp-planning/cycles/${id}/status`, { status })
      .then((r) => r.data),

  teamGrid: (cycleId: string, employeeIds: string[]) =>
    api
      .get<TeamGrid>(`/compbenefits/comp-planning/cycles/${cycleId}/team-grid`,
        { params: { employeeIds: employeeIds.join(',') } })
      .then((r) => r.data),
  proposals: (cycleId: string) =>
    api
      .get<ProposalResponse[]>(`/compbenefits/comp-planning/cycles/${cycleId}/proposals`)
      .then((r) => r.data),

  upsertProposal: (req: ProposalRequest) =>
    api.post<ProposalResponse>('/compbenefits/comp-planning/proposals', req).then((r) => r.data),
  submitProposal: (id: string) =>
    api.post<ProposalResponse>(`/compbenefits/comp-planning/proposals/${id}/submit`, {}).then((r) => r.data),
  decideProposal: (id: string, req: DecisionRequest) =>
    api.post<ProposalResponse>(`/compbenefits/comp-planning/proposals/${id}/decide`, req).then((r) => r.data),
}
