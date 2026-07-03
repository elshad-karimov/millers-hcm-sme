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

export type EnrollmentStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'ENROLLED'
  | 'WAIVED'
  | 'SUSPENDED'
  | 'TERMINATED'
  | 'CANCELLED'
  | 'REJECTED'

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
  coverageTierCode?: CoverageTier | null
  dependentIds?: string[]
  dependentsCovered?: number
  notes?: string
}

export interface CoveredDependent {
  id: string
  name?: string | null
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
  coverageTierCode?: CoverageTier | null
  planYear?: number | null
  startDate: string
  endDate?: string | null
  dependentsCovered: number
  coveredDependents?: CoveredDependent[]
  notes?: string | null
  enrolledBy?: string | null
  enrolledAt: string
  terminatedBy?: string | null
  terminatedAt?: string | null
  terminationReason?: string | null
  currency?: string | null
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
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  ENROLLED: 'green',
  WAIVED: 'default',
  SUSPENDED: 'orange',
  TERMINATED: 'red',
  CANCELLED: 'default',
  REJECTED: 'red',
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

// ─── Provider-file reconciliation + cost report (HCM_11 M383/M384) ───────────

export interface ReconcileLine {
  reference: string
  employeeName?: string | null
  systemAmount?: number | null
  fileAmount?: number | null
  result: 'MATCHED' | 'AMOUNT_MISMATCH' | 'MISSING_IN_FILE' | 'EXTRA_IN_FILE'
}

export interface ReconcileResponse {
  providerName: string
  systemMembers: number
  fileMembers: number
  matched: number
  amountMismatch: number
  missingInFile: number
  extraInFile: number
  systemTotal: number
  fileTotal: number
  lines: ReconcileLine[]
}

export interface CategorySpend {
  category: string
  enrollments: number
  employerMonthly: number
  employeeMonthly: number
}

export interface EmployerCostReport {
  employerMonthly: number
  employerAnnual: number
  byCategory: CategorySpend[]
}

export interface BenefitDashboard {
  totalPlans: number
  activePlans: number
  totalEnrollments: number
  activeEnrollments: number
  pendingApprovals: number
  employerMonthly: number
  employeeMonthly: number
  employerAnnual: number
  enrollmentsByStatus: Record<string, number>
  byCategory: CategorySpend[]
  claimsByStatus: Record<string, number>
  claimsPaidTotal: number
  openLifeEventWindows: number
  expiringPlans: number
}

export const benefitReconcileApi = {
  reconcile: (providerId: string, rows: { reference: string; amount: number }[]) =>
    api
      .post<ReconcileResponse>('/compbenefits/reconcile', { providerId, rows })
      .then((r) => r.data),
  employerCost: () =>
    api.get<EmployerCostReport>('/compbenefits/reports/employer-cost').then((r) => r.data),
  dashboard: () =>
    api.get<BenefitDashboard>('/compbenefits/reports/dashboard').then((r) => r.data),
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

// ─── Open enrollment (HCM_11 M379) ───────────────────────────────────────────

export interface OpenEnrollmentWindowRequest {
  planYear: number
  name: string
  startDate: string
  endDate: string
  notes?: string
  active?: boolean
}

export interface OpenEnrollmentWindowResponse {
  id: string
  planYear: number
  name: string
  startDate: string
  endDate: string
  active: boolean
  openNow: boolean
  notes?: string | null
  createdAt: string
}

export interface OpenEnrollmentStatus {
  open: boolean
  windowId?: string | null
  windowName?: string | null
  planYear?: number | null
  startDate?: string | null
  endDate?: string | null
}

export const openEnrollmentApi = {
  listWindows: (activeOnly = false) =>
    api
      .get<OpenEnrollmentWindowResponse[]>('/compbenefits/open-enrollment/windows', {
        params: { activeOnly },
      })
      .then((r) => r.data),
  status: () =>
    api.get<OpenEnrollmentStatus>('/compbenefits/open-enrollment/status').then((r) => r.data),
  createWindow: (req: OpenEnrollmentWindowRequest) =>
    api
      .post<OpenEnrollmentWindowResponse>('/compbenefits/open-enrollment/windows', req)
      .then((r) => r.data),
  updateWindow: (id: string, req: OpenEnrollmentWindowRequest) =>
    api
      .put<OpenEnrollmentWindowResponse>(`/compbenefits/open-enrollment/windows/${id}`, req)
      .then((r) => r.data),
}

// ─── Qualifying life events (HCM_11 M380) ────────────────────────────────────

export type LifeEventType =
  | 'MARRIAGE'
  | 'DIVORCE'
  | 'BIRTH'
  | 'ADOPTION'
  | 'DEATH'
  | 'DEPENDENT_LOSS'
  | 'OTHER'

export const LIFE_EVENT_LABEL: Record<LifeEventType, string> = {
  MARRIAGE: 'Marriage',
  DIVORCE: 'Divorce',
  BIRTH: 'Birth',
  ADOPTION: 'Adoption',
  DEATH: 'Death',
  DEPENDENT_LOSS: 'Loss of dependent',
  OTHER: 'Other',
}

export type LifeEventStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CLOSED'

export const LIFE_EVENT_STATUS_COLOR: Record<LifeEventStatus, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CLOSED: 'default',
}

export interface LifeEventRequest {
  employeeId: string
  eventType: LifeEventType
  eventDate: string
  windowDays?: number
  notes?: string
}

export interface LifeEventResponse {
  id: string
  employeeId: string
  employeeName?: string | null
  eventType: LifeEventType
  eventDate: string
  windowDays: number
  windowEnd: string
  windowOpenNow: boolean
  status: LifeEventStatus
  notes?: string | null
  reportedBy?: string | null
  reviewedBy?: string | null
  reviewedAt?: string | null
  reviewNotes?: string | null
  createdAt: string
}

export const lifeEventsApi = {
  list: (params: { status?: string; employeeId?: string } = {}) =>
    api.get<LifeEventResponse[]>('/compbenefits/life-events', { params }).then((r) => r.data),
  report: (req: LifeEventRequest) =>
    api.post<LifeEventResponse>('/compbenefits/life-events', req).then((r) => r.data),
  approve: (id: string, reviewNotes?: string) =>
    api
      .post<LifeEventResponse>(`/compbenefits/life-events/${id}/approve`, { reviewNotes })
      .then((r) => r.data),
  reject: (id: string, reviewNotes?: string) =>
    api
      .post<LifeEventResponse>(`/compbenefits/life-events/${id}/reject`, { reviewNotes })
      .then((r) => r.data),
}

// ─── Benefit claims (HCM_11 M381/M382) ───────────────────────────────────────

export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PAID' | 'CANCELLED'

export const CLAIM_STATUS_COLOR: Record<ClaimStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'gold',
  APPROVED: 'blue',
  REJECTED: 'red',
  PAID: 'green',
  CANCELLED: 'default',
}

