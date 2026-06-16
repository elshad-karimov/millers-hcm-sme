// M105/M106 — Onboarding / offboarding checklists.

import { api } from './client'

export type ChecklistFlowType = 'ONBOARDING' | 'OFFBOARDING'
export type ChecklistAssignmentStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type ChecklistTaskStatusValue = 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED'

export interface TemplateTaskRequest {
  stepOrder: number
  title: string
  description?: string
  defaultOwnerRole?: string
  dueOffsetDays?: number
  required?: boolean
}

/** M298 — one match rule for a template. Non-null fields are AND-ed filters. */
export interface TemplateRuleRequest {
  departmentName?: string | null
  positionId?: string | null
  orgUnitId?: string | null
  workLocationId?: string | null
  employmentType?: string | null
  employeeCategory?: string | null
  nationality?: string | null
  active?: boolean
}

export interface TemplateRuleResponse {
  id: string
  departmentName?: string | null
  positionId?: string | null
  orgUnitId?: string | null
  workLocationId?: string | null
  employmentType?: string | null
  employeeCategory?: string | null
  nationality?: string | null
  active: boolean
  specificity: number
}

export interface TemplateRequest {
  code: string
  name: string
  description?: string
  flowType: ChecklistFlowType
  active?: boolean
  priority?: number
  tasks: TemplateTaskRequest[]
  rules?: TemplateRuleRequest[]
}

export interface TemplateTaskResponse {
  id: string
  stepOrder: number
  title: string
  description?: string | null
  defaultOwnerRole?: string | null
  dueOffsetDays?: number | null
  required: boolean
}

export interface TemplateResponse {
  id: string
  code: string
  name: string
  description?: string | null
  flowType: ChecklistFlowType
  active: boolean
  priority: number
  tasks: TemplateTaskResponse[]
  rules: TemplateRuleResponse[]
}

/** M298 — one row of a match preview for an employee + flow. */
export interface TemplateMatchResult {
  templateId: string
  code: string
  name: string
  priority: number
  specificity: number
  isDefault: boolean
  selected: boolean
}

export interface StartAssignmentRequest {
  templateId: string
  employeeId: string
  anchorDate?: string
  notes?: string
}

export interface UpdateTaskRequest {
  status?: ChecklistTaskStatusValue
  assignedToUsername?: string
  dueDate?: string
  notes?: string
}

export interface TaskStatusResponse {
  id: string
  templateTaskId: string
  stepOrder: number
  title: string
  description?: string | null
  ownerRole?: string | null
  assignedToUsername?: string | null
  dueDate?: string | null
  required: boolean
  status: ChecklistTaskStatusValue
  completedAt?: string | null
  completedBy?: string | null
  notes?: string | null
}

export interface AssignmentResponse {
  id: string
  templateId: string
  templateCode?: string | null
  templateName?: string | null
  flowType: ChecklistFlowType
  employeeId: string
  employeeName?: string | null
  anchorDate: string
  status: ChecklistAssignmentStatus
  startedAt: string
  completedAt?: string | null
  startedBy?: string | null
  notes?: string | null
  totalTasks: number
  completedTasks: number
  requiredTotal: number
  requiredCompleted: number
  progressPercent: number
  tasks: TaskStatusResponse[]
}

export const TASK_STATUS_COLOR: Record<ChecklistTaskStatusValue, string> = {
  PENDING: 'default',
  IN_PROGRESS: 'gold',
  DONE: 'green',
  SKIPPED: 'default',
}

export const checklistsApi = {
  upsertTemplate: (req: TemplateRequest) =>
    api.post<TemplateResponse>('/checklists/templates', req).then((r) => r.data),
  getTemplate: (id: string) =>
    api.get<TemplateResponse>(`/checklists/templates/${id}`).then((r) => r.data),
  templatesByFlow: (flow: ChecklistFlowType) =>
    api.get<TemplateResponse[]>('/checklists/templates', { params: { flow } }).then((r) => r.data),
  matchPreview: (employeeId: string, flow: ChecklistFlowType) =>
    api
      .get<TemplateMatchResult[]>('/checklists/templates/match', { params: { employeeId, flow } })
      .then((r) => r.data),
  start: (req: StartAssignmentRequest) =>
    api.post<AssignmentResponse>('/checklists/assignments', req).then((r) => r.data),
  updateTask: (taskStatusId: string, req: UpdateTaskRequest) =>
    api.put<AssignmentResponse>(`/checklists/task-statuses/${taskStatusId}`, req).then((r) => r.data),
  cancel: (id: string, reason?: string) =>
    api.delete<AssignmentResponse>(`/checklists/assignments/${id}`, { params: reason ? { reason } : {} }).then((r) => r.data),
  getAssignment: (id: string) =>
    api.get<AssignmentResponse>(`/checklists/assignments/${id}`).then((r) => r.data),
  forEmployee: (employeeId: string) =>
    api.get<AssignmentResponse[]>(`/checklists/assignments/employees/${employeeId}`).then((r) => r.data),
  active: (flow: ChecklistFlowType) =>
    api.get<AssignmentResponse[]>('/checklists/assignments/active', { params: { flow } }).then((r) => r.data),
}
