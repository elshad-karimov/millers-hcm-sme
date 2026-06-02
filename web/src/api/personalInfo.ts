// M79 — Personal-info change requests + masking helpers shared with the SPA.

import { api } from './client'

export type PersonalInfoChangeStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'APPLIED'
  | 'CANCELLED'

/** Whitelist mirrored from PersonalInfoFieldValidator on the backend. */
export const PERSONAL_INFO_FIELDS = [
  'email',
  'phone',
  'addressLine1',
  'addressLine2',
  'city',
  'district',
  'postalCode',
  'country',
  'maritalStatus',
  'emergencyContactName',
  'emergencyContactPhone',
] as const

export type PersonalInfoFieldKey = (typeof PERSONAL_INFO_FIELDS)[number]

export interface PersonalInfoChange {
  id: string
  requestNo: string
  employeeId: string
  fieldKey: PersonalInfoFieldKey
  oldValue?: string | null
  newValue?: string | null
  reason?: string | null
  status: PersonalInfoChangeStatus
  workflowInstanceId?: string | null
  submittedAt: string
  submittedBy?: string | null
  decidedAt?: string | null
  decidedBy?: string | null
  decisionComment?: string | null
  appliedAt?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface PersonalInfoChangeSubmit {
  fieldKey: PersonalInfoFieldKey
  newValue?: string
  reason?: string
}

export interface PersonalInfoChangePage {
  content: PersonalInfoChange[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const personalInfoApi = {
  list: (status?: PersonalInfoChangeStatus, page = 0, size = 20) =>
    api
      .get<PersonalInfoChangePage>('/personal-info-changes', {
        params: { status, page, size },
      })
      .then((r) => r.data),

  get: (id: string) =>
    api.get<PersonalInfoChange>(`/personal-info-changes/${id}`).then((r) => r.data),
}

export const selfPersonalInfoApi = {
  list: () =>
    api.get<PersonalInfoChange[]>('/self/personal-info').then((r) => r.data),

  submit: (req: PersonalInfoChangeSubmit) =>
    api
      .post<PersonalInfoChange>('/self/personal-info/submit', {
        // employeeId is forced to the caller by the backend
        employeeId: '00000000-0000-0000-0000-000000000000',
        ...req,
      })
      .then((r) => r.data),
}
