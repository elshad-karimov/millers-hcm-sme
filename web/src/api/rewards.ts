// M481 + M483 — Reward points & redemptions API client

import { api } from './client'

export interface RewardCatalog {
  id: string
  tenantId: string
  code: string
  name: string
  description?: string
  points: number
  monetaryValue?: number
  category?: string
  taxable: boolean
  active: boolean
  createdAt: string
  updatedAt: string
  createdBy?: string
  updatedBy?: string
}

export interface RewardWallet {
  id: string
  tenantId: string
  employeeId: string
  balance: number
  lifetimeEarned: number
  lifetimeSpent: number
  updatedAt: string
}

export interface RewardRedemption {
  id: string
  tenantId: string
  redemptionNo: string
  employeeId: string
  catalogItemId: string
  points: number
  status: string
  requestedAt: string
  fulfilledAt?: string
  fulfilledBy?: string
  payrollBonusId?: string
  deliveryAddress?: string
  notes?: string
  createdAt: string
  updatedAt: string
}

export interface RewardBudget {
  id: string
  tenantId: string
  year: number
  orgUnitId?: string
  totalAmount: number
  usedAmount: number
  createdAt: string
  updatedAt: string
  createdBy?: string
  updatedBy?: string
}

export interface RewardCatalogRequest {
  code: string
  name: string
  description?: string
  points: number
  monetaryValue?: number
  category?: string
  taxable?: boolean
  active?: boolean
}

export interface GrantPointsRequest {
  employeeId: string
  points: number
  note?: string
  orgUnitId?: string
}

export interface RedeemRewardRequest {
  catalogItemId: string
  deliveryAddress?: string
}

export interface FulfillRedemptionRequest {
  payrollRunId: string
}

export interface BudgetRequest {
  year: number
  orgUnitId?: string
  totalAmount: number
}

export const rewardCatalogApi = {
  list: (activeOnly = true) =>
    api.get<RewardCatalog[]>('/api/engagement/rewards/catalog', { params: { activeOnly } })
      .then(r => r.data),
  get: (id: string) =>
    api.get<RewardCatalog>(`/api/engagement/rewards/catalog/${id}`)
      .then(r => r.data),
  create: (req: RewardCatalogRequest) =>
    api.post<RewardCatalog>('/api/engagement/rewards/catalog', req)
      .then(r => r.data),
  update: (id: string, req: RewardCatalogRequest) =>
    api.put<RewardCatalog>(`/api/engagement/rewards/catalog/${id}`, req)
      .then(r => r.data),
}

export const rewardWalletApi = {
  get: (employeeId: string) =>
    api.get<RewardWallet>(`/api/engagement/rewards/wallet/${employeeId}`)
      .then(r => r.data),
  grantPoints: (req: GrantPointsRequest) =>
    api.post('/api/engagement/rewards/grant', req),
}

export const rewardRedemptionApi = {
  list: (status?: string) =>
    api.get<RewardRedemption[]>('/api/engagement/rewards/redemptions', { params: { status } })
      .then(r => r.data),
  listMy: (employeeId: string) =>
    api.get<RewardRedemption[]>(`/api/engagement/rewards/redemptions/my/${employeeId}`)
      .then(r => r.data),
  get: (id: string) =>
    api.get<RewardRedemption>(`/api/engagement/rewards/redemptions/${id}`)
      .then(r => r.data),
  redeem: (req: RedeemRewardRequest) =>
    api.post<RewardRedemption>('/api/engagement/rewards/redeem', req)
      .then(r => r.data),
  fulfill: (id: string, req: FulfillRedemptionRequest) =>
    api.post(`/api/engagement/rewards/redemptions/${id}/fulfill`, req),
  reject: (id: string, reason: string) =>
    api.post(`/api/engagement/rewards/redemptions/${id}/reject`, null, { params: { reason } }),
}

export const rewardBudgetApi = {
  list: (year: number) =>
    api.get<RewardBudget[]>('/api/engagement/rewards/budgets', { params: { year } })
      .then(r => r.data),
  createOrUpdate: (req: BudgetRequest) =>
    api.post<RewardBudget>('/api/engagement/rewards/budgets', req)
      .then(r => r.data),
}

export const REDEMPTION_STATUS_COLOR: Record<string, string> = {
  REQUESTED: 'blue',
  FULFILLED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}
