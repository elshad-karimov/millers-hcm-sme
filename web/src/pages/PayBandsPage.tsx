import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Space,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  compensationApi,
  type PayBandResponse,
  type PayBandRequest,
} from '../api/compensation'
import { gradeApi, type Grade } from '../api/staffingCatalog'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title } = Typography

interface FormValues {
  code: string
  name: string
  gradeId?: string
  currency: string
  minSalary: number
  midSalary: number
  maxSalary: number
  q1Salary?: number
  q3Salary?: number
  effectiveFrom: string
  effectiveTo?: string
  isActive: boolean
}

export function PayBandsPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [rows, setRows] = useState<PayBandResponse[]>([])
  const [grades, setGrades] = useState<Grade[]>([])
  const [loading, setLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<PayBandResponse | null>(null)
  const [form] = Form.useForm<FormValues>()

  const minSalary = Form.useWatch('minSalary', form)
  const midSalary = Form.useWatch('midSalary', form)
  const maxSalary = Form.useWatch('maxSalary', form)

  const load = () => {
    setLoading(true)
    Promise.all([compensationApi.listPayBands(), gradeApi.list(false)])
      .then(([bands, grds]) => {
        setRows(bands)
        setGrades(grds)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load pay bands'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ isActive: true, currency: 'AZN' })
    setDrawerOpen(true)
  }

  const openEdit = (b: PayBandResponse) => {
    setEditing(b)
    form.setFieldsValue({
      code: b.code,
      name: b.name,
      gradeId: b.gradeId ?? undefined,
      currency: b.currency,
      minSalary: b.minSalary,
      midSalary: b.midSalary,
      maxSalary: b.maxSalary,
      q1Salary: b.q1Salary ?? undefined,
      q3Salary: b.q3Salary ?? undefined,
      effectiveFrom: b.effectiveFrom,
      effectiveTo: b.effectiveTo ?? undefined,
      isActive: b.isActive,
    })
    setDrawerOpen(true)
  }

  const submit = async (v: FormValues) => {
    // Client-side hint (backend enforces)
    if (v.minSalary > v.midSalary || v.midSalary > v.maxSalary) {
      message.warning('Min ≤ Mid ≤ Max required')
      return
    }

    const payload: PayBandRequest = {
      code: v.code,
      name: v.name,
      gradeId: v.gradeId ?? null,
      currency: v.currency,
      minSalary: v.minSalary,
      midSalary: v.midSalary,
      maxSalary: v.maxSalary,
      q1Salary: v.q1Salary ?? null,
      q3Salary: v.q3Salary ?? null,
      effectiveFrom: v.effectiveFrom,
      effectiveTo: v.effectiveTo ?? null,
      isActive: v.isActive,
    }

    try {
      if (editing) {
        await compensationApi.updatePayBand(editing.id, payload)
        message.success('Pay band updated')
      } else {
        await compensationApi.createPayBand(payload)
        message.success('Pay band created')
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

  const handleDeactivate = (b: PayBandResponse) => {
    modal.confirm({
      title: 'Deactivate Pay Band?',
      content: `Deactivate ${b.code} — ${b.name}?`,
      okText: 'Deactivate',
      onOk: async () => {
        try {
          await compensationApi.deactivatePayBand(b.id)
          message.success('Pay band deactivated')
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

  const columns: ColumnsType<PayBandResponse> = [
    { title: 'Code', dataIndex: 'code', key: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Grade',
      dataIndex: 'gradeId',
      key: 'gradeId',
      render: (gid) => {
        if (!gid) return <span style={{ color: '#999' }}>—</span>
        const g = grades.find((x) => x.id === gid)
        return g ? g.name : gid
      },
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 80 },
    {
      title: 'Min',
      dataIndex: 'minSalary',
      key: 'minSalary',
      align: 'right',
      render: (v) => v.toLocaleString(),
    },
    {
      title: 'Mid',
      dataIndex: 'midSalary',
      key: 'midSalary',
      align: 'right',
      render: (v) => v.toLocaleString(),
    },
    {
      title: 'Max',
      dataIndex: 'maxSalary',
      key: 'maxSalary',
      align: 'right',
      render: (v) => v.toLocaleString(),
    },
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      width: 120,
    },
    {
      title: 'Effective To',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      width: 120,
      render: (v) => v ?? <span style={{ color: '#999' }}>—</span>,
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
        <Title level={2}>Pay Bands</Title>
        {canWrite && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Create Pay Band
          </Button>
        )}
      </div>

      <Card>
        <Table
          dataSource={rows}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
        />
      </Card>

      <Drawer
        title={editing ? 'Edit Pay Band' : 'Create Pay Band'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="code" label="Code" rules={[{ required: true, message: 'Required' }]}>
            <Input disabled={!!editing} />
          </Form.Item>

          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Required' }]}>
            <Input />
          </Form.Item>

          <Form.Item name="gradeId" label="Grade (optional)">
            <Select
              options={grades.map((g) => ({ value: g.id, label: `${g.code} — ${g.name}` }))}
              allowClear
            />
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
            name="minSalary"
            label="Min Salary"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="midSalary"
            label="Mid Salary"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="maxSalary"
            label="Max Salary"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>

          {minSalary && midSalary && maxSalary && (
            <div style={{ marginBottom: 16, color: '#999', fontSize: 12 }}>
              Hint: Min ({minSalary.toLocaleString()}) ≤ Mid ({midSalary.toLocaleString()}) ≤ Max
              ({maxSalary.toLocaleString()})
            </div>
          )}

          <Form.Item name="q1Salary" label="Q1 Salary (optional)">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="q3Salary" label="Q3 Salary (optional)">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
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

          <Form.Item name="isActive" label="Active" valuePropName="checked">
            <Switch />
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
