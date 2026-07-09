import { useEffect, useState } from 'react'
import {
  Button,
  Card,
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
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  complianceApi,
  type ComplianceDeadline,
  type UpcomingDeadline,
} from '../api/compliance'

export function ComplianceCalendarPage() {
  const { message } = AntdApp.useApp()
  const [deadlines, setDeadlines] = useState<ComplianceDeadline[]>([])
  const [upcoming, setUpcoming] = useState<UpcomingDeadline[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ComplianceDeadline | null>(null)
  const [form] = Form.useForm<ComplianceDeadline>()
  const [upcomingDays, setUpcomingDays] = useState(60)

  const load = async () => {
    setLoading(true)
    try {
      const [d, u] = await Promise.all([
        complianceApi.listDeadlines(),
        complianceApi.upcomingDeadlines(upcomingDays),
      ])
      setDeadlines(d)
      setUpcoming(u)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load deadlines',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [upcomingDays])

  const handleSubmit = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await complianceApi.updateDeadline(editing.id, { ...editing, ...values })
        message.success('Deadline updated')
      } else {
        await complianceApi.createDeadline(values)
        message.success('Deadline created')
      }
      setModalOpen(false)
      form.resetFields()
      setEditing(null)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to save deadline',
      )
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await complianceApi.deleteDeadline(id)
      message.success('Deadline deleted')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to delete deadline',
      )
    }
  }

  const columns: ColumnsType<ComplianceDeadline> = [
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Frequency',
      dataIndex: 'frequency',
      width: 120,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    { title: 'Due day', dataIndex: 'dueDay', width: 90 },
    {
      title: 'Month',
      dataIndex: 'month',
      width: 100,
      render: (v?: number) => (v ? `Month ${v}` : '—'),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? 'Yes' : 'No'}</Tag>,
    },
    {
      title: '',
      width: 150,
      render: (_, r) => (
        <Space size="small">
          <Button
            size="small"
            onClick={() => {
              setEditing(r)
              form.setFieldsValue(r)
              setModalOpen(true)
            }}
          >
            Edit
          </Button>
          <Popconfirm title="Delete this deadline?" onConfirm={() => handleDelete(r.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const upcomingColumns: ColumnsType<UpcomingDeadline> = [
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Frequency',
      dataIndex: 'frequency',
      width: 120,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    { title: 'Next Due', dataIndex: 'nextDue', width: 120 },
    {
      title: 'Days Until',
      dataIndex: 'daysUntil',
      width: 120,
      render: (v: number) => (
        <Tag color={v < 0 ? 'red' : v < 7 ? 'orange' : v < 30 ? 'gold' : 'green'}>
          {v < 0 ? `${Math.abs(v)} days overdue` : `${v} days`}
        </Tag>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="Compliance Deadlines"
        extra={
          <Button type="primary" onClick={() => setModalOpen(true)}>
            New Deadline
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={deadlines}
          loading={loading}
          pagination={false}
          size="small"
        />
      </Card>

      <Card
        title={
          <Space>
            <span>Upcoming Deadlines</span>
            <Select
              value={upcomingDays}
              onChange={setUpcomingDays}
              size="small"
              style={{ width: 120 }}
            >
              <Select.Option value={30}>30 days</Select.Option>
              <Select.Option value={60}>60 days</Select.Option>
              <Select.Option value={90}>90 days</Select.Option>
            </Select>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={upcomingColumns}
          dataSource={upcoming}
          loading={loading}
          pagination={false}
          size="small"
        />
      </Card>

      <Modal
        title={editing ? 'Edit Deadline' : 'New Deadline'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
          setEditing(null)
        }}
        onOk={handleSubmit}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="Title"
            rules={[{ required: true, message: 'Title is required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="frequency"
            label="Frequency"
            rules={[{ required: true, message: 'Frequency is required' }]}
          >
            <Select>
              <Select.Option value="MONTHLY">Monthly</Select.Option>
              <Select.Option value="QUARTERLY">Quarterly</Select.Option>
              <Select.Option value="ANNUAL">Annual</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="dueDay"
            label="Due Day (of month)"
            rules={[{ required: true, message: 'Due day is required' }]}
          >
            <InputNumber min={1} max={31} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="month"
            label="Month (for annual deadlines)"
            help="Leave empty for monthly/quarterly"
          >
            <InputNumber min={1} max={12} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
