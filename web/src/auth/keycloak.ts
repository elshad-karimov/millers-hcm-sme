import Keycloak from 'keycloak-js'

/**
 * Single Keycloak instance for the SPA. Authorization Code + PKCE (S256).
 *
 * The token is still mirrored into localStorage by AuthContext so the
 * existing axios interceptors keep adding the Bearer header unchanged.
 * The keycloak-js library itself maintains in-memory token state and
 * a silent refresh timer.
 */
/**
 * In dev the SPA talks to Keycloak through the Vite proxy (see
 * web/vite.config.ts) so the browser doesn't need to trust the
 * self-signed cert at https://localhost:8443 — every Keycloak path
 * (/realms, /resources, /js) is proxied from the SPA's own origin.
 * That makes the browser handshake plain HTTP between client + Vite,
 * and HTTPS between Vite + nginx (with secure:false to ignore the
 * self-signed cert).
 *
 * In production set VITE_KEYCLOAK_URL to the real Keycloak base
 * (e.g. https://auth.example.com) — keycloak-js will then build
 * URLs against that origin and skip the proxy entirely.
 */
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL
    ?? (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:5180'),
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'millers-hcm',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT ?? 'hcm-web',
})

/**
 * Initialises Keycloak once. We use {@code check-sso} (rather than
 * {@code login-required}) so the SPA can render its own splash / route
 * to /login itself — gives us control over the loading UX.
 *
 * <p>Module-scoped guard: React 18's StrictMode invokes effects twice
 * in dev (mount → unmount → mount) to surface accidental side-effect
 * dependencies. Keycloak refuses to be {@code init()}'d more than once
 * on the same instance — the second call throws
 * "A 'Keycloak' instance can only be initialized once" and unmounts
 * the entire AuthProvider tree (blank page, no console output unless
 * you have DevTools open). Caching the in-flight promise here makes
 * the second call return the same resolved authenticated state.
 */
let initPromise: Promise<boolean> | null = null

export function initKeycloak(): Promise<boolean> {
  if (initPromise) return initPromise
  initPromise = keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
  })
  return initPromise
}
