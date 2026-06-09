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
import { useTranslation } from 'react-i18next'
import {
  notificationPreferencesApi,
  type CategoryRow,
  type NotificationChannel,
  type PreferenceGrid,
} from '../api/notificationPreferences'

const { Title, Text, Paragraph } = Typography

export function NotificationPreferencesPage() {
  const { message } = AntdApp.useApp()
  // M238 — channel labels + descriptions live in JSON keyed by the
  // enum value, so the same mapping table that drove the hard-coded
  // Records is now generated via t(`channels.${ch}`).
  const { t } = useTranslation('notifications')
  const [grid, setGrid] = useState<PreferenceGrid | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState<string | null>(null)

  useEffect(() => {
    notificationPreferencesApi
      .mine()
      .then(setGrid)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? t('preferences.messages.loadFailed')),
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
        t('preferences.messages.toggled', {
          category: row.displayName,
          channel: t(`preferences.channels.${channel}`),
          state: enabled ? t('preferences.messages.enabled') : t('preferences.messages.muted'),
        }),
      )
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? t('preferences.messages.saveFailed'),
      )
    } finally {
      setSaving(null)
    }
  }

  if (loading) return <Spin />
  if (!grid) return <Empty description={t('preferences.noData')} />

  const columns: ColumnsType<CategoryRow> = [
    {
      title: t('preferences.columns.category'),
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Space>
            <Text strong>{r.displayName}</Text>
            {!r.mutable && <Tag color="default">{t('preferences.alwaysOn')}</Tag>}
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.description}</Text>
        </Space>
      ),
    },
    ...(['IN_APP', 'EMAIL', 'PUSH'] as NotificationChannel[]).map<ColumnsType<CategoryRow>[number]>(
      (ch) => ({
        title: (
          <Space direction="vertical" size={0}>
            <Text strong>{t(`preferences.channels.${ch}`)}</Text>
            <Text type="secondary" style={{ fontSize: 11, fontWeight: 'normal' }}>
              {t(`preferences.channelDescriptions.${ch}`)}
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
      <Title level={3} style={{ margin: 0 }}>{t('preferences.title')}</Title>
      <Paragraph type="secondary">
        {t('preferences.intro')}
      </Paragraph>

      <Alert
        type="info"
        showIcon
        message={t('preferences.alertTitle')}
        description={t('preferences.alertDescription')}
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
