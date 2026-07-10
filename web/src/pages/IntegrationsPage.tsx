// M492 — Integration config registry. SYSTEM_ADMIN configures external
// integrations (webhooks, file feeds, API connectors). Endpoint URLs are
// encrypted; credentials stored by reference only (never the actual secret).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Drawer,
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
import dayjs from 'dayjs'
import { api } from '../api/client'

const { Title, Text } = Typography

type IntegrationDirection = 'INBOUND' | 'OUTBOUND'
type IntegrationType = 'WEBHOOK' | 'FILE' | 'API'
type IntegrationStatus = 'SUCCESS' | 'FAILED'

interface IntegrationConfig {
  id: string
  code: string
  name: string
  direction: IntegrationDirection
  type: IntegrationType
  endpointUrl: string | null
  credentialsRef: string | null
  enabled: boolean
  notes: string | null
  lastRunAt: string | null
  lastRunStatus: IntegrationStatus | null
  createdAt: string
  updatedAt: string
}

interface IntegrationLog {
  id: string
  configId: string
  runAt: string
  status: IntegrationStatus
  recordsProcessed: number | null
  errorMessage: string | null
}

const DIR_COLOR: Record<IntegrationDirection, string> = {
  INBOUND: 'blue',
  OUTBOUND: 'green',
}

const STATUS_COLOR: Record<IntegrationStatus, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
}

