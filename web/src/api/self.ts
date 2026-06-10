import { api } from './client'
import type { LeaveBalance, LeaveRequest, LeaveSubmitRequest, LeaveType } from './leave'
import type { PermissionBalance, PermissionRequest, PermissionSubmitRequest } from './permission'
import type { BusinessTrip, BusinessTripSubmitRequest } from './businessTrip'
import type { Timesheet } from './timesheet'
import type { PayrollResult } from './payroll'
import type { Enrollment, Certificate, EmployeeCompetency } from './learning'
import type { PerformanceReview, Goal } from './performance'
import type { EmployeeAllowance } from './compbenefits'

export interface SelfProfile {
  id: string
  employeeNo: string
  username?: string | null
  firstName: string
  lastName: string
  middleName?: string | null
  email?: string | null
  phone?: string | null
  hireDate: string
  employmentStatus?: string | null
  departmentName?: string | null
  positionTitle?: string | null
  orgUnitId?: string | null
  positionId?: string | null
  managerId?: string | null
}

export interface EmployeePeer {
  id: string
  employeeNo: string
  firstName: string
  lastName: string
}

export interface SelfSummary {
  employeeId: string
  employeeNo: string
  fullName: string
  employmentStatus?: string | null
  year: number
  annualLeaveRemaining?: number | null
  leaveRequestsPending: number
  permissionHoursRemaining?: number | null
  permissionRequestsPending: number
  currentTimesheetStatus?: string | null
  lastPayslipAt?: string | null
  lastPayslipNet?: number | null
  mandatoryCoursesPending: number
  certificatesHeld: number
  activeCycleCode?: string | null
  activeReviewStatus?: string | null
}

// M263 — Phase F.5a "you owe HR" self surface.
export type RequiredDocumentStatus = 'PENDING' | 'SATISFIED' | 'WAIVED' | 'EXPIRED'
export interface SelfRequiredDocument {
  id: string
  employeeId: string
  documentType: string
  label: string
  requiredByDate?: string | null
  status: RequiredDocumentStatus
  attachmentId?: string | null
  satisfiedAt?: string | null
  satisfiedBy?: string | null
  source: string
  sourceGrantId?: string | null
  notes?: string | null
  createdAt: string
  createdBy?: string | null
  updatedAt: string
  updatedBy?: string | null
}

export const selfApi = {
  profile: () => api.get<SelfProfile>('/self/employee').then((r) => r.data),
  summary: () => api.get<SelfSummary>('/self/summary').then((r) => r.data),
  peers: () => api.get<EmployeePeer[]>('/self/peers').then((r) => r.data),
  // M263 — pending required-document obligations for the current employee.
  requiredDocuments: () =>
    api.get<SelfRequiredDocument[]>('/self/required-documents').then((r) => r.data),

  leaveBalances: () => api.get<LeaveBalance[]>('/self/leave/balances').then((r) => r.data),
  leaveTypes: () => api.get<LeaveType[]>('/self/leave/types').then((r) => r.data),
  leaveRequests: () => api.get<LeaveRequest[]>('/self/leave/requests').then((r) => r.data),
  submitLeave: (payload: LeaveSubmitRequest) =>
    api.post<LeaveRequest>('/self/leave/submit', payload).then((r) => r.data),

  permissionBalances: () =>
    api.get<PermissionBalance[]>('/self/permission/balances').then((r) => r.data),
  permissionRequests: () =>
    api.get<PermissionRequest[]>('/self/permission/requests').then((r) => r.data),
  submitPermission: (payload: PermissionSubmitRequest) =>
    api.post<PermissionRequest>('/self/permission/submit', payload).then((r) => r.data),

  businessTrips: () => api.get<BusinessTrip[]>('/self/business-trips').then((r) => r.data),
  submitBusinessTrip: (payload: BusinessTripSubmitRequest) =>
    api.post<BusinessTrip>('/self/business-trips/submit', payload).then((r) => r.data),

  timesheets: () => api.get<Timesheet[]>('/self/timesheets').then((r) => r.data),

  payslips: () => api.get<PayrollResult[]>('/self/payslips').then((r) => r.data),

  enrollments: () => api.get<Enrollment[]>('/self/learning/enrollments').then((r) => r.data),
  certificates: () => api.get<Certificate[]>('/self/learning/certificates').then((r) => r.data),
  competencies: () =>
    api.get<EmployeeCompetency[]>('/self/learning/competencies').then((r) => r.data),

  reviews: () => api.get<PerformanceReview[]>('/self/performance/reviews').then((r) => r.data),
  goals: () => api.get<Goal[]>('/self/performance/goals').then((r) => r.data),

  allowances: () => api.get<EmployeeAllowance[]>('/self/allowances').then((r) => r.data),
}
