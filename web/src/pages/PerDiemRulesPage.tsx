// M452 — Per-diem rules admin (HR only).
// CRUD interface for per-diem allowance rules by country/city/grade/tripType.

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

type TripType = 'DOMESTIC' | 'INTERNATIONAL'

interface PerDiemRuleRequest {
  destinationCountry: string
  destinationCity?: string | null
  employeeGrade?: string | null
  tripType?: TripType | null
  mealAllowance: number
  lodgingAllowance: number
  incidentals: number
  currency?: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  active?: boolean
}

interface PerDiemRuleResponse {
  id: string
  destinationCountry: string
  destinationCity?: string | null
  employeeGrade?: string | null
  tripType?: TripType | null
  mealAllowance: number
  lodgingAllowance: number
  incidentals: number
  currency: string
  effectiveFrom?: string | null
  effectiveTo?: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

interface FormValues {
  destinationCountry: string
  destinationCity?: string
  employeeGrade?: string
  tripType?: TripType
  mealAllowance: number
  lodgingAllowance: number
  incidentals: number
  currency: string
  effectiveRange?: [dayjs.Dayjs, dayjs.Dayjs] | null
  active: boolean
}

const perDiemApi = {
  list: () =>
    api.get<PerDiemRuleResponse[]>('/business-trips/per-diem/rules').then((r) => r.data),
  create: (req: PerDiemRuleRequest) =>
    api.post<PerDiemRuleResponse>('/business-trips/per-diem/rules', req).then((r) => r.data),
  update: (id: string, req: PerDiemRuleRequest) =>
    api.put<PerDiemRuleResponse>(`/business-trips/per-diem/rules/${id}`, req).then((r) => r.data),
  delete: (id: string) =>
    api.delete(`/business-trips/per-diem/rules/${id}`).then((r) => r.data),
}

export function PerDiemRulesPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [rules, setRules] = useState<PerDiemRuleResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<PerDiemRuleResponse | null>(null)

  const load = () => {
    setLoading(true)
    perDiemApi.list()
      .then(setRules)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load rules'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ currency: 'AZN', active: true })
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (r: PerDiemRuleResponse) => {
    setEditing(r)
    form.setFieldsValue({
      destinationCountry: r.destinationCountry,
      destinationCity: r.destinationCity ?? undefined,
      employeeGrade: r.employeeGrade ?? undefined,
      tripType: r.tripType ?? undefined,
      mealAllowance: r.mealAllowance,
      lodgingAllowance: r.lodgingAllowance,
      incidentals: r.incidentals,
      currency: r.currency,
      effectiveRange:
        r.effectiveFrom && r.effectiveTo
          ? [dayjs(r.effectiveFrom), dayjs(r.effectiveTo)]
          : undefined,
      active: r.active,
    })
    setModalOpen(true)
  }

  const onFinish = async (v: FormValues) => {
    const payload: PerDiemRuleRequest = {
      destinationCountry: v.destinationCountry,
      destinationCity: v.destinationCity || null,
      employeeGrade: v.employeeGrade || null,
      tripType: v.tripType || null,
      mealAllowance: v.mealAllowance,
      lodgingAllowance: v.lodgingAllowance,
      incidentals: v.incidentals,
      currency: v.currency,
      effectiveFrom: v.effectiveRange?.[0]?.format('YYYY-MM-DD') ?? null,
      effectiveTo: v.effectiveRange?.[1]?.format('YYYY-MM-DD') ?? null,
      active: v.active,
    }
    try {
      if (editing) {
        await perDiemApi.update(editing.id, payload)
        message.success('Rule updated')
      } else {
        await perDiemApi.create(payload)
        message.success('Rule created')
      }
      setModalOpen(false)
      load()
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Save failed')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await perDiemApi.delete(id)
      message.success('Rule deleted')
      load()
    } catch (e) {
      message.error((e as any)?.response?.data?.message ?? 'Delete failed')
    }
  }

  const columns: ColumnsType<PerDiemRuleResponse> = [
    { title: 'Country', dataIndex: 'destinationCountry', width: 140 },
    {
      title: 'City',
      dataIndex: 'destinationCity',
      width: 120,
      render: (v) => v || <Tag>Any</Tag>,
    },
    {
      title: 'Grade',
      dataIndex: 'employeeGrade',
      width: 100,
      render: (v) => v || <Tag>Any</Tag>,
    },
    {
      title: 'Trip Type',
      dataIndex: 'tripType',
      width: 120,
      render: (v) => (v ? <Tag color="blue">{v}</Tag> : <Tag>Any</Tag>),
    },
    {
      title: 'Meals',
      dataIndex: 'mealAllowance',
      width: 100,
      render: (v, r) => `${r.currency} ${v}`,
    },
    {
      title: 'Lodging',
      dataIndex: 'lodgingAllowance',
      width: 100,
      render: (v, r) => `${r.currency} ${v}`,
    },
    {
      title: 'Incidentals',
      dataIndex: 'incidentals',
      width: 110,
      render: (v, r) => `${r.currency} ${v}`,
    },
    {
      title: 'Effective',
      key: 'effective',
      width: 200,
      render: (_, r) =>
        r.effectiveFrom && r.effectiveTo
          ? `${dayjs(r.effectiveFrom).format('DD MMM YYYY')} – ${dayjs(r.effectiveTo).format('DD MMM YYYY')}`
          : 'Always',
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
      render: (_, r) => (
        <Space size="small">
          <Button size="small" onClick={() => openEdit(r)}>
            Edit
          </Button>
          <Popconfirm title="Delete this rule?" onConfirm={() => handleDelete(r.id)}>
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
        title={<Title level={4}>Per-Diem Rules</Title>}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            New Rule
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={rules}
          loading={loading}
          pagination={false}
          scroll={{ x: 1200 }}
        />
      </Card>

      <Modal
        title={editing ? 'Edit Per-Diem Rule' : 'New Per-Diem Rule'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        width={700}
      >
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ marginTop: 16 }}>
          <Form.Item
            name="destinationCountry"
            label="Country"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. Turkey" />
          </Form.Item>
          <Form.Item name="destinationCity" label="City (optional)">
            <Input placeholder="e.g. Istanbul — leave blank for any city" />
          </Form.Item>
          <Form.Item name="employeeGrade" label="Employee Grade (optional)">
            <Input placeholder="e.g. Senior — leave blank for any grade" />
          </Form.Item>
          <Form.Item name="tripType" label="Trip Type (optional)">
            <Select
              allowClear
              placeholder="Leave blank for any trip type"
              options={[
                { value: 'DOMESTIC', label: 'Domestic' },
                { value: 'INTERNATIONAL', label: 'International' },
              ]}
            />
          </Form.Item>
          <Space style={{ width: '100%' }} size="middle">
            <Form.Item
              name="mealAllowance"
              label="Meal Allowance"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber min={0} step={1} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item
              name="lodgingAllowance"
              label="Lodging Allowance"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber min={0} step={1} style={{ width: 150 }} />
            </Form.Item>
            <Form.Item
              name="incidentals"
              label="Incidentals"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber min={0} step={1} style={{ width: 150 }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="currency"
            label="Currency"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="AZN" />
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
