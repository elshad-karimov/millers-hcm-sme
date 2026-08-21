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

/**
 * True from the moment Sign out is clicked until the browser leaves the page.
 *
 * Navigating to Keycloak's end-session endpoint is not instant, and the SPA
 * keeps running in the meantime. Anything that reacts to "there is no user" by
 * redirecting to the login page — RequireAuth's effect, a failed token refresh —
 * would assign window.location a second time and SUPERSEDE the logout
 * navigation. The browser then lands on the authorize endpoint instead, where
 * the Keycloak session cookie is still valid because the logout never
 * completed, so it silently re-authenticates and drops the user back on the
 * home page having apparently done nothing.
 *
 * Module scope rather than state: it must be readable synchronously by whatever
 * runs next, without waiting for a React re-render.
 */
let signingOut = false

/** Whether a sign-out redirect is in flight; suppresses any automatic re-login. */
export const isSigningOut = () => signingOut

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
          //
          // Unless we are on our way out: a refresh that fails *because* the
          // session was just ended would otherwise clear the user mid-logout
          // and trigger the same re-login race.
          if (signingOut) return
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
        signingOut = true
        tokenStore.clear()
        // Deliberately NOT clearing `user` here. Doing so re-rendered
        // RequireAuth, whose effect calls login() when there is no user, and
        // that redirect raced the logout one — see `signingOut` above. The page
        // is navigating away regardless, so there is nothing to tidy up.
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
