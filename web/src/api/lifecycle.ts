import { api } from './client'
import type { PageResponse } from './employees'

export type TerminationReason =
  | 'VOLUNTARY_RESIGNATION'
  | 'INVOLUNTARY_DISMISSAL'
  | 'MUTUAL_AGREEMENT'
  | 'END_OF_CONTRACT'
  | 'RETIREMENT'
  | 'REDUNDANCY'
  | 'PROBATION_FAIL'
  | 'DEATH'
  | 'OTHER'

export type TerminationStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'PROCESSED'

export type ChangeType =
  | 'SALARY'
  | 'POSITION'
  | 'DEPARTMENT'
  | 'MANAGER'
  | 'GRADE'
  | 'LOCATION'
  | 'JOB_TITLE'
  | 'EMPLOYMENT_TYPE'
  | 'COST_CENTRE'

export type ContractChangeStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'APPLIED'

export interface Termination {
  id: string
  terminationNo: string
  employeeId: string
  reasonCode: TerminationReason
  reasonDetail?: string | null
  noticeDate: string
  lastWorkingDate: string
  effectiveDate: string
  status: TerminationStatus
  workflowInstanceId?: string | null
  unusedLeaveDays?: number | null
  unusedLeavePayout?: number | null
  severanceAmount?: number | null
  finalSettlementAmount?: number | null
  currency?: string | null
  payoutCalcDetails?: Record<string, unknown> | null
  clearanceIt: boolean
  clearanceHr: boolean
  clearanceFinance: boolean
  clearanceAssets: boolean
  attachmentUrls?: string | null
  note?: string | null
  createdAt: string
  createdBy?: string | null
  processedAt?: string | null
  processedBy?: string | null
}

export interface TerminationSubmitRequest {
  employeeId: string
  reasonCode: TerminationReason
  reasonDetail?: string
  noticeDate: string
  lastWorkingDate: string
  effectiveDate: string
  attachmentUrls?: string
  note?: string
}

export interface ClearanceUpdateRequest {
  clearanceIt?: boolean
  clearanceHr?: boolean
  clearanceFinance?: boolean
  clearanceAssets?: boolean
}

export interface ExitInterview {
  id: string
  terminationId: string
  conductedAt?: string | null
  conductedBy?: string | null
  overallRating?: number | null
  wouldRecommend?: boolean | null
  reasonForLeaving?: string | null
  feedback?: string | null
  improvementSuggestions?: string | null
}

export interface ExitInterviewRequest {
  overallRating?: number
  wouldRecommend?: boolean
  reasonForLeaving?: string
  feedback?: string
  improvementSuggestions?: string
}

export interface ContractChange {
  id: string
  changeNo: string
  employeeId: string
  changeType: ChangeType
  effectiveDate: string
  reason?: string | null
  oldValue?: Record<string, unknown> | null
  newValue: Record<string, unknown>
  status: ContractChangeStatus
  workflowInstanceId?: string | null
  appliedAt?: string | null
  appliedBy?: string | null
  attachmentUrls?: string | null
  note?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface ContractChangeSubmitRequest {
  employeeId: string
  changeType: ChangeType
  effectiveDate: string
  reason?: string
  newValue: Record<string, unknown>
  attachmentUrls?: string
  note?: string
}

export const lifecycleApi = {
  // Terminations
  terminations: (params: {
    employeeId?: string
    status?: TerminationStatus
    page?: number
    size?: number
  }) =>
    api
      .get<PageResponse<Termination>>('/lifecycle/terminations', { params })
      .then((r) => r.data),
  termination: (id: string) =>
    api.get<Termination>(`/lifecycle/terminations/${id}`).then((r) => r.data),
  submitTermination: (payload: TerminationSubmitRequest) =>
    api
      .post<Termination>('/lifecycle/terminations/submit', payload)
      .then((r) => r.data),
  updateClearance: (id: string, payload: ClearanceUpdateRequest) =>
    api
      .post<Termination>(`/lifecycle/terminations/${id}/clearance`, payload)
      .then((r) => r.data),
  recordExitInterview: (id: string, payload: ExitInterviewRequest) =>
    api
      .post<ExitInterview>(`/lifecycle/terminations/${id}/exit-interview`, payload)
      .then((r) => r.data),
  processTermination: (id: string) =>
    api
      .post<Termination>(`/lifecycle/terminations/${id}/process`)
      .then((r) => r.data),

  // Contract changes
  contractChanges: (params: {
    employeeId?: string
    type?: ChangeType
    status?: ContractChangeStatus
    page?: number
    size?: number
  }) =>
    api
      .get<PageResponse<ContractChange>>('/lifecycle/contract-changes', { params })
      .then((r) => r.data),
  contractChange: (id: string) =>
    api
      .get<ContractChange>(`/lifecycle/contract-changes/${id}`)
      .then((r) => r.data),
  submitContractChange: (payload: ContractChangeSubmitRequest) =>
    api
      .post<ContractChange>('/lifecycle/contract-changes/submit', payload)
      .then((r) => r.data),
}
