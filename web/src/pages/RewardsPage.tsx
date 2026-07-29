// M481 + M483 — Reward points & redemptions (HR admin surface)

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  rewardCatalogApi,
  rewardRedemptionApi,
  rewardBudgetApi,
  rewardWalletApi,
  REDEMPTION_STATUS_COLOR,
  type RewardCatalog,
  type RewardRedemption,
  type RewardBudget,
  type RewardCatalogRequest,
  type GrantPointsRequest,
} from '../api/rewards'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'
import { EmployeePicker } from '../components/EmployeePicker'
import { payrollApi } from '../api/payroll'

const { Title, Text } = Typography

// ─── Catalog tab ─────────────────────────────────────────────────────────────

function CatalogTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [items, setItems] = useState<RewardCatalog[]>([])
  const [loading, setLoading] = useState(true)
  const [activeOnly, setActiveOnly] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<RewardCatalog | null>(null)
  const [form] = Form.useForm<RewardCatalogRequest>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    rewardCatalogApi.list(activeOnly)
      .then(setItems)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load catalog'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [activeOnly])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ active: true, taxable: false })
    setOpen(true)
  }

  const startEdit = (item: RewardCatalog) => {
    setEditing(item)
    form.setFieldsValue({
      code: item.code,
      name: item.name,
      description: item.description,
      points: item.points,
      monetaryValue: item.monetaryValue,
      category: item.category,
      taxable: item.taxable,
      active: item.active,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    const api = editing ? rewardCatalogApi.update(editing.id, values) : rewardCatalogApi.create(values)
    api
      .then(() => {
        message.success(editing ? 'Catalog item updated' : 'Catalog item created')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to save'))
      .finally(() => setSaving(false))
  }

  const columns: ColumnsType<RewardCatalog> = [
    { title: 'Code', dataIndex: 'code', key: 'code' },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Category', dataIndex: 'category', key: 'category', render: c => c || '—' },
    { title: 'Points', dataIndex: 'points', key: 'points', align: 'right' },
    {
      title: 'Value',
      dataIndex: 'monetaryValue',
      key: 'monetaryValue',
      align: 'right',
      render: v => v ? `${Number(v).toFixed(2)}` : '—',
    },
    { title: 'Taxable', dataIndex: 'taxable', key: 'taxable', render: t => t ? 'Yes' : 'No' },
    {
      title: 'Status',
      dataIndex: 'active',
      key: 'active',
      render: a => <Tag color={a ? 'green' : 'default'}>{a ? 'Active' : 'Inactive'}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, rec) => canWrite && (
        <Button size="small" type="link" onClick={() => startEdit(rec)}>Edit</Button>
      ),
    },
  ]

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space>
              <Text>Show:</Text>
              <Select value={activeOnly ? 'active' : 'all'} onChange={v => setActiveOnly(v === 'active')} style={{ width: 120 }}>
                <Select.Option value="active">Active only</Select.Option>
                <Select.Option value="all">All items</Select.Option>
              </Select>
            </Space>
          </Col>
          {canWrite && (
            <Col>
              <Button type="primary" onClick={startCreate}>Create Item</Button>
            </Col>
          )}
        </Row>
        <Table
          dataSource={items}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Modal
        title={editing ? 'Edit Catalog Item' : 'Create Catalog Item'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        width={600}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Code" name="code" rules={[{ required: true }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item label="Name" name="name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Description" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="Points" name="points" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Monetary Value" name="monetaryValue">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Category" name="category">
            <Input />
          </Form.Item>
          <Form.Item label="Taxable" name="taxable" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ─── Redemption queue tab ────────────────────────────────────────────────────

function RedemptionQueueTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [redemptions, setRedemptions] = useState<RewardRedemption[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string | undefined>('REQUESTED')
  const [fulfillModal, setFulfillModal] = useState<string | null>(null)
  const [payrollRunId, setPayrollRunId] = useState<string>('')
  const [payrollRuns, setPayrollRuns] = useState<Array<{ id: string; runNo: string }>>([])
  const [rejectModal, setRejectModal] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  const load = () => {
    setLoading(true)
    rewardRedemptionApi.list(statusFilter)
      .then(setRedemptions)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load redemptions'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [statusFilter])

  useEffect(() => {
    payrollApi.runs().then(runs => setPayrollRuns(runs)).catch(() => {})
  }, [])

  const handleFulfill = () => {
    if (!fulfillModal || !payrollRunId) return
    rewardRedemptionApi.fulfill(fulfillModal, { payrollRunId })
      .then(() => {
        message.success('Redemption fulfilled')
        setFulfillModal(null)
        setPayrollRunId('')
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to fulfill'))
  }

  const handleReject = () => {
    if (!rejectModal || !rejectReason) return
    rewardRedemptionApi.reject(rejectModal, rejectReason)
      .then(() => {
        message.success('Redemption rejected')
        setRejectModal(null)
        setRejectReason('')
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to reject'))
  }

  const columns: ColumnsType<RewardRedemption> = [
    { title: 'No.', dataIndex: 'redemptionNo', key: 'redemptionNo' },
    { title: 'Employee', dataIndex: 'employeeId', key: 'employeeId', render: id => id.slice(0, 8) },
    { title: 'Points', dataIndex: 'points', key: 'points', align: 'right' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: s => <Tag color={REDEMPTION_STATUS_COLOR[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Requested',
      dataIndex: 'requestedAt',
      key: 'requestedAt',
      render: t => dayjs(t).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Fulfilled',
      dataIndex: 'fulfilledAt',
      key: 'fulfilledAt',
      render: t => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '—',
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, rec) => canWrite && rec.status === 'REQUESTED' && (
        <Space>
          <Button size="small" type="link" onClick={() => setFulfillModal(rec.id)}>Fulfill</Button>
          <Button size="small" type="link" danger onClick={() => setRejectModal(rec.id)}>Reject</Button>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space>
              <Text>Status:</Text>
              <Select value={statusFilter} onChange={setStatusFilter} style={{ width: 150 }} allowClear placeholder="All">
                <Select.Option value="REQUESTED">Requested</Select.Option>
                <Select.Option value="FULFILLED">Fulfilled</Select.Option>
                <Select.Option value="REJECTED">Rejected</Select.Option>
              </Select>
            </Space>
          </Col>
        </Row>
        <Table
          dataSource={redemptions}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Space>

      <Modal
        title="Fulfill Redemption"
        open={!!fulfillModal}
        onCancel={() => { setFulfillModal(null); setPayrollRunId('') }}
        onOk={handleFulfill}
        okButtonProps={{ disabled: !payrollRunId }}
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Payroll Run (optional for taxable items)" required={false}>
            <Select
              value={payrollRunId || undefined}
              onChange={setPayrollRunId}
              placeholder="Select payroll run"
              allowClear
              showSearch
              filterOption={(input, opt) => String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())}
            >
              {payrollRuns.map(r => (
                <Select.Option key={r.id} value={r.id} label={r.runNo}>{r.runNo}</Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Reject Redemption"
        open={!!rejectModal}
        onCancel={() => { setRejectModal(null); setRejectReason('') }}
        onOk={handleReject}
        okButtonProps={{ disabled: !rejectReason }}
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Reason" required>
            <Input.TextArea rows={3} value={rejectReason} onChange={e => setRejectReason(e.target.value)} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ─── Budgets tab ─────────────────────────────────────────────────────────────

function BudgetsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [budgets, setBudgets] = useState<RewardBudget[]>([])
  const [loading, setLoading] = useState(true)
  const [year, setYear] = useState(dayjs().year())
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<{ year: number; orgUnitId?: string; totalAmount: number }>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    rewardBudgetApi.list(year)
      .then(setBudgets)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load budgets'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [year])

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    rewardBudgetApi.createOrUpdate(values)
      .then(() => {
        message.success('Budget saved')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to save'))
      .finally(() => setSaving(false))
  }

  const columns: ColumnsType<RewardBudget> = [
    { title: 'Year', dataIndex: 'year', key: 'year' },
    { title: 'Org Unit', dataIndex: 'orgUnitId', key: 'orgUnitId', render: id => id || 'Global' },
    {
      title: 'Total',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      align: 'right',
      render: v => Number(v).toFixed(2),
    },
    {
      title: 'Used',
      dataIndex: 'usedAmount',
      key: 'usedAmount',
      align: 'right',
      render: v => Number(v).toFixed(2),
    },
    {
      title: 'Remaining',
      key: 'remaining',
      align: 'right',
      render: (_, rec) => (Number(rec.totalAmount) - Number(rec.usedAmount)).toFixed(2),
    },
  ]

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space>
              <Text>Year:</Text>
              <InputNumber value={year} onChange={v => setYear(v || dayjs().year())} style={{ width: 100 }} />
            </Space>
          </Col>
          {canWrite && (
            <Col>
              <Button type="primary" onClick={() => { form.resetFields(); form.setFieldsValue({ year }); setOpen(true) }}>
                Set Budget
              </Button>
            </Col>
          )}
        </Row>
        <Table
          dataSource={budgets}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
        />
      </Space>

      <Modal
        title="Set Reward Budget"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Year" name="year" rules={[{ required: true }]}>
            <InputNumber min={2020} max={2100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Org Unit (leave blank for global)" name="orgUnitId">
            <Input placeholder="Optional: org unit ID" />
          </Form.Item>
          <Form.Item label="Total Amount" name="totalAmount" rules={[{ required: true }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

// ─── Grant points tab ────────────────────────────────────────────────────────

function GrantPointsTab() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<GrantPointsRequest>()
  const [saving, setSaving] = useState(false)

  const handleGrant = async () => {
    const values = await form.validateFields()
    setSaving(true)
    rewardWalletApi.grantPoints(values)
      .then(() => {
        message.success('Points granted')
        form.resetFields()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to grant points'))
      .finally(() => setSaving(false))
  }

  return (
    <Card title="Grant Points to Employee">
      <Form form={form} layout="vertical" onFinish={handleGrant}>
        <Form.Item label="Employee" name="employeeId" rules={[{ required: true }]}>
          <EmployeePicker placeholder="Select employee" />
        </Form.Item>
        <Form.Item label="Points" name="points" rules={[{ required: true }]}>
          <InputNumber min={1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label="Note" name="note">
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item label="Org Unit (optional)" name="orgUnitId">
          <Input placeholder="Org unit ID" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={saving}>Grant Points</Button>
        </Form.Item>
      </Form>
    </Card>
  )
}

// ─── Main page ───────────────────────────────────────────────────────────────

export function RewardsPage() {
  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>Reward Points & Redemptions</Title>
      <Tabs
        items={[
          { key: 'catalog', label: 'Catalog', children: <CatalogTab /> },
          { key: 'redemptions', label: 'Redemption Queue', children: <RedemptionQueueTab /> },
          { key: 'budgets', label: 'Budgets', children: <BudgetsTab /> },
          { key: 'grant', label: 'Grant Points', children: <GrantPointsTab /> },
        ]}
      />
    </div>
  )
}
