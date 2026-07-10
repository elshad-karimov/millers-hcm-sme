// M485 — Labor rates (Finance/HR confidential)

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
  Table,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  laborRatesApi,
  type LaborRate,
  type LaborRateRequest,
} from '../api/laborRates'
import { useAuth } from '../auth/AuthContext'

const { Title, Text } = Typography

export function LaborRatesPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole('PAYROLL_ADMIN', 'FINANCE_ADMIN')

  const [rates, setRates] = useState<LaborRate[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<LaborRate | null>(null)
  const [form] = Form.useForm<LaborRateRequest>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    laborRatesApi.list()
      .then(setRates)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load labor rates'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ effectiveFrom: dayjs().format('YYYY-MM-DD') as any })
    setOpen(true)
  }

  const startEdit = (rate: LaborRate) => {
    setEditing(rate)
    form.setFieldsValue({
      gradeId: rate.gradeId,
      positionId: rate.positionId,
      hourlyRate: rate.hourlyRate,
      effectiveFrom: rate.effectiveFrom,
      effectiveTo: rate.effectiveTo,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    const payload: LaborRateRequest = {
      gradeId: values.gradeId || undefined,
      positionId: values.positionId || undefined,
      hourlyRate: values.hourlyRate,
      effectiveFrom: typeof values.effectiveFrom === 'string' ? values.effectiveFrom : dayjs(values.effectiveFrom).format('YYYY-MM-DD'),
      effectiveTo: values.effectiveTo ? (typeof values.effectiveTo === 'string' ? values.effectiveTo : dayjs(values.effectiveTo).format('YYYY-MM-DD')) : undefined,
    }
    setSaving(true)
    const api = editing ? laborRatesApi.update(editing.id, payload) : laborRatesApi.create(payload)
    api
      .then(() => {
        message.success(editing ? 'Labor rate updated' : 'Labor rate created')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to save'))
      .finally(() => setSaving(false))
  }

  const columns: ColumnsType<LaborRate> = [
    {
      title: 'Grade',
      dataIndex: 'gradeId',
      key: 'gradeId',
      render: id => id ? id.slice(0, 8) : '—',
    },
    {
      title: 'Position',
      dataIndex: 'positionId',
      key: 'positionId',
      render: id => id ? id.slice(0, 8) : '—',
    },
    {
      title: 'Hourly Rate',
      dataIndex: 'hourlyRate',
      key: 'hourlyRate',
      align: 'right',
      render: r => Number(r).toFixed(2),
    },
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      render: d => dayjs(d).format('YYYY-MM-DD'),
    },
    {
      title: 'Effective To',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      render: d => d ? dayjs(d).format('YYYY-MM-DD') : 'Open',
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
    <div style={{ padding: 24 }}>
      <Title level={2}>Labor Rates</Title>
      <Text type="secondary">
        Hourly labor rates by grade or position. Used for labor cost calculations.
      </Text>

      <Card style={{ marginTop: 24 }}>
        <div style={{ marginBottom: 16, textAlign: 'right' }}>
          {canWrite && (
            <Button type="primary" onClick={startCreate}>Create Labor Rate</Button>
          )}
        </div>
        <Table
          dataSource={rates}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title={editing ? 'Edit Labor Rate' : 'Create Labor Rate'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        width={600}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            label="Grade ID (optional)"
            name="gradeId"
            help="Leave blank if rate applies to a specific position"
          >
            <Input placeholder="Grade UUID" />
          </Form.Item>
          <Form.Item
            label="Position ID (optional)"
            name="positionId"
            help="Leave blank if rate applies to a grade"
          >
            <Input placeholder="Position UUID" />
          </Form.Item>
          <Form.Item label="Hourly Rate" name="hourlyRate" rules={[{ required: true }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Effective From" name="effectiveFrom" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Effective To (optional)" name="effectiveTo">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
