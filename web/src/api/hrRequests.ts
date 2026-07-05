import { api } from './client'

export type ServiceRequestCategory =
  | 'SALARY_CERT'
  | 'EMPLOYMENT_LETTER'
  | 'PAYROLL_INQUIRY'
  | 'POLICY_QUESTION'
  | 'GRIEVANCE'
  | 'OTHER'

export type ServiceRequestPriority = 'LOW' | 'NORMAL' | 'HIGH'

export type ServiceRequestStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export interface HrServiceRequest {
  id: string
  requestNo: string
  employeeId: string
  category: ServiceRequestCategory
  priority: ServiceRequestPriority
  subject: string
  description?: string
  status: ServiceRequestStatus
  assignedToUsername?: string
  slaDue?: string
  resolvedAt?: string
  resolutionNotes?: string
  createdAt: string
  createdBy: string
}

export interface SubmitServiceRequest {
  category: ServiceRequestCategory
  priority?: ServiceRequestPriority
  subject: string
  description?: string
}

export interface HrServiceRequestComment {
  id: string
  requestId: string
  authorUsername: string
  isInternal: boolean
  body: string
  createdAt: string
}

export const hrRequestsApi = {
  submit: (dto: SubmitServiceRequest) =>
    api.post<HrServiceRequest>('/self/hr-requests', dto),
  myRequests: () =>
    api.get<HrServiceRequest[]>('/self/hr-requests'),
  getComments: (requestId: string) =>
    api.get<HrServiceRequestComment[]>(`/self/hr-requests/${requestId}/comments`),
  addComment: (requestId: string, body: string) =>
    api.post<HrServiceRequestComment>(`/self/hr-requests/${requestId}/comments`, { body }),
}

export const hrServiceQueueApi = {
  queue: (params?: { category?: string; status?: string; overdue?: boolean }) =>
    api.get<HrServiceRequest[]>('/hr/service-requests', { params }),
  assign: (id: string, assignToUsername: string) =>
    api.post<HrServiceRequest>(`/hr/service-requests/${id}/assign`, { assignToUsername }),
  start: (id: string) =>
    api.post<HrServiceRequest>(`/hr/service-requests/${id}/start`),
  resolve: (id: string, resolutionNotes: string) =>
    api.post<HrServiceRequest>(`/hr/service-requests/${id}/resolve`, { resolutionNotes }),
  close: (id: string) =>
    api.post<HrServiceRequest>(`/hr/service-requests/${id}/close`),
  reopen: (id: string) =>
    api.post<HrServiceRequest>(`/hr/service-requests/${id}/reopen`),
  getComments: (requestId: string) =>
    api.get<HrServiceRequestComment[]>(`/hr/service-requests/${requestId}/comments`),
  addComment: (requestId: string, body: string, isInternal: boolean) =>
    api.post<HrServiceRequestComment>(`/hr/service-requests/${requestId}/comments`, { body, isInternal }),
}

export const REQUEST_CATEGORY_COLOR: Record<ServiceRequestCategory, string> = {
  SALARY_CERT: 'blue',
  EMPLOYMENT_LETTER: 'cyan',
  PAYROLL_INQUIRY: 'orange',
  POLICY_QUESTION: 'purple',
  GRIEVANCE: 'red',
  OTHER: 'default',
}

export const REQUEST_STATUS_COLOR: Record<ServiceRequestStatus, string> = {
  OPEN: 'gold',
  IN_PROGRESS: 'blue',
  RESOLVED: 'green',
  CLOSED: 'default',
}

export const REQUEST_PRIORITY_COLOR: Record<ServiceRequestPriority, string> = {
  HIGH: 'red',
  NORMAL: 'orange',
  LOW: 'default',
}
