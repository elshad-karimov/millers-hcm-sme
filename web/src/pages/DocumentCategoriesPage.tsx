import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Switch,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, EditOutlined } from '@ant-design/icons'
import { api } from '../api/client'

interface DocumentCategory {
  id: string
  code: string
  name: string
  mandatory: boolean
  retentionDays?: number
  autoRenewal: boolean
  sortOrder?: number
  active: boolean
  createdAt: string
  createdBy: string
}

export function DocumentCategoriesPage() {
  const { message } = AntdApp.useApp()
  const [categories, setCategories] = useState<DocumentCategory[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState<DocumentCategory | null>(null)

  const [createForm] = Form.useForm()
  const [editForm] = Form.useForm()

  const fetchCategories = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/api/documents/categories')
      setCategories(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load categories')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCategories()
  }, [])

  const handleCreate = async (values: any) => {
    try {
      await api.post('/api/documents/categories', {
        code: values.code,
        name: values.name,
        mandatory: values.mandatory ?? false,
        retentionDays: values.retentionDays || null,
        autoRenewal: values.autoRenewal ?? false,
        sortOrder: values.sortOrder || null,
      })
      message.success('Document category created')
      setCreateOpen(false)
      createForm.resetFields()
      fetchCategories()
    } catch (err: any) {
      message.error(err.message || 'Failed to create category')
    }
  }

  const handleUpdate = async (categoryId: string, values: any) => {
    try {
      await api.put(`/api/documents/categories/${categoryId}`, {
        name: values.name,
        mandatory: values.mandatory,
        retentionDays: values.retentionDays || null,
        autoRenewal: values.autoRenewal,
        sortOrder: values.sortOrder || null,
        active: values.active,
      })
      message.success('Category updated')
      setEditOpen(null)
      editForm.resetFields()
      fetchCategories()
    } catch (err: any) {
      message.error(err.message || 'Failed to update category')
    }
  }

  const columns: ColumnsType<DocumentCategory> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 150,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Mandatory',
      dataIndex: 'mandatory',
      key: 'mandatory',
      width: 120,
      render: (val: boolean) => (val ? <Tag color="red">Yes</Tag> : <Tag>No</Tag>),
    },
    {
      title: 'Retention Days',
      dataIndex: 'retentionDays',
      key: 'retentionDays',
      width: 140,
      align: 'right',
      render: (val) => val || '—',
    },
    {
      title: 'Auto Renewal',
      dataIndex: 'autoRenewal',
      key: 'autoRenewal',
      width: 130,
      render: (val: boolean) => (val ? <Tag color="blue">Yes</Tag> : <Tag>No</Tag>),
    },
    {
      title: 'Sort Order',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 110,
      align: 'right',
      render: (val) => val || '—',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      render: (val: boolean) =>
        val ? <Tag color="success">Active</Tag> : <Tag color="default">Inactive</Tag>,
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, rec) => (
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          onClick={() => {
            setEditOpen(rec)
            editForm.setFieldsValue({
              name: rec.name,
              mandatory: rec.mandatory,
              retentionDays: rec.retentionDays,
              autoRenewal: rec.autoRenewal,
              sortOrder: rec.sortOrder,
              active: rec.active,
            })
          }}
        >
          Edit
        </Button>
      ),
    },
  ]

  return (
    <Card
      title="Document Categories"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          New Category
        </Button>
      }
    >
      <Table
        dataSource={categories}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1200 }}
      />

      {/* Create Modal */}
      <Modal
        title="New Document Category"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          createForm.resetFields()
        }}
        onOk={() => createForm.submit()}
        width={600}
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="code"
            label="Code"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. CONTRACT, PASSPORT" />
          </Form.Item>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g. Employment Contract, Passport Copy" />
          </Form.Item>
          <Form.Item name="mandatory" label="Mandatory" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="retentionDays" label="Retention Days (optional)">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="e.g. 365" />
          </Form.Item>
          <Form.Item name="autoRenewal" label="Auto Renewal" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="sortOrder" label="Sort Order (optional)">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        title={`Edit Category: ${editOpen?.code}`}
        open={!!editOpen}
        onCancel={() => {
          setEditOpen(null)
          editForm.resetFields()
        }}
        onOk={() => editForm.submit()}
        width={600}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={(values) => {
            if (editOpen) handleUpdate(editOpen.id, values)
          }}
        >
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="mandatory" label="Mandatory" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="retentionDays" label="Retention Days">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="autoRenewal" label="Auto Renewal" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="sortOrder" label="Sort Order">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
