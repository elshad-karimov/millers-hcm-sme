// M104 — Travel expense claims.

import { api } from './client'

export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PAID'
export type ExpenseCategory =
  | 'ACCOMMODATION' | 'MEALS' | 'TRANSPORT' | 'FLIGHT'
  | 'VISA_FEES' | 'COMMUNICATION' | 'REGISTRATION' | 'OTHER'

export interface ItemRequest {
  category: ExpenseCategory
  description?: string
  amount: number
  itemDate?: string
  receiptUrl?: string
}

export interface CreateClaimRequest {
  tripId: string
  currency?: string
  notes?: string
  items: ItemRequest[]
}

export interface ItemResponse {
  id: string
  category: ExpenseCategory
  description?: string | null
  amount: number
  itemDate?: string | null
  receiptUrl?: string | null
}

export interface ClaimResponse {
  id: string
  claimNo: string
  tripId: string
  tripNo?: string | null
  destination?: string | null
  employeeId: string
  employeeName?: string | null
  currency: string
  status: ClaimStatus
  totalAmount: number
  submittedAt?: string | null
  approvedAt?: string | null
  approvedBy?: string | null
  rejectedAt?: string | null
  rejectionReason?: string | null
  paidAt?: string | null
  notes?: string | null
  createdAt: string
  items: ItemResponse[]
}

export const CLAIM_STATUS_COLOR: Record<ClaimStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  PAID: 'purple',
}

export const expenseClaimsApi = {
  create: (req: CreateClaimRequest) =>
    api.post<ClaimResponse>('/expense-claims', req).then((r) => r.data),
  submit: (id: string) =>
    api.post<ClaimResponse>(`/expense-claims/${id}/submit`).then((r) => r.data),
  approve: (id: string) =>
    api.post<ClaimResponse>(`/expense-claims/${id}/approve`).then((r) => r.data),
  reject: (id: string, reason?: string) =>
    api.post<ClaimResponse>(`/expense-claims/${id}/reject`, { reason }).then((r) => r.data),
  markPaid: (id: string) =>
    api.post<ClaimResponse>(`/expense-claims/${id}/paid`).then((r) => r.data),
  get: (id: string) =>
    api.get<ClaimResponse>(`/expense-claims/${id}`).then((r) => r.data),
  list: (status?: ClaimStatus) =>
    api.get<ClaimResponse[]>('/expense-claims', { params: status ? { status } : {} }).then((r) => r.data),
  forEmployee: (employeeId: string) =>
    api.get<ClaimResponse[]>(`/expense-claims/employees/${employeeId}`).then((r) => r.data),
}
