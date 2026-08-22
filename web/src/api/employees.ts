import { api } from './client'

export type EmploymentStatus =
  | 'ACTIVE'
  | 'ON_PROBATION'
  | 'ON_LEAVE'
  | 'ON_BUSINESS_TRIP'
  | 'SUSPENDED'
  | 'TERMINATED'
  | 'RETIRED'
  | 'CONTRACTOR'
  | 'INTERN'
  // M78 / P2-14 — new statuses
  | 'MATERNITY_LEAVE'
  | 'MILITARY_SERVICE'
  | 'EDUCATIONAL_LEAVE'
  | 'GARDEN_LEAVE'
  | 'NON_ACTIVE'

// M61 / P1-09 — employment type, separate from employment status
export type EmploymentType =
  | 'PERMANENT'
  | 'FIXED_TERM'
  | 'PART_TIME'
  | 'PROBATIONARY'
  | 'CONTRACTOR'
  | 'INTERN'

/**
 * M150 — where the work is physically performed. Distinct from employment
 * type (contractual form) and work location (the specific site); this is what
 * selects the applicable rate and schedule pattern.
 */
export type EmployeeWorkType = 'ONSHORE' | 'OFFSHORE' | 'QUAYSIDE' | 'HYBRID'

// M61 / P1-18 — marital status (PRD §8.1)
export type MaritalStatus =
  | 'SINGLE'
  | 'MARRIED'
  | 'DIVORCED'
  | 'WIDOWED'
  | 'CIVIL_PARTNERSHIP'
  | 'OTHER'

export interface Employee {
  id: string
  employeeNo: string
  firstName: string
  lastName: string
  middleName?: string | null
  birthDate?: string | null
  gender?: string | null
  /** M61 / P1-18 */
  maritalStatus?: MaritalStatus | null
  /** M61 / P1-18 — ISO 3166-1 alpha-2 country code */
  nationality?: string | null
  nationalId?: string | null
  email?: string | null
  phone?: string | null
  hireDate: string
  employmentStatus: EmploymentStatus
  /** M61 / P1-09 — drives payroll pro-rata */
  employmentType: EmploymentType
  /** M61 / P1-09 — FTE percentage (0..100); only applied for non-salaried types */
  ftePercent: number
  departmentName?: string | null
  // Master-data ids behind the department/position names. The API has
  // always returned them; the employee form now picks by id.
  orgUnitId?: string | null
  positionId?: string | null
  positionTitle?: string | null
  costCentre?: string | null
  managerId?: string | null
  /** M141 — primary work location */
  workLocationId?: string | null
  /** M66 / P1-08 — null = use the default leave group */
  leaveGroupId?: string | null
  /** M75 / P2-19 — null = use the default payroll group */
  payrollGroupId?: string | null
  /** M75 / P2-21 — dotted-line manager (informational) */
  matrixManagerId?: string | null
  /** M146 / §9 — functional manager (informational) */
  functionalManagerId?: string | null
  /** M78 / P2-15 — rehire-eligible flag, default true */
  rehireEligible?: boolean
  /** M78 / P2-15 — soft self-FK populated by the rehire flow */
  previousEmployeeId?: string | null
  /** M78 / P2-15 — captured at rehire time, audited */
  rehireReason?: string | null
  // M132 — Section 1 cosmetic fields
  birthCountry?: string | null
  birthCity?: string | null
  birthAddress?: string | null
  /** O+/O-/A+/A-/B+/B-/AB+/AB- */
  /** ISO 639-1 alpha-2 lowercase (en/az/ru/tr/…) */
  // M133 — Section 3 contact fields
  altPhone?: string | null
  workEmail?: string | null
  workPhone?: string | null
  // M134 — Section 4 employment fields
  employeeCategory?: string | null
  /** Tenure anchor when set; otherwise hireDate is used downstream. */
  seniorityDate?: string | null
  // M150 — workforce-register master data
  /** Customer's GHRS / legacy-HRIS number. Unique per tenant. */
  externalHrId?: string | null
  /** Full legal name in local script — "SURNAME Name Patronymic oğlu". */
  fullNameLocal?: string | null
  sourceOfHire?: string | null
  positionTitleLocal?: string | null
  /** State occupational classifier ("Məşğulluq təsnifatı"). */
  occupationClassification?: string | null
  positionClassification?: string | null
  workType?: EmployeeWorkType | null
  projectName?: string | null
  professionalExperienceYears?: number | null
  jobDescriptionStatus?: string | null
  /** Null = fall back to the line manager. */
  timesheetApproverId?: string | null
  /** Null = fall back to the line manager. */
  expenseApproverId?: string | null
  hrTimesheetVerifierId?: string | null
  workScheduleText?: string | null
  workTimeText?: string | null
  lunchTimeText?: string | null
  offshoreWorkScheduleText?: string | null
  summarizedPeriodMethod?: string | null
  /** Sign-in name, or null when this employee has no login yet. */
  username?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
  updatedBy?: string | null
}

