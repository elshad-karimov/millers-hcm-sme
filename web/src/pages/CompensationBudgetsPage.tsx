import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  compensationApi,
  type CompensationBudgetDto,
  type CompensationBudgetRequest,
  type BudgetType,
  type BudgetScopeType,
} from '../api/compensation'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const BUDGET_TYPE_COLOR: Record<BudgetType, string> = {
  MERIT: 'blue',
  BONUS: 'green',
  INCENTIVE: 'purple',
  PROMOTION: 'orange',
}

interface FormValues {
  cycleId?: string
  scopeType: BudgetScopeType
  scopeRef?: string
  budgetType: BudgetType
  amount: number
  currency: string
  effectiveFrom: string
  effectiveTo?: string
  isActive: boolean
}

export function CompensationBudgetsPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [rows, setRows] = useState<CompensationBudgetDto[]>([])
  const [loading, setLoading] = useState(false)
  const [budgetTypeFilter, setBudgetTypeFilter] = useState<BudgetType | undefined>()
  const [scopeTypeFilter, setScopeTypeFilter] = useState<BudgetScopeType | undefined>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<CompensationBudgetDto | null>(null)
  const [form] = Form.useForm<FormValues>()

  const selectedScopeType = Form.useWatch('scopeType', form)

  const load = () => {
    setLoading(true)
    compensationApi
      .listBudgets({ budgetType: budgetTypeFilter, scopeType: scopeTypeFilter })
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load budgets'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [budgetTypeFilter, scopeTypeFilter])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ isActive: true, currency: 'AZN', scopeType: 'GLOBAL' })
    setDrawerOpen(true)
  }

  const openEdit = (b: CompensationBudgetDto) => {
    setEditing(b)
    form.setFieldsValue({
      cycleId: b.cycleId ?? undefined,
      scopeType: b.scopeType,
      scopeRef: b.scopeRef ?? undefined,
      budgetType: b.budgetType,
      amount: b.amount,
      currency: b.currency,
      effectiveFrom: b.effectiveFrom,
      effectiveTo: b.effectiveTo ?? undefined,
      isActive: b.isActive,
    })
    setDrawerOpen(true)
  }

  const submit = async (v: FormValues) => {
    const payload: CompensationBudgetRequest = {
      cycleId: v.cycleId ?? null,
      scopeType: v.scopeType,
      scopeRef: v.scopeType === 'GLOBAL' ? null : v.scopeRef ?? null,
      budgetType: v.budgetType,
      amount: v.amount,
      currency: v.currency,
      effectiveFrom: v.effectiveFrom,
      effectiveTo: v.effectiveTo ?? null,
      isActive: v.isActive,
    }

    try {
      if (editing) {
        await compensationApi.updateBudget(editing.id, payload)
        message.success('Budget updated')
      } else {
        await compensationApi.createBudget(payload)
        message.success('Budget created')
      }
      setDrawerOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const handleDeactivate = (b: CompensationBudgetDto) => {
    modal.confirm({
      title: 'Deactivate Budget?',
      content: `Deactivate ${b.budgetType} budget (${b.amount.toLocaleString()} ${b.currency})?`,
      okText: 'Deactivate',
      onOk: async () => {
        try {
          await compensationApi.deactivateBudget(b.id)
          message.success('Budget deactivated')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Deactivate failed',
          )
        }
      },
    })
  }

  const columns: ColumnsType<CompensationBudgetDto> = [
    {
      title: 'Budget Type',
      dataIndex: 'budgetType',
      key: 'budgetType',
      width: 120,
      render: (t: BudgetType) => <Tag color={BUDGET_TYPE_COLOR[t]}>{t}</Tag>,
    },
    {
      title: 'Scope',
      width: 200,
      render: (_, rec) => (
        <div>
          <Tag color="cyan" style={{ fontSize: 11 }}>
            {rec.scopeType}
          </Tag>
          {rec.scopeRef && (
            <div style={{ fontSize: 12, color: '#666', marginTop: 2 }}>{rec.scopeRef}</div>
          )}
        </div>
      ),
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      width: 120,
      render: (v: number, rec) => `${v.toLocaleString()} ${rec.currency}`,
    },
    {
      title: 'Consumed',
      dataIndex: 'consumedAmount',
      key: 'consumedAmount',
      align: 'right',
      width: 120,
      render: (v: number, rec) => `${v.toLocaleString()} ${rec.currency}`,
    },
    {
      title: 'Remaining',
      width: 150,
      render: (_, rec) => {
        const remaining = rec.amount - rec.consumedAmount
        const color = remaining >= 0 ? '#52c41a' : '#ff4d4f'
        return (
          <div>
            <Text style={{ color, fontWeight: 600 }}>
              {remaining.toLocaleString()} {rec.currency}
            </Text>
            <Progress
              percent={Math.min((rec.consumedAmount / rec.amount) * 100, 100)}
              size="small"
              status={remaining < 0 ? 'exception' : 'active'}
              showInfo={false}
              style={{ marginTop: 4 }}
            />
          </div>
        )
      },
    },
    {
      title: 'Effective',
      width: 150,
      render: (_, rec) => (
        <div style={{ fontSize: 12 }}>
          <div>{rec.effectiveFrom}</div>
          {rec.effectiveTo && <div style={{ color: '#999' }}>to {rec.effectiveTo}</div>}
        </div>
      ),
    },
    {
      title: 'Active',
      dataIndex: 'isActive',
      key: 'isActive',
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
            onClick={() => openEdit(rec)}
            disabled={!canWrite}
          />
          {rec.isActive && (
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDeactivate(rec)}
              disabled={!canWrite}
            />
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Compensation Budgets</Title>
        {canWrite && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Create Budget
          </Button>
        )}
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="Filter by budget type"
            style={{ width: 180 }}
            allowClear
            value={budgetTypeFilter}
            onChange={setBudgetTypeFilter}
            options={[
              { value: 'MERIT', label: 'Merit' },
              { value: 'BONUS', label: 'Bonus' },
              { value: 'INCENTIVE', label: 'Incentive' },
              { value: 'PROMOTION', label: 'Promotion' },
            ]}
          />
          <Select
            placeholder="Filter by scope type"
            style={{ width: 180 }}
            allowClear
            value={scopeTypeFilter}
            onChange={setScopeTypeFilter}
            options={[
              { value: 'GLOBAL', label: 'Global' },
              { value: 'DEPARTMENT', label: 'Department' },
              { value: 'MANAGER', label: 'Manager' },
              { value: 'GRADE', label: 'Grade' },
              { value: 'LEGAL_ENTITY', label: 'Legal Entity' },
            ]}
          />
        </Space>
        <Table
          dataSource={rows}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Drawer
        title={editing ? 'Edit Budget' : 'Create Budget'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="budgetType"
            label="Budget Type"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={[
                { value: 'MERIT', label: 'Merit' },
                { value: 'BONUS', label: 'Bonus' },
                { value: 'INCENTIVE', label: 'Incentive' },
                { value: 'PROMOTION', label: 'Promotion' },
              ]}
            />
          </Form.Item>

          <Form.Item
            name="scopeType"
            label="Scope Type"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={[
                { value: 'GLOBAL', label: 'Global' },
                { value: 'DEPARTMENT', label: 'Department' },
                { value: 'MANAGER', label: 'Manager' },
                { value: 'GRADE', label: 'Grade' },
                { value: 'LEGAL_ENTITY', label: 'Legal Entity' },
              ]}
            />
          </Form.Item>

          {selectedScopeType && selectedScopeType !== 'GLOBAL' && (
            <Form.Item
              name="scopeRef"
              label="Scope Reference"
              rules={[{ required: true, message: 'Required for non-global scope' }]}
            >
              <Input placeholder="Department ID, Manager ID, Grade ID, or Legal Entity ID" />
            </Form.Item>
          )}

          <Form.Item
            name="amount"
            label="Amount"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
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

          <Form.Item
            name="effectiveFrom"
            label="Effective From"
            rules={[{ required: true, message: 'Required' }]}
            getValueProps={(value) => ({ value: value ? dayjs(value) : undefined })}
            getValueFromEvent={(date) => (date ? date.format('YYYY-MM-DD') : undefined)}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="effectiveTo"
            label="Effective To (optional)"
            getValueProps={(value) => ({ value: value ? dayjs(value) : undefined })}
            getValueFromEvent={(date) => (date ? date.format('YYYY-MM-DD') : undefined)}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="cycleId" label="Cycle ID (optional)">
            <Input placeholder="Link to a compensation cycle (if applicable)" />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {editing ? 'Update' : 'Create'}
              </Button>
              <Button onClick={() => setDrawerOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
