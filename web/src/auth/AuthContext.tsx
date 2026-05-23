import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { tokenStore } from '../api/client'
import { initKeycloak, keycloak } from './keycloak'

interface AuthUser {
  username: string
  roles: string[]
}

interface AuthContextValue {
  user: AuthUser | null
  loading: boolean
  /** Redirect to Keycloak login. The SPA only ever sees an issued bearer token. */
  login: () => void
  /** Clears local + Keycloak sessions and returns to the SPA origin. */
  logout: () => void
  hasRole: (...roles: string[]) => boolean
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

/** Refresh the token if it expires in the next 60 s; keycloak-js no-ops otherwise. */
const REFRESH_LEEWAY_SECONDS = 60
const REFRESH_TICK_MS = 30_000

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)
  const refreshTimer = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false

    initKeycloak()
      .then(async (authenticated) => {
        if (cancelled) return
        if (!authenticated) {
          tokenStore.clear()
          return
        }
        if (keycloak.token) {
          tokenStore.set(keycloak.token)
        }
        try {
          const me = await authApi.me()
          if (!cancelled) setUser({ username: me.username, roles: me.roles })
        } catch {
          tokenStore.clear()
        }
      })
      .catch(() => {
        tokenStore.clear()
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    // Silent refresh — keycloak-js handles the actual /token round-trip,
    // we just nudge it on a cadence and mirror the new token to localStorage.
    refreshTimer.current = window.setInterval(() => {
      keycloak
        .updateToken(REFRESH_LEEWAY_SECONDS)
        .then((refreshed) => {
          if (refreshed && keycloak.token) tokenStore.set(keycloak.token)
        })
        .catch(() => {
          // Refresh failed (e.g. session expired Keycloak-side). Drop local
          // state — the axios 401 interceptor will bounce to login on the
          // next request.
          tokenStore.clear()
          setUser(null)
        })
    }, REFRESH_TICK_MS)

    return () => {
      cancelled = true
      if (refreshTimer.current !== null) {
        window.clearInterval(refreshTimer.current)
      }
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      login: () => {
        keycloak.login({ redirectUri: window.location.origin + '/' })
      },
      logout: () => {
        tokenStore.clear()
        setUser(null)
        keycloak.logout({ redirectUri: window.location.origin + '/' })
      },
      hasRole: (...roles) => !!user && roles.some((r) => user.roles.includes(`ROLE_${r}`)),
    }),
    [user, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
