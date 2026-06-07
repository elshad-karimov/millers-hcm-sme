// M65 — External certifications + occupational health.

import { api } from './client'
import type { VerificationStatus } from './personalDetails'

// ── External certifications ─────────────────────────────────────────────────

export interface Certification {
  id: string
  employeeId: string
  certificationName: string
  issuingAuthority?: string | null
  /** Last-4 mask. */
  licenseNumberMasked?: string | null
  /** Plaintext — only when the caller has SYSTEM_ADMIN / HR_ADMIN / AUDITOR. */
  licenseNumber?: string | null
  issueDate?: string | null
  expiryDate?: string | null
  requiredForPositionId?: string | null
  verificationStatus: VerificationStatus
  verifiedBy?: string | null
  verifiedAt?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface CertificationRequest {
  certificationName: string
  issuingAuthority?: string
  licenseNumber?: string
  issueDate?: string
  expiryDate?: string
  requiredForPositionId?: string
  notes?: string
}

// ── Occupational health (restricted) ────────────────────────────────────────

export interface Health {
  id: string
  employeeId: string
  fitnessCertificateDate?: string | null
  nextExamDate?: string | null
  occupationalHealthNotes?: string | null
  restrictions?: string | null
  confidential: boolean
  // M137 — Section 18 disability fields
  disabilityStatus?: string | null
  disabilityPercent?: number | null
  disabilityNote?: string | null
  accommodationsNote?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface HealthRequest {
  fitnessCertificateDate?: string
  nextExamDate?: string
  occupationalHealthNotes?: string
  restrictions?: string
  confidential?: boolean
  // M137 — Section 18 disability
  disabilityStatus?: string
  disabilityPercent?: number
  disabilityNote?: string
  accommodationsNote?: string
}

// ── M137 — Vaccinations (one row per dose, same role gate as Health) ──────

export interface Vaccination {
  id: string
  employeeId: string
  vaccineCode: string
  vaccineName: string
  administeredDate: string
  administeredBy?: string | null
  lotNumber?: string | null
  nextDoseDate?: string | null
  attachmentUrl?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface VaccinationRequest {
  vaccineCode: string
  vaccineName: string
  administeredDate: string
  administeredBy?: string
  lotNumber?: string
  nextDoseDate?: string
  attachmentUrl?: string
  notes?: string
}

// ── API ─────────────────────────────────────────────────────────────────────

export const credentialsApi = {
  // certifications
  listCertifications: (employeeId: string) =>
    api
      .get<Certification[]>(`/employees/${employeeId}/certifications`)
      .then((r) => r.data),

  createCertification: (employeeId: string, payload: CertificationRequest) =>
    api
      .post<Certification>(`/employees/${employeeId}/certifications`, payload)
      .then((r) => r.data),

  updateCertification: (
    employeeId: string,
    certId: string,
    payload: CertificationRequest,
  ) =>
    api
      .put<Certification>(`/employees/${employeeId}/certifications/${certId}`, payload)
      .then((r) => r.data),

  verifyCertification: (
    employeeId: string,
    certId: string,
    status: 'VERIFIED' | 'REJECTED',
  ) =>
    api
      .post<Certification>(
        `/employees/${employeeId}/certifications/${certId}/verify`,
        null,
        { params: { status } },
      )
      .then((r) => r.data),

  deleteCertification: (employeeId: string, certId: string) =>
    api.delete(`/employees/${employeeId}/certifications/${certId}`),

  // health
  getHealth: (employeeId: string) =>
    api
      .get<Health | ''>(`/employees/${employeeId}/health`)
      .then((r) => (r.data === '' ? null : r.data)),

  upsertHealth: (employeeId: string, payload: HealthRequest) =>
    api.put<Health>(`/employees/${employeeId}/health`, payload).then((r) => r.data),

  deleteHealth: (employeeId: string) =>
    api.delete(`/employees/${employeeId}/health`),

  // M137 — vaccinations
  listVaccinations: (employeeId: string) =>
    api
      .get<Vaccination[]>(`/employees/${employeeId}/vaccinations`)
      .then((r) => r.data),
  createVaccination: (employeeId: string, payload: VaccinationRequest) =>
    api
      .post<Vaccination>(`/employees/${employeeId}/vaccinations`, payload)
      .then((r) => r.data),
  updateVaccination: (employeeId: string, vaccinationId: string, payload: VaccinationRequest) =>
    api
      .put<Vaccination>(`/employees/${employeeId}/vaccinations/${vaccinationId}`, payload)
      .then((r) => r.data),
  deleteVaccination: (employeeId: string, vaccinationId: string) =>
    api.delete(`/employees/${employeeId}/vaccinations/${vaccinationId}`),
}
