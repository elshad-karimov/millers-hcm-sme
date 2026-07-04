// HCM_20 M425 — Budget cycles management page.

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
  Table,
  Tag,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  budgetApi,
  type BudgetCycle,
  type BudgetCycleStatus,
  type CycleType,
} from '../api/budgeting'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const CYCLE_TYPE_OPTIONS: Array<{ value: CycleType; label: string }> = [
  { value: 'ANNUAL', label: 'Annual' },
  { value: 'QUARTERLY', label: 'Quarterly' },
  { value: 'ROLLING', label: 'Rolling' },
]

const STATUS_COLOR: Record<BudgetCycleStatus, string> = {
  DRAFT: 'default',
  OPEN: 'green',
  LOCKED: 'orange',
  CLOSED: 'red',
}

export function BudgetCyclesPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [cycles, setCycles] = useState<BudgetCycle[]>([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState<BudgetCycle | 'new' | null>(null)
  const [form] = Form.useForm()

  const refresh = () => {
    setLoading(true)
    budgetApi.listCycles().then(setCycles).catch((err) =>
      message.error(err?.response?.data?.message ?? 'Could not load cycles'),
    ).finally(() => setLoading(false))
  }
  useEffect(refresh, [])

  const openModal = (cycle: BudgetCycle | 'new') => {
    if (cycle === 'new') {
      form.resetFields()
      form.setFieldsValue({ cycleType: 'ANNUAL', status: 'DRAFT' })
    } else {
      form.setFieldsValue({
        code: cycle.code,
        name: cycle.name,
        cycleType: cycle.cycleType,
        periodStart: dayjs(cycle.periodStart),
        periodEnd: dayjs(cycle.periodEnd),
        submissionDeadline: cycle.submissionDeadline ? dayjs(cycle.submissionDeadline) : null,
      })
    }
    setModal(cycle)
  }

  const onOk = async () => {
    const v = await form.validateFields()
    const body = {
      ...v,
      periodStart: v.periodStart.format('YYYY-MM-DD'),
      periodEnd: v.periodEnd.format('YYYY-MM-DD'),
      submissionDeadline: v.submissionDeadline ? v.submissionDeadline.format('YYYY-MM-DD') : undefined,
    }
    try {
      if (modal === 'new') {
        await budgetApi.createCycle(body)
        message.success('Budget cycle created')
      } else if (modal) {
        await budgetApi.updateCycle(modal.id, body)
        message.success('Budget cycle updated')
      }
      setModal(null)
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Could not save')
    }
  }

  const onDelete = async (id: string) => {
    try {
      await budgetApi.deleteCycle(id)
      message.success('Budget cycle deleted')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Delete failed')
    }
  }

  const onChangeStatus = async (id: string, status: BudgetCycleStatus) => {
    try {
      await budgetApi.updateCycleStatus(id, status)
      message.success('Status updated')
      refresh()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      message.error(e?.response?.data?.message ?? 'Status change failed')
    }
  }

  const columns: ColumnsType<BudgetCycle> = [
    { title: 'Code', dataIndex: 'code', width: 150 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Type',
      dataIndex: 'cycleType',
      width: 120,
      render: (v: CycleType) => <Tag>{v}</Tag>,
    },
    { title: 'Start', dataIndex: 'periodStart', width: 120 },
    { title: 'End', dataIndex: 'periodEnd', width: 120 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (v: BudgetCycleStatus) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    ...(canWrite
      ? [
          {
            title: 'Actions',
            width: 300,
            render: (_: unknown, r: BudgetCycle) => (
              <Space size={4}>
                <Button size="small" onClick={() => navigate(`/budgets/departments?cycleId=${r.id}`)}>
                  Depts
                </Button>
                {r.status === 'DRAFT' && (
                  <>
                    <Button size="small" onClick={() => openModal(r)}>
                      Edit
                    </Button>
                    <Button size="small" onClick={() => onChangeStatus(r.id, 'OPEN')}>
                      Open
                    </Button>
                    <Popconfirm title="Delete?" onConfirm={() => onDelete(r.id)}>
                      <Button size="small" danger>
                        Delete
                      </Button>
                    </Popconfirm>
                  </>
                )}
                {r.status === 'OPEN' && (
                  <Button size="small" onClick={() => onChangeStatus(r.id, 'LOCKED')}>
                    Lock
                  </Button>
                )}
                {r.status === 'LOCKED' && (
                  <Button size="small" danger onClick={() => onChangeStatus(r.id, 'CLOSED')}>
                    Close
                  </Button>
                )}
              </Space>
            ),
          } as ColumnsType<BudgetCycle>[number],
        ]
      : []),
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="Budget Cycles"
        extra={
          canWrite && (
            <Button type="primary" onClick={() => openModal('new')}>
              + Create cycle
            </Button>
          )
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={cycles}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        title={modal === 'new' ? 'Create Budget Cycle' : 'Edit Budget Cycle'}
        open={!!modal}
        onOk={onOk}
        onCancel={() => setModal(null)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="code" label="Code" rules={[{ required: true }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="cycleType" label="Type" rules={[{ required: true }]}>
            <Select options={CYCLE_TYPE_OPTIONS} />
          </Form.Item>
          <Space>
            <Form.Item name="periodStart" label="Start" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="periodEnd" label="End" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.Item name="submissionDeadline" label="Submission deadline">
            <DatePicker />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
