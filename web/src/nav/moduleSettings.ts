import { useEffect, useSyncExternalStore } from 'react'
import { api } from '../api/client'

/**
 * Per-tenant module enablement, fetched once from GET /api/module-settings and
 * shared across the nav (springboard, Navigator, search, route guard).
 *
 * Two reasons a module can be unavailable, and they need different UI:
 *   • notInPlan        — the tenant's edition doesn't include it → upsell
 *   • disabledByTenant — their own admin switched it off → they can undo it
 * `disabled` is the union, which is what the nav hides either way.
 *
 * Fails OPEN: if the endpoint errors or hasn't loaded yet, nothing is hidden —
 * a config glitch must never lock a tenant out of its own modules. The server
 * enforces entitlement regardless (403), so failing open here costs nothing but
 * a dead-end click.
 */

export type PlanName = 'LITE' | 'STANDARD' | 'ENTERPRISE'

export interface UpgradeTarget {
  module: string
  label: string
  requiredPlan: PlanName
}

export interface PlanLimits {
  /** null = unlimited */
  maxActiveEmployees: number | null
}

interface ModuleSettingsResponse {
  plan?: PlanName
  disabled?: string[]
  enabled?: string[]
  disabledByTenant?: string[]
  notInPlan?: string[]
  upgrades?: UpgradeTarget[]
  limits?: PlanLimits
}

interface Snapshot {
  plan: PlanName
  disabled: Set<string>
  disabledByTenant: Set<string>
  notInPlan: Set<string>
  upgrades: Map<string, PlanName>
  limits: PlanLimits
}

let snapshot: Snapshot | null = null // null = not loaded yet
let inFlight = false
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())

const EMPTY_SET: ReadonlySet<string> = new Set()
const EMPTY_UPGRADES: ReadonlyMap<string, PlanName> = new Map()
const NO_LIMITS: PlanLimits = { maxActiveEmployees: null }

/** Fail-open snapshot used before load and after an error. */
const OPEN: Snapshot = {
  plan: 'ENTERPRISE',
  disabled: new Set(),
  disabledByTenant: new Set(),
  notInPlan: new Set(),
  upgrades: new Map(),
  limits: NO_LIMITS,
}

async function load() {
  if (inFlight) return
  inFlight = true
  try {
    const r = await api.get<ModuleSettingsResponse>('/module-settings')
    const d = r.data ?? {}
    snapshot = {
      plan: d.plan ?? 'ENTERPRISE',
      disabled: new Set(d.disabled ?? []),
      disabledByTenant: new Set(d.disabledByTenant ?? []),
      notInPlan: new Set(d.notInPlan ?? []),
      upgrades: new Map((d.upgrades ?? []).map((u) => [u.module, u.requiredPlan])),
      limits: d.limits ?? NO_LIMITS,
    }
  } catch {
    snapshot = OPEN // fail open
  } finally {
    inFlight = false
    emit()
  }
}

/** Force a re-fetch (call after an admin saves the module toggles). */
export function refreshModuleSettings() {
  snapshot = null
  inFlight = false
  load()
}

export function useEnabledModules() {
  const snap = useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => {
        listeners.delete(l)
      }
    },
    () => snapshot,
    () => snapshot,
  )
  useEffect(() => {
    if (snapshot === null) load()
  }, [])

  const disabled = snap?.disabled ?? EMPTY_SET
  return {
    /** True once the tenant config has been fetched (open until then). */
    loaded: snap !== null,
    /** The tenant's edition. */
    plan: snap?.plan ?? 'ENTERPRISE',
    /** Every module the nav should hide, whatever the reason. */
    disabled,
    /** Off because the plan excludes it — show an upgrade prompt. */
    notInPlan: snap?.notInPlan ?? EMPTY_SET,
    /** Off because the tenant's own admin switched it off. */
    disabledByTenant: snap?.disabledByTenant ?? EMPTY_SET,
    /** module key → cheapest plan that unlocks it. */
    upgrades: snap?.upgrades ?? EMPTY_UPGRADES,
    limits: snap?.limits ?? NO_LIMITS,
    isEnabled: (key: string | undefined) => (key ? !disabled.has(key) : true),
  }
}
