// M138 — HR admin surface for the policy library.
//
// HR drafts versioned policy documents, publishes them, and watches the
// pending-acknowledgement list per policy. Once a row is PUBLISHED the
// only allowed mutation is the ARCHIVE transition; new content requires
// a new draft (next version), preserving an immutable audit trail.

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import {
  policiesApi,
  type AcknowledgementResponse,
  type PolicyBodyFormat,
  type PolicyRequest,
  type PolicyResponse,
  type PolicyStatus,
} from '../api/policies'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text, Paragraph } = Typography

const STATUS_COLOR: Record<PolicyStatus, string> = {
  DRAFT: 'gold',
  PUBLISHED: 'green',
  ARCHIVED: 'default',
}

const BODY_FORMATS: PolicyBodyFormat[] = ['MARKDOWN', 'HTML', 'PDF', 'URL']
const CATEGORY_SUGGESTIONS = [
  'CODE_OF_CONDUCT', 'HR_POLICY', 'SECURITY', 'IT', 'COMPLIANCE',
  'HEALTH_SAFETY', 'EXPENSE', 'TRAVEL', 'BENEFITS', 'GENERAL',
]

export function PoliciesAdminPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [policies, setPolicies] = useState<PolicyResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PolicyResponse | null>(null)
  const [form] = Form.useForm<{
    code: string
    title: string
    summary?: string
    category: string
    bodyFormat: PolicyBodyFormat
    bodyText?: string
    attachmentUrl?: string
    window?: [ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]
    requiresAck: boolean
  }>()

  const [acksOpen, setAcksOpen] = useState<PolicyResponse | null>(null)
  const [acks, setAcks] = useState<AcknowledgementResponse[]>([])

  const load = () => {
    setLoading(true)
    policiesApi.list()
      .then(setPolicies)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load policies'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ bodyFormat: 'MARKDOWN', requiresAck: false })
    setOpen(true)
  }

  const startEdit = (p: PolicyResponse) => {
    setEditing(p)
    form.setFieldsValue({
      code: p.code,
      title: p.title,
      summary: p.summary ?? undefined,
      category: p.category,
      bodyFormat: p.bodyFormat,
      bodyText: p.bodyText ?? undefined,
      attachmentUrl: p.attachmentUrl ?? undefined,
      window: p.effectiveFrom
        ? [dayjs(p.effectiveFrom), p.effectiveTo ? dayjs(p.effectiveTo) : dayjs(p.effectiveFrom).add(1, 'year')]
        : undefined,
      requiresAck: p.requiresAck,
    })
    setOpen(true)
  }

  const submit = async () => {
    const v = await form.validateFields()
    const req: PolicyRequest = {
      code: v.code,
      title: v.title,
      summary: v.summary,
      category: v.category,
      bodyFormat: v.bodyFormat,
      bodyText: v.bodyText,
      attachmentUrl: v.attachmentUrl,
      effectiveFrom: v.window?.[0]?.format('YYYY-MM-DD'),
      effectiveTo: v.window?.[1]?.format('YYYY-MM-DD'),
      requiresAck: v.requiresAck,
    }
    try {
      if (editing) {
        await policiesApi.update(editing.id, req)
        message.success('Draft updated')
      } else {
        await policiesApi.create(req)
        message.success('Draft created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    }
  }

  const transition = async (p: PolicyResponse, target: PolicyStatus) => {
    try {
      await policiesApi.changeStatus(p.id, target)
      message.success(`Moved to ${target}`)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Transition failed',
      )
    }
  }

  const openAcks = async (p: PolicyResponse) => {
    setAcksOpen(p)
    try {
      const a = await policiesApi.acknowledgements(p.id)
      setAcks(a)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to load acknowledgements',
      )
    }
  }

  const cols: ColumnsType<PolicyResponse> = useMemo(() => [
    {
      title: 'Code / version', width: 220,
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.code}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>v{r.version}</Text>
        </Space>
      ),
    },
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Category', dataIndex: 'category', width: 160,
      render: (c: string) => <Tag>{c}</Tag>,
    },
    {
      title: 'Status', dataIndex: 'status', width: 110,
      render: (s: PolicyStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Ack required', dataIndex: 'requiresAck', width: 110, align: 'center',
      render: (v: boolean) => v ? <Tag color="blue">YES</Tag> : '—',
    },
    {
      title: '', width: 280, align: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" onClick={() => openAcks(r)}>Acks</Button>
          {canWrite && r.status === 'DRAFT' && (
            <>
              <Button size="small" onClick={() => startEdit(r)}>Edit</Button>
              <Popconfirm title="Publish this draft?" onConfirm={() => transition(r, 'PUBLISHED')}>
                <Button size="small" type="primary">Publish</Button>
              </Popconfirm>
            </>
          )}
          {canWrite && r.status === 'PUBLISHED' && (
            <Popconfirm title="Archive this policy? Employees stop seeing it on browse." onConfirm={() => transition(r, 'ARCHIVED')}>
              <Button size="small" danger>Archive</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ], [canWrite]) // eslint-disable-line

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Company policies</Title>
        {canWrite && <Button type="primary" onClick={startCreate}>New policy…</Button>}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={policies}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No policies yet" /> }}
        />
      </Card>

      <Alert
        type="info" showIcon
        message="Versioning"
        description={
          <>
            New drafts auto-increment versions by code. Once a policy is PUBLISHED the only
            allowed change is ARCHIVE. Editing the body requires a new draft (next version).
            See the self-service surface at <Link to="/self/policies">/self/policies</Link>.
          </>
        }
      />

      <Modal
        open={open}
        title={editing ? `Edit draft — ${editing.code} v${editing.version}` : 'New policy draft'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        okText={editing ? 'Save' : 'Create'}
        width={760}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 80 }]}>
                <Input placeholder="CODE-OF-CONDUCT" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={14}>
              <Form.Item name="title" label="Title" rules={[{ required: true, max: 240 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="summary" label="One-line summary">
            <Input maxLength={500} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item name="category" label="Category" rules={[{ required: true }]}>
                <Select
                  showSearch
                  options={CATEGORY_SUGGESTIONS.map((c) => ({ value: c, label: c }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="bodyFormat" label="Body format" rules={[{ required: true }]}>
                <Select options={BODY_FORMATS.map((c) => ({ value: c, label: c }))} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="requiresAck" label="Ack required" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="bodyText" label="Body (MARKDOWN / HTML)">
            <Input.TextArea rows={8} />
          </Form.Item>
          <Form.Item name="attachmentUrl" label="Attachment URL (PDF / URL)">
            <Input placeholder="https://docs.example.com/code-of-conduct-v2.pdf" />
          </Form.Item>
          <Form.Item name="window" label="Effective window">
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!acksOpen}
        title={acksOpen ? `Acknowledgements — ${acksOpen.code} v${acksOpen.version}` : ''}
        onCancel={() => { setAcksOpen(null); setAcks([]) }}
        footer={null}
        width={640}
      >
        {acksOpen && (
          acks.length === 0
            ? <Empty description="No acknowledgements yet" />
            : <Table
                rowKey="id"
                size="small"
                pagination={false}
                columns={[
                  { title: 'Employee', dataIndex: 'employeeId',
                    render: (v: string) => (
                      <Tooltip title={v}>
                        <Text style={{ fontFamily: 'monospace' }}>{v.slice(0, 8)}</Text>
                      </Tooltip>
                    ),
                  },
                  { title: 'Version', dataIndex: 'versionAcknowledged', width: 80, align: 'right' },
                  { title: 'When', dataIndex: 'acknowledgedAt',
                    render: (s: string) => dayjs(s).format('YYYY-MM-DD HH:mm') },
                  { title: 'IP', dataIndex: 'acknowledgedIp', render: (v?: string | null) => v ?? '—' },
                ]}
                dataSource={acks}
              />
        )}
        {acksOpen?.bodyText && (
          <>
            <Paragraph type="secondary" style={{ marginTop: 16, fontSize: 12 }}>Body preview:</Paragraph>
            <pre style={{ background: '#fafafa', padding: 8, maxHeight: 200, overflow: 'auto' }}>
              {acksOpen.bodyText}
            </pre>
          </>
        )}
      </Modal>
    </Space>
  )
}
