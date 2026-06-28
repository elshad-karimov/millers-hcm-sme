import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi, type LeaveCategory, type LeaveCategoryRequest } from '../api/leave'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title } = Typography

export function LeaveCategoriesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canEdit = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<LeaveCategory[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<LeaveCategory | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<LeaveCategoryRequest>()

  const load = () => {
    setLoading(true)
    leaveApi
      .categories()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load categories'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openAdd = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ paidDefault: true, active: true })
    setOpen(true)
  }

  const openEdit = (r: LeaveCategory) => {
    setEditing(r)
    form.setFieldsValue({
      code: r.code,
      name: r.name,
      description: r.description ?? '',
      paidDefault: r.paidDefault,
      reportingGroup: r.reportingGroup ?? '',
      active: r.active,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    const p = editing
      ? leaveApi.updateCategory(editing.id, values)
      : leaveApi.createCategory(values)
    p.then(() => {
      message.success(editing ? 'Category updated' : 'Category created')
      setOpen(false)
      load()
    })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Save failed'),
      )
      .finally(() => setSaving(false))
  }

  const handleDeactivate = (r: LeaveCategory) => {
    leaveApi
      .deactivateCategory(r.id)
      .then(() => {
        message.success('Category deactivated')
        load()
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed'),
      )
  }

  const columns: ColumnsType<LeaveCategory> = [
    { title: 'Code', dataIndex: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Reporting Group',
      dataIndex: 'reportingGroup',
      width: 150,
      render: (v?: string | null) => v ?? '—',
    },
    {
      title: 'Default',
      dataIndex: 'paidDefault',
      width: 90,
      render: (v: boolean) => (
        <Tag color={v ? 'green' : 'default'}>{v ? 'Paid' : 'Unpaid'}</Tag>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) => (
        <Tag color={v ? 'green' : 'default'}>{v ? 'Active' : 'Inactive'}</Tag>
      ),
    },
    ...(canEdit
      ? [
          {
            title: '',
            width: 120,
            render: (_: unknown, r: LeaveCategory) => (
              <Space>
                <Button size="small" onClick={() => openEdit(r)}>
                  Edit
                </Button>
                {r.active && (
                  <Button size="small" danger onClick={() => handleDeactivate(r)}>
                    Deactivate
                  </Button>
                )}
              </Space>
            ),
          },
        ]
      : []),
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={<Title level={4} style={{ margin: 0 }}>Leave Categories</Title>}
        extra={canEdit && <Button type="primary" onClick={openAdd}>Add Category</Button>}
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        title={editing ? 'Edit Category' : 'Add Category'}
        open={open}
        onOk={handleSave}
        onCancel={() => setOpen(false)}
        confirmLoading={saving}
        destroyOnHidden
        width={480}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="Code" rules={[{ required: true }]}>
            <Input disabled={!!editing} placeholder="e.g. MEDICAL" />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="reportingGroup" label="Reporting Group">
            <Input placeholder="e.g. STATUTORY" />
          </Form.Item>
          <Form.Item name="paidDefault" label="Paid by default" valuePropName="checked">
            <Switch />
          </Form.Item>
          {editing && (
            <Form.Item name="active" label="Active" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  )
}
