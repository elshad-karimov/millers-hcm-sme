// M138 — self-service policy browse + acknowledgement.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Modal,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { CheckOutlined, FileTextOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useTranslation } from 'react-i18next'
import { policiesApi, type SelfPolicyView } from '../api/policies'

const { Title, Text, Paragraph } = Typography

export function MyPoliciesPage() {
  const { message } = AntdApp.useApp()
  // M239 — self-service policy browse; uses i18next plural rules for
  // the pendingAlert title (1 policy vs N policies).
  const { t } = useTranslation('policies')
  const [items, setItems] = useState<SelfPolicyView[]>([])
  const [loading, setLoading] = useState(true)
  const [reading, setReading] = useState<SelfPolicyView | null>(null)

  const load = () => {
    setLoading(true)
    policiesApi.myPolicies()
      .then(setItems)
      .catch((e) => message.error(e?.response?.data?.message ?? t('my.messages.loadFailed')))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line

  const ack = async (id: string) => {
    try {
      await policiesApi.acknowledge(id)
      message.success(t('my.messages.acknowledged'))
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? t('my.messages.ackFailed'),
      )
    }
  }

  const pending = useMemo(
    () => items.filter((v) => v.policy.requiresAck && !v.acknowledged),
    [items],
  )

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>{t('my.title')}</Title>

      {pending.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message={t('my.pendingAlert', { count: pending.length })}
          description={t('my.pendingDescription')}
        />
      )}

      {items.length === 0
        ? <Card><Empty description={t('my.empty')} /></Card>
        : items.map((v) => {
          const p = v.policy
          return (
            <Card
              key={p.id}
              title={
                <Space>
                  <FileTextOutlined />
                  <Text strong>{p.title}</Text>
                  <Tag>{p.category}</Tag>
                  <Text type="secondary" style={{ fontSize: 11 }}>{t('my.version', { version: p.version })}</Text>
                </Space>
              }
              size="small"
              extra={
                v.acknowledged
                  ? <Tag color="green" icon={<CheckOutlined />}>
                      {v.acknowledgedAt
                        ? t('my.ackedOn', { date: dayjs(v.acknowledgedAt).format('YYYY-MM-DD') })
                        : t('my.ackedNoDate')}
                    </Tag>
                  : p.requiresAck
                    ? <Tag color="orange">{t('my.ackRequired')}</Tag>
                    : null
              }
            >
              <Paragraph type="secondary">{p.summary ?? '—'}</Paragraph>
              <Space>
                <Button onClick={() => setReading(v)}>{t('my.actions.read')}</Button>
                {p.attachmentUrl && (p.bodyFormat === 'PDF' || p.bodyFormat === 'URL') && (
                  <Button href={p.attachmentUrl} target="_blank" rel="noreferrer">{t('my.actions.openAttachment')}</Button>
                )}
                {p.requiresAck && !v.acknowledged && (
                  <Button type="primary" onClick={() => ack(p.id)}>
                    {t('my.actions.acknowledge')}
                  </Button>
                )}
              </Space>
            </Card>
          )
        })}

      <Modal
        open={!!reading}
        title={reading?.policy.title}
        onCancel={() => setReading(null)}
        footer={
          reading?.policy.requiresAck && !reading?.acknowledged
            ? <Space>
                <Button onClick={() => setReading(null)}>{t('my.actions.close')}</Button>
                <Button type="primary" onClick={async () => {
                  if (reading) { await ack(reading.policy.id); setReading(null) }
                }}>{t('my.actions.acknowledge')}</Button>
              </Space>
            : <Button onClick={() => setReading(null)}>{t('my.actions.close')}</Button>
        }
        width={760}
      >
        {reading && (
          reading.policy.bodyFormat === 'HTML' && reading.policy.bodyText
            ? <div dangerouslySetInnerHTML={{ __html: reading.policy.bodyText }} />
            : reading.policy.bodyText
              ? <pre style={{ whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>
                  {reading.policy.bodyText}
                </pre>
              : reading.policy.attachmentUrl
                ? <Text>
                    {t('my.modal.attachmentBody')}{' '}
                    <a href={reading.policy.attachmentUrl} target="_blank" rel="noreferrer">
                      {t('my.modal.openHere')}
                    </a>.
                  </Text>
                : <Empty description={t('my.modal.noBody')} />
        )}
      </Modal>
    </Space>
  )
}
