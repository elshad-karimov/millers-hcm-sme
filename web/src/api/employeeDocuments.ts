// M169 — Employee Document Management (PRD §8.1.3)

import { api } from './client'

export type EmployeeDocumentType =
  | 'EMPLOYMENT_CONTRACT'
  | 'ID_COPY'
  | 'PASSPORT_COPY'
  | 'CERTIFICATE'
  | 'EDUCATION'
  | 'MEDICAL'
  | 'ORDER'
  | 'SALARY_CHANGE_DOC'
  | 'TERMINATION_DOC'
  | 'OTHER'

export const DOCUMENT_TYPE_LABELS: Record<EmployeeDocumentType, string> = {
  EMPLOYMENT_CONTRACT: 'Employment Contract',
  ID_COPY: 'ID Copy',
  PASSPORT_COPY: 'Passport Copy',
  CERTIFICATE: 'Certificate',
  EDUCATION: 'Education',
  MEDICAL: 'Medical',
  ORDER: 'Order',
  SALARY_CHANGE_DOC: 'Salary Change Document',
  TERMINATION_DOC: 'Termination Document',
  OTHER: 'Other',
}

export interface EmployeeDocument {
  id: string
  employeeId: string
  documentType: EmployeeDocumentType
  attachmentId?: string | null
  title?: string | null
  expiryDate?: string | null
  restricted: boolean
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface EmployeeDocumentRequest {
  documentType: EmployeeDocumentType
  attachmentId?: string
  title?: string
  expiryDate?: string
  restricted?: boolean
  notes?: string
}

export const employeeDocumentsApi = {
  list: (employeeId: string) =>
    api
      .get<EmployeeDocument[]>(`/employees/${employeeId}/documents`)
      .then((r) => r.data),

  get: (employeeId: string, docId: string) =>
    api
      .get<EmployeeDocument>(`/employees/${employeeId}/documents/${docId}`)
      .then((r) => r.data),

  create: (employeeId: string, payload: EmployeeDocumentRequest) =>
    api
      .post<EmployeeDocument>(`/employees/${employeeId}/documents`, payload)
      .then((r) => r.data),

  update: (employeeId: string, docId: string, payload: EmployeeDocumentRequest) =>
    api
      .put<EmployeeDocument>(`/employees/${employeeId}/documents/${docId}`, payload)
      .then((r) => r.data),

  remove: (employeeId: string, docId: string) =>
    api.delete(`/employees/${employeeId}/documents/${docId}`),
}
