// M71 — Dependents + Education + Work Experience sub-entities.

import { api } from './client'
import type { VerificationStatus } from './personalDetails'

// ── Dependents ──────────────────────────────────────────────────────────────

export type DependentRelationship =
  | 'SPOUSE'
  | 'CHILD'
  | 'PARENT'
  | 'SIBLING'
  | 'GUARDIAN'
  | 'OTHER'

/** M135 — whitelist mirrored in the backend enum + V93 CHECK. */
export type DependentEligibilityEndReason =
  | 'AGED_OUT'
  | 'DIVORCE'
  | 'DEATH'
  | 'MARRIED'
  | 'EMPLOYED'
  | 'STUDENT_STATUS_LOST'
  | 'OTHER'

export interface Dependent {
  id: string
  employeeId: string
  relationshipType: DependentRelationship
  firstName: string
  lastName: string
  middleName?: string | null
  dateOfBirth?: string | null
  gender?: string | null
  nationalIdMasked?: string | null
  nationalId?: string | null
  phone?: string | null
  email?: string | null
  insuranceEligible: boolean
  benefitEligible: boolean
  active: boolean
  // M135 — eligibility termination
  eligibilityEndDate?: string | null
  eligibilityEndReason?: DependentEligibilityEndReason | null
  /** Derived server-side: active AND (no end date OR end date in future). */
  currentlyEligible: boolean
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface DependentRequest {
  relationshipType: DependentRelationship
  firstName: string
  lastName: string
  middleName?: string
  dateOfBirth?: string
  gender?: string
  nationalId?: string
  phone?: string
  email?: string
  insuranceEligible?: boolean
  benefitEligible?: boolean
  active?: boolean
  // M135 — must be set together or both null
  eligibilityEndDate?: string
  eligibilityEndReason?: DependentEligibilityEndReason
  notes?: string
}

// ── Education ───────────────────────────────────────────────────────────────

export type EducationLevel =
  | 'HIGH_SCHOOL'
  | 'DIPLOMA'
  | 'VOCATIONAL'
  | 'ASSOCIATE'
  | 'BACHELOR'
  | 'MASTER'
  | 'DOCTORATE'
  | 'PROFESSIONAL'
  | 'OTHER'

export interface Education {
  id: string
  employeeId: string
  educationLevel: EducationLevel
  institutionName: string
  country?: string | null
  degree?: string | null
  major?: string | null
  startDate?: string | null
  endDate?: string | null
  gpa?: number | null
  verificationStatus: VerificationStatus
  verifiedBy?: string | null
  verifiedAt?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface EducationRequest {
  educationLevel: EducationLevel
  institutionName: string
  country?: string
  degree?: string
  major?: string
  startDate?: string
  endDate?: string
  gpa?: number
  notes?: string
}

// ── Work experience ─────────────────────────────────────────────────────────

export type WorkExperienceType = 'EXTERNAL' | 'INTERNAL'

export interface WorkExperience {
  id: string
  employeeId: string
  experienceType: WorkExperienceType
  employerName: string
  industry?: string | null
  jobTitle: string
  startDate: string
  endDate?: string | null
  reasonForLeaving?: string | null
  /** Plaintext salary — only for cleared roles. */
  lastSalary?: number | null
  lastSalaryCurrency?: string | null
  responsibilities?: string | null
  referenceContact?: string | null
  referenceVerified: boolean
  verificationStatus: VerificationStatus
  verifiedBy?: string | null
  verifiedAt?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export interface WorkExperienceRequest {
  experienceType?: WorkExperienceType
  employerName: string
  industry?: string
  jobTitle: string
  startDate: string
  endDate?: string
  reasonForLeaving?: string
  lastSalary?: number
  lastSalaryCurrency?: string
  responsibilities?: string
  referenceContact?: string
  referenceVerified?: boolean
  notes?: string
}

// ── API ─────────────────────────────────────────────────────────────────────

export const profileTabsApi = {
  // dependents
  listDependents: (employeeId: string, activeOnly = false) =>
    api
      .get<Dependent[]>(`/employees/${employeeId}/dependents`, { params: { activeOnly } })
      .then((r) => r.data),

  createDependent: (employeeId: string, payload: DependentRequest) =>
    api
      .post<Dependent>(`/employees/${employeeId}/dependents`, payload)
      .then((r) => r.data),

  updateDependent: (employeeId: string, depId: string, payload: DependentRequest) =>
    api
      .put<Dependent>(`/employees/${employeeId}/dependents/${depId}`, payload)
      .then((r) => r.data),

  deleteDependent: (employeeId: string, depId: string) =>
    api.delete(`/employees/${employeeId}/dependents/${depId}`),

  // education
  listEducation: (employeeId: string) =>
    api.get<Education[]>(`/employees/${employeeId}/education`).then((r) => r.data),

  createEducation: (employeeId: string, payload: EducationRequest) =>
    api
      .post<Education>(`/employees/${employeeId}/education`, payload)
      .then((r) => r.data),

  updateEducation: (employeeId: string, eduId: string, payload: EducationRequest) =>
    api
      .put<Education>(`/employees/${employeeId}/education/${eduId}`, payload)
      .then((r) => r.data),

  verifyEducation: (
    employeeId: string,
    eduId: string,
    status: 'VERIFIED' | 'REJECTED',
  ) =>
    api
      .post<Education>(`/employees/${employeeId}/education/${eduId}/verify`, null, {
        params: { status },
      })
      .then((r) => r.data),

  deleteEducation: (employeeId: string, eduId: string) =>
    api.delete(`/employees/${employeeId}/education/${eduId}`),

  // work experience
  listExperience: (employeeId: string) =>
    api
      .get<WorkExperience[]>(`/employees/${employeeId}/work-experience`)
      .then((r) => r.data),

  createExperience: (employeeId: string, payload: WorkExperienceRequest) =>
    api
      .post<WorkExperience>(`/employees/${employeeId}/work-experience`, payload)
      .then((r) => r.data),

  updateExperience: (
    employeeId: string,
    expId: string,
    payload: WorkExperienceRequest,
  ) =>
    api
      .put<WorkExperience>(`/employees/${employeeId}/work-experience/${expId}`, payload)
      .then((r) => r.data),

  verifyExperience: (
    employeeId: string,
    expId: string,
    status: 'VERIFIED' | 'REJECTED',
  ) =>
    api
      .post<WorkExperience>(
        `/employees/${employeeId}/work-experience/${expId}/verify`,
        null,
        { params: { status } },
      )
      .then((r) => r.data),

  deleteExperience: (employeeId: string, expId: string) =>
    api.delete(`/employees/${employeeId}/work-experience/${expId}`),
}
