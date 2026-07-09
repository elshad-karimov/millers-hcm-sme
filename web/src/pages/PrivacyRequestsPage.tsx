import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  complianceApi,
  type PrivacyRequest,
  type PrivacyRequestStatus,
  type PrivacyRequestType,
} from '../api/compliance'

const TYPE_COLOR: Record<PrivacyRequestType, string> = {
  ACCESS: 'blue',
  EXPORT: 'cyan',
  DELETE: 'red',
  CORRECTION: 'orange',
}

const STATUS_COLOR: Record<PrivacyRequestStatus, string> = {
  OPEN: 'default',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  REJECTED: 'error',
}

export function PrivacyRequestsPage() {
  const { message } = AntdApp.useApp()
  const [requests, setRequests] = useState<PrivacyRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<PrivacyRequest | null>(null)
  const [form] = Form.useForm<PrivacyRequest>()
  const [statusModalOpen, setStatusModalOpen] = useState(false)
  const [statusForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const data = await complianceApi.listPrivacyRequests()
      setRequests(data)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load requests',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSubmit = async () => {
    const values = await form.validateFields()
    const payload: any = {
      ...values,
      dueDate: values.dueDate ? dayjs(values.dueDate).format('YYYY-MM-DD') : dayjs().add(30, 'day').format('YYYY-MM-DD'),
    }
    try {
      if (editing) {
        await complianceApi.updatePrivacyRequest(editing.id, { ...editing, ...payload })
        message.success('Request updated')
      } else {
        await complianceApi.createPrivacyRequest(payload)
        message.success('Request created')
      }
      setModalOpen(false)
      form.resetFields()
      setEditing(null)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to save request',
      )
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await complianceApi.deletePrivacyRequest(id)
      message.success('Request deleted')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to delete request',
      )
    }
  }

  const handleUpdateStatus = async () => {
    if (!editing) return
    const values = await statusForm.validateFields()
    try {
      await complianceApi.updatePrivacyRequestStatus(
        editing.id,
        values.status,
        values.resolutionNotes,
      )
      message.success('Status updated')
      setStatusModalOpen(false)
      statusForm.resetFields()
      setEditing(null)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to update status',
      )
    }
  }

  const columns: ColumnsType<PrivacyRequest> = [
    {
      title: 'Request Type',
      dataIndex: 'requestType',
      width: 130,
      render: (v: PrivacyRequestType) => <Tag color={TYPE_COLOR[v]}>{v}</Tag>,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 140,
      render: (v: PrivacyRequestStatus) => (
        <Tag color={STATUS_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: 'Due Date',
      dataIndex: 'dueDate',
      width: 120,
      render: (v: string) => {
        const date = dayjs(v)
        const daysUntil = date.diff(dayjs(), 'day')
        return (
          <span style={{ color: daysUntil < 0 ? '#ff4d4f' : daysUntil < 7 ? '#fa8c16' : undefined }}>
            {date.format('YYYY-MM-DD')}
          </span>
        )
      },
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      width: 180,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '',
      width: 200,
      render: (_, r) => (
        <Space size="small">
          <Button
            size="small"
            onClick={() => {
              setEditing(r)
              form.setFieldsValue({
                ...r,
                dueDate: r.dueDate ? dayjs(r.dueDate) as any : undefined,
              })
              setModalOpen(true)
            }}
          >
            Edit
          </Button>
          <Button
            size="small"
            onClick={() => {
              setEditing(r)
              statusForm.setFieldsValue({ status: r.status, resolutionNotes: r.resolutionNotes })
              setStatusModalOpen(true)
            }}
          >
            Status
          </Button>
          <Popconfirm title="Delete this request?" onConfirm={() => handleDelete(r.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="Privacy Requests"
        extra={
          <Button type="primary" onClick={() => setModalOpen(true)}>
            New Request
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={requests}
          loading={loading}
          pagination={{ pageSize: 20 }}
          size="small"
        />
      </Card>

      <Modal
        title={editing ? 'Edit Privacy Request' : 'New Privacy Request'}
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
            name="requestType"
            label="Request Type"
            rules={[{ required: true, message: 'Type is required' }]}
          >
            <Select>
              <Select.Option value="ACCESS">ACCESS</Select.Option>
              <Select.Option value="EXPORT">EXPORT</Select.Option>
              <Select.Option value="DELETE">DELETE</Select.Option>
              <Select.Option value="CORRECTION">CORRECTION</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="description"
            label="Description"
            rules={[{ required: true, message: 'Description is required' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="dueDate" label="Due Date">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="resolutionNotes" label="Resolution Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Update Request Status"
        open={statusModalOpen}
        onCancel={() => {
          setStatusModalOpen(false)
          statusForm.resetFields()
          setEditing(null)
        }}
        onOk={handleUpdateStatus}
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item
            name="status"
            label="Status"
            rules={[{ required: true, message: 'Status is required' }]}
          >
            <Select>
              <Select.Option value="OPEN">OPEN</Select.Option>
              <Select.Option value="IN_PROGRESS">IN PROGRESS</Select.Option>
              <Select.Option value="COMPLETED">COMPLETED</Select.Option>
              <Select.Option value="REJECTED">REJECTED</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="resolutionNotes" label="Resolution Notes">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
