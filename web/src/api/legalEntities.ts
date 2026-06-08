// M140 — legal entity master client.

import { api } from './client'

export interface LegalEntityRequest {
  code: string
  name: string
  registrationNumber?: string
  taxId?: string
  socialInsuranceRegNumber?: string
  legalAddress?: string
  country?: string
  currency?: string
  fiscalCalendar?: string
  payrollBankName?: string
  payrollBankAccount?: string
  payrollBankSwift?: string
  defaultCostCentreCode?: string
  chartOfAccountsRef?: string
  legalRepresentativeName?: string
  legalRepresentativeTitle?: string
  companySealUrl?: string
  active?: boolean
  effectiveFrom?: string
  effectiveTo?: string
  notes?: string
}

export interface LegalEntityResponse {
  id: string
  code: string
  name: string
  registrationNumber?: string | null
  taxId?: string | null
  socialInsuranceRegNumber?: string | null
  legalAddress?: string | null
  country?: string | null
  currency?: string | null
  fiscalCalendar?: string | null
  payrollBankName?: string | null
  /** Last-4 mask. */
  payrollBankAccountMasked?: string | null
  /** Plaintext — only for HR_ADMIN / SYSTEM_ADMIN. */
  payrollBankAccount?: string | null
  payrollBankSwift?: string | null
  defaultCostCentreCode?: string | null
  chartOfAccountsRef?: string | null
  legalRepresentativeName?: string | null
  legalRepresentativeTitle?: string | null
  companySealUrl?: string | null
  active: boolean
  effectiveFrom?: string | null
  effectiveTo?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export const legalEntitiesApi = {
  list: (activeOnly = false) =>
    api.get<LegalEntityResponse[]>('/legal-entities', { params: { activeOnly } })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<LegalEntityResponse>(`/legal-entities/${id}`).then((r) => r.data),
  create: (payload: LegalEntityRequest) =>
    api.post<LegalEntityResponse>('/legal-entities', payload).then((r) => r.data),
  update: (id: string, payload: LegalEntityRequest) =>
    api.put<LegalEntityResponse>(`/legal-entities/${id}`, payload).then((r) => r.data),
  activate: (id: string) =>
    api.post<LegalEntityResponse>(`/legal-entities/${id}/activate`).then((r) => r.data),
  deactivate: (id: string) =>
    api.post<LegalEntityResponse>(`/legal-entities/${id}/deactivate`).then((r) => r.data),
}