export function IntegrationsPage() {
  const { message } = AntdApp.useApp()
  const [configs, setConfigs] = useState<IntegrationConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<IntegrationConfig | null>(null)
  const [form] = Form.useForm()

  const [logsDrawer, setLogsDrawer] = useState<IntegrationConfig | null>(null)
  const [logs, setLogs] = useState<IntegrationLog[]>([])
  const [loadingLogs, setLoadingLogs] = useState(false)

  const [failures, setFailures] = useState<IntegrationLog[]>([])
  const [loadingFailures, setLoadingFailures] = useState(false)

  useEffect(() => {
    load()
  }, [])

  const load = () => {
    setLoading(true)
    api
      .get<IntegrationConfig[]>('/admin/integrations')
      .then((r) => setConfigs(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load integrations'))
      .finally(() => setLoading(false))
  }

  const loadFailures = () => {
    setLoadingFailures(true)
    api
      .get<IntegrationLog[]>('/admin/integrations/failures', { params: { limit: 100 } })
      .then((r) => setFailures(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load failures'))
      .finally(() => setLoadingFailures(false))
  }

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ direction: 'INBOUND', type: 'API', enabled: true })
    setOpen(true)
  }

  const startEdit = (c: IntegrationConfig) => {
    setEditing(c)
    form.setFieldsValue({
      name: c.name,
      direction: c.direction,
      type: c.type,
      endpointUrl: c.endpointUrl,
      credentialsRef: c.credentialsRef,
      enabled: c.enabled,
      notes: c.notes,
    })
    setOpen(true)
  }

  const submit = async () => {
    try {
      const values = await form.validateFields()
      if (editing) {
        await api.put(`/admin/integrations/${editing.id}`, values)
        message.success('Integration updated')
      } else {
        await api.post('/admin/integrations', {
          code: values.code,
          name: values.name,
          direction: values.direction,
          type: values.type,
          endpointUrl: values.endpointUrl,
          credentialsRef: values.credentialsRef,
          notes: values.notes,
        })
        message.success('Integration created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to save')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await api.delete(`/admin/integrations/${id}`)
      message.success('Integration deleted')
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to delete')
    }
  }

  const openLogs = async (c: IntegrationConfig) => {
    setLogsDrawer(c)
    setLoadingLogs(true)
    try {
      const r = await api.get<IntegrationLog[]>(`/admin/integrations/${c.id}/logs`, {
        params: { limit: 100 },
      })
      setLogs(r.data)
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to load logs')
    } finally {
      setLoadingLogs(false)
    }
  }

  const toggleEnabled = async (c: IntegrationConfig) => {
    try {
      await api.put(`/admin/integrations/${c.id}`, {
        name: c.name,
        direction: c.direction,
        type: c.type,
        endpointUrl: c.endpointUrl,
        credentialsRef: c.credentialsRef,
        enabled: !c.enabled,
        notes: c.notes,
      })
      message.success(c.enabled ? 'Integration disabled' : 'Integration enabled')
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to toggle')
    }
  }

  const cols: ColumnsType<IntegrationConfig> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 180,
      render: (c: string) => <Text code>{c}</Text>,
    },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Direction',
      dataIndex: 'direction',
      width: 120,
      render: (d: IntegrationDirection) => <Tag color={DIR_COLOR[d]}>{d}</Tag>,
    },
    {
      title: 'Type',
      dataIndex: 'type',
      width: 100,
      render: (t: IntegrationType) => <Tag>{t}</Tag>,
    },
    {
      title: 'Enabled',
      dataIndex: 'enabled',
      width: 100,
      align: 'center',
      render: (v: boolean, r) => (
        <Switch checked={v} size="small" onChange={() => toggleEnabled(r)} />
      ),
    },
    {
      title: 'Last run',
      width: 180,
      render: (_, r) =>
        r.lastRunAt ? (
          <Space direction="vertical" size={0}>
            <Text style={{ fontSize: 12 }}>{dayjs(r.lastRunAt).format('YYYY-MM-DD HH:mm')}</Text>
            {r.lastRunStatus && <Tag color={STATUS_COLOR[r.lastRunStatus]}>{r.lastRunStatus}</Tag>}
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: '',
      width: 200,
      align: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" onClick={() => openLogs(r)}>
            Logs
          </Button>
          <Button size="small" onClick={() => startEdit(r)}>
            Edit
          </Button>
          <Popconfirm title="Delete this integration?" onConfirm={() => handleDelete(r.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const logCols: ColumnsType<IntegrationLog> = [
    {
      title: 'Run time',
      dataIndex: 'runAt',
      render: (s: string) => dayjs(s).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (s: IntegrationStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Records',
      dataIndex: 'recordsProcessed',
      width: 100,
      align: 'right',
      render: (v: number | null) => v ?? '—',
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
      <Title level={3} style={{ margin: 0 }}>Integrations</Title>

      <Tabs
        defaultActiveKey="configs"
        items={[
          {
            key: 'configs',
            label: 'Configurations',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  <Button type="primary" onClick={startCreate}>
                    New integration
                  </Button>
                </Space>
                <Card>
                  <Table
                    rowKey="id"
                    columns={cols}
                    dataSource={configs}
                    size="small"
                    pagination={{ pageSize: 25 }}
                    locale={{ emptyText: <Empty description="No integrations configured" /> }}
                  />
                </Card>
              </Space>
            ),
          },
          {
            key: 'failures',
            label: 'Recent failures',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Button onClick={loadFailures} loading={loadingFailures}>
                  Load failures
                </Button>
                <Card>
                  <Table
                    rowKey="id"
                    columns={logCols}
                    dataSource={failures}
                    size="small"
                    pagination={{ pageSize: 50 }}
                    locale={{ emptyText: <Empty description="No recent failures" /> }}
                  />
                </Card>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        open={open}
        title={editing ? `Edit integration — ${editing.code}` : 'New integration'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        width={640}
      >
        <Form form={form} layout="vertical">
          {!editing && (
            <Form.Item name="code" label="Code" rules={[{ required: true, max: 80 }]}>
              <Input placeholder="ERP-PAYROLL-POST" />
            </Form.Item>
          )}
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 240 }]}>
            <Input placeholder="ERP payroll posting" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="direction" label="Direction" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'INBOUND', label: 'Inbound' },
                    { value: 'OUTBOUND', label: 'Outbound' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="type" label="Type" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'WEBHOOK', label: 'Webhook' },
                    { value: 'FILE', label: 'File' },
                    { value: 'API', label: 'API' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="endpointUrl" label="Endpoint URL">
            <Input.Password
              placeholder="https://erp.example.com/api/payroll"
              autoComplete="off"
            />
          </Form.Item>
          <Form.Item
            name="credentialsRef"
            label="Credentials reference"
            extra="Reference name only (e.g., ERP-API-KEY) — never store the actual secret here"
          >
            <Input placeholder="ERP-API-KEY" />
          </Form.Item>
          {editing && (
            <Form.Item name="enabled" label="Enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={!!logsDrawer}
        title={logsDrawer ? `Logs — ${logsDrawer.name}` : ''}
        onClose={() => {
          setLogsDrawer(null)
          setLogs([])
        }}
        width={720}
      >
        {loadingLogs && <Spin />}
        {!loadingLogs && (
          <Table
            rowKey="id"
            columns={logCols}
            dataSource={logs}
            size="small"
            pagination={{ pageSize: 50 }}
            locale={{ emptyText: <Empty description="No logs yet" /> }}
          />
        )}
      </Drawer>
    </Space>
  )
}
