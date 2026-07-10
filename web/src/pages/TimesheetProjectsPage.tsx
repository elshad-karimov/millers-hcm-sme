// M484 — Timesheet projects (HR admin)

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  timesheetProjectsApi,
  type TimesheetProject,
  type TimesheetProjectRequest,
} from '../api/timesheetProjects'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

export function TimesheetProjectsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [projects, setProjects] = useState<TimesheetProject[]>([])
  const [loading, setLoading] = useState(true)
  const [activeOnly, setActiveOnly] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<TimesheetProject | null>(null)
  const [form] = Form.useForm<TimesheetProjectRequest>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    timesheetProjectsApi.list(activeOnly)
      .then(setProjects)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load projects'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() /* eslint-disable-next-line */ }, [activeOnly])

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ active: true })
    setOpen(true)
  }

  const startEdit = (proj: TimesheetProject) => {
    setEditing(proj)
    form.setFieldsValue({
      code: proj.code,
      name: proj.name,
      description: proj.description,
      billingRate: proj.billingRate,
      active: proj.active,
    })
    setOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    const api = editing ? timesheetProjectsApi.update(editing.id, values) : timesheetProjectsApi.create(values)
    api
      .then(() => {
        message.success(editing ? 'Project updated' : 'Project created')
        setOpen(false)
        load()
      })
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to save'))
      .finally(() => setSaving(false))
  }

  const columns: ColumnsType<TimesheetProject> = [
    { title: 'Code', dataIndex: 'code', key: 'code' },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Description', dataIndex: 'description', key: 'description', render: d => d || '—' },
    {
      title: 'Billing Rate',
      dataIndex: 'billingRate',
      key: 'billingRate',
      align: 'right',
      render: r => r ? Number(r).toFixed(2) : '—',
    },
    {
      title: 'Status',
      dataIndex: 'active',
      key: 'active',
      render: a => <Tag color={a ? 'green' : 'default'}>{a ? 'Active' : 'Inactive'}</Tag>,
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
      <Title level={2}>Timesheet Projects</Title>
      <Text type="secondary">
        Projects for timesheet billing and costing. Used as a dimension in labor cost reports.
      </Text>

      <Card style={{ marginTop: 24 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Row justify="space-between" align="middle">
            <Col>
              <Space>
                <Text>Show:</Text>
                <Select
                  value={activeOnly ? 'active' : 'all'}
                  onChange={v => setActiveOnly(v === 'active')}
                  style={{ width: 120 }}
                >
                  <Select.Option value="active">Active only</Select.Option>
                  <Select.Option value="all">All projects</Select.Option>
                </Select>
              </Space>
            </Col>
            {canWrite && (
              <Col>
                <Button type="primary" onClick={startCreate}>Create Project</Button>
              </Col>
            )}
          </Row>
          <Table
            dataSource={projects}
            columns={columns}
            rowKey="id"
            loading={loading}
            pagination={{ pageSize: 20 }}
          />
        </Space>
      </Card>

      <Modal
        title={editing ? 'Edit Project' : 'Create Project'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        width={600}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Code" name="code" rules={[{ required: true }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item label="Name" name="name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Description" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="Billing Rate (per hour)" name="billingRate">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
