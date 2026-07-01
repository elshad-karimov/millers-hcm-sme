import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
  Alert,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  compensationApi,
  type IncentivePlanDto,
  type IncentivePlanRequest,
  type IncentiveMeasure,
  type IncentivePayoutDto,
  type IncentivePayoutStatus,
  type CreateIncentivePayoutRequest,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const PAYOUT_STATUS_COLOR: Record<IncentivePayoutStatus, string> = {
  DRAFT: 'default',
  APPROVED: 'cyan',
  PAID: 'green',
  CANCELLED: 'default',
}

interface PlanFormValues {
  code: string
  name: string
  measure: IncentiveMeasure
  targetPct: number
  thresholdAchievement: number
  targetAchievement: number
  capAchievement: number
  maxPayoutPct: number
  currency: string
  active: boolean
}

interface PayoutFormValues {
  planId: string
  employeeId: string
  period: string
  achievementPct: number
}

export function IncentivePlansPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [activeTab, setActiveTab] = useState<'plans' | 'payouts'>('plans')

  // Plans
  const [plans, setPlans] = useState<IncentivePlanDto[]>([])
  const [loadingPlans, setLoadingPlans] = useState(false)
  const [planDrawerOpen, setPlanDrawerOpen] = useState(false)
  const [editingPlan, setEditingPlan] = useState<IncentivePlanDto | null>(null)
  const [planForm] = Form.useForm<PlanFormValues>()

  // Payouts
  const [payouts, setPayouts] = useState<IncentivePayoutDto[]>([])
  const [loadingPayouts, setLoadingPayouts] = useState(false)
  const [payoutModalOpen, setPayoutModalOpen] = useState(false)
  const [payoutForm] = Form.useForm<PayoutFormValues>()
  const [statusFilter, setStatusFilter] = useState<IncentivePayoutStatus | undefined>()

  const [employees, setEmployees] = useState<Employee[]>([])
  const [loadingEmployees, setLoadingEmployees] = useState(false)

  const loadPlans = () => {
    setLoadingPlans(true)
    compensationApi
      .listIncentivePlans()
      .then(setPlans)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load incentive plans'),
      )
      .finally(() => setLoadingPlans(false))
  }

  const loadPayouts = () => {
    setLoadingPayouts(true)
    compensationApi
      .listIncentivePayouts({ status: statusFilter })
      .then(setPayouts)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load incentive payouts'),
      )
      .finally(() => setLoadingPayouts(false))
  }

  useEffect(() => {
    loadPlans()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (activeTab === 'payouts') {
      loadPayouts()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, statusFilter])

  // Plan actions
  const openCreatePlan = () => {
    setEditingPlan(null)
    planForm.resetFields()
    planForm.setFieldsValue({ active: true, currency: 'AZN' })
    setPlanDrawerOpen(true)
  }

  const openEditPlan = (plan: IncentivePlanDto) => {
    setEditingPlan(plan)
    planForm.setFieldsValue(plan)
    setPlanDrawerOpen(true)
  }

  const submitPlan = async (v: PlanFormValues) => {
    const payload: IncentivePlanRequest = {
      code: v.code,
      name: v.name,
      measure: v.measure,
      targetPct: v.targetPct,
      thresholdAchievement: v.thresholdAchievement,
      targetAchievement: v.targetAchievement,
      capAchievement: v.capAchievement,
      maxPayoutPct: v.maxPayoutPct,
      currency: v.currency,
      active: v.active,
    }

    try {
      if (editingPlan) {
        await compensationApi.updateIncentivePlan(editingPlan.id, payload)
        message.success('Incentive plan updated')
      } else {
        await compensationApi.createIncentivePlan(payload)
        message.success('Incentive plan created')
      }
      setPlanDrawerOpen(false)
      loadPlans()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const handleDeactivatePlan = (plan: IncentivePlanDto) => {
    modal.confirm({
      title: 'Deactivate Incentive Plan?',
      content: `Deactivate ${plan.code} — ${plan.name}?`,
      okText: 'Deactivate',
      onOk: async () => {
        try {
          await compensationApi.deactivateIncentivePlan(plan.id)
          message.success('Incentive plan deactivated')
          loadPlans()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Deactivate failed',
          )
        }
      },
    })
  }

  // Payout actions
  const handleSearchEmployees = (search: string) => {
    if (search.length < 2) {
      setEmployees([])
      return
    }
    setLoadingEmployees(true)
    employeesApi
      .list({ search, size: 50 })
      .then((resp) => setEmployees(resp.content))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to search employees'),
      )
      .finally(() => setLoadingEmployees(false))
  }

  const openCreatePayout = () => {
    payoutForm.resetFields()
    setPayoutModalOpen(true)
  }

  const submitPayout = async (v: PayoutFormValues) => {
    const payload: CreateIncentivePayoutRequest = {
      planId: v.planId,
      employeeId: v.employeeId,
      period: v.period,
      achievementPct: v.achievementPct,
    }

    try {
      await compensationApi.createIncentivePayout(payload)
      message.success('Incentive payout created (DRAFT)')
      setPayoutModalOpen(false)
      loadPayouts()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Create failed',
      )
    }
  }

  const handleApprovePayout = (payout: IncentivePayoutDto) => {
    modal.confirm({
      title: 'Approve Incentive Payout?',
      content: `Approve payout of ${payout.payoutAmount.toLocaleString()} for ${payout.employeeName ?? payout.employeeId}?`,
      okText: 'Approve',
      onOk: async () => {
        try {
          await compensationApi.approveIncentivePayout(payout.id)
          message.success('Incentive payout approved. It will be transferred to payroll in Phase E.')
          loadPayouts()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Approve failed',
          )
        }
      },
    })
  }

  const handleCancelPayout = (payout: IncentivePayoutDto) => {
    modal.confirm({
      title: 'Cancel Incentive Payout?',
      content: 'Cancel this payout?',
      okText: 'Cancel Payout',
      onOk: async () => {
        try {
          await compensationApi.cancelIncentivePayout(payout.id)
          message.success('Incentive payout cancelled')
          loadPayouts()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Cancel failed',
          )
        }
      },
    })
  }

  const planColumns: ColumnsType<IncentivePlanDto> = [
    { title: 'Code', dataIndex: 'code', key: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Measure', dataIndex: 'measure', key: 'measure', width: 120 },
    {
      title: 'Target %',
      dataIndex: 'targetPct',
      key: 'targetPct',
      width: 100,
      align: 'right',
      render: (v: number) => `${v.toFixed(2)}%`,
    },
    {
      title: 'Threshold',
      dataIndex: 'thresholdAchievement',
      key: 'thresholdAchievement',
      width: 100,
      align: 'right',
      render: (v: number) => `${v.toFixed(0)}%`,
    },
    {
      title: 'Target',
      dataIndex: 'targetAchievement',
      key: 'targetAchievement',
      width: 100,
      align: 'right',
      render: (v: number) => `${v.toFixed(0)}%`,
    },
    {
      title: 'Cap',
      dataIndex: 'capAchievement',
      key: 'capAchievement',
      width: 100,
      align: 'right',
      render: (v: number) => `${v.toFixed(0)}%`,
    },
    {
      title: 'Max Payout %',
      dataIndex: 'maxPayoutPct',
      key: 'maxPayoutPct',
      width: 120,
      align: 'right',
      render: (v: number) => `${v.toFixed(2)}%`,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 80,
      render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? 'Active' : 'Inactive'}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_, rec) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEditPlan(rec)}
            disabled={!canWrite}
          />
          {rec.active && (
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDeactivatePlan(rec)}
              disabled={!canWrite}
            />
          )}
        </Space>
      ),
    },
  ]

  const employeeMap = new Map(employees.map((e) => [e.id, e]))

  const payoutColumns: ColumnsType<IncentivePayoutDto> = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      key: 'employeeName',
      render: (name: string | undefined, rec) => {
        const e = employeeMap.get(rec.employeeId)
        return name ?? (e ? `${e.employeeNo} — ${e.firstName} ${e.lastName}` : rec.employeeId)
      },
    },
    {
      title: 'Plan',
      dataIndex: 'planId',
      key: 'planId',
      render: (id: string) => {
        const plan = plans.find((p) => p.id === id)
        return plan ? `${plan.code} — ${plan.name}` : id
      },
    },
    { title: 'Period', dataIndex: 'period', key: 'period', width: 100 },
    {
      title: 'Eligible Salary',
      dataIndex: 'eligibleSalary',
      key: 'eligibleSalary',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Achievement %',
      dataIndex: 'achievementPct',
      key: 'achievementPct',
      width: 120,
      align: 'right',
      render: (v: number) => `${v.toFixed(2)}%`,
    },
    {
      title: 'Payout %',
      dataIndex: 'payoutPct',
      key: 'payoutPct',
      width: 100,
      align: 'right',
      render: (v: number) => `${v.toFixed(2)}%`,
    },
    {
      title: 'Payout Amount',
      dataIndex: 'payoutAmount',
      key: 'payoutAmount',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: IncentivePayoutStatus) => (
        <Tag color={PAYOUT_STATUS_COLOR[s]}>{s.replace('_', ' ')}</Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 150,
      render: (_, rec) => {
        const canApprove = rec.status === 'DRAFT' && canWrite
        const canCancel = rec.status === 'DRAFT' && canWrite
        return (
          <Space>
            {canApprove && (
              <Button size="small" type="primary" onClick={() => handleApprovePayout(rec)}>
                Approve
              </Button>
            )}
            {canCancel && (
              <Button size="small" onClick={() => handleCancelPayout(rec)}>
                Cancel
              </Button>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Incentive Plans & Payouts</Title>
        {canWrite && activeTab === 'plans' && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreatePlan}>
            Create Incentive Plan
          </Button>
        )}
        {canWrite && activeTab === 'payouts' && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreatePayout}>
            New Payout
          </Button>
        )}
      </div>

      <Card>
        <Tabs activeKey={activeTab} onChange={(key) => setActiveTab(key as 'plans' | 'payouts')}>
          <Tabs.TabPane tab="Plans" key="plans">
            <Table
              dataSource={plans}
              columns={planColumns}
              rowKey="id"
              loading={loadingPlans}
              pagination={{ pageSize: 20 }}
            />
          </Tabs.TabPane>

          <Tabs.TabPane tab="Payouts" key="payouts">
            <Alert
              type="info"
              message="Note: Approved payouts are not immediately attached to payroll. A later 'Compensation → Payroll transfer' step (Phase E) will push them to the payroll run."
              style={{ marginBottom: 16 }}
              closable
            />
            <Space style={{ marginBottom: 16 }}>
              <Select
                placeholder="Filter by status"
                style={{ width: 180 }}
                allowClear
                value={statusFilter}
                onChange={setStatusFilter}
                options={[
                  { value: 'DRAFT', label: 'Draft' },
                  { value: 'APPROVED', label: 'Approved' },
                  { value: 'PAID', label: 'Paid' },
                  { value: 'CANCELLED', label: 'Cancelled' },
                ]}
              />
            </Space>
            <Table
              dataSource={payouts}
              columns={payoutColumns}
              rowKey="id"
              loading={loadingPayouts}
              pagination={{ pageSize: 20 }}
            />
          </Tabs.TabPane>
        </Tabs>
      </Card>

      {/* Plan Drawer */}
      <Drawer
        title={editingPlan ? 'Edit Incentive Plan' : 'Create Incentive Plan'}
        open={planDrawerOpen}
        onClose={() => setPlanDrawerOpen(false)}
        width={600}
      >
        <Form form={planForm} layout="vertical" onFinish={submitPlan}>
          <Form.Item name="code" label="Code" rules={[{ required: true, message: 'Required' }]}>
            <Input disabled={!!editingPlan} />
          </Form.Item>

          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Required' }]}>
            <Input />
          </Form.Item>

          <Form.Item name="measure" label="Measure" rules={[{ required: true, message: 'Required' }]}>
            <Select
              options={[
                { value: 'KPI', label: 'KPI' },
                { value: 'SALES', label: 'Sales' },
                { value: 'PRODUCTION', label: 'Production' },
                { value: 'OTHER', label: 'Other' },
              ]}
            />
          </Form.Item>

          <Form.Item
            name="targetPct"
            label="Target % (of salary)"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} max={100} precision={2} style={{ width: '100%' }} addonAfter="%" />
          </Form.Item>

          <Form.Item
            name="thresholdAchievement"
            label="Threshold Achievement %"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} max={200} precision={0} style={{ width: '100%' }} addonAfter="%" />
          </Form.Item>

          <Form.Item
            name="targetAchievement"
            label="Target Achievement %"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} max={200} precision={0} style={{ width: '100%' }} addonAfter="%" />
          </Form.Item>

          <Form.Item
            name="capAchievement"
            label="Cap Achievement %"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} max={300} precision={0} style={{ width: '100%' }} addonAfter="%" />
          </Form.Item>

          <Form.Item
            name="maxPayoutPct"
            label="Max Payout %"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} max={100} precision={2} style={{ width: '100%' }} addonAfter="%" />
          </Form.Item>

          <Form.Item
            name="currency"
            label="Currency"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={[
                { value: 'AZN', label: 'AZN' },
                { value: 'USD', label: 'USD' },
                { value: 'EUR', label: 'EUR' },
              ]}
            />
          </Form.Item>

          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {editingPlan ? 'Update' : 'Create'}
              </Button>
              <Button onClick={() => setPlanDrawerOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>

      {/* Payout Modal */}
      <Modal
        title="New Incentive Payout"
        open={payoutModalOpen}
        onCancel={() => setPayoutModalOpen(false)}
        footer={null}
        width={600}
      >
        <Form form={payoutForm} layout="vertical" onFinish={submitPayout}>
          <Form.Item name="planId" label="Plan" rules={[{ required: true, message: 'Required' }]}>
            <Select
              options={plans
                .filter((p) => p.active)
                .map((p) => ({ value: p.id, label: `${p.code} — ${p.name}` }))}
            />
          </Form.Item>

          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              showSearch
              placeholder="Type employee name or number..."
              filterOption={false}
              onSearch={handleSearchEmployees}
              loading={loadingEmployees}
              notFoundContent={loadingEmployees ? 'Loading...' : 'Type to search'}
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>

          <Form.Item name="period" label="Period (e.g., 2026-Q1)" rules={[{ required: true, message: 'Required' }]}>
            <Input />
          </Form.Item>

          <Form.Item
            name="achievementPct"
            label="Achievement %"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} addonAfter="%" />
          </Form.Item>

          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 16 }}>
            Payout will be computed based on the plan's formula and the employee's eligible salary.
          </Text>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Create Payout
              </Button>
              <Button onClick={() => setPayoutModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
