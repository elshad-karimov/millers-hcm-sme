import { useEffect, useSyncExternalStore } from 'react'
import { api } from '../api/client'

/**
 * Per-tenant module enablement, fetched once from GET /api/module-settings and
 * shared across the nav (springboard, Navigator, search, route guard).
 *
 * Fails OPEN: if the endpoint errors or hasn't loaded yet, nothing is hidden —
 * a config glitch must never lock a tenant out of its own modules.
 */

let disabled: Set<string> | null = null // null = not loaded yet
let inFlight = false
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())

async function load() {
  if (inFlight) return
  inFlight = true
  try {
    const r = await api.get<{ disabled: string[] }>('/module-settings')
    disabled = new Set(r.data?.disabled ?? [])
  } catch {
    disabled = new Set() // fail open
  } finally {
    inFlight = false
    emit()
  }
}

/** Force a re-fetch (call after an admin saves the module toggles). */
export function refreshModuleSettings() {
  disabled = null
  inFlight = false
  load()
}

const EMPTY: ReadonlySet<string> = new Set()

export function useEnabledModules() {
  const snap = useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => {
        listeners.delete(l)
      }
    },
    () => disabled,
    () => disabled,
  )
  useEffect(() => {
    if (disabled === null) load()
  }, [])
  const dis = snap ?? EMPTY
  return {
    /** True once the tenant config has been fetched (open until then). */
    loaded: snap !== null,
    /** Set of disabled module keys. */
    disabled: dis,
    isEnabled: (key: string | undefined) => (key ? !dis.has(key) : true),
  }
}
