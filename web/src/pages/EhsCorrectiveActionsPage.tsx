import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import {
  correctiveActionsApi,
  type CorrectiveActionResponse,
  type CorrectiveActionStatus,
  type CorrectiveActionPriority,
  CORRECTIVE_ACTION_PRIORITY_OPTIONS,
  CORRECTIVE_ACTION_STATUS_OPTIONS,
  CORRECTIVE_ACTION_PRIORITY_COLOR,
  CORRECTIVE_ACTION_STATUS_COLOR,
} from '../api/ehs'

export function EhsCorrectiveActionsPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [actions, setActions] = useState<CorrectiveActionResponse[]>([])
  const [filterStatus, setFilterStatus] = useState<CorrectiveActionStatus | undefined>()
  const [filterPriority, setFilterPriority] = useState<CorrectiveActionPriority | undefined>()

  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    loadActions()
  }, [])

  const loadActions = async () => {
    setLoading(true)
    try {
      const data = await correctiveActionsApi.list()
      setActions(data)
    } catch (err) {
      message.error('Failed to load corrective actions')
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (values: any) => {
    setSubmitting(true)
    try {
      await correctiveActionsApi.create({
        incidentId: values.incidentId || undefined,
        inspectionId: values.inspectionId || undefined,
        riskAssessmentId: values.riskAssessmentId || undefined,
        description: values.description,
        responsibleUsername: values.responsibleUsername || undefined,
        dueDate: values.dueDate?.format('YYYY-MM-DD') || undefined,
        priority: values.priority || undefined,
      })
      message.success('Corrective action created')
      setModalOpen(false)
      form.resetFields()
      loadActions()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to create action')
    } finally {
      setSubmitting(false)
    }
  }

  const handleStatusChange = async (id: string, status: CorrectiveActionStatus) => {
    try {
      await correctiveActionsApi.updateStatus(id, { status })
      message.success('Status updated')
      loadActions()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to update status')
    }
  }

  const filteredActions = actions.filter((a) => {
    if (filterStatus && a.status !== filterStatus) return false
    if (filterPriority && a.priority !== filterPriority) return false
    return true
  })

  const columns: ColumnsType<CorrectiveActionResponse> = [
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      width: 300,
      ellipsis: true,
    },
    {
      title: 'Responsible',
      dataIndex: 'responsibleUsername',
      key: 'responsibleUsername',
      width: 140,
      render: (text) => text || '—',
    },
    {
      title: 'Due date',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 110,
      sorter: (a, b) => (a.dueDate || '').localeCompare(b.dueDate || ''),
      render: (text) => {
        if (!text) return '—'
        const isOverdue = dayjs(text).isBefore(dayjs(), 'day')
        return isOverdue ? <Tag color="red">{text}</Tag> : text
      },
    },
    {
      title: 'Priority',
      dataIndex: 'priority',
      key: 'priority',
      width: 100,
      render: (val: CorrectiveActionPriority) =>
        val ? <Tag color={CORRECTIVE_ACTION_PRIORITY_COLOR[val]}>{val}</Tag> : '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (val: CorrectiveActionStatus) => (
        <Tag color={CORRECTIVE_ACTION_STATUS_COLOR[val]}>{val.replace('_', ' ')}</Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Select
          size="small"
          value={record.status}
          onChange={(val) => handleStatusChange(record.id, val)}
          options={CORRECTIVE_ACTION_STATUS_OPTIONS}
          style={{ width: 150 }}
        />
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="EHS Corrective Actions"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            New action
          </Button>
        }
      >
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="Filter by status"
            style={{ width: 180 }}
            allowClear
            value={filterStatus}
            onChange={setFilterStatus}
            options={CORRECTIVE_ACTION_STATUS_OPTIONS}
          />
          <Select
            placeholder="Filter by priority"
            style={{ width: 150 }}
            allowClear
            value={filterPriority}
            onChange={setFilterPriority}
            options={CORRECTIVE_ACTION_PRIORITY_OPTIONS}
          />
        </Space>

        <Table
          loading={loading}
          dataSource={filteredActions}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* Create Modal */}
      <Modal
        title="New corrective action"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
        }}
        footer={null}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="description"
            label="Description"
            rules={[{ required: true, max: 1000 }]}
          >
            <Input.TextArea rows={4} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="responsibleUsername" label="Responsible person">
                <Input placeholder="Username" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="dueDate" label="Due date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="priority" label="Priority">
                <Select options={CORRECTIVE_ACTION_PRIORITY_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="incidentId" label="Incident ID">
                <Input placeholder="Optional" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="inspectionId" label="Inspection ID">
                <Input placeholder="Optional" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="riskAssessmentId" label="Risk ID">
                <Input placeholder="Optional" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Create
              </Button>
              <Button onClick={() => { setModalOpen(false); form.resetFields() }}>
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
