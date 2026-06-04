// M108 — Benefits administration.

import { api } from './client'

export type BenefitType =
  | 'HEALTH'
  | 'DENTAL'
  | 'VISION'
  | 'LIFE_INSURANCE'
  | 'DISABILITY'
  | 'PENSION'
  | 'WELLNESS'
  | 'OTHER'

export type EnrollmentStatus = 'ENROLLED' | 'WAIVED' | 'TERMINATED'

export interface PlanRequest {
  code: string
  name: string
  description?: string
  benefitType: BenefitType
  provider?: string
  coverageDetails?: string
  eligibility?: string
  employerContribution?: number
  employeeContribution?: number
  currency?: string
  effectiveFrom: string
  effectiveTo?: string
  active?: boolean
}

export interface PlanResponse {
  id: string
  code: string
  name: string
  description?: string | null
  benefitType: BenefitType
  provider?: string | null
  coverageDetails?: string | null
  eligibility?: string | null
  employerContribution: number
  employeeContribution: number
  totalContribution: number
  currency: string
  effectiveFrom: string
  effectiveTo?: string | null
  active: boolean
  activeEnrolments: number
  createdAt: string
}

export interface EnrollmentRequest {
  planId: string
  employeeId: string
  startDate: string
  status?: EnrollmentStatus
  dependentsCovered?: number
  notes?: string
}

export interface TerminateRequest {
  endDate: string
  terminationReason?: string
}

export interface EnrollmentResponse {
  id: string
  planId: string
  planCode?: string | null
  planName?: string | null
  benefitType?: BenefitType | null
  employeeId: string
  employeeName?: string | null
  status: EnrollmentStatus
  startDate: string
  endDate?: string | null
  dependentsCovered: number
  notes?: string | null
  enrolledBy?: string | null
  enrolledAt: string
  terminatedBy?: string | null
  terminatedAt?: string | null
  terminationReason?: string | null
  employerContribution?: number | null
  employeeContribution?: number | null
}

export const BENEFIT_TYPE_LABEL: Record<BenefitType, string> = {
  HEALTH: 'Health',
  DENTAL: 'Dental',
  VISION: 'Vision',
  LIFE_INSURANCE: 'Life insurance',
  DISABILITY: 'Disability',
  PENSION: 'Pension',
  WELLNESS: 'Wellness',
  OTHER: 'Other',
}

export const BENEFIT_TYPE_COLOR: Record<BenefitType, string> = {
  HEALTH: 'red',
  DENTAL: 'cyan',
  VISION: 'blue',
  LIFE_INSURANCE: 'purple',
  DISABILITY: 'orange',
  PENSION: 'gold',
  WELLNESS: 'green',
  OTHER: 'default',
}

export const ENROLLMENT_STATUS_COLOR: Record<EnrollmentStatus, string> = {
  ENROLLED: 'green',
  WAIVED: 'default',
  TERMINATED: 'red',
}

export const benefitsApi = {
  listPlans: (activeOnly = false) =>
    api
      .get<PlanResponse[]>('/compbenefits/benefits/plans', { params: { activeOnly } })
      .then((r) => r.data),
  getPlan: (id: string) =>
    api.get<PlanResponse>(`/compbenefits/benefits/plans/${id}`).then((r) => r.data),
  createPlan: (req: PlanRequest) =>
    api.post<PlanResponse>('/compbenefits/benefits/plans', req).then((r) => r.data),
  updatePlan: (id: string, req: PlanRequest) =>
    api.put<PlanResponse>(`/compbenefits/benefits/plans/${id}`, req).then((r) => r.data),

  listEnrolments: (params: {
    employeeId?: string
    planId?: string
    status?: EnrollmentStatus
  } = {}) =>
    api
      .get<EnrollmentResponse[]>('/compbenefits/benefits/enrollments', { params })
      .then((r) => r.data),
  enrol: (req: EnrollmentRequest) =>
    api
      .post<EnrollmentResponse>('/compbenefits/benefits/enrollments', req)
      .then((r) => r.data),
  terminate: (id: string, req: TerminateRequest) =>
    api
      .post<EnrollmentResponse>(`/compbenefits/benefits/enrollments/${id}/terminate`, req)
      .then((r) => r.data),
  myEnrolments: () =>
    api.get<EnrollmentResponse[]>('/compbenefits/benefits/me').then((r) => r.data),
}
