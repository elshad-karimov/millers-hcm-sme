// HCM_20 M425 — Department budgets page.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useSearchParams } from 'react-router-dom'
import {
  budgetApi,
  type BudgetCycle,
  type DepartmentBudget,
  type DepartmentBudgetStatus,
} from '../api/budgeting'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<DepartmentBudgetStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
}

export function DepartmentBudgetsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const [searchParams] = useSearchParams()
  const cycleId = searchParams.get('cycleId')
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [cycle, setCycle] = useState<BudgetCycle | null>(null)
  const [budgets, setBudgets] = useState<DepartmentBudget[]>([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState<DepartmentBudget | 'new' | null>(null)
  const [form] = Form.useForm()

  const refresh = () => {
    if (!cycleId) return
    setLoading(true)
    budgetApi.getCycle(cycleId).then(setCycle).catch(() => setCycle(null))
    budgetApi.listDepartmentBudgets(cycleId).then(setBudgets).catch((err) =>
      message.error(err?.response?.data?.message ?? 'Could not load budgets'),
    ).finally(() => setLoading(false))
  }
  useEffect(refresh, [cycleId])

  const openModal = (budget: DepartmentBudget | 'new') => {
    if (budget === 'new') {
      form.resetFields()
      form.setFieldsValue({
        salaryBudget: 0,
        headcountBudget: 0,
        benefitsBudget: 0,
        trainingBudget: 0,
        recruitmentBudget: 0,
        overtimeBudget: 0,
      })
    } else {
      form.setFieldsValue({
        orgUnitId: budget.orgUnitId,
        salaryBudget: budget.salaryBudget,
        headcountBudget: budget.headcountBudget,
        benefitsBudget: budget.benefitsBudget,
        trainingBudget: budget.trainingBudget,
        recruitmentBudget: budget.recruitmentBudget,
        overtimeBudget: budget.overtimeBudget,
      })
    }
    setModal(budget)
  }

  const onOk = async () => {
    if (!cycleId) return
    const v = await form.validateFields()
    try {
      await budgetApi.upsertDepartmentBudget(cycleId, v)
      message.success('Department budget saved')
      setModal(null)
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not save')
    }
  }

  const onSubmit = async (id: string) => {
    try {
      await budgetApi.submitDepartmentBudget(id)
      message.success('Budget submitted for approval')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Submit failed')
    }
  }

  const totals = budgets.reduce(
    (acc, b) => ({
      salary: acc.salary + Number(b.salaryBudget),
      headcount: acc.headcount + b.headcountBudget,
      total: acc.total + Number(b.totalBudget),
      consumed: acc.consumed + Number(b.consumedAmount),
    }),
    { salary: 0, headcount: 0, total: 0, consumed: 0 },
  )

  const columns: ColumnsType<DepartmentBudget> = [
    { title: 'Org Unit ID', dataIndex: 'orgUnitId', width: 280, ellipsis: true },
    {
      title: 'Salary',
      dataIndex: 'salaryBudget',
      align: 'right' as const,
      width: 120,
      render: (v: number) => Number(v).toLocaleString(),
    },
    {
      title: 'HC',
      dataIndex: 'headcountBudget',
      align: 'right' as const,
      width: 80,
    },
    {
      title: 'Benefits',
      dataIndex: 'benefitsBudget',
      align: 'right' as const,
      width: 110,
      render: (v: number) => Number(v).toLocaleString(),
    },
    {
      title: 'Training',
      dataIndex: 'trainingBudget',
      align: 'right' as const,
      width: 110,
      render: (v: number) => Number(v).toLocaleString(),
    },
    {
      title: 'Total',
      dataIndex: 'totalBudget',
      align: 'right' as const,
      width: 130,
      render: (v: number) => <strong>{Number(v).toLocaleString()}</strong>,
    },
    {
      title: 'Consumed',
      dataIndex: 'consumedAmount',
      align: 'right' as const,
      width: 120,
      render: (v: number) => Number(v).toLocaleString(),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v: DepartmentBudgetStatus) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    ...(canWrite
      ? [
          {
            title: 'Actions',
            width: 160,
            render: (_: unknown, r: DepartmentBudget) => (
              <Space size={4}>
                {r.status === 'DRAFT' && (
                  <>
                    <Button size="small" onClick={() => openModal(r)}>
                      Edit
                    </Button>
                    <Button size="small" type="primary" onClick={() => onSubmit(r.id)}>
                      Submit
                    </Button>
                  </>
                )}
              </Space>
            ),
          } as ColumnsType<DepartmentBudget>[number],
        ]
      : []),
  ]

  if (!cycleId) {
    return <Card>Please select a budget cycle from the cycles page.</Card>
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {cycle && (
        <Card size="small">
          <Space size="large">
            <Typography.Title level={5} style={{ margin: 0 }}>
              {cycle.name} ({cycle.code})
            </Typography.Title>
            <Tag>{cycle.cycleType}</Tag>
            <Tag>{cycle.status}</Tag>
          </Space>
        </Card>
      )}

      <Card size="small">
        <Space size="large" wrap>
          <Statistic title="Total salary budget" value={totals.salary} precision={0} />
          <Statistic title="Total headcount" value={totals.headcount} />
          <Statistic title="Total budget" value={totals.total} precision={0} />
          <Statistic title="Total consumed" value={totals.consumed} precision={0} />
        </Space>
      </Card>

      <Card
        title="Department Budgets"
        extra={
          canWrite &&
          cycle?.status === 'OPEN' && (
            <Button type="primary" onClick={() => openModal('new')}>
              + Add department budget
            </Button>
          )
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={budgets}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        title={modal === 'new' ? 'Create Department Budget' : 'Edit Department Budget'}
        open={!!modal}
        onOk={onOk}
        onCancel={() => setModal(null)}
        destroyOnClose
        width={600}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="orgUnitId" label="Org Unit ID" rules={[{ required: true }]}>
            <Input placeholder="UUID of the org unit" />
          </Form.Item>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Space>
              <Form.Item name="salaryBudget" label="Salary budget" rules={[{ required: true }]}>
                <InputNumber min={0} step={1000} style={{ width: 160 }} />
              </Form.Item>
              <Form.Item name="headcountBudget" label="Headcount" rules={[{ required: true }]}>
                <InputNumber min={0} step={1} style={{ width: 120 }} />
              </Form.Item>
            </Space>
            <Space>
              <Form.Item name="benefitsBudget" label="Benefits budget">
                <InputNumber min={0} step={100} style={{ width: 160 }} />
              </Form.Item>
              <Form.Item name="trainingBudget" label="Training budget">
                <InputNumber min={0} step={100} style={{ width: 160 }} />
              </Form.Item>
            </Space>
            <Space>
              <Form.Item name="recruitmentBudget" label="Recruitment budget">
                <InputNumber min={0} step={100} style={{ width: 160 }} />
              </Form.Item>
              <Form.Item name="overtimeBudget" label="Overtime budget">
                <InputNumber min={0} step={100} style={{ width: 160 }} />
              </Form.Item>
            </Space>
          </Space>
        </Form>
      </Modal>
    </Space>
  )
}
