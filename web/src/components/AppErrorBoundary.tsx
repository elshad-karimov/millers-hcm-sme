import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Alert, Button, Space, Typography } from 'antd'

interface Props { children: ReactNode }
interface State { error: Error | null; stack?: string }

/**
 * Catches render errors anywhere below it and shows them.
 *
 * Without a boundary React unmounts the entire tree on a render error, so
 * #root empties and the app paints pure white — indistinguishable from a
 * dead server, a hung auth round-trip, or a blank route. That ambiguity is
 * expensive to debug, especially since main.tsx hides the boot splash once
 * React has mounted, leaving nothing on screen to explain the failure.
 *
 * The component stack is rendered in dev only; in production it would leak
 * internals to an end user who cannot act on them.
 */
export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Keep the console copy — it is the only place the full stack survives
    // once the tree is torn down.
    console.error('Unhandled render error:', error, info.componentStack)
    this.setState({ error, stack: info.componentStack ?? undefined })
  }

  render() {
    const { error, stack } = this.state
    if (!error) return this.props.children

    return (
      <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
        <Alert
          type="error"
          showIcon
          message="Something in this page failed to render"
          description={
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              <Typography.Text strong>{error.message || String(error)}</Typography.Text>
              {import.meta.env.DEV && stack && (
                <Typography.Paragraph>
                  <pre style={{
                    whiteSpace: 'pre-wrap',
                    fontSize: 12,
                    maxHeight: 320,
                    overflow: 'auto',
                    margin: 0,
                  }}>{stack}</pre>
                </Typography.Paragraph>
              )}
              <Space>
                <Button onClick={() => this.setState({ error: null, stack: undefined })}>
                  Try again
                </Button>
                <Button type="primary" onClick={() => window.location.reload()}>
                  Reload
                </Button>
              </Space>
            </Space>
          }
        />
      </div>
    )
  }
}
