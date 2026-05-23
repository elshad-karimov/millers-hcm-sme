import { Spin } from 'antd'
import { useEffect, type ReactNode } from 'react'
import { useAuth } from './AuthContext'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading, login } = useAuth()

  // Once Keycloak init has finished and the user is still unauthenticated,
  // hand off to Keycloak's hosted login page. The redirect-back lands on
  // the same SPA route and finishes the OIDC handshake.
  useEffect(() => {
    if (!loading && !user) {
      login()
    }
  }, [loading, user, login])

  if (loading || !user) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin size="large" tip="Signing in…" />
      </div>
    )
  }
  return <>{children}</>
}
