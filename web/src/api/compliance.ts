import { api } from './client'

// ──────────────────────────────────────────────────────────────────────────────
// Statutory Report Templates (M468)
// ──────────────────────────────────────────────────────────────────────────────

export interface StatutoryReportTemplate {
  id: string
  tenantId?: string
  code: string
  name: string
  country: string
  frequency: 'MONTHLY' | 'QUARTERLY' | 'ANNUAL'
  fileFormat: 'XLSX' | 'CSV'
  dueDay: number
  description?: string
  active: boolean
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
}

// ──────────────────────────────────────────────────────────────────────────────
// Statutory Report Submissions (M469)
// ──────────────────────────────────────────────────────────────────────────────

export type SubmissionStatus = 'DRAFT' | 'GENERATED' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED'

export interface StatutoryReportSubmission {
  id: string
  tenantId?: string
  templateId: string
  periodStart: string // ISO date
  periodEnd: string // ISO date
  status: SubmissionStatus
  attachmentId?: string
  generatedAt?: string
  generatedBy?: string
  submittedAt?: string
  responseNotes?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
}

// ──────────────────────────────────────────────────────────────────────────────
// Compliance Deadlines (M470)
// ──────────────────────────────────────────────────────────────────────────────

export interface ComplianceDeadline {
  id: string
  tenantId?: string
  templateId?: string
  title: string
  frequency: 'MONTHLY' | 'QUARTERLY' | 'ANNUAL'
  dueDay: number
  month?: number
  active: boolean
  createdAt?: string
  createdBy?: string
}

export interface UpcomingDeadline {
  id: string
  title: string
  frequency: string
  nextDue: string // ISO date
  daysUntil: number
}

// ──────────────────────────────────────────────────────────────────────────────
// Work Authorization (M471)
// ──────────────────────────────────────────────────────────────────────────────

export interface ExpiringWorkAuth {
  id: string
  employeeNo: string
  firstName: string
  lastName: string
  expiryDate: string // ISO date
  daysUntilExpiry: number
}

// ──────────────────────────────────────────────────────────────────────────────
// Privacy Requests (M472)
// ──────────────────────────────────────────────────────────────────────────────

export type PrivacyRequestType = 'ACCESS' | 'EXPORT' | 'DELETE' | 'CORRECTION'
export type PrivacyRequestStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED'

export interface PrivacyRequest {
  id: string
  tenantId?: string
  employeeId?: string
  requestType: PrivacyRequestType
  description?: string
  status: PrivacyRequestStatus
  dueDate: string // ISO date
  resolutionNotes?: string
  createdAt?: string
  createdBy?: string
  updatedAt?: string
  updatedBy?: string
}

// ──────────────────────────────────────────────────────────────────────────────
// API Client
// ──────────────────────────────────────────────────────────────────────────────

export const complianceApi = {
  // Statutory Report Templates
  listTemplates: () =>
    api.get<StatutoryReportTemplate[]>('/compliance/statutory-report-templates').then((r) => r.data),
  getTemplate: (id: string) =>
    api.get<StatutoryReportTemplate>(`/compliance/statutory-report-templates/${id}`).then((r) => r.data),
  createTemplate: (template: StatutoryReportTemplate) =>
    api.post<StatutoryReportTemplate>('/compliance/statutory-report-templates', template).then((r) => r.data),
  updateTemplate: (id: string, template: StatutoryReportTemplate) =>
    api.put<StatutoryReportTemplate>(`/compliance/statutory-report-templates/${id}`, template).then((r) => r.data),
  deleteTemplate: (id: string) =>
    api.delete(`/compliance/statutory-report-templates/${id}`),

  // Statutory Report Submissions
  listSubmissions: () =>
    api.get<StatutoryReportSubmission[]>('/compliance/statutory-submissions').then((r) => r.data),
  getSubmission: (id: string) =>
    api.get<StatutoryReportSubmission>(`/compliance/statutory-submissions/${id}`).then((r) => r.data),
  createSubmission: (templateId: string, periodStart: string, periodEnd: string) =>
    api.post<StatutoryReportSubmission>('/compliance/statutory-submissions', null, {
      params: { templateId, periodStart, periodEnd },
    }).then((r) => r.data),
  generateSubmission: (id: string) =>
    api.post<StatutoryReportSubmission>(`/compliance/statutory-submissions/${id}/generate`).then((r) => r.data),
  updateSubmissionStatus: (id: string, status: SubmissionStatus, responseNotes?: string) =>
    api.put<StatutoryReportSubmission>(`/compliance/statutory-submissions/${id}/status`, {
      status,
      responseNotes,
    }).then((r) => r.data),
  downloadSubmission: async (attachmentId: string, filename: string) => {
    const response = await api.get(`/attachments/${attachmentId}/download`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data as BlobPart])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  },

  // Compliance Deadlines
  listDeadlines: () =>
    api.get<ComplianceDeadline[]>('/compliance/deadlines').then((r) => r.data),
  getDeadline: (id: string) =>
    api.get<ComplianceDeadline>(`/compliance/deadlines/${id}`).then((r) => r.data),
  createDeadline: (deadline: ComplianceDeadline) =>
    api.post<ComplianceDeadline>('/compliance/deadlines', deadline).then((r) => r.data),
  updateDeadline: (id: string, deadline: ComplianceDeadline) =>
    api.put<ComplianceDeadline>(`/compliance/deadlines/${id}`, deadline).then((r) => r.data),
  deleteDeadline: (id: string) =>
    api.delete(`/compliance/deadlines/${id}`),
  upcomingDeadlines: (days: number = 30) =>
    api.get<UpcomingDeadline[]>('/compliance/deadlines/upcoming', {
      params: { days },
    }).then((r) => r.data),

  // Work Authorization
  expiringWorkAuth: (days: number = 90) =>
    api.get<ExpiringWorkAuth[]>('/compliance/work-authorization/expiring', {
      params: { days },
    }).then((r) => r.data),

  // Privacy Requests
  listPrivacyRequests: () =>
    api.get<PrivacyRequest[]>('/compliance/privacy-requests').then((r) => r.data),
  getPrivacyRequest: (id: string) =>
    api.get<PrivacyRequest>(`/compliance/privacy-requests/${id}`).then((r) => r.data),
  createPrivacyRequest: (request: PrivacyRequest) =>
    api.post<PrivacyRequest>('/compliance/privacy-requests', request).then((r) => r.data),
  updatePrivacyRequest: (id: string, request: PrivacyRequest) =>
    api.put<PrivacyRequest>(`/compliance/privacy-requests/${id}`, request).then((r) => r.data),
  updatePrivacyRequestStatus: (id: string, status: PrivacyRequestStatus, resolutionNotes?: string) =>
    api.put<PrivacyRequest>(`/compliance/privacy-requests/${id}/status`, {
      status,
      resolutionNotes,
    }).then((r) => r.data),
  deletePrivacyRequest: (id: string) =>
    api.delete(`/compliance/privacy-requests/${id}`),
}
