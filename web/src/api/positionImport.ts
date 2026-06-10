import { api } from './client'

/**
 * M255 — Position bulk-import API client.
 *
 * The backend returns the same RowResult shape from /preview and
 * /commit so the SPA renders one table view for both phases.
 */

export interface ImportRowResult {
  rowNumber: number
  referenceCode: string | null
  title: string | null
  orgUnitLabel: string | null
  grade: string | null
  jobFamily: string | null
  jobLevel: string | null
  approvedHeadcount: number
  salaryMin: number | null
  salaryMax: number | null
  currency: string | null
  employmentType: string | null
  costCentre: string | null
  budgetCode: string | null
  location: string | null
  errors: string[]
}

export interface ImportResult {
  rows: ImportRowResult[]
  totalRows: number
  validRows: number
  errorRows: number
  totalErrors: number
}

export const positionImportApi = {
  /** GET the .xlsx template — returns a Blob the SPA saves to disk. */
  async downloadTemplate(): Promise<Blob> {
    const r = await api.get('/positions/import/template', {
      responseType: 'blob',
    })
    return r.data as Blob
  },

  async preview(file: File): Promise<ImportResult> {
    const fd = new FormData()
    fd.append('file', file)
    const r = await api.post<ImportResult>('/positions/import/preview', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return r.data
  },

  async commit(file: File): Promise<ImportResult> {
    const fd = new FormData()
    fd.append('file', file)
    const r = await api.post<ImportResult>('/positions/import/commit', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return r.data
  },
}
