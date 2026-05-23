import { api } from './client'

export interface AuthMeResponse {
  username: string
  roles: string[]
  email?: string | null
  name?: string | null
  issuedAt?: string | null
  expiresAt?: string | null
}

/** /auth/me now returns the token's username + roles + selected claims. */
export const authApi = {
  me: () => api.get<AuthMeResponse>('/auth/me').then((r) => r.data),
}
