// M103 — Named successor nominations for positions.

import { api } from './client'
export type Readiness = 'READY_NOW' | 'READY_SOON' | 'READY_LONG_TERM' | 'UNDER_DEVELOPMENT'

export interface NominationRequest {
  nomineeEmployeeId: string
  readinessTier: Readiness
  notes?: string
  riskOfLoss?: string
  impactOfLoss?: string
  riskReason?: string
  retentionAction?: string
}

export interface CancelRequest {
  reason?: string
}

export interface NominationResponse {
  id: string
  positionId: string
  positionCode?: string | null
  positionTitle?: string | null
  nomineeEmployeeId: string
  nomineeName?: string | null
  nomineeEmployeeNo?: string | null
  department?: string | null
  readinessTier: Readiness
  notes?: string | null
  nominatedBy?: string | null
  createdAt: string
  active: boolean
  riskOfLoss?: string | null
  impactOfLoss?: string | null
  riskReason?: string | null
  retentionAction?: string | null
}

export const READINESS_LABEL: Record<Readiness, string> = {
  READY_NOW: 'Ready Now',
  READY_SOON: 'Ready Soon',
  READY_LONG_TERM: 'Long-term',
  UNDER_DEVELOPMENT: 'Under Development',
}

export const READINESS_COLOR: Record<Readiness, string> = {
  READY_NOW: 'green',
  READY_SOON: 'blue',
  READY_LONG_TERM: 'gold',
  UNDER_DEVELOPMENT: 'orange',
}

export const nominationsApi = {
  nominate: (positionId: string, req: NominationRequest) =>
    api
      .post<NominationResponse>(
        `/performance/succession/nominations/positions/${positionId}`,
        req,
      )
      .then((r) => r.data),
  cancel: (nominationId: string, req?: CancelRequest) =>
    api
      .delete<NominationResponse>(
        `/performance/succession/nominations/${nominationId}`,
        { data: req },
      )
      .then((r) => r.data),
  forPosition: (positionId: string) =>
    api
      .get<NominationResponse[]>(
        `/performance/succession/nominations/positions/${positionId}`,
      )
      .then((r) => r.data),
  forEmployee: (employeeId: string) =>
    api
      .get<NominationResponse[]>(
        `/performance/succession/nominations/employees/${employeeId}`,
      )
      .then((r) => r.data),
}
