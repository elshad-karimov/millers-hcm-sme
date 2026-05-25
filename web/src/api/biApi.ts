import { api } from './client'

const BASE = '/bi'

// ─── Types ───────────────────────────────────────────────────────────────────

export interface BiExportLogEntry {
  id: string
  entity: string
  format: 'ODATA' | 'CSV'
  requestedBy: string
  rowCount: number
  exportedAt: string
}

export interface BiEntity {
  key: string
  label: string
  description: string
}

// ─── Supported entities ───────────────────────────────────────────────────────

export const BI_ENTITIES: BiEntity[] = [
  {
    key: 'employees',
    label: 'Employees',
    description: 'Employee directory with status, department, and contract details',
  },
  {
    key: 'payroll_runs',
    label: 'Payroll Runs',
    description: 'Payroll run history with gross, tax, and net totals per period',
  },
  {
    key: 'leave_balances',
    label: 'Leave Balances',
    description: 'Employee leave entitlements, used days, and remaining balance by year',
  },
  {
    key: 'attendance_summary',
    label: 'Attendance Summary',
    description: 'Monthly attendance aggregates: expected hours, actual hours, overtime, late days',
  },
  {
    key: 'headcount_trend',
    label: 'Headcount Trend',
    description: 'Monthly headcount by department — active employees, new hires, and terminations',
  },
]

// ─── API calls ────────────────────────────────────────────────────────────────

/**
 * Returns the OData feed URL for the given entity (for Copy to clipboard).
 * The URL is absolute so Power BI can use it directly.
 */
export function getODataUrl(entity: string): string {
  return `${window.location.origin}/api/bi/odata/${entity}`
}

/**
 * Returns the CSV download URL for the given entity (for direct browser download).
 */
export function getCsvUrl(entity: string): string {
  return `/api/bi/export/${entity}.csv`
}

/**
 * Triggers a CSV download by creating a temporary anchor element.
 * Includes the current bearer token so the request is authenticated.
 */
export async function downloadCsv(entity: string): Promise<void> {
  const token = localStorage.getItem('hcm.token')
  const response = await fetch(`/api/bi/export/${entity}.csv`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    throw new Error(`CSV download failed: ${response.status}`)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${entity}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

/**
 * Returns the 50 most recent BI export log entries.
 */
export function listExportLog(): Promise<BiExportLogEntry[]> {
  return api.get<BiExportLogEntry[]>(`${BASE}/export-log`).then((r) => r.data)
}
