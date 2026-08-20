import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import { api } from '../api/client'
import dayjs from 'dayjs'

type CorrectiveActionStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE' | 'ESCALATED'

interface CorrectiveActionPlan {
  id: string
  planNumber: string
  erCaseId?: string
  disciplinaryActionId?: string
  employeeId: string
  employeeName?: string
  employeeNo?: string
  actionRequired: string
  responsibleUsername: string
  dueDate: string
  followUpDate?: string
  status: CorrectiveActionStatus
  completedAt?: string
  notes?: string
  createdAt: string
  createdBy: string
}

const STATUS_OPTIONS: CorrectiveActionStatus[] = [
  'PLANNED',
  'IN_PROGRESS',
  'COMPLETED',
  'OVERDUE',
  'ESCALATED',
]

const STATUS_COLOR: Record<CorrectiveActionStatus, string> = {
  PLANNED: 'blue',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  OVERDUE: 'error',
  ESCALATED: 'warning',
}

export function CorrectiveActionsPage() {
  const { message } = AntdApp.useApp()
  const [actions, setActions] = useState<CorrectiveActionPlan[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [statusModal, setStatusModal] = useState<string | null>(null)
  const [filters, setFilters] = useState<{
    status?: CorrectiveActionStatus
    responsible?: string
  }>({})

  const [form] = Form.useForm()
  const [statusForm] = Form.useForm()

  const fetchActions = async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (filters.status) params.append('status', filters.status)
      if (filters.responsible) params.append('responsible', filters.responsible)

      const { data } = await api.get(`/er/corrective-actions?${params}`)
      setActions(data)
    } catch (err: any) {
      message.error(err.message || 'Failed to load corrective actions')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchActions()
  }, [filters])

  const handleCreate = async (values: any) => {
    try {
      await api.post('/er/corrective-actions', {
        erCaseId: values.erCaseId || null,
        disciplinaryActionId: values.disciplinaryActionId || null,
        employeeId: values.employeeId,
        actionRequired: values.actionRequired,
        responsibleUsername: values.responsibleUsername,
        dueDate: values.dueDate.format('YYYY-MM-DD'),
        followUpDate: values.followUpDate?.format('YYYY-MM-DD') || null,
      })
      message.success('Corrective action created')
      setCreateOpen(false)
      form.resetFields()
      fetchActions()
    } catch (err: any) {
      message.error(err.message || 'Failed to create corrective action')
    }
  }

  const handleStatusUpdate = async (actionId: string, values: any) => {
    try {
      await api.put(`/er/corrective-actions/${actionId}/status`, {
        status: values.status,
        notes: values.notes,
      })
      message.success('Status updated')
      setStatusModal(null)
      statusForm.resetFields()
      fetchActions()
    } catch (err: any) {
      message.error(err.message || 'Failed to update status')
    }
  }

  const columns: ColumnsType<CorrectiveActionPlan> = [
    {
      title: 'Plan #',
      dataIndex: 'planNumber',
      key: 'planNumber',
      width: 140,
    },
    {
      title: 'Employee',
      key: 'employee',
      width: 200,
      render: (_, rec) =>
        rec.employeeName ? `${rec.employeeName} (${rec.employeeNo})` : rec.employeeId,
    },
    {
      title: 'Action Required',
      dataIndex: 'actionRequired',
      key: 'actionRequired',
    },
    {
      title: 'Responsible',
      dataIndex: 'responsibleUsername',
      key: 'responsibleUsername',
      width: 140,
    },
    {
      title: 'Due Date',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 140,
      render: (val) => {
        const date = dayjs(val)
        const isOverdue = date.isBefore(dayjs(), 'day')
        return (
          <span style={{ color: isOverdue ? 'red' : 'inherit' }}>
            {date.format('YYYY-MM-DD')}
            {isOverdue && <Tag color="red" style={{ marginLeft: 8 }}>OVERDUE</Tag>}
          </span>
        )
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: CorrectiveActionStatus) => (
        <Tag color={STATUS_COLOR[status]}>{status}</Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, rec) => (
        <Space size="small">
          {rec.status === 'PLANNED' && (
            <Button
              size="small"
              type="link"
              onClick={() => {
                setStatusModal(rec.id)
                statusForm.setFieldsValue({ status: 'IN_PROGRESS' })
              }}
            >
              Start
            </Button>
          )}
          {rec.status === 'IN_PROGRESS' && (
            <Button
              size="small"
              type="link"
              onClick={() => {
                setStatusModal(rec.id)
                statusForm.setFieldsValue({ status: 'COMPLETED' })
              }}
            >
              Complete
            </Button>
          )}
          {(rec.status === 'PLANNED' || rec.status === 'IN_PROGRESS') && (
            <Button
              size="small"
              type="link"
              danger
              onClick={() => {
                setStatusModal(rec.id)
                statusForm.setFieldsValue({ status: 'ESCALATED' })
              }}
            >
              Escalate
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      title="Corrective Actions"
      extra={
        <Space>
          <Select
            placeholder="Status"
            allowClear
            style={{ width: 140 }}
            value={filters.status}
            onChange={(status) => setFilters({ ...filters, status })}
          >
            {STATUS_OPTIONS.map((st) => (
              <Select.Option key={st} value={st}>
                {st}
              </Select.Option>
            ))}
          </Select>
          <Input
            placeholder="Filter by Responsible"
            style={{ width: 180 }}
            value={filters.responsible}
            onChange={(e) => setFilters({ ...filters, responsible: e.target.value })}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            New Action Plan
          </Button>
        </Space>
      }
    >
      <Table
        dataSource={actions}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1400 }}
      />

      {/* Create Modal */}
      <Modal
        title="New Corrective Action Plan"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="employeeId"
            label="Employee ID"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="Employee UUID" />
          </Form.Item>
          <Form.Item
            name="actionRequired"
            label="Action Required"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input.TextArea rows={3} placeholder="Describe the corrective action" />
          </Form.Item>
          <Form.Item
            name="responsibleUsername"
            label="Responsible (Username)"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="Assign to user" />
          </Form.Item>
          <Form.Item
            name="dueDate"
            label="Due Date"
            rules={[{ required: true, message: 'Required' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="followUpDate" label="Follow-up Date (optional)">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="erCaseId" label="ER Case ID (optional)">
            <Input placeholder="UUID" />
          </Form.Item>
          <Form.Item name="disciplinaryActionId" label="Disciplinary Action ID (optional)">
            <Input placeholder="UUID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Status Update Modal */}
      <Modal
        title="Update Status"
        open={!!statusModal}
        onCancel={() => {
          setStatusModal(null)
          statusForm.resetFields()
        }}
        onOk={() => statusForm.submit()}
      >
        <Form
          form={statusForm}
          layout="vertical"
          onFinish={(values) => {
            if (statusModal) handleStatusUpdate(statusModal, values)
          }}
        >
          <Form.Item
            name="status"
            label="Status"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select>
              {STATUS_OPTIONS.map((st) => (
                <Select.Option key={st} value={st}>
                  {st}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
