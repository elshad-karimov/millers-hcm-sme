// M95 — Learning path templates + assignments.

import { api } from './client'

export interface LearningPath {
  id: string
  pathNo: string
  name: string
  description?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface LearningPathStep {
  id: string
  pathId: string
  courseId: string
  courseCode?: string | null
  courseTitle?: string | null
  stepOrder: number
  requiredToAdvance: boolean
}

export interface LearningPathDetail extends LearningPath {
  steps: LearningPathStep[]
}

export type PathAssignmentStatus =
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'

export interface StepProgress {
  stepId: string
  stepOrder: number
  courseId: string
  courseCode?: string | null
  courseTitle?: string | null
  requiredToAdvance: boolean
  status: string
  completed: boolean
}

export interface AssignmentResponse {
  id: string
  pathId: string
  pathName?: string | null
  employeeId: string
  employeeName?: string | null
  status: PathAssignmentStatus
  assignedAt: string
  assignedBy?: string | null
  targetCompletionDate?: string | null
  completedAt?: string | null
  cancelledAt?: string | null
  cancellationReason?: string | null
  notes?: string | null
  totalSteps: number
  completedSteps: number
  requiredSteps: number
  requiredCompleted: number
  progressPercent: number
  steps: StepProgress[]
}

export interface AssignRequest {
  employeeId: string
  targetCompletionDate?: string
  notes?: string
}

export interface CancelRequest {
  reason?: string
}

export const learningPathsApi = {
  list: (active?: boolean) =>
    api
      .get<LearningPath[]>('/learning/paths', { params: active != null ? { active } : {} })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<LearningPathDetail>(`/learning/paths/${id}`).then((r) => r.data),
}

export const pathAssignmentsApi = {
  assign: (pathId: string, req: AssignRequest) =>
    api
      .post<AssignmentResponse>(`/learning/path-assignments/paths/${pathId}/assign`, req)
      .then((r) => r.data),
  cancel: (assignmentId: string, req?: CancelRequest) =>
    api
      .delete<AssignmentResponse>(`/learning/path-assignments/${assignmentId}`, { data: req })
      .then((r) => r.data),
  get: (assignmentId: string) =>
    api.get<AssignmentResponse>(`/learning/path-assignments/${assignmentId}`).then((r) => r.data),
  forEmployee: (employeeId: string) =>
    api
      .get<AssignmentResponse[]>(`/learning/path-assignments/employees/${employeeId}`)
      .then((r) => r.data),
  forPath: (pathId: string) =>
    api.get<AssignmentResponse[]>(`/learning/path-assignments/paths/${pathId}`).then((r) => r.data),
  mine: () =>
    api.get<AssignmentResponse[]>('/learning/path-assignments/me').then((r) => r.data),
}