export interface EmployeeRequest {
  firstName: string
  lastName: string
  middleName?: string
  birthDate?: string
  gender?: string
  /** M61 / P1-18 */
  maritalStatus?: MaritalStatus
  /** M61 / P1-18 — ISO 3166-1 alpha-2 country code */
  nationality?: string
  nationalId?: string
  email?: string
  phone?: string
  hireDate: string
  departmentName?: string
  positionTitle?: string
  costCentre?: string
  managerId?: string
  /** M141 — primary work location */
  workLocationId?: string
  /** M61 / P1-09 — defaults to PERMANENT */
  employmentType?: EmploymentType
  /** M61 / P1-09 — defaults to 100 */
  ftePercent?: number
  /** M66 / P1-08 — null = use the default leave group */
  leaveGroupId?: string
  /** M75 / P2-19 — null = use the default payroll group */
  payrollGroupId?: string
  /** M75 / P2-21 — dotted-line manager (informational) */
  matrixManagerId?: string
  /** M146 / §9 — functional manager (informational) */
  functionalManagerId?: string
  /** M78 / P2-15 — null = leave the existing flag untouched */
  rehireEligible?: boolean
  // M132 — Section 1 cosmetic fields
  birthCountry?: string
  birthCity?: string
  birthAddress?: string
  // M133 — Section 3 contact fields
  altPhone?: string
  workEmail?: string
  workPhone?: string
  // M134 — Section 4 employment fields
  employeeCategory?: string
  seniorityDate?: string
  // M150 — workforce-register master data
  externalHrId?: string
  fullNameLocal?: string
  sourceOfHire?: string
  positionTitleLocal?: string
  occupationClassification?: string
  positionClassification?: string
  workType?: EmployeeWorkType
  projectName?: string
  professionalExperienceYears?: number
  jobDescriptionStatus?: string
  timesheetApproverId?: string
  expenseApproverId?: string
  hrTimesheetVerifierId?: string
  workScheduleText?: string
  workTimeText?: string
  lunchTimeText?: string
  offshoreWorkScheduleText?: string
  summarizedPeriodMethod?: string
  // PRD §4 steps 4-5 — optional; when present the hire also opens the
  // contract and the first effective-dated salary row.
  contractStartDate?: string
  contractEndDate?: string
  contractType?: string
  monthlyBaseSalary?: number
  salaryEffectiveFrom?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditEntry {
  id: string
  actor: string
  module: string
  action: string
  oldValue?: string | null
  newValue?: string | null
  ipAddress?: string | null
  createdAt: string
}

// M69 / P1-15 — advanced search filters
export interface EmployeeListParams {
  page?: number
  size?: number
  search?: string
  /** Single status or multi-value array. */
  status?: EmploymentStatus | EmploymentStatus[]
  employmentType?: EmploymentType
  /** ISO 3166-1 alpha-2. */
  nationality?: string
  maritalStatus?: MaritalStatus
  departmentOrgUnitId?: string
  departmentName?: string
  positionId?: string
  managerId?: string
  leaveGroupId?: string
  costCentre?: string
  hireDateFrom?: string
  hireDateTo?: string
}

// M69 / P1-16 — bulk import job tracking
export type ImportJobStatus = 'PENDING' | 'VALIDATING' | 'PREVIEW' | 'COMMITTED' | 'FAILED'

export interface ImportRowError {
  row: number
  message: string
  fields: string[]
}

export interface ImportJob {
  id: string
  startedAt: string
  finishedAt?: string | null
  startedBy: string
  fileName: string
  fileSizeBytes?: number | null
  status: ImportJobStatus
  dryRun: boolean
  rowsTotal: number
  rowsValid: number
  rowsInvalid: number
  rowsCommitted: number
  errorReport?: ImportRowError[] | null
  errorMessage?: string | null
}

export const employeesApi = {
  list: (params: EmployeeListParams = {}) =>
    api.get<PageResponse<Employee>>('/employees', { params }).then((r) => r.data),

  /**
   * Bulk import endpoint (M69 / P1-16). Pass an XLSX file picked from a file
   * input; dryRun=true validates without committing.
   */
  import: (file: File, dryRun = false) => {
    const fd = new FormData()
    fd.append('file', file)
    return api
      .post<ImportJob>('/employees/import', fd, {
        params: { dryRun },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },

  importHistory: (params: { page?: number; size?: number } = {}) =>
    api
      .get<PageResponse<ImportJob>>('/employees/import/history', { params })
      .then((r) => r.data),
  get: (id: string) => api.get<Employee>(`/employees/${id}`).then((r) => r.data),
  create: (payload: EmployeeRequest) =>
    api.post<Employee>('/employees', payload).then((r) => r.data),
  update: (id: string, payload: EmployeeRequest) =>
    api.put<Employee>(`/employees/${id}`, payload).then((r) => r.data),
  /** Creates the sign-in account for an employee hired before provisioning existed. */
  createLogin: (id: string) =>
    api.post<Employee>(`/employees/${id}/login`).then((r) => r.data),
  changeStatus: (id: string, newStatus: EmploymentStatus, reason?: string) =>
    api.post<Employee>(`/employees/${id}/status`, { newStatus, reason }).then((r) => r.data),
  audit: (id: string) =>
    api.get<AuditEntry[]>(`/employees/${id}/audit`).then((r) => r.data),
}