export interface ClaimItemRequest {
  serviceDate?: string
  description: string
  amount: number
}

export interface ClaimItem {
  id: string
  serviceDate?: string | null
  description: string
  amount: number
}

export interface ClaimRequest {
  employeeId: string
  enrollmentId?: string | null
  planId?: string | null
  claimDate: string
  currency?: string
  description?: string
  items: ClaimItemRequest[]
}

export interface ClaimResponse {
  id: string
  claimNo: string
  employeeId: string
  employeeName?: string | null
  enrollmentId?: string | null
  planId?: string | null
  planName?: string | null
  claimDate: string
  currency: string
  totalAmount: number
  approvedAmount?: number | null
  status: ClaimStatus
  description?: string | null
  submittedBy?: string | null
  submittedAt?: string | null
  reviewedBy?: string | null
  reviewedAt?: string | null
  reviewNotes?: string | null
  paidBy?: string | null
  paidAt?: string | null
  paymentReference?: string | null
  items: ClaimItem[]
  createdAt: string
}

export const claimsApi = {
  list: (params: { status?: string; employeeId?: string } = {}) =>
    api.get<ClaimResponse[]>('/compbenefits/claims', { params }).then((r) => r.data),
  mine: () => api.get<ClaimResponse[]>('/compbenefits/claims/me').then((r) => r.data),
  create: (req: ClaimRequest) =>
    api.post<ClaimResponse>('/compbenefits/claims', req).then((r) => r.data),
  submit: (id: string) =>
    api.post<ClaimResponse>(`/compbenefits/claims/${id}/submit`).then((r) => r.data),
  approve: (id: string, approvedAmount?: number, reviewNotes?: string) =>
    api
      .post<ClaimResponse>(`/compbenefits/claims/${id}/approve`, { approvedAmount, reviewNotes })
      .then((r) => r.data),
  reject: (id: string, reviewNotes?: string) =>
    api.post<ClaimResponse>(`/compbenefits/claims/${id}/reject`, { reviewNotes }).then((r) => r.data),
  pay: (id: string, paymentReference?: string) =>
    api.post<ClaimResponse>(`/compbenefits/claims/${id}/pay`, { paymentReference }).then((r) => r.data),
  cancel: (id: string) =>
    api.post<ClaimResponse>(`/compbenefits/claims/${id}/cancel`).then((r) => r.data),
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
  submit: (id: string) =>
    api
      .post<EnrollmentResponse>(`/compbenefits/benefits/enrollments/${id}/submit`)
      .then((r) => r.data),
  cancel: (id: string) =>
    api
      .post<EnrollmentResponse>(`/compbenefits/benefits/enrollments/${id}/cancel`)
      .then((r) => r.data),
  suspend: (id: string) =>
    api
      .post<EnrollmentResponse>(`/compbenefits/benefits/enrollments/${id}/suspend`)
      .then((r) => r.data),
  resume: (id: string) =>
    api
      .post<EnrollmentResponse>(`/compbenefits/benefits/enrollments/${id}/resume`)
      .then((r) => r.data),
  myEnrolments: () =>
    api.get<EnrollmentResponse[]>('/compbenefits/benefits/me').then((r) => r.data),
}
