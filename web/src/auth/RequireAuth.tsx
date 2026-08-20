import { Spin, Typography } from 'antd'
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
    // The label is a sibling, not Spin's `tip` prop: antd v5 only honours
    // `tip` in the nest (Spin wrapping children) or fullscreen patterns, and
    // silently renders NOTHING otherwise. That turned every auth wait — a
    // slow token round-trip, a backend still starting — into a blank white
    // page with no spinner and no message, which reads as a crashed app.
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 16,
          padding: 64,
        }}
      >
        <Spin size="large" />
        <Typography.Text type="secondary">Signing in…</Typography.Text>
      </div>
    )
  }
  return <>{children}</>
}
