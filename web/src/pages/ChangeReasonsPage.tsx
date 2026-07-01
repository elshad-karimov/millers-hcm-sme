import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
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
import {
  compensationApi,
  type ChangeReasonResponse,
  type ChangeReasonRequest,
  type ChangeReasonCategory,
} from '../api/compensation'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title } = Typography

const CATEGORY_COLOR: Record<ChangeReasonCategory, string> = {
  MERIT: 'blue',
  PROMOTION: 'green',
  MARKET: 'orange',
  ADJUSTMENT: 'purple',
  OTHER: 'default',
}

const CATEGORIES: { value: ChangeReasonCategory; label: string }[] = [
  { value: 'MERIT', label: 'Merit' },
  { value: 'PROMOTION', label: 'Promotion' },
  { value: 'MARKET', label: 'Market' },
  { value: 'ADJUSTMENT', label: 'Adjustment' },
  { value: 'OTHER', label: 'Other' },
]

interface FormValues {
  code: string
  name: string
  category: ChangeReasonCategory
  affectsWorkflow: boolean
  isActive: boolean
}

export function ChangeReasonsPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [rows, setRows] = useState<ChangeReasonResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<ChangeReasonResponse | null>(null)
  const [form] = Form.useForm<FormValues>()

  const load = () => {
    setLoading(true)
    compensationApi
      .listChangeReasons()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load change reasons'),
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
    form.setFieldsValue({ isActive: true, affectsWorkflow: false })
    setDrawerOpen(true)
  }

  const openEdit = (r: ChangeReasonResponse) => {
    setEditing(r)
    form.setFieldsValue({
      code: r.code,
      name: r.name,
      category: r.category,
      affectsWorkflow: r.affectsWorkflow,
      isActive: r.isActive,
    })
    setDrawerOpen(true)
  }

  const submit = async (v: FormValues) => {
    const payload: ChangeReasonRequest = {
      code: v.code,
      name: v.name,
      category: v.category,
      affectsWorkflow: v.affectsWorkflow,
      isActive: v.isActive,
    }

    try {
      if (editing) {
        await compensationApi.updateChangeReason(editing.id, payload)
        message.success('Change reason updated')
      } else {
        await compensationApi.createChangeReason(payload)
        message.success('Change reason created')
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

  const handleDeactivate = (r: ChangeReasonResponse) => {
    modal.confirm({
      title: 'Deactivate Change Reason?',
      content: `Deactivate ${r.code} — ${r.name}?`,
      okText: 'Deactivate',
      onOk: async () => {
        try {
          await compensationApi.deactivateChangeReason(r.id)
          message.success('Change reason deactivated')
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

  const columns: ColumnsType<ChangeReasonResponse> = [
    { title: 'Code', dataIndex: 'code', key: 'code', width: 150 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      render: (cat: ChangeReasonCategory) => <Tag color={CATEGORY_COLOR[cat]}>{cat}</Tag>,
    },
    {
      title: 'Affects Workflow',
      dataIndex: 'affectsWorkflow',
      key: 'affectsWorkflow',
      render: (v) => (v ? 'Yes' : 'No'),
    },
    {
      title: 'Active',
      dataIndex: 'isActive',
      key: 'isActive',
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
        <Title level={2}>Change Reasons</Title>
        {canWrite && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Create Change Reason
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
        title={editing ? 'Edit Change Reason' : 'Create Change Reason'}
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

          <Form.Item
            name="category"
            label="Category"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select options={CATEGORIES} />
          </Form.Item>

          <Form.Item name="affectsWorkflow" label="Affects Workflow" valuePropName="checked">
            <Switch />
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
