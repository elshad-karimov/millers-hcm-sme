import axios from 'axios'

const TOKEN_KEY = 'hcm.token'

/**
 * The `/api` prefix lives HERE and nowhere else. Call paths passed to this
 * client start at the segment after it — `api.get('/analytics/warehouse/status')`,
 * not `api.get('/api/analytics/warehouse/status')`, which requests `/api/api/...`
 * and 404s. 186 call sites across the SPA had the prefix twice; the doubled path
 * is silent at build time and only shows up as a 404 in the browser.
 *
 * Raw browser URLs — an `href`, an `<img src>`, a bare `fetch()` — do NOT go
 * through this client and so DO need the full `/api/...` path.
 */
export const api = axios.create({
  baseURL: '/api',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      // Lazy-import to avoid circular dep with auth/keycloak.ts.
      const { keycloak } = await import('../auth/keycloak')
      // Hand off to Keycloak. After successful login we land back on the
      // current route and the SPA picks up the new token.
      keycloak.login({ redirectUri: window.location.href })
    }
    return Promise.reject(error)
  },
)

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (value: string) => localStorage.setItem(TOKEN_KEY, value),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}
