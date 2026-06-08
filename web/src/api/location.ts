import { api } from './client'

export type LocationType =
  | 'HEAD_OFFICE'
  | 'BRANCH'
  | 'STORE'
  | 'WAREHOUSE'
  | 'FACTORY'
  | 'RESTAURANT'
  | 'DISTRIBUTION_CENTER'
  | 'REMOTE'
  | 'CLIENT_SITE'
  | 'PROJECT_SITE'

export interface LocationResponse {
  id: string
  code: string
  name: string
  locationType: LocationType
  country?: string | null
  city?: string | null
  region?: string | null
  address?: string | null
  latitude?: number | null
  longitude?: number | null
  timezone?: string | null
  holidayJurisdiction?: string | null
  workCalendarCode?: string | null
  defaultShiftGroupId?: string | null
  branchManagerId?: string | null
  legalEntityId?: string | null
  costCentreCode?: string | null
  phone?: string | null
  email?: string | null
  active: boolean
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface LocationRequest {
  code: string
  name: string
  locationType: LocationType
  country?: string
  city?: string
  region?: string
  address?: string
  latitude?: number
  longitude?: number
  timezone?: string
  holidayJurisdiction?: string
  workCalendarCode?: string
  defaultShiftGroupId?: string
  branchManagerId?: string
  legalEntityId?: string
  costCentreCode?: string
  phone?: string
  email?: string
  active?: boolean
  notes?: string
}

export const locationApi = {
  list: (activeOnly = false) =>
    api.get<LocationResponse[]>('/locations', { params: { activeOnly } }).then((r) => r.data),
  get: (id: string) =>
    api.get<LocationResponse>(`/locations/${id}`).then((r) => r.data),
  create: (payload: LocationRequest) =>
    api.post<LocationResponse>('/locations', payload).then((r) => r.data),
  update: (id: string, payload: LocationRequest) =>
    api.put<LocationResponse>(`/locations/${id}`, payload).then((r) => r.data),
  activate: (id: string) =>
    api.post<LocationResponse>(`/locations/${id}/activate`).then((r) => r.data),
  deactivate: (id: string) =>
    api.post<LocationResponse>(`/locations/${id}/deactivate`).then((r) => r.data),
}
