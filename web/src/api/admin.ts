import { api } from './client'

export interface AdminUser {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  enabled: boolean
  roles: string[]
}

export const adminApi = {
  /** List all Keycloak realm users with their current HCM roles. */
  listUsers: (): Promise<AdminUser[]> =>
    api.get<AdminUser[]>('/admin/users').then((r) => r.data),

  /** Returns the ordered list of assignable HCM role names. */
  getAvailableRoles: (): Promise<string[]> =>
    api.get<string[]>('/admin/users/roles').then((r) => r.data),

  /**
   * Issues a one-time password for a user who has none. Returned once and not
   * recoverable afterwards — show it to the administrator, do not store it.
   */
  issueTemporaryPassword: (userId: string): Promise<string> =>
    api
      .post<{ temporaryPassword: string }>(`/admin/users/${userId}/temporary-password`)
      .then((r) => r.data.temporaryPassword),

  /** Replace a user's role set with the supplied list. */
  setUserRoles: (userId: string, roles: string[]): Promise<void> =>
    api.put(`/admin/users/${userId}/roles`, roles).then(() => undefined),
}
