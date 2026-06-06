// M126 — Workflow SLA breach dashboard client.

import { api } from './client'

export type EscalationAction = 'NOTIFY' | 'REASSIGN' | 'AUTO_APPROVE' | 'AUTO_REJECT'

export interface SlaBreachResponse {
  id: string
  instanceId: string
  stepIndex: number
  breachedAt: string
  hoursOverdue: number
  actionTaken: EscalationAction
  notifiedTarget?: string | null
  definitionCode?: string | null
  subjectModule?: string | null
  subjectEntity?: string | null
  subjectId?: string | null
  title?: string | null
  currentStepRole?: string | null
}

export const workflowSlaApi = {
  recentBreaches: (limit = 100) =>
    api.get<SlaBreachResponse[]>('/workflow/sla/breaches', { params: { limit } }).then((r) => r.data),
  historyForInstance: (instanceId: string) =>
    api.get<SlaBreachResponse[]>(`/workflow/sla/instances/${instanceId}/breaches`).then((r) => r.data),
}
