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
import { policiesApi, type SelfPolicyView } from '../api/policies'

const { Title, Text, Paragraph } = Typography

export function MyPoliciesPage() {
  const { message } = AntdApp.useApp()
  const [items, setItems] = useState<SelfPolicyView[]>([])
  const [loading, setLoading] = useState(true)
  const [reading, setReading] = useState<SelfPolicyView | null>(null)

  const load = () => {
    setLoading(true)
    policiesApi.myPolicies()
      .then(setItems)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load policies'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line

  const ack = async (id: string) => {
    try {
      await policiesApi.acknowledge(id)
      message.success('Acknowledged')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Ack failed',
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
      <Title level={3} style={{ margin: 0 }}>Company policies</Title>

      {pending.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`${pending.length} polic${pending.length === 1 ? 'y' : 'ies'} need your acknowledgement`}
          description="Tap the policy title to read it, then press Acknowledge."
        />
      )}

      {items.length === 0
        ? <Card><Empty description="No published policies yet" /></Card>
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
                  <Text type="secondary" style={{ fontSize: 11 }}>v{p.version}</Text>
                </Space>
              }
              size="small"
              extra={
                v.acknowledged
                  ? <Tag color="green" icon={<CheckOutlined />}>
                      Acked {v.acknowledgedAt && dayjs(v.acknowledgedAt).format('YYYY-MM-DD')}
                    </Tag>
                  : p.requiresAck
                    ? <Tag color="orange">Ack required</Tag>
                    : null
              }
            >
              <Paragraph type="secondary">{p.summary ?? '—'}</Paragraph>
              <Space>
                <Button onClick={() => setReading(v)}>Read</Button>
                {p.attachmentUrl && (p.bodyFormat === 'PDF' || p.bodyFormat === 'URL') && (
                  <Button href={p.attachmentUrl} target="_blank" rel="noreferrer">Open attachment</Button>
                )}
                {p.requiresAck && !v.acknowledged && (
                  <Button type="primary" onClick={() => ack(p.id)}>
                    Acknowledge
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
                <Button onClick={() => setReading(null)}>Close</Button>
                <Button type="primary" onClick={async () => {
                  if (reading) { await ack(reading.policy.id); setReading(null) }
                }}>Acknowledge</Button>
              </Space>
            : <Button onClick={() => setReading(null)}>Close</Button>
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
                    Body is published as an attachment.{' '}
                    <a href={reading.policy.attachmentUrl} target="_blank" rel="noreferrer">
                      Open here
                    </a>.
                  </Text>
                : <Empty description="No body content" />
        )}
      </Modal>
    </Space>
  )
}
