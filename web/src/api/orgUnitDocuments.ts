import { api } from './client'

export interface OrgUnitDocument {
  id: string
  orgUnitId: string
  title: string
  docType?: string | null
  documentRef?: string | null
  issuedDate?: string | null
  expiryDate?: string | null
  responsibleEmployeeId?: string | null
  notes?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface OrgUnitDocumentRequest {
  title: string
  docType?: string
  documentRef?: string
  issuedDate?: string
  expiryDate?: string
  responsibleEmployeeId?: string
  notes?: string
}

export const orgUnitDocumentsApi = {
  list: (unitId: string) =>
    api.get<OrgUnitDocument[]>(`/org/units/${unitId}/documents`).then((r) => r.data),
  create: (unitId: string, payload: OrgUnitDocumentRequest) =>
    api.post<OrgUnitDocument>(`/org/units/${unitId}/documents`, payload).then((r) => r.data),
  update: (unitId: string, docId: string, payload: OrgUnitDocumentRequest) =>
    api.put<OrgUnitDocument>(`/org/units/${unitId}/documents/${docId}`, payload).then((r) => r.data),
  delete: (unitId: string, docId: string) =>
    api.delete(`/org/units/${unitId}/documents/${docId}`),
}
