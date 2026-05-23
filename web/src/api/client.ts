import axios from 'axios'

const TOKEN_KEY = 'hcm.token'

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
