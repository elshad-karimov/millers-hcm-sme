// M77 — HR letter template admin page.

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  letterTemplatesApi,
  type LetterOutputFormat,
  type LetterTemplate,
  type LetterTemplateRequest,
} from '../api/letters'

export function LetterTemplatesPage() {
  const { message } = AntdApp.useApp()
  const [templates, setTemplates] = useState<LetterTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<LetterTemplate | null>(null)
  const [previewing, setPreviewing] = useState<LetterTemplate | null>(null)
  const [form] = Form.useForm<LetterTemplateRequest>()

  const load = () => {
    setLoading(true)
    letterTemplatesApi
      .list(false)
      .then(setTemplates)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load templates'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      outputFormat: 'TEXT',
      requiresApproval: true,
      active: true,
    } as LetterTemplateRequest)
    setDrawerOpen(true)
  }

  const openEdit = (t: LetterTemplate) => {
    setEditing(t)
    const placeholdersJsonString =
      t.placeholdersJson != null ? JSON.stringify(t.placeholdersJson, null, 2) : ''
    form.setFieldsValue({
      code: t.code,
      name: t.name,
      description: t.description ?? undefined,
      body: t.body,
      // form holds JSON as a string; we parse it back on submit
      placeholdersJson: placeholdersJsonString as unknown as Record<string, string>,
      outputFormat: t.outputFormat,
      requiresApproval: t.requiresApproval,
      active: t.active,
    })
    setDrawerOpen(true)
  }

  const onFinish = async (values: LetterTemplateRequest) => {
    let placeholdersJson: Record<string, string> | undefined
    const rawPlaceholders = values.placeholdersJson as unknown
    if (typeof rawPlaceholders === 'string' && rawPlaceholders.trim()) {
      try {
        placeholdersJson = JSON.parse(rawPlaceholders)
      } catch {
        message.error('Placeholders JSON is not valid')
        return
      }
    } else if (rawPlaceholders && typeof rawPlaceholders === 'object') {
      placeholdersJson = rawPlaceholders as Record<string, string>
    }
    const payload: LetterTemplateRequest = {
      ...values,
      placeholdersJson,
    }
    try {
      if (editing) {
        await letterTemplatesApi.update(editing.id, payload)
        message.success('Template updated')
      } else {
        await letterTemplatesApi.create(payload)
        message.success('Template created')
      }
      setDrawerOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const deactivate = async (t: LetterTemplate) => {
    try {
      await letterTemplatesApi.deactivate(t.id)
      message.success('Template deactivated')
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Deactivate failed',
      )
    }
  }

  const columns: ColumnsType<LetterTemplate> = [
    { title: 'Code', dataIndex: 'code', width: 220 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Format',
      dataIndex: 'outputFormat',
      width: 80,
      render: (v: LetterOutputFormat) => <Tag>{v}</Tag>,
    },
    {
      title: 'Approval',
      dataIndex: 'requiresApproval',
      width: 110,
      render: (v: boolean) =>
        v ? <Tag color="blue">REQUIRED</Tag> : <Tag color="green">AUTO-ISSUE</Tag>,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) =>
        v ? <Tag color="green">YES</Tag> : <Tag>INACTIVE</Tag>,
    },
    {
      title: '',
      width: 220,
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => setPreviewing(r)}>Preview</Button>
          <Button size="small" type="link" onClick={() => openEdit(r)}>Edit</Button>
          {r.active && (
            <Popconfirm title="Deactivate this template?" onConfirm={() => deactivate(r)}>
              <Button size="small" type="link" danger>Deactivate</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={<Typography.Title level={4} style={{ margin: 0 }}>Letter templates</Typography.Title>}
        extra={<Button type="primary" onClick={openCreate}>New template</Button>}
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={templates}
          loading={loading}
          pagination={false}
        />
      </Card>

      <Drawer
        open={drawerOpen}
        title={editing ? `Edit ${editing.code}` : 'New letter template'}
        width={720}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item
            label="Code"
            name="code"
            rules={[
              { required: true, message: 'Required' },
              { pattern: /^[A-Z0-9_-]+$/, message: 'Uppercase alphanumeric, _ or -' },
            ]}
          >
            <Input disabled={!!editing} placeholder="EMPLOYMENT_VERIFICATION" />
          </Form.Item>
          <Form.Item label="Name" name="name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Description" name="description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item label="Output format" name="outputFormat" rules={[{ required: true }]}>
            <Select options={[{ value: 'TEXT', label: 'Plain text' }, { value: 'HTML', label: 'HTML' }]} />
          </Form.Item>
          <Form.Item label="Body (use {{employee.firstName}}, {{custom.X}}, {{today}})"
            name="body" rules={[{ required: true }]}>
            <Input.TextArea rows={12} style={{ fontFamily: 'monospace' }} />
          </Form.Item>
          <Form.Item label="Placeholders (JSON: {field: description})" name="placeholdersJson">
            <Input.TextArea rows={4} style={{ fontFamily: 'monospace' }}
              placeholder={'{\n  "purpose": "Reason this letter is requested"\n}'} />
          </Form.Item>
          <Form.Item label="Requires approval" name="requiresApproval" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">{editing ? 'Save changes' : 'Create'}</Button>
            <Button onClick={() => setDrawerOpen(false)}>Cancel</Button>
          </Space>
        </Form>
      </Drawer>

      <Modal
        open={!!previewing}
        title={previewing ? `Preview: ${previewing.name}` : ''}
        footer={<Button onClick={() => setPreviewing(null)}>Close</Button>}
        onCancel={() => setPreviewing(null)}
        width={720}
      >
        {previewing && (
          previewing.outputFormat === 'HTML' ? (
            <div dangerouslySetInnerHTML={{ __html: previewing.body }} />
          ) : (
            <pre style={{ whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{previewing.body}</pre>
          )
        )}
      </Modal>
    </Space>
  )
}
