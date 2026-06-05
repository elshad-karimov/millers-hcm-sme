// M123 — HR admin page for leave blackout windows.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import {
  leaveBlackoutsApi,
  type BlackoutResponse,
  type BlackoutRequest,
  type BlackoutScope,
  type BlackoutSeverity,
} from '../api/leaveBlackouts'
import { leaveApi, type LeaveType } from '../api/leave'
import { orgApi, type OrgUnitResponse } from '../api/org'

const { Title, Text, Paragraph } = Typography

const SCOPE_OPTIONS: { value: BlackoutScope; label: string }[] = [
  { value: 'GLOBAL', label: 'Global (everyone)' },
  { value: 'ORG_UNIT', label: 'Org unit (descendants included)' },
  { value: 'LEAVE_TYPE', label: 'Leave type only' },
]

const SEVERITY_COLOR: Record<BlackoutSeverity, string> = {
  BLOCK: 'red',
  REQUIRES_APPROVAL: 'gold',
}

export function LeaveBlackoutsPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<BlackoutResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [leaveTypes, setLeaveTypes] = useState<LeaveType[]>([])
  const [orgUnits, setOrgUnits] = useState<OrgUnitResponse[]>([])
  const [form] = Form.useForm()

  const reload = () => {
    setLoading(true)
    leaveBlackoutsApi.list()
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load blackouts'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    reload()
    leaveApi.types().then(setLeaveTypes).catch(() => {})
    // Pull org units from the active structure version so HR can scope a window.
    orgApi.active()
      .then((v) => (v ? orgApi.units(v.id) : Promise.resolve([] as OrgUnitResponse[])))
      .then(setOrgUnits)
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ scope: 'GLOBAL', severity: 'BLOCK', active: true })
    setEditingId(null)
    setModalOpen(true)
  }

  const openEdit = (row: BlackoutResponse) => {
    setEditingId(row.id)
    form.setFieldsValue({
      name: row.name,
      description: row.description,
      scope: row.scope,
      orgUnitId: row.orgUnitId ?? undefined,
      leaveTypeId: row.leaveTypeId ?? undefined,
      dateRange: [dayjs(row.startDate), dayjs(row.endDate)],
      severity: row.severity,
      reason: row.reason,
      active: row.active,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    try {
      const values = await form.validateFields()
      const [start, end] = values.dateRange ?? []
      const body: BlackoutRequest = {
        name: values.name,
        description: values.description,
        scope: values.scope,
        orgUnitId: values.scope === 'ORG_UNIT' ? values.orgUnitId : null,
        leaveTypeId: values.scope === 'LEAVE_TYPE' ? values.leaveTypeId : null,
        startDate: start.format('YYYY-MM-DD'),
        endDate: end.format('YYYY-MM-DD'),
        severity: values.severity,
        reason: values.reason,
        active: values.active,
      }
      if (editingId) {
        await leaveBlackoutsApi.update(editingId, body)
        message.success('Updated')
      } else {
        await leaveBlackoutsApi.create(body)
        message.success('Created')
      }
      setModalOpen(false)
      reload()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const onDelete = async (id: string) => {
    try {
      await leaveBlackoutsApi.delete(id)
      message.success('Deleted')
      reload()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Delete failed')
    }
  }

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      render: (name: string, r: BlackoutResponse) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          {r.description && (
            <Text type="secondary" style={{ fontSize: 11 }}>{r.description}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Range',
      render: (_: unknown, r: BlackoutResponse) => (
        <Text>{r.startDate} → {r.endDate}</Text>
      ),
      width: 200,
    },
    {
      title: 'Scope',
      render: (_: unknown, r: BlackoutResponse) => (
        <Space size={4} direction="vertical">
          <Tag>{r.scope}</Tag>
          {r.orgUnitName && <Text type="secondary" style={{ fontSize: 11 }}>{r.orgUnitName}</Text>}
          {r.leaveTypeCode && <Text type="secondary" style={{ fontSize: 11 }}>{r.leaveTypeCode}</Text>}
        </Space>
      ),
      width: 180,
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      render: (s: BlackoutSeverity) => <Tag color={SEVERITY_COLOR[s]}>{s.replace('_', ' ')}</Tag>,
      width: 150,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      render: (a: boolean) => (a ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
      width: 80,
    },
    {
      title: 'Actions',
      render: (_: unknown, r: BlackoutResponse) => (
        <Space>
          <a onClick={() => openEdit(r)}>Edit</a>
          <Popconfirm title={`Delete "${r.name}"?`} onConfirm={() => onDelete(r.id)}>
            <a style={{ color: 'red' }}>Delete</a>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const watchScope = Form.useWatch('scope', form)

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>Leave blackout windows</Title>
        <Button type="primary" onClick={openCreate}>New window</Button>
      </Space>

      <Card size="small">
        <Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 12 }}>
          Date ranges where leave requests are blocked (e.g. year-end close, retail
          holidays) or routed through stricter approval. Scope can be global, a
          specific org unit (descendants included), or a specific leave type.
        </Paragraph>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={onSave}
        okText="Save"
        title={editingId ? 'Edit blackout window' : 'New blackout window'}
        width={620}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="e.g. 2026 year-end close" />
          </Form.Item>
          <Form.Item name="description" label="Description (HR-internal)">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="scope" label="Scope" rules={[{ required: true }]}>
            <Select options={SCOPE_OPTIONS} />
          </Form.Item>
          {watchScope === 'ORG_UNIT' && (
            <Form.Item name="orgUnitId" label="Org unit" rules={[{ required: true }]}>
              <Select
                showSearch
                placeholder="Pick a unit"
                filterOption={(input, opt) =>
                  String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
                }
                options={orgUnits.map((u) => ({ value: u.id, label: u.name }))}
              />
            </Form.Item>
          )}
          {watchScope === 'LEAVE_TYPE' && (
            <Form.Item name="leaveTypeId" label="Leave type" rules={[{ required: true }]}>
              <Select
                options={leaveTypes.map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` }))}
              />
            </Form.Item>
          )}
          <Form.Item name="dateRange" label="Date range" rules={[{ required: true }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="severity" label="Severity" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'BLOCK', label: 'BLOCK — hard reject' },
                { value: 'REQUIRES_APPROVAL', label: 'REQUIRES_APPROVAL — flag for HR' },
              ]}
            />
          </Form.Item>
          <Form.Item name="reason" label="Reason shown to user (optional)">
            <Input.TextArea rows={2} placeholder="e.g. Finance month-end close" />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
