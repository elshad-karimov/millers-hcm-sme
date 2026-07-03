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
  categoryId?: string | null
  planYear?: number | null
  provider?: string
  providerId?: string | null
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
  categoryId?: string | null
  categoryName?: string | null
  planYear?: number | null
  provider?: string | null
  providerId?: string | null
  providerName?: string | null
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

// ─── Benefit categories (HCM_11 M373) ────────────────────────────────────────

export interface BenefitCategoryRequest {
  code: string
  name: string
  description?: string
  taxable?: boolean
  requiresProvider?: boolean
  displayOrder?: number
  active?: boolean
}

export interface BenefitCategoryResponse {
  id: string
  code: string
  name: string
  description?: string | null
  taxable: boolean
  requiresProvider: boolean
  displayOrder: number
  active: boolean
  createdAt: string
}

export const benefitCategoriesApi = {
  list: (activeOnly = false) =>
    api
      .get<BenefitCategoryResponse[]>('/compbenefits/benefit-categories', {
        params: { activeOnly },
      })
      .then((r) => r.data),
  create: (req: BenefitCategoryRequest) =>
    api.post<BenefitCategoryResponse>('/compbenefits/benefit-categories', req).then((r) => r.data),
  update: (id: string, req: BenefitCategoryRequest) =>
    api
      .put<BenefitCategoryResponse>(`/compbenefits/benefit-categories/${id}`, req)
      .then((r) => r.data),
}

// ─── Plan coverage tiers + eligibility rules (HCM_11 M375) ───────────────────

export type CoverageTier =
  | 'EMPLOYEE_ONLY'
  | 'EMPLOYEE_SPOUSE'
  | 'EMPLOYEE_CHILDREN'
  | 'FAMILY'
  | 'CUSTOM'

export const COVERAGE_TIER_LABEL: Record<CoverageTier, string> = {
  EMPLOYEE_ONLY: 'Employee only',
  EMPLOYEE_SPOUSE: 'Employee + spouse',
  EMPLOYEE_CHILDREN: 'Employee + children',
  FAMILY: 'Family',
  CUSTOM: 'Custom',
}

export interface PlanTier {
  id?: string
  planId?: string
  tierCode: CoverageTier
  tierLabel?: string | null
  employerContribution?: number
  employeeContribution?: number
  coverageAmount?: number | null
  displayOrder?: number
  active?: boolean
}

export interface EligibilityRule {
  id?: string
  planId?: string
  employmentType?: string | null
  departmentId?: string | null
  orgUnitId?: string | null
  gradeId?: string | null
  employeeCategory?: string | null
  minServiceMonths?: number | null
  description?: string | null
  active?: boolean
}

export const benefitPlanConfigApi = {
  listTiers: (planId: string) =>
    api.get<PlanTier[]>(`/compbenefits/benefits/plans/${planId}/tiers`).then((r) => r.data),
  replaceTiers: (planId: string, tiers: PlanTier[]) =>
    api
      .put<PlanTier[]>(`/compbenefits/benefits/plans/${planId}/tiers`, { tiers })
      .then((r) => r.data),
  listRules: (planId: string) =>
    api
      .get<EligibilityRule[]>(`/compbenefits/benefits/plans/${planId}/eligibility-rules`)
      .then((r) => r.data),
  replaceRules: (planId: string, rules: EligibilityRule[]) =>
    api
      .put<EligibilityRule[]>(`/compbenefits/benefits/plans/${planId}/eligibility-rules`, { rules })
      .then((r) => r.data),
}

// ─── Benefit providers / vendors (HCM_11 M374) ───────────────────────────────

export type BenefitProviderType =
  | 'INSURER'
  | 'PENSION_FUND'
  | 'CLINIC'
  | 'VENDOR'
  | 'BANK'
  | 'OTHER'

export const PROVIDER_TYPE_LABEL: Record<BenefitProviderType, string> = {
  INSURER: 'Insurer',
  PENSION_FUND: 'Pension fund',
  CLINIC: 'Clinic',
  VENDOR: 'Vendor',
  BANK: 'Bank',
  OTHER: 'Other',
}

export interface BenefitProviderRequest {
  code: string
  name: string
  providerType: BenefitProviderType
  contactName?: string
  contactEmail?: string
  contactPhone?: string
  website?: string
  contractNo?: string
  contractStart?: string
  contractEnd?: string
  notes?: string
  active?: boolean
}

export interface BenefitProviderResponse {
  id: string
  code: string
  name: string
  providerType: BenefitProviderType
  contactName?: string | null
  contactEmail?: string | null
  contactPhone?: string | null
  website?: string | null
  contractNo?: string | null
  contractStart?: string | null
  contractEnd?: string | null
  notes?: string | null
  active: boolean
  createdAt: string
}

export const benefitProvidersApi = {
  list: (activeOnly = false) =>
    api
      .get<BenefitProviderResponse[]>('/compbenefits/benefit-providers', {
        params: { activeOnly },
      })
      .then((r) => r.data),
  create: (req: BenefitProviderRequest) =>
    api.post<BenefitProviderResponse>('/compbenefits/benefit-providers', req).then((r) => r.data),
  update: (id: string, req: BenefitProviderRequest) =>
    api
      .put<BenefitProviderResponse>(`/compbenefits/benefit-providers/${id}`, req)
      .then((r) => r.data),
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
