// M120 — API keys admin page.
//
// Admin can issue, view, and revoke API keys. Plaintext is shown exactly
// once after issue, in a copy-to-clipboard modal. Subsequent reads show
// only the last4 fingerprint.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import dayjs from 'dayjs'
import {
  apiKeysApi,
  type ApiKeySummary,
  type IssueRequest,
  type UsageResponse,
} from '../api/apiKeys'

const { Title, Text, Paragraph } = Typography

const SCOPE_OPTIONS = [
  'SYSTEM_ADMIN',
  'HR_ADMIN',
  'HR_SPECIALIST',
  'AUDITOR',
  'RECRUITER',
  'DEPARTMENT_MANAGER',
  'EMPLOYEE',
  'OCCUPATIONAL_HEALTH',
  'PAYROLL_SPECIALIST',
  'FINANCE_USER',
]

export function ApiKeysPage() {
  const { message } = AntdApp.useApp()
  const [keys, setKeys] = useState<ApiKeySummary[]>([])
  const [loading, setLoading] = useState(false)
  const [issueOpen, setIssueOpen] = useState(false)
  const [revealedKey, setRevealedKey] = useState<{ summary: ApiKeySummary; plaintext: string } | null>(null)
  const [usageFor, setUsageFor] = useState<ApiKeySummary | null>(null)
  const [usage, setUsage] = useState<UsageResponse | null>(null)
  const [form] = Form.useForm()

  const reload = () => {
    setLoading(true)
    apiKeysApi.list()
      .then(setKeys)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load keys'))
      .finally(() => setLoading(false))
  }

  useEffect(reload, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!usageFor) { setUsage(null); return }
    apiKeysApi.usage(usageFor.id, 24)
      .then(setUsage)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load usage'))
  }, [usageFor, message])

  const onIssue = async () => {
    try {
      const values = await form.validateFields()
      const body: IssueRequest = {
        label: values.label,
        description: values.description,
        scopes: values.scopes,
        rateLimitPerMin: values.rateLimitPerMin,
        expiresAt: values.expiresAt ? values.expiresAt.toISOString() : null,
      }
      const result = await apiKeysApi.issue(body)
      setIssueOpen(false)
      form.resetFields()
      setRevealedKey({ summary: result.summary, plaintext: result.plaintextKey })
      reload()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Issue failed')
    }
  }

  const onRevoke = async (id: string, reason?: string) => {
    try {
      await apiKeysApi.revoke(id, reason)
      message.success('Key revoked')
      reload()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Revoke failed')
    }
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('Copied to clipboard')
  }

  const columns = [
    {
      title: 'Label',
      dataIndex: 'label',
      key: 'label',
      render: (label: string, k: ApiKeySummary) => (
        <Space direction="vertical" size={0}>
          <Text strong>{label}</Text>
          {k.description && (
            <Text type="secondary" style={{ fontSize: 11 }}>{k.description}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Key',
      key: 'fingerprint',
      render: (_: unknown, k: ApiKeySummary) => (
        <Text code style={{ fontSize: 12 }}>hcm_…{k.last4}</Text>
      ),
    },
    {
      title: 'Scopes',
      dataIndex: 'scopes',
      key: 'scopes',
      render: (scopes: string[]) => (
        <Space size={4} wrap>
          {scopes.map((s) => <Tag key={s} color="blue" style={{ fontSize: 10 }}>{s}</Tag>)}
        </Space>
      ),
    },
    {
      title: 'Rate / min',
      dataIndex: 'rateLimitPerMin',
      key: 'rateLimitPerMin',
      width: 90,
      align: 'right' as const,
    },
    {
      title: 'Status',
      key: 'status',
      render: (_: unknown, k: ApiKeySummary) => {
        if (k.revokedAt) return <Tag color="red">REVOKED</Tag>
        if (k.expiresAt && dayjs(k.expiresAt).isBefore(dayjs())) return <Tag color="orange">EXPIRED</Tag>
        if (!k.active) return <Tag color="default">INACTIVE</Tag>
        return <Tag color="green">ACTIVE</Tag>
      },
    },
    {
      title: 'Last used',
      key: 'lastUsedAt',
      render: (_: unknown, k: ApiKeySummary) => k.lastUsedAt
        ? (
          <Space direction="vertical" size={0}>
            <Text style={{ fontSize: 12 }}>{dayjs(k.lastUsedAt).format('YYYY-MM-DD HH:mm')}</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>{k.lastUsedIp ?? ''} · {k.usageCount} reqs</Text>
          </Space>
        )
        : <Text type="secondary">never</Text>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, k: ApiKeySummary) => (
        <Space>
          <a onClick={() => setUsageFor(k)}>Usage</a>
          {!k.revokedAt && (
            <Popconfirm
              title={`Revoke "${k.label}"? This is permanent.`}
              onConfirm={() => onRevoke(k.id, 'Revoked by admin')}
            >
              <a style={{ color: 'red' }}>Revoke</a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>API keys</Title>
        <Button type="primary" onClick={() => setIssueOpen(true)}>Issue key</Button>
      </Space>

      <Card size="small">
        <Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 12 }}>
          Integration credentials for machine-to-machine API access. Each key carries a
          set of role authorities and a per-minute rate limit. Revoking a key takes
          effect within seconds.
        </Paragraph>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={keys}
          pagination={{ pageSize: 20 }}
          size="small"
        />
      </Card>

      {/* ── Issue modal ─────────────────────────────────────────── */}
      <Modal
        open={issueOpen}
        onCancel={() => { setIssueOpen(false); form.resetFields() }}
        onOk={onIssue}
        okText="Issue"
        title="Issue new API key"
        width={560}
      >
        <Form form={form} layout="vertical" initialValues={{ rateLimitPerMin: 60 }}>
          <Form.Item
            name="label"
            label="Label"
            rules={[{ required: true, message: 'Label is required' }]}
          >
            <Input placeholder="e.g. Payroll BI extract" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name="scopes"
            label="Scopes (role authorities granted)"
            rules={[{ required: true, message: 'At least one scope' }]}
          >
            <Select
              mode="multiple"
              placeholder="Pick roles this key will authenticate as"
              options={SCOPE_OPTIONS.map((s) => ({ value: s, label: s }))}
            />
          </Form.Item>
          <Form.Item name="rateLimitPerMin" label="Rate limit (requests / minute)">
            <InputNumber min={1} max={10000} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="expiresAt" label="Expiry (optional)">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ── Reveal modal — plaintext shown ONCE ────────────────── */}
      <Modal
        open={!!revealedKey}
        onCancel={() => setRevealedKey(null)}
        onOk={() => setRevealedKey(null)}
        okText="I've copied it"
        cancelButtonProps={{ style: { display: 'none' } }}
        title="Your new API key"
        width={620}
        maskClosable={false}
      >
        {revealedKey && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Alert
              type="warning"
              showIcon
              message="This is the only time the plaintext key will be shown."
              description="Store it in your secret manager now. It cannot be retrieved later — if you lose it, revoke this key and issue a new one."
            />
            <div style={{
              background: '#fafafa', border: '1px solid #d9d9d9', borderRadius: 4,
              padding: 12, fontFamily: 'monospace', fontSize: 13,
              wordBreak: 'break-all',
            }}>
              {revealedKey.plaintext}
            </div>
            <Button type="primary" onClick={() => copyToClipboard(revealedKey.plaintext)}>
              Copy to clipboard
            </Button>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Use it via header: <Text code>X-API-Key: {revealedKey.plaintext.slice(0, 8)}…{revealedKey.summary.last4}</Text>
            </Text>
          </Space>
        )}
      </Modal>

      {/* ── Usage drawer ─────────────────────────────────────────── */}
      <Drawer
        open={!!usageFor}
        onClose={() => setUsageFor(null)}
        title={usageFor ? `Usage — ${usageFor.label}` : ''}
        width={680}
      >
        {usage && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large">
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>REQUESTS (24h)</Text>
                <div><Text strong style={{ fontSize: 18 }}>{usage.totalRequests.toLocaleString()}</Text></div>
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>RATE-LIMITED</Text>
                <div>
                  <Text strong style={{ fontSize: 18, color: usage.totalRejected ? '#cf1322' : undefined }}>
                    {usage.totalRejected.toLocaleString()}
                  </Text>
                </div>
              </div>
            </Space>

            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={[...usage.buckets].reverse().map(b => ({
                t: dayjs(b.minuteBucket).format('HH:mm'),
                accepted: b.requestCount,
                rejected: b.rejectedCount,
              }))}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="t" />
                <YAxis />
                <Tooltip />
                <Line type="monotone" dataKey="accepted" stroke="#1677ff" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="rejected" stroke="#cf1322" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>

            {usage.buckets.length === 0 && (
              <Text type="secondary">No traffic in the last 24 hours.</Text>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  )
}
