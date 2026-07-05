// M454 — Expense policy rules admin (HR only).
// CRUD interface for expense policy limits by category/grade.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { api } from '../api/client'

const { Title } = Typography

type ExpenseCategory =
  | 'ACCOMMODATION'
  | 'MEALS'
  | 'TRANSPORT'
  | 'FLIGHT'
  | 'VISA_FEES'
  | 'COMMUNICATION'
  | 'REGISTRATION'
  | 'OTHER'

const CATEGORY_LABEL: Record<ExpenseCategory, string> = {
  ACCOMMODATION: 'Accommodation',
  MEALS: 'Meals',
  TRANSPORT: 'Transport',
  FLIGHT: 'Flight',
  VISA_FEES: 'Visa fees',
  COMMUNICATION: 'Communication',
  REGISTRATION: 'Registration',
  OTHER: 'Other',
}

interface ExpensePolicyRequest {
  category: ExpenseCategory
  employeeGrade?: string | null
  maxPerTransaction?: number | null
  maxDaily?: number | null
  receiptRequiredAbove?: number | null
  blocked?: boolean
  effectiveFrom?: string | null
  effectiveTo?: string | null
  active?: boolean
}

interface ExpensePolicyResponse {
  id: string
  category: ExpenseCategory
  employeeGrade?: string | null
  maxPerTransaction?: number | null
  maxDaily?: number | null
  receiptRequiredAbove: number
  blocked: boolean
  effectiveFrom?: string | null
  effectiveTo?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

interface FormValues {
  category: ExpenseCategory
  employeeGrade?: string
  maxPerTransaction?: number
  maxDaily?: number
  receiptRequiredAbove?: number
  blocked: boolean
  effectiveRange?: [dayjs.Dayjs, dayjs.Dayjs] | null
  active: boolean
}

const expensePolicyApi = {
  list: () =>
    api.get<ExpensePolicyResponse[]>('/business-trips/expense-policies').then((r) => r.data),
  create: (req: ExpensePolicyRequest) =>
    api.post<ExpensePolicyResponse>('/business-trips/expense-policies', req).then((r) => r.data),
  update: (id: string, req: ExpensePolicyRequest) =>
    api.put<ExpensePolicyResponse>(`/business-trips/expense-policies/${id}`, req).then((r) => r.data),
  delete: (id: string) =>
    api.delete(`/business-trips/expense-policies/${id}`).then((r) => r.data),
}

export function ExpensePoliciesPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [policies, setPolicies] = useState<ExpensePolicyResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ExpensePolicyResponse | null>(null)

  const load = () => {
    setLoading(true)
    expensePolicyApi.list()
      .then(setPolicies)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load policies'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ active: true, blocked: false, receiptRequiredAbove: 20 })
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (p: ExpensePolicyResponse) => {
    setEditing(p)
    form.setFieldsValue({
      category: p.category,
      employeeGrade: p.employeeGrade ?? undefined,
      maxPerTransaction: p.maxPerTransaction ?? undefined,
      maxDaily: p.maxDaily ?? undefined,
      receiptRequiredAbove: p.receiptRequiredAbove,
      blocked: p.blocked,
      effectiveRange:
        p.effectiveFrom && p.effectiveTo
          ? [dayjs(p.effectiveFrom), dayjs(p.effectiveTo)]
          : undefined,
      active: p.active,
    })
    setModalOpen(true)
  }

  const onFinish = async (v: FormValues) => {
    const payload: ExpensePolicyRequest = {
      category: v.category,
      employeeGrade: v.employeeGrade || null,
      maxPerTransaction: v.maxPerTransaction ?? null,
      maxDaily: v.maxDaily ?? null,
      receiptRequiredAbove: v.receiptRequiredAbove ?? null,
      blocked: v.blocked,
      effectiveFrom: v.effectiveRange?.[0]?.format('YYYY-MM-DD') ?? null,
      effectiveTo: v.effectiveRange?.[1]?.format('YYYY-MM-DD') ?? null,
      active: v.active,
    }
    try {
      if (editing) {
        await expensePolicyApi.update(editing.id, payload)
        message.success('Policy updated')
      } else {
        await expensePolicyApi.create(payload)
        message.success('Policy created')
      }
      setModalOpen(false)
      load()
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Save failed')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await expensePolicyApi.delete(id)
      message.success('Policy deleted')
      load()
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Delete failed')
    }
  }

  const columns: ColumnsType<ExpensePolicyResponse> = [
    {
      title: 'Category',
      dataIndex: 'category',
      width: 140,
      render: (v) => CATEGORY_LABEL[v as ExpenseCategory],
    },
    {
      title: 'Grade',
      dataIndex: 'employeeGrade',
      width: 100,
      render: (v) => v || <Tag>Any</Tag>,
    },
    {
      title: 'Max / Transaction',
      dataIndex: 'maxPerTransaction',
      width: 130,
      render: (v) => (v !== null && v !== undefined ? `AZN ${v}` : <Tag>None</Tag>),
    },
    {
      title: 'Max / Day',
      dataIndex: 'maxDaily',
      width: 110,
      render: (v) => (v !== null && v !== undefined ? `AZN ${v}` : <Tag>None</Tag>),
    },
    {
      title: 'Receipt Required Above',
      dataIndex: 'receiptRequiredAbove',
      width: 170,
      render: (v) => `AZN ${v}`,
    },
    {
      title: 'Blocked',
      dataIndex: 'blocked',
      width: 90,
      render: (v) => (v ? <Tag color="red">Yes</Tag> : <Tag>No</Tag>),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 140,
      render: (_, p) => (
        <Space size="small">
          <Button size="small" onClick={() => openEdit(p)}>
            Edit
          </Button>
          <Popconfirm title="Delete this policy?" onConfirm={() => handleDelete(p.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card
        title={<Title level={4}>Expense Policies</Title>}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            New Policy
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={policies}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        title={editing ? 'Edit Expense Policy' : 'New Expense Policy'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ marginTop: 16 }}>
          <Form.Item
            name="category"
            label="Category"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select
              options={Object.entries(CATEGORY_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="employeeGrade" label="Employee Grade (optional)">
            <Input placeholder="e.g. Senior — leave blank for any grade" />
          </Form.Item>
          <Form.Item name="maxPerTransaction" label="Max per Transaction (optional)">
            <InputNumber min={0} step={1} style={{ width: '100%' }} placeholder="Leave blank for no limit" />
          </Form.Item>
          <Form.Item name="maxDaily" label="Max per Day (optional)">
            <InputNumber min={0} step={1} style={{ width: '100%' }} placeholder="Leave blank for no limit" />
          </Form.Item>
          <Form.Item name="receiptRequiredAbove" label="Receipt Required Above (AZN)">
            <InputNumber min={0} step={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="blocked" label="Blocked (disallow this category)" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="effectiveRange" label="Effective Range (optional)">
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
