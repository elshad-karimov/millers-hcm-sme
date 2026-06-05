// M119 — Custom report builder API client.
//
// Mirrors CustomReportDtos.java. Shape kept close to the wire shape so
// the builder page can pass spec objects straight through to /preview
// or /save.

import { api } from './client'

export type FieldType =
  | 'STRING'
  | 'INTEGER'
  | 'DECIMAL'
  | 'DATE'
  | 'DATETIME'
  | 'BOOLEAN'
  | 'UUID'

export type FilterOp =
  | 'EQ'
  | 'NEQ'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'LIKE'
  | 'NOT_LIKE'
  | 'IN'
  | 'NOT_IN'
  | 'IS_NULL'
  | 'IS_NOT_NULL'
  | 'BETWEEN'

export type SortDirection = 'ASC' | 'DESC'

export interface FieldCatalog {
  key: string
  label: string
  type: FieldType
  filterable: boolean
  sortable: boolean
}

export interface SourceCatalog {
  key: string
  label: string
  scopeMode: 'EMPLOYEE' | 'GLOBAL'
  fields: FieldCatalog[]
}

export interface OpCatalog {
  op: FilterOp
  valueCount: number
  compatibleTypes: FieldType[]
}

export interface CatalogResponse {
  sources: SourceCatalog[]
  ops: OpCatalog[]
}

export interface FilterSpec {
  fieldKey: string
  op: FilterOp
  values: string[]
}

export interface SortSpec {
  fieldKey: string
  direction: SortDirection
}

export interface SaveRequest {
  name?: string | null
  description?: string | null
  sourceKey: string
  fieldKeys: string[]
  filters: FilterSpec[]
  sorts: SortSpec[]
  rowLimit?: number | null
  shared?: boolean | null
}

export interface CustomReportSummary {
  id: string
  name: string
  description?: string | null
  sourceKey: string
  sourceLabel: string
  shared: boolean
  ownerUser: string
  mine: boolean
  updatedAt: string
  lastRunAt?: string | null
  lastRunRows?: number | null
}

export interface CustomReportDetail extends CustomReportSummary {
  fieldKeys: string[]
  filters: FilterSpec[]
  sorts: SortSpec[]
  rowLimit: number
}

export interface ColumnDto {
  key: string
  label: string
  type: FieldType
}

export interface RunResponse {
  sourceKey: string
  sourceLabel: string
  columns: ColumnDto[]
  rows: Array<Array<unknown>>
  rowCount: number
  rowLimit: number
  truncated: boolean
}

export const customReportsApi = {
  catalog: () =>
    api.get<CatalogResponse>('/custom-reports/catalog').then((r) => r.data),
  list: () =>
    api.get<CustomReportSummary[]>('/custom-reports').then((r) => r.data),
  get: (id: string) =>
    api.get<CustomReportDetail>(`/custom-reports/${id}`).then((r) => r.data),
  save: (body: SaveRequest) =>
    api.post<CustomReportDetail>('/custom-reports', body).then((r) => r.data),
  delete: (id: string) =>
    api.delete<void>(`/custom-reports/${id}`).then((r) => r.data),
  runSaved: (id: string) =>
    api.post<RunResponse>(`/custom-reports/${id}/run`).then((r) => r.data),
  runPreview: (body: SaveRequest) =>
    api.post<RunResponse>('/custom-reports/preview', body).then((r) => r.data),
}
