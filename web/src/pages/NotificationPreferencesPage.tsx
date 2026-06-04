// M115 — Per-user notification preferences.
//
// Settings page where each authenticated user can opt out of specific
// notification categories on specific channels. TRANSACTIONAL stays
// always-on (workflow approvals, security alerts).

import { useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Card,
  Empty,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  notificationPreferencesApi,
  type CategoryRow,
  type NotificationChannel,
  type PreferenceGrid,
} from '../api/notificationPreferences'

const { Title, Text, Paragraph } = Typography

const CHANNEL_LABEL: Record<NotificationChannel, string> = {
  EMAIL: 'Email',
  PUSH: 'Push',
  IN_APP: 'In-app',
}

const CHANNEL_DESCRIPTION: Record<NotificationChannel, string> = {
  EMAIL: 'Sent to your work email address.',
  PUSH: 'Mobile push (if you have a registered device).',
  IN_APP: 'Shows up in the bell at the top of the platform.',
}

export function NotificationPreferencesPage() {
  const { message } = AntdApp.useApp()
  const [grid, setGrid] = useState<PreferenceGrid | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState<string | null>(null)

  useEffect(() => {
    notificationPreferencesApi
      .mine()
      .then(setGrid)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load preferences'),
      )
      .finally(() => setLoading(false))
  }, [message])

  const toggle = async (
    row: CategoryRow,
    channel: NotificationChannel,
    enabled: boolean,
  ) => {
    if (!row.mutable) return
    const key = `${row.category}|${channel}`
    setSaving(key)
    try {
      const fresh = await notificationPreferencesApi.toggle(row.category, channel, enabled)
      setGrid(fresh)
      message.success(
        `${row.displayName}: ${CHANNEL_LABEL[channel]} ${enabled ? 'enabled' : 'muted'}`,
      )
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(null)
    }
  }

  if (loading) return <Spin />
  if (!grid) return <Empty description="No data" />

  const columns: ColumnsType<CategoryRow> = [
    {
      title: 'Category',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Space>
            <Text strong>{r.displayName}</Text>
            {!r.mutable && <Tag color="default">always on</Tag>}
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.description}</Text>
        </Space>
      ),
    },
    ...(['IN_APP', 'EMAIL', 'PUSH'] as NotificationChannel[]).map<ColumnsType<CategoryRow>[number]>(
      (ch) => ({
        title: (
          <Space direction="vertical" size={0}>
            <Text strong>{CHANNEL_LABEL[ch]}</Text>
            <Text type="secondary" style={{ fontSize: 11, fontWeight: 'normal' }}>
              {CHANNEL_DESCRIPTION[ch]}
            </Text>
          </Space>
        ),
        width: 130,
        align: 'center' as const,
        render: (_: unknown, r: CategoryRow) => {
          const enabled = r.channels[ch] ?? true
          const key = `${r.category}|${ch}`
          return (
            <Switch
              checked={enabled}
              disabled={!r.mutable}
              loading={saving === key}
              onChange={(v) => toggle(r, ch, v)}
            />
          )
        },
      }),
    ),
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Notification preferences</Title>
      <Paragraph type="secondary">
        Control which alerts the platform sends you, per channel. Everything is on by default;
        flip a switch off to opt out. Transactional notifications — workflow approvals you must
        act on, security alerts, urgent escalations — are always delivered regardless of your
        choices.
      </Paragraph>

      <Alert
        type="info"
        showIcon
        message="Changes apply immediately"
        description="Scheduled senders (expiry alerts, learning reminders, stale-pool digests) read your preferences at the moment of delivery — no need to wait for the next batch."
      />

      <Card>
        <Table
          rowKey="category"
          columns={columns}
          dataSource={grid.categories}
          size="middle"
          pagination={false}
        />
      </Card>
    </Space>
  )
}
