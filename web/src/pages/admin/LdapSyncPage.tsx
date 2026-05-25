import { useCallback, useEffect, useState } from 'react'
import {
  App,
  Button,
  Card,
  Descriptions,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { SyncOutlined, LinkOutlined, SettingOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { ldapApi, LdapStatus, LdapSyncResult } from '../../api/ldapApi'

const { Title, Text } = Typography

// ─── helpers ─────────────────────────────────────────────────────────────────

/**
 * Formats an epoch-seconds timestamp as a relative "time ago" string.
 * Returns "Never" when the value is 0 (meaning the sync has not run yet).
 */
function formatRelative(epochSeconds: number): string {
  if (!epochSeconds) return 'Never'
  const diffMs = Date.now() - epochSeconds * 1000
  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return `${diffSec}s ago`
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin}m ago`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}h ago`
  return new Date(epochSeconds * 1000).toLocaleString()
}

// ─── component ───────────────────────────────────────────────────────────────

export function LdapSyncPage() {
  const { message: msg } = App.useApp()

  const [status, setStatus]           = useState<LdapStatus | null>(null)
  const [loading, setLoading]         = useState(true)
  const [configuring, setConfiguring] = useState(false)
  const [syncResult, setSyncResult]   = useState<LdapSyncResult | null>(null)
  const [syncing, setSyncing]         = useState<'full' | 'changed' | null>(null)

  const fetchStatus = useCallback(async () => {
    try {
      const s = await ldapApi.status()
      setStatus(s)
    } catch {
      msg.error('Failed to fetch LDAP status')
    }
  }, [msg])

  useEffect(() => {
    setLoading(true)
    fetchStatus().finally(() => setLoading(false))
  }, [fetchStatus])

  const handleSetup = async () => {
    setConfiguring(true)
    setSyncResult(null)
    try {
      await ldapApi.setup()
      msg.success('LDAP provider configuration triggered')
      // Re-fetch status after a short delay to show the updated state
      setTimeout(() => fetchStatus(), 2000)
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message
      msg.error('Setup failed: ' + (detail ?? 'unknown error'))
    } finally {
      setConfiguring(false)
    }
  }

  const handleSync = async (type: 'full' | 'changed') => {
    setSyncing(type)
    setSyncResult(null)
    try {
      const result = await ldapApi.sync(type)
      setSyncResult(result)
      msg.success(
        `Sync complete — added: ${result.added}, updated: ${result.updated}, ` +
        `removed: ${result.removed}, failed: ${result.failed}`
      )
      fetchStatus()
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message
      msg.error('Sync failed: ' + (detail ?? 'unknown error'))
    } finally {
      setSyncing(null)
    }
  }

  if (loading) {
    return (
      <Space style={{ width: '100%', justifyContent: 'center', paddingTop: 48 }}>
        <Spin size="large" />
      </Space>
    )
  }

  const isConfigured = status?.setupStatus === 'configured'

  return (
    <App>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        {/* ── Page header ─────────────────────────────────────────── */}
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Title level={3} style={{ margin: 0 }}>
            <LinkOutlined style={{ marginRight: 8 }} />
            LDAP / Active Directory Sync
          </Title>
          <Tag
            color={isConfigured ? 'success' : 'error'}
            icon={isConfigured ? <CheckCircleOutlined /> : <SettingOutlined />}
            style={{ fontSize: 14, padding: '4px 12px' }}
          >
            {isConfigured ? 'Configured' : 'Not configured'}
          </Tag>
        </Space>

        {/* ── Provider status card ─────────────────────────────────── */}
        <Card title="Federation Provider">
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="Provider name">
              {status?.providerName ?? '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Enabled">
              {status?.enabled ? (
                <Tag color="success">Yes</Tag>
              ) : (
                <Tag color="default">No</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Connection URL" span={2}>
              <Text code>{status?.connectionUrl ?? '—'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Users DN" span={2}>
              <Text code>{status?.usersDn ?? '—'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Edit mode">
              <Tag color="blue">{status?.editMode ?? '—'}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Provider ID">
              <Text type="secondary" style={{ fontFamily: 'monospace', fontSize: 12 }}>
                {status?.providerId ?? '—'}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Last full sync">
              {formatRelative(status?.lastFullSync ?? 0)}
            </Descriptions.Item>
            <Descriptions.Item label="Last changed sync">
              {formatRelative(status?.lastChangedSync ?? 0)}
            </Descriptions.Item>
          </Descriptions>
        </Card>

        {/* ── Actions card ─────────────────────────────────────────── */}
        <Card title="Actions">
          <Space wrap>
            {!isConfigured && (
              <Button
                type="primary"
                icon={<SettingOutlined />}
                loading={configuring}
                onClick={handleSetup}
              >
                Configure Now
              </Button>
            )}
            {isConfigured && (
              <>
                <Button
                  type="primary"
                  icon={<SyncOutlined />}
                  loading={syncing === 'full'}
                  disabled={syncing !== null}
                  onClick={() => handleSync('full')}
                >
                  Full Sync
                </Button>
                <Button
                  icon={<SyncOutlined />}
                  loading={syncing === 'changed'}
                  disabled={syncing !== null}
                  onClick={() => handleSync('changed')}
                >
                  Changed Sync
                </Button>
                <Button
                  icon={<SettingOutlined />}
                  loading={configuring}
                  onClick={handleSetup}
                >
                  Re-configure
                </Button>
              </>
            )}
          </Space>
        </Card>

        {/* ── Last sync result ─────────────────────────────────────── */}
        {syncResult && (
          <Card title="Last sync result">
            <Descriptions bordered column={4} size="small">
              <Descriptions.Item label="Action">
                <Text code>{syncResult.action}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Added">
                <Tag color="green">{syncResult.added}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Updated">
                <Tag color="blue">{syncResult.updated}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Removed">
                <Tag color="orange">{syncResult.removed}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Failed">
                <Tag color={syncResult.failed > 0 ? 'error' : 'default'}>
                  {syncResult.failed}
                </Tag>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        )}
      </Space>
    </App>
  )
}
