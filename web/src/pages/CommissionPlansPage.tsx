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
  Divider,
  Alert,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, MinusCircleOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  compensationApi,
  type CommissionPlanDto,
  type CommissionPlanRequest,
  type CommissionBasis,
  type CommissionTierDto,
  type CommissionPayoutDto,
  type CommissionPayoutStatus,
  type CreateCommissionPayoutRequest,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const PAYOUT_STATUS_COLOR: Record<CommissionPayoutStatus, string> = {
  DRAFT: 'default',
  APPROVED: 'cyan',
  PAID: 'green',
  CANCELLED: 'default',
}

interface PlanFormValues {
  code: string
  name: string
  basis: CommissionBasis
  flatRatePct?: number
  tiered: boolean
  currency: string
  active: boolean
  tiers?: CommissionTierDto[]
}

interface PayoutFormValues {
  planId: string
  employeeId: string
  period: string
  salesAmount: number
}

export function CommissionPlansPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [activeTab, setActiveTab] = useState<'plans' | 'payouts'>('plans')

  // Plans
  const [plans, setPlans] = useState<CommissionPlanDto[]>([])
  const [loadingPlans, setLoadingPlans] = useState(false)
  const [planDrawerOpen, setPlanDrawerOpen] = useState(false)
  const [editingPlan, setEditingPlan] = useState<CommissionPlanDto | null>(null)
  const [planForm] = Form.useForm<PlanFormValues>()

  const isTiered = Form.useWatch('tiered', planForm)

  // Payouts
  const [payouts, setPayouts] = useState<CommissionPayoutDto[]>([])
  const [loadingPayouts, setLoadingPayouts] = useState(false)
  const [payoutModalOpen, setPayoutModalOpen] = useState(false)
  const [payoutForm] = Form.useForm<PayoutFormValues>()
  const [statusFilter, setStatusFilter] = useState<CommissionPayoutStatus | undefined>()

  const [employees, setEmployees] = useState<Employee[]>([])
  const [loadingEmployees, setLoadingEmployees] = useState(false)

  const loadPlans = () => {
    setLoadingPlans(true)
    compensationApi
      .listCommissionPlans()
      .then(setPlans)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load commission plans'),
      )
      .finally(() => setLoadingPlans(false))
  }

  const loadPayouts = () => {
    setLoadingPayouts(true)
    compensationApi
      .listCommissionPayouts({ status: statusFilter })
      .then(setPayouts)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load commission payouts'),
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
    planForm.setFieldsValue({ active: true, currency: 'AZN', tiered: false })
    setPlanDrawerOpen(true)
  }

  const openEditPlan = (plan: CommissionPlanDto) => {
    setEditingPlan(plan)
    planForm.setFieldsValue({
      code: plan.code,
      name: plan.name,
      basis: plan.basis,
      flatRatePct: plan.flatRatePct ?? undefined,
      tiered: plan.tiered,
      currency: plan.currency,
      active: plan.active,
      tiers: plan.tiers.length > 0 ? plan.tiers : undefined,
    })
    setPlanDrawerOpen(true)
  }

  const submitPlan = async (v: PlanFormValues) => {
    const payload: CommissionPlanRequest = {
      code: v.code,
      name: v.name,
      basis: v.basis,
      flatRatePct: v.tiered ? null : (v.flatRatePct ?? null),
      tiered: v.tiered,
      currency: v.currency,
      active: v.active,
      tiers: v.tiered ? (v.tiers ?? []) : [],
    }

    try {
      if (editingPlan) {
        await compensationApi.updateCommissionPlan(editingPlan.id, payload)
        message.success('Commission plan updated')
      } else {
        await compensationApi.createCommissionPlan(payload)
        message.success('Commission plan created')
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

  const handleDeactivatePlan = (plan: CommissionPlanDto) => {
    modal.confirm({
      title: 'Deactivate Commission Plan?',
      content: `Deactivate ${plan.code} — ${plan.name}?`,
      okText: 'Deactivate',
      onOk: async () => {
        try {
          await compensationApi.deactivateCommissionPlan(plan.id)
          message.success('Commission plan deactivated')
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
    const payload: CreateCommissionPayoutRequest = {
      planId: v.planId,
      employeeId: v.employeeId,
      period: v.period,
      salesAmount: v.salesAmount,
    }

    try {
      await compensationApi.createCommissionPayout(payload)
      message.success('Commission payout created (DRAFT)')
      setPayoutModalOpen(false)
      loadPayouts()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Create failed',
      )
    }
  }

  const handleApprovePayout = (payout: CommissionPayoutDto) => {
    modal.confirm({
      title: 'Approve Commission Payout?',
      content: `Approve commission of ${payout.commissionAmount.toLocaleString()} for ${payout.employeeName ?? payout.employeeId}?`,
      okText: 'Approve',
      onOk: async () => {
        try {
          await compensationApi.approveCommissionPayout(payout.id)
          message.success('Commission payout approved. It will be transferred to payroll in Phase E.')
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

  const handleCancelPayout = (payout: CommissionPayoutDto) => {
    modal.confirm({
      title: 'Cancel Commission Payout?',
      content: 'Cancel this payout?',
      okText: 'Cancel Payout',
      onOk: async () => {
        try {
          await compensationApi.cancelCommissionPayout(payout.id)
          message.success('Commission payout cancelled')
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

  const planColumns: ColumnsType<CommissionPlanDto> = [
    { title: 'Code', dataIndex: 'code', key: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Basis', dataIndex: 'basis', key: 'basis', width: 120 },
    {
      title: 'Flat Rate %',
      dataIndex: 'flatRatePct',
      key: 'flatRatePct',
      width: 100,
      align: 'right',
      render: (v: number | null) => (v !== null ? `${v.toFixed(2)}%` : '—'),
    },
    {
      title: 'Tiered',
      dataIndex: 'tiered',
      key: 'tiered',
      width: 80,
      render: (v) => <Tag color={v ? 'blue' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
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

  const payoutColumns: ColumnsType<CommissionPayoutDto> = [
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
      title: 'Sales Amount',
      dataIndex: 'salesAmount',
      key: 'salesAmount',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Commission Amount',
      dataIndex: 'commissionAmount',
      key: 'commissionAmount',
      width: 140,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: CommissionPayoutStatus) => (
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
        <Title level={2}>Commission Plans & Payouts</Title>
        {canWrite && activeTab === 'plans' && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreatePlan}>
            Create Commission Plan
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
        title={editingPlan ? 'Edit Commission Plan' : 'Create Commission Plan'}
        open={planDrawerOpen}
        onClose={() => setPlanDrawerOpen(false)}
        width={700}
      >
        <Form form={planForm} layout="vertical" onFinish={submitPlan}>
          <Form.Item name="code" label="Code" rules={[{ required: true, message: 'Required' }]}>
            <Input disabled={!!editingPlan} />
          </Form.Item>

          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Required' }]}>
            <Input />
          </Form.Item>

          <Form.Item name="basis" label="Basis" rules={[{ required: true, message: 'Required' }]}>
            <Select
              options={[
                { value: 'REVENUE', label: 'Revenue' },
                { value: 'GROSS_MARGIN', label: 'Gross Margin' },
                { value: 'COLLECTIONS', label: 'Collections' },
              ]}
            />
          </Form.Item>

          <Form.Item name="tiered" label="Tiered" valuePropName="checked">
            <Switch />
          </Form.Item>

          {!isTiered && (
            <Form.Item
              name="flatRatePct"
              label="Flat Rate %"
              rules={[{ required: !isTiered, message: 'Required' }]}
            >
              <InputNumber min={0} max={100} precision={2} style={{ width: '100%' }} addonAfter="%" />
            </Form.Item>
          )}

          {isTiered && (
            <>
              <Divider>Commission Tiers</Divider>
              <Form.List name="tiers">
                {(fields, { add, remove }) => (
                  <>
                    {fields.map((field, index) => (
                      <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                        <Form.Item
                          {...field}
                          name={[field.name, 'fromAmount']}
                          label={index === 0 ? 'From' : undefined}
                          rules={[{ required: true, message: 'Required' }]}
                        >
                          <InputNumber min={0} precision={2} placeholder="From" style={{ width: 120 }} />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[field.name, 'toAmount']}
                          label={index === 0 ? 'To' : undefined}
                        >
                          <InputNumber min={0} precision={2} placeholder="To (or leave empty)" style={{ width: 150 }} />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[field.name, 'ratePct']}
                          label={index === 0 ? 'Rate %' : undefined}
                          rules={[{ required: true, message: 'Required' }]}
                        >
                          <InputNumber min={0} max={100} precision={2} placeholder="Rate %" style={{ width: 100 }} />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[field.name, 'sortOrder']}
                          initialValue={index}
                          hidden
                        >
                          <InputNumber />
                        </Form.Item>
                        <MinusCircleOutlined onClick={() => remove(field.name)} />
                      </Space>
                    ))}
                    <Form.Item>
                      <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                        Add Tier
                      </Button>
                    </Form.Item>
                  </>
                )}
              </Form.List>
            </>
          )}

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
        title="New Commission Payout"
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

          <Form.Item name="period" label="Period (e.g., 2026-06)" rules={[{ required: true, message: 'Required' }]}>
            <Input />
          </Form.Item>

          <Form.Item
            name="salesAmount"
            label="Sales Amount"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>

          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 16 }}>
            Commission will be computed based on the plan's rate (flat or tiered).
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
