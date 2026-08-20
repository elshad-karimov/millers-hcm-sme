import { api } from './client'

/**
 * M151 — the itemised annual leave entitlement.
 *
 * Azerbaijani annual leave is a statutory sum, not one number, and an
 * inspection asks for the breakdown. These endpoints expose the addends that
 * explain `LeaveBalance.entitlementDays`.
 */

export type EntitlementComponentCode =
  | 'BASE'
  | 'SENIORITY'
  | 'HAZARDOUS'
  | 'CHILDREN'
  | 'BLOOD_DONATION'
  | 'OTHER'

/** DERIVED rows are recomputed on every recalculation; MANUAL rows are HR's and survive one. */
export type EntitlementComponentSource = 'DERIVED' | 'MANUAL'

export interface EntitlementComponent {
  id: string
  componentCode: EntitlementComponentCode
  days: number
  source: EntitlementComponentSource
  /** Human-readable justification, e.g. "8.5 yrs professional experience → 10-year bracket". */
  basis?: string | null
  computedAt?: string | null
  updatedBy?: string | null
}

export interface EntitlementBreakdown {
  employeeId: string
  leaveTypeId: string
  year: number
  totalDays: number
  components: EntitlementComponent[]
}

export interface ManualComponentRequest {
  componentCode: EntitlementComponentCode
  /** null clears the override and hands the component back to the resolvers. */
  days?: number | null
  basis?: string
}

/** Statutory label for each component, shown on the breakdown. */
export const COMPONENT_LABELS: Record<EntitlementComponentCode, string> = {
  BASE: 'Base entitlement (Art. 114)',
  SENIORITY: 'Seniority — professional experience (Art. 116.1)',
  HAZARDOUS: 'Harmful working conditions (Art. 115.2)',
  CHILDREN: 'Women with children (Art. 117)',
  BLOOD_DONATION: 'Blood donation (rest days, not vacation)',
  OTHER: 'Other',
}

/**
 * Which components add up to the annual vacation entitlement.
 *
 * Mirrors `EntitlementComponentCode.countsTowardAnnualEntitlement()` on the
 * server — the total on screen must be the one written into the balance.
 * Blood-donation days are earned rest days rather than vacation: in the
 * customer's register, every row whose stated total disagreed with the sum of
 * its own parts disagreed by exactly the blood-donation figure.
 */
export const COUNTS_TOWARD_TOTAL: Record<EntitlementComponentCode, boolean> = {
  BASE: true,
  SENIORITY: true,
  HAZARDOUS: true,
  CHILDREN: true,
  BLOOD_DONATION: false,
  OTHER: true,
}

const base = (employeeId: string, leaveTypeId: string) =>
  `/employees/${employeeId}/leave-entitlement/${leaveTypeId}`

export const leaveEntitlementApi = {
  breakdown: (employeeId: string, leaveTypeId: string, year?: number) =>
    api
      .get<EntitlementBreakdown>(base(employeeId, leaveTypeId), { params: { year } })
      .then((r) => r.data),

  recalculate: (employeeId: string, leaveTypeId: string, year?: number) =>
    api
      .post<EntitlementBreakdown>(`${base(employeeId, leaveTypeId)}/recalculate`, null, {
        params: { year },
      })
      .then((r) => r.data),

  setManual: (
    employeeId: string,
    leaveTypeId: string,
    payload: ManualComponentRequest,
    year?: number,
  ) =>
    api
      .put<EntitlementBreakdown>(`${base(employeeId, leaveTypeId)}/manual`, payload, {
        params: { year },
      })
      .then((r) => r.data),
}
