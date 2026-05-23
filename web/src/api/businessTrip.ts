import { api } from './client'
import type { PageResponse } from './employees'

export type TripType = 'DOMESTIC' | 'INTERNATIONAL'

export type TripStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'COMPLETED'

export interface BusinessTrip {
  id: string
  tripNo: string
  employeeId: string
  tripType: TripType
  destinationCountry?: string | null
  destinationCity: string
  purpose?: string | null
  project?: string | null
  costCentre?: string | null
  startDate: string
  endDate: string
  totalDays: number
  currency: string
  dailyAllowance?: number | null
  requestedAdvance?: number | null
  approvedAdvance?: number | null
  paidAdvance: number
  actualExpense?: number | null
  advanceBalance: number
  mealsProvided: boolean
  accommodationProvided: boolean
  attachmentUrls?: string | null
  status: TripStatus
  workflowInstanceId?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string | null
}

export interface BusinessTripSubmitRequest {
  employeeId: string
  tripType: TripType
  destinationCountry?: string
  destinationCity: string
  purpose?: string
  project?: string
  costCentre?: string
  startDate: string
  endDate: string
  currency?: string
  dailyAllowance?: number
  requestedAdvance?: number
  mealsProvided?: boolean
  accommodationProvided?: boolean
  attachmentUrls?: string
}

export interface ExpenseReconcileRequest {
  actualExpense: number
  paidAdvance?: number
  reason: string
}

export const businessTripApi = {
  list: (params: {
    employeeId?: string
    status?: TripStatus
    page?: number
    size?: number
  }) =>
    api.get<PageResponse<BusinessTrip>>('/business-trips', { params }).then((r) => r.data),
  get: (id: string) => api.get<BusinessTrip>(`/business-trips/${id}`).then((r) => r.data),
  submit: (payload: BusinessTripSubmitRequest) =>
    api.post<BusinessTrip>('/business-trips/submit', payload).then((r) => r.data),
  reconcile: (id: string, payload: ExpenseReconcileRequest) =>
    api.post<BusinessTrip>(`/business-trips/${id}/reconcile`, payload).then((r) => r.data),
}
