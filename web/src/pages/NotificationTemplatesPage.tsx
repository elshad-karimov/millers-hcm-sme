// M493 — Notification templates + delivery logs. HR_ADMIN manages email/in-app
// notification templates with variable substitution (e.g., {{employeeName}}).
// Delivery logs track sent vs failed notifications.

import { useEffect, useState } from 'react'
import {
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
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { Dayjs } from 'dayjs'
import { api } from '../api/client'

const { Title, Text } = Typography

type NotificationChannel = 'EMAIL' | 'PUSH' | 'IN_APP'
type DeliveryStatus = 'SENT' | 'FAILED'

interface NotificationTemplate {
  id: string
  code: string
  name: string
  channel: NotificationChannel
  subjectTemplate: string | null
  bodyTemplate: string
  active: boolean
  createdAt: string
  updatedAt: string
}

interface DeliveryLog {
  id: string
  channel: NotificationChannel
  recipientId: string
  subject: string | null
  body: string
  status: DeliveryStatus
  errorMessage: string | null
  sentAt: string
}

const CHANNEL_COLOR: Record<NotificationChannel, string> = {
  EMAIL: 'blue',
  PUSH: 'purple',
  IN_APP: 'green',
}

const STATUS_COLOR: Record<DeliveryStatus, string> = {
  SENT: 'green',
  FAILED: 'red',
}

export function NotificationTemplatesPage() {
  const { message } = AntdApp.useApp()
  const [templates, setTemplates] = useState<NotificationTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<NotificationTemplate | null>(null)
  const [form] = Form.useForm()

  const [logs, setLogs] = useState<DeliveryLog[]>([])
  const [loadingLogs, setLoadingLogs] = useState(false)
  const [filterChannel, setFilterChannel] = useState<NotificationChannel | undefined>()
  const [filterStatus, setFilterStatus] = useState<DeliveryStatus | undefined>()
  const [filterRange, setFilterRange] = useState<[Dayjs, Dayjs] | null>(null)

  useEffect(() => {
    load()
  }, [])

  const load = () => {
    setLoading(true)
    api
      .get<NotificationTemplate[]>('/notifications/templates')
      .then((r) => setTemplates(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load templates'))
      .finally(() => setLoading(false))
  }

  const loadLogs = () => {
    setLoadingLogs(true)
    const params: Record<string, unknown> = { limit: 100 }
    if (filterChannel) params.channel = filterChannel
    if (filterStatus) params.status = filterStatus
    if (filterRange) {
      params.from = filterRange[0].toISOString()
      params.to = filterRange[1].toISOString()
    }
    api
      .get<DeliveryLog[]>('/notifications/templates/delivery-logs', { params })
      .then((r) => setLogs(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load logs'))
      .finally(() => setLoadingLogs(false))
  }

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ channel: 'EMAIL', active: true })
    setOpen(true)
  }

  const startEdit = (t: NotificationTemplate) => {
    setEditing(t)
    form.setFieldsValue({
      name: t.name,
      channel: t.channel,
      subjectTemplate: t.subjectTemplate,
      bodyTemplate: t.bodyTemplate,
      active: t.active,
    })
    setOpen(true)
  }

  const submit = async () => {
    try {
      const values = await form.validateFields()
      if (editing) {
        await api.put(`/notifications/templates/${editing.id}`, values)
        message.success('Template updated')
      } else {
        await api.post('/notifications/templates', {
          code: values.code,
          name: values.name,
          channel: values.channel,
          subjectTemplate: values.subjectTemplate,
          bodyTemplate: values.bodyTemplate,
        })
        message.success('Template created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to save')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await api.delete(`/notifications/templates/${id}`)
      message.success('Template deleted')
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to delete')
    }
  }

  const cols: ColumnsType<NotificationTemplate> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 200,
      render: (c: string) => <Text code>{c}</Text>,
    },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Channel',
      dataIndex: 'channel',
      width: 120,
      render: (c: NotificationChannel) => <Tag color={CHANNEL_COLOR[c]}>{c}</Tag>,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 100,
      align: 'center',
      render: (v: boolean) => (v ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
    },
    {
      title: '',
      width: 200,
      align: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" onClick={() => startEdit(r)}>
            Edit
          </Button>
          <Popconfirm title="Delete this template?" onConfirm={() => handleDelete(r.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const logCols: ColumnsType<DeliveryLog> = [
    {
      title: 'Sent at',
      dataIndex: 'sentAt',
      width: 180,
      render: (s: string) => dayjs(s).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: 'Channel',
      dataIndex: 'channel',
      width: 100,
      render: (c: NotificationChannel) => <Tag color={CHANNEL_COLOR[c]}>{c}</Tag>,
    },
    {
      title: 'Recipient',
      dataIndex: 'recipientId',
      width: 120,
      render: (v: string) => <Text style={{ fontFamily: 'monospace' }}>{v.slice(0, 8)}</Text>,
    },
    { title: 'Subject', dataIndex: 'subject', render: (v: string | null) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (s: DeliveryStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Error',
      dataIndex: 'errorMessage',
      render: (v: string | null) => (v ? <Text type="danger">{v}</Text> : '—'),
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Notification templates</Title>

      <Tabs
        defaultActiveKey="templates"
        items={[
          {
            key: 'templates',
            label: 'Templates',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  <Button type="primary" onClick={startCreate}>
                    New template
                  </Button>
                </Space>
                <Card>
                  <Table
                    rowKey="id"
                    columns={cols}
                    dataSource={templates}
                    size="small"
                    pagination={{ pageSize: 25 }}
                    locale={{ emptyText: <Empty description="No templates yet" /> }}
                  />
                </Card>
                <Card size="small">
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Use variables in templates: e.g., <Text code>{'{{employeeName}}'}</Text>,{' '}
                    <Text code>{'{{requestNo}}'}</Text>. The system replaces them at send-time.
                  </Text>
                </Card>
              </Space>
            ),
          },
          {
            key: 'logs',
            label: 'Delivery logs',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Card size="small">
                  <Space wrap>
                    <Select
                      placeholder="Channel"
                      allowClear
                      style={{ width: 120 }}
                      value={filterChannel}
                      onChange={setFilterChannel}
                      options={[
                        { value: 'EMAIL', label: 'EMAIL' },
                        { value: 'PUSH', label: 'PUSH' },
                        { value: 'IN_APP', label: 'IN_APP' },
                      ]}
                    />
                    <Select
                      placeholder="Status"
                      allowClear
                      style={{ width: 120 }}
                      value={filterStatus}
                      onChange={setFilterStatus}
                      options={[
                        { value: 'SENT', label: 'SENT' },
                        { value: 'FAILED', label: 'FAILED' },
                      ]}
                    />
                    <DatePicker.RangePicker
                      value={filterRange}
                      onChange={(v) => setFilterRange(v as [Dayjs, Dayjs] | null)}
                    />
                    <Button type="primary" onClick={loadLogs} loading={loadingLogs}>
                      Load logs
                    </Button>
                  </Space>
                </Card>
                <Card>
                  <Table
                    rowKey="id"
                    columns={logCols}
                    dataSource={logs}
                    size="small"
                    pagination={{ pageSize: 50 }}
                    locale={{ emptyText: <Empty description="No logs loaded" /> }}
                  />
                </Card>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        open={open}
        title={editing ? `Edit template — ${editing.code}` : 'New notification template'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        width={720}
      >
        <Form form={form} layout="vertical">
          {!editing && (
            <Form.Item name="code" label="Code" rules={[{ required: true, max: 80 }]}>
              <Input placeholder="LEAVE-REQUEST-APPROVED" />
            </Form.Item>
          )}
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 240 }]}>
                <Input placeholder="Leave request approved notification" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="channel" label="Channel" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'EMAIL', label: 'Email' },
                    { value: 'PUSH', label: 'Push' },
                    { value: 'IN_APP', label: 'In-app' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="subjectTemplate" label="Subject (email only)">
            <Input placeholder="Your leave request {{requestNo}} has been approved" />
          </Form.Item>
          <Form.Item name="bodyTemplate" label="Body" rules={[{ required: true }]}>
            <Input.TextArea
              rows={6}
              placeholder={'Hello {{employeeName}},\n\nYour leave request has been approved...'}
            />
          </Form.Item>
          {editing && (
            <Form.Item name="active" label="Active" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Space>
  )
}
