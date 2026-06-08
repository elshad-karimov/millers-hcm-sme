import { api } from './client'

export type WorkflowStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'RETURNED'
  | 'CANCELLED'
  | 'AUTO_APPROVED'

export type WorkflowActionType =
  | 'START'
  | 'APPROVE'
  | 'REJECT'
  | 'RETURN'
  | 'COMMENT'
  | 'CANCEL'
  | 'AUTO_APPROVE'
  | 'DELEGATE'
  | 'ATTACH_DOCUMENT'
  | 'RESUBMIT'

export interface WorkflowInstance {
  id: string
  definitionId: string
  definitionCode: string
  subjectModule: string
  subjectEntity: string
  subjectId: string
  title: string
  currentStepIndex: number
  currentStepRole?: string | null
  status: WorkflowStatus
  initiatedBy: string
  initiatedAt: string
  completedAt?: string | null
  payload?: string | null
  delegatedTo?: string | null
  /** M174 — how many times this instance has been returned and re-submitted */
  resubmitCount?: number
}

export interface WorkflowAction {
  id: string
  instanceId: string
  stepIndex: number
  stepName?: string | null
  action: WorkflowActionType
  actor: string
  comment?: string | null
  ipAddress?: string | null
  /** M166 — non-null for ATTACH_DOCUMENT actions */
  documentRef?: string | null
  createdAt: string
}

export interface WorkflowDefinitionStep {
  order: number
  name: string
  approverRole: string
  slaHours?: number | null
}

export interface WorkflowDefinition {
  id: string
  code: string
  name: string
  description?: string | null
  autoApprove: boolean
  active: boolean
  steps: WorkflowDefinitionStep[]
}

export const workflowApi = {
  definitions: () =>
    api.get<WorkflowDefinition[]>('/workflow/definitions').then((r) => r.data),
  inbox: () => api.get<WorkflowInstance[]>('/workflow/inbox').then((r) => r.data),
  initiated: () => api.get<WorkflowInstance[]>('/workflow/initiated').then((r) => r.data),
  get: (id: string) =>
    api.get<WorkflowInstance>(`/workflow/instances/${id}`).then((r) => r.data),
  history: (id: string) =>
    api.get<WorkflowAction[]>(`/workflow/instances/${id}/history`).then((r) => r.data),
  bySubject: (module: string, entity: string, id: string) =>
    api
      .get<WorkflowInstance[]>('/workflow/instances', {
        params: { module, entity, id },
      })
      .then((r) => r.data),
  act: (id: string, action: WorkflowActionType, comment?: string, delegateTo?: string, documentRef?: string) =>
    api
      .post<WorkflowInstance>(`/workflow/instances/${id}/actions`, { action, comment, delegateTo, documentRef })
      .then((r) => r.data),
  /** M174 — re-submit a RETURNED instance after corrections */
  resubmit: (id: string, comment?: string) =>
    api
      .post<WorkflowInstance>(`/workflow/instances/${id}/resubmit`, null, {
        params: comment ? { comment } : {},
      })
      .then((r) => r.data),
}
