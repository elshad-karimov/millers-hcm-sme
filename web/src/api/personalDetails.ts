// M63 — sub-entities on the employee aggregate: identification documents,
// addresses, emergency contacts.
//
// One API client module per concern would be three near-identical files;
// they all hang off the same /api/employees/{id} prefix and share a role
// gate, so they live together here. The companion backend controller is
// EmployeePersonalDetailsController.java.

import { api } from './client'

// ── Identification documents ────────────────────────────────────────────────

export type IdentificationDocumentType =
  | 'NATIONAL_ID'
  | 'PASSPORT'
  | 'VISA'
  | 'WORK_PERMIT'
  | 'RESIDENCY_PERMIT'
  | 'DRIVER_LICENSE'

export type VerificationStatus = 'UNVERIFIED' | 'VERIFIED' | 'REJECTED'

export interface Identification {
  id: string
  employeeId: string
  documentType: IdentificationDocumentType
  /** Always returned — last 4 chars, prefixed with "…". */
  documentNumberMasked: string | null
  /** Plaintext — present only for callers cleared to see PII. */
  documentNumber?: string | null
  issueDate?: string | null
  expiryDate?: string | null
  issuingAuthority?: string | null
  issuingCountry?: string | null
  verificationStatus: VerificationStatus
  verifiedBy?: string | null
  verifiedAt?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface IdentificationRequest {
  documentType: IdentificationDocumentType
  documentNumber: string
  issueDate?: string
  expiryDate?: string
  issuingAuthority?: string
  /** ISO 3166-1 alpha-2 code. */
  issuingCountry?: string
  notes?: string
}

// ── Addresses ───────────────────────────────────────────────────────────────

export type AddressType = 'HOME' | 'MAILING' | 'WORK' | 'EMERGENCY'

export interface Address {
  id: string
  employeeId: string
  addressType: AddressType
  addressLine1: string
  addressLine2?: string | null
  city?: string | null
  district?: string | null
  country?: string | null
  postalCode?: string | null
  effectiveFrom: string
  effectiveTo?: string | null
  current: boolean
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface AddressRequest {
  addressType: AddressType
  addressLine1: string
  addressLine2?: string
  city?: string
  district?: string
  /** ISO 3166-1 alpha-2 code. */
  country?: string
  postalCode?: string
  effectiveFrom: string
}

// ── Emergency contacts ──────────────────────────────────────────────────────

export type EmergencyRelationship =
  | 'SPOUSE'
  | 'CHILD'
  | 'PARENT'
  | 'SIBLING'
  | 'GUARDIAN'
  | 'FRIEND'
  | 'OTHER'

export interface EmergencyContact {
  id: string
  employeeId: string
  name: string
  relationship: EmergencyRelationship
  phone: string
  altPhone?: string | null
  email?: string | null
  address?: string | null
  primary: boolean
  priorityOrder: number
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface EmergencyContactRequest {
  name: string
  relationship: EmergencyRelationship
  phone: string
  altPhone?: string
  email?: string
  address?: string
  primary?: boolean
  priorityOrder?: number
}

// ── API ─────────────────────────────────────────────────────────────────────

export const personalDetailsApi = {
  // identifications
  listIdentifications: (employeeId: string) =>
    api
      .get<Identification[]>(`/employees/${employeeId}/identifications`)
      .then((r) => r.data),

  createIdentification: (employeeId: string, payload: IdentificationRequest) =>
    api
      .post<Identification>(`/employees/${employeeId}/identifications`, payload)
      .then((r) => r.data),

  updateIdentification: (
    employeeId: string,
    idId: string,
    payload: IdentificationRequest,
  ) =>
    api
      .put<Identification>(`/employees/${employeeId}/identifications/${idId}`, payload)
      .then((r) => r.data),

  verifyIdentification: (
    employeeId: string,
    idId: string,
    status: 'VERIFIED' | 'REJECTED',
  ) =>
    api
      .post<Identification>(
        `/employees/${employeeId}/identifications/${idId}/verify`,
        null,
        { params: { status } },
      )
      .then((r) => r.data),

  deleteIdentification: (employeeId: string, idId: string) =>
    api.delete(`/employees/${employeeId}/identifications/${idId}`),

  // addresses
  listAddresses: (employeeId: string, currentOnly = false) =>
    api
      .get<Address[]>(`/employees/${employeeId}/addresses`, { params: { currentOnly } })
      .then((r) => r.data),

  createAddress: (employeeId: string, payload: AddressRequest) =>
    api
      .post<Address>(`/employees/${employeeId}/addresses`, payload)
      .then((r) => r.data),

  updateAddress: (employeeId: string, addressId: string, payload: AddressRequest) =>
    api
      .put<Address>(`/employees/${employeeId}/addresses/${addressId}`, payload)
      .then((r) => r.data),

  deleteAddress: (employeeId: string, addressId: string) =>
    api.delete(`/employees/${employeeId}/addresses/${addressId}`),

  // emergency contacts
  listEmergencyContacts: (employeeId: string) =>
    api
      .get<EmergencyContact[]>(`/employees/${employeeId}/emergency-contacts`)
      .then((r) => r.data),

  createEmergencyContact: (employeeId: string, payload: EmergencyContactRequest) =>
    api
      .post<EmergencyContact>(`/employees/${employeeId}/emergency-contacts`, payload)
      .then((r) => r.data),

  updateEmergencyContact: (
    employeeId: string,
    contactId: string,
    payload: EmergencyContactRequest,
  ) =>
    api
      .put<EmergencyContact>(
        `/employees/${employeeId}/emergency-contacts/${contactId}`,
        payload,
      )
      .then((r) => r.data),

  deleteEmergencyContact: (employeeId: string, contactId: string) =>
    api.delete(`/employees/${employeeId}/emergency-contacts/${contactId}`),
}
