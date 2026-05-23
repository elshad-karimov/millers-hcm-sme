import { api } from './client'
import type { PageResponse } from './employees'

export type ReportType =
  | 'HEADCOUNT'
  | 'ATTRITION'
  | 'PAYROLL'
  | 'LEAVE'
  | 'ATTENDANCE'
  | 'TRAINING'
  | 'PERFORMANCE'
  | 'RECRUITMENT'

export type ReportFormat = 'PDF' | 'XLSX'
export type ReportRunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'
export type TriggerSource = 'MANUAL' | 'SCHEDULED'
export type EmailStatus = 'NOT_REQUESTED' | 'SKIPPED' | 'SENT' | 'FAILED'

export interface ReportDefinition {
  id: string
  definitionNo: string
  name: string
  reportType: ReportType
  defaultFormat: ReportFormat
  parameters: Record<string, unknown>
  description?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface ReportDefinitionRequest {
  name: string
  reportType: ReportType
  defaultFormat?: ReportFormat
  parameters?: Record<string, unknown>
  description?: string
  active?: boolean
}

export type WebhookType = 'NONE' | 'SLACK' | 'TEAMS'
export type WebhookStatus = 'NOT_REQUESTED' | 'SKIPPED' | 'SENT' | 'FAILED'

export interface ReportSchedule {
  id: string
  scheduleNo: string
  name: string
  definitionId: string
  cron: string
  recipients?: string | null
  webhookType: WebhookType
  webhookUrl?: string | null
  active: boolean
  lastRunAt?: string | null
  nextRunAt?: string | null
  lastStatus?: string | null
  createdAt: string
  createdBy?: string | null
}

export interface ReportScheduleRequest {
  name: string
  definitionId: string
  cron: string
  recipients?: string
  webhookType?: WebhookType
  webhookUrl?: string
  active?: boolean
}

export interface ReportScheduleUpdateRequest {
  name?: string
  cron?: string
  recipients?: string
  webhookType?: WebhookType
  webhookUrl?: string
  active?: boolean
}

export interface ReportRun {
  id: string
  runNo: string
  definitionId?: string | null
  scheduleId?: string | null
  reportType: ReportType
  format: ReportFormat
  parameters: Record<string, unknown>
  attachmentId?: string | null
  fileName?: string | null
  sizeBytes?: number | null
  status: ReportRunStatus
  errorMessage?: string | null
  startedAt: string
  finishedAt?: string | null
  triggeredBy?: string | null
  triggerSource: TriggerSource
  emailStatus: EmailStatus
  emailRecipients?: string | null
  emailSentAt?: string | null
  emailError?: string | null
  webhookStatus: WebhookStatus
  webhookTarget?: string | null
  webhookSentAt?: string | null
  webhookError?: string | null
}

interface ExportParams {
  year?: number
  from?: string
  to?: string
  cycleId?: string
}

export const reportSchedulingApi = {
  // Sync export — trigger save-as via blob URL
  exportDownload: async (type: ReportType, format: ReportFormat, params?: ExportParams) => {
    const res = await api.get(`/reports/export/${type}`, {
      params: { format, ...(params ?? {}) },
      responseType: 'blob',
    })
    const url = URL.createObjectURL(res.data as Blob)
    const link = document.createElement('a')
    link.href = url
    const ext = format.toLowerCase()
    const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
    link.download = `${type.toLowerCase()}-${stamp}.${ext}`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  },

  // Definitions
  definitions: (params?: { type?: ReportType; activeOnly?: boolean }) =>
    api.get<ReportDefinition[]>('/reports/definitions', { params }).then((r) => r.data),
  definition: (id: string) =>
    api.get<ReportDefinition>(`/reports/definitions/${id}`).then((r) => r.data),
  createDefinition: (payload: ReportDefinitionRequest) =>
    api.post<ReportDefinition>('/reports/definitions', payload).then((r) => r.data),
  updateDefinition: (id: string, payload: ReportDefinitionRequest) =>
    api.put<ReportDefinition>(`/reports/definitions/${id}`, payload).then((r) => r.data),
  runDefinition: (id: string, format?: ReportFormat) =>
    api
      .post<ReportRun>(`/reports/definitions/${id}/run`, null, { params: { format } })
      .then((r) => r.data),

  // Schedules
  schedules: () => api.get<ReportSchedule[]>('/reports/schedules').then((r) => r.data),
  schedule: (id: string) => api.get<ReportSchedule>(`/reports/schedules/${id}`).then((r) => r.data),
  createSchedule: (payload: ReportScheduleRequest) =>
    api.post<ReportSchedule>('/reports/schedules', payload).then((r) => r.data),
  updateSchedule: (id: string, payload: ReportScheduleUpdateRequest) =>
    api.put<ReportSchedule>(`/reports/schedules/${id}`, payload).then((r) => r.data),
  runScheduleNow: (id: string) =>
    api.post<ReportSchedule>(`/reports/schedules/${id}/run-now`).then((r) => r.data),

  // Runs
  runs: (params: { page?: number; size?: number }) =>
    api.get<PageResponse<ReportRun>>('/reports/runs', { params }).then((r) => r.data),
  resendRunEmail: (id: string) =>
    api.post<ReportRun>(`/reports/runs/${id}/resend-email`).then((r) => r.data),
  resendRunWebhook: (id: string) =>
    api.post<ReportRun>(`/reports/runs/${id}/resend-webhook`).then((r) => r.data),
}
