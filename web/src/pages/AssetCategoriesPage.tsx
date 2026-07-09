// M456 — Asset category administration (HR_ADMIN write).

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../api/client'

interface AssetCategory {
  id: string
  tenantId: string
  code: string
  name: string
  description?: string
  defaultDepreciationMethod?: string
  defaultUsefulLifeYears?: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export function AssetCategoriesPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<AssetCategory[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [current, setCurrent] = useState<AssetCategory | null>(null)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    api
      .get<AssetCategory[]>('/assets/categories')
      .then((r) => setRows(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load categories'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    setCurrent(null)
    setModalOpen(true)
  }

  const openEdit = (cat: AssetCategory) => {
    setCurrent(cat)
    form.setFieldsValue(cat)
    setModalOpen(true)
  }

  const submit = async () => {
    try {
      const values = await form.validateFields()
      if (current) {
        await api.put(`/assets/categories/${current.id}`, values)
        message.success('Category updated')
      } else {
        await api.post('/assets/categories', values)
        message.success('Category created')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const columns: ColumnsType<AssetCategory> = [
    { title: 'Code', dataIndex: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Description', dataIndex: 'description', ellipsis: true },
    {
      title: 'Depreciation',
      dataIndex: 'defaultDepreciationMethod',
      width: 140,
      render: (v?: string) => (v ? <Tag>{v}</Tag> : '—'),
    },
    {
      title: 'Life (years)',
      dataIndex: 'defaultUsefulLifeYears',
      width: 110,
      align: 'right',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: 'Actions',
      width: 100,
      render: (_, r) => (
        <a onClick={() => openEdit(r)}>Edit</a>
      ),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Asset Categories</Typography.Title>}
      extra={
        <Button type="primary" onClick={openCreate}>
          New Category
        </Button>
      }
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        open={modalOpen}
        title={current ? 'Edit Category' : 'New Category'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText="Save"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="Code" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="defaultDepreciationMethod" label="Default Depreciation Method">
            <Input placeholder="e.g. STRAIGHT_LINE" />
          </Form.Item>
          <Form.Item name="defaultUsefulLifeYears" label="Default Useful Life (years)">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" initialValue={true} valuePropName="checked">
            <Input type="checkbox" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
