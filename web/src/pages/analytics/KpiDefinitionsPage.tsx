// M473 — KPI definitions CRUD (HR_ADMIN only).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { kpiDefinitionsApi, type KpiDefinition, type KpiCategory } from '../../api/analytics'

const KPI_CATEGORIES: KpiCategory[] = [
  'HEADCOUNT',
  'TURNOVER',
  'COST',
  'COMPLIANCE',
  'ENGAGEMENT',
  'LEARNING',
]

const CATEGORY_COLORS: Record<KpiCategory, string> = {
  HEADCOUNT: 'blue',
  TURNOVER: 'orange',
  COST: 'green',
  COMPLIANCE: 'purple',
  ENGAGEMENT: 'cyan',
  LEARNING: 'magenta',
}

export function KpiDefinitionsPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<KpiDefinition[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    kpiDefinitionsApi
      .listAll(false)
      .then(setRows)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    setEditingId(null)
    setModalOpen(true)
  }

  const openEdit = (kpi: KpiDefinition) => {
    form.setFieldsValue(kpi)
    setEditingId(kpi.id!)
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingId) {
        await kpiDefinitionsApi.update(editingId, values)
        message.success('KPI updated')
      } else {
        await kpiDefinitionsApi.create(values)
        message.success('KPI created')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: 'Delete KPI?',
      content: 'This action cannot be undone.',
      onOk: async () => {
        try {
          await kpiDefinitionsApi.delete(id)
          message.success('KPI deleted')
          load()
        } catch (e: any) {
          message.error(e?.response?.data?.message ?? 'Delete failed')
        }
      },
    })
  }

  const columns: ColumnsType<KpiDefinition> = [
    { title: 'Code', dataIndex: 'code', width: 180 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Category',
      dataIndex: 'category',
      width: 140,
      render: (cat: KpiCategory) => <Tag color={CATEGORY_COLORS[cat]}>{cat}</Tag>,
    },
    { title: 'Unit', dataIndex: 'unit', width: 100 },
    {
      title: 'Target',
      dataIndex: 'targetValue',
      width: 100,
      align: 'right',
      render: (v) => (v != null ? v : '—'),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (a: boolean) => <Tag color={a ? 'green' : 'default'}>{a ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Actions',
      width: 150,
      render: (_, r) => (
        <Space>
          <a onClick={() => openEdit(r)}>Edit</a>
          <a onClick={() => handleDelete(r.id!)} style={{ color: 'red' }}>
            Delete
          </a>
        </Space>
      ),
    },
  ]

  return (
    <Card title="KPI Definitions" extra={<Button type="primary" onClick={openCreate}>Create KPI</Button>}>
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} />

      <Modal
        title={editingId ? 'Edit KPI' : 'Create KPI'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="Code" rules={[{ required: true, message: 'Required' }]}>
            <Input placeholder="HEADCOUNT_ACTIVE" />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Required' }]}>
            <Input placeholder="Active Headcount" />
          </Form.Item>
          <Form.Item name="category" label="Category" rules={[{ required: true, message: 'Required' }]}>
            <Select>
              {KPI_CATEGORIES.map((cat) => (
                <Select.Option key={cat} value={cat}>
                  {cat}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="unit" label="Unit">
            <Input placeholder="employees, %, AZN, days" />
          </Form.Item>
          <Form.Item name="targetValue" label="Target Value">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
