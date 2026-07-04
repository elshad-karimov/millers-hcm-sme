// HCM_15 M411 — talent review cycles + panel decisions (PRD §15.7/§15.8).
// HR panels place employees on 9-box, designate HiPo, assess retention risk.
// Decisions roll onto talent_profile (M409). HR-only visibility.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api } from '../../api/client'
import { employeesApi, type Employee } from '../../api/employees'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title } = Typography
const { TextArea } = Input

interface TalentReviewCycle {
  id: string
  name: string
  year: number
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED'
  createdBy?: string
  createdAt: string
}

interface TalentReview {
  id: string
  cycleId: string
  employeeId: string
  performanceBox?: number
  potentialBox?: number
  hipoDecision: boolean
  retentionRisk?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  retentionAction?: string
  notes?: string
  decidedBy?: string
  decidedAt?: string
}

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ACTIVE: 'blue',
  COMPLETED: 'green',
}

const RISK_COLOR: Record<string, string> = {
  LOW: 'green',
  MEDIUM: 'gold',
  HIGH: 'orange',
  CRITICAL: 'red',
}

export function TalentReviewsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [cycles, setCycles] = useState<TalentReviewCycle[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)

  const [cycleOpen, setCycleOpen] = useState(false)
  const [cycleForm] = Form.useForm()

  const [viewingCycle, setViewingCycle] = useState<TalentReviewCycle | null>(null)
  const [reviews, setReviews] = useState<TalentReview[]>([])
  const [loadingReviews, setLoadingReviews] = useState(false)

  const [reviewOpen, setReviewOpen] = useState(false)
  const [reviewForm] = Form.useForm()

  const load = () => {
    setLoading(true)
    Promise.all([
      api.get<TalentReviewCycle[]>('/talent/reviews/cycles'),
      employeesApi.list({ size: 1000 }),
    ])
      .then(([c, e]) => {
        setCycles(c.data)
        setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
      })
      .catch(() => message.error('Failed to load talent review cycles'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const loadReviews = (cycleId: string) => {
    setLoadingReviews(true)
    api
      .get<TalentReview[]>(`/talent/reviews/cycles/${cycleId}/reviews`)
      .then((res) => setReviews(res.data))
      .catch(() => message.error('Failed to load reviews'))
      .finally(() => setLoadingReviews(false))
  }

  const empName = (id?: string) => {
    if (!id) return '—'
    const e = employees.find((e) => e.id === id)
    return e ? `${e.firstName} ${e.lastName}` : id
  }

  const handleCreateCycle = () => {
    cycleForm.validateFields().then((values) => {
      api
        .post('/talent/reviews/cycles', values)
        .then(() => {
          message.success('Cycle created')
          setCycleOpen(false)
          cycleForm.resetFields()
          load()
        })
        .catch(() => message.error('Failed to create cycle'))
    })
  }

  const handleChangeStatus = (cycleId: string, status: string) => {
    api
      .put(`/talent/reviews/cycles/${cycleId}/status?status=${status}`)
      .then(() => {
        message.success('Status updated')
        load()
        if (viewingCycle?.id === cycleId) {
          setViewingCycle((prev) => (prev ? { ...prev, status: status as any } : null))
        }
      })
      .catch(() => message.error('Failed to update status'))
  }

  const handleRecordReview = () => {
    if (!viewingCycle) return
    reviewForm.validateFields().then((values) => {
      api
        .post(`/talent/reviews/cycles/${viewingCycle.id}/reviews`, values)
        .then(() => {
          message.success('Review recorded')
          setReviewOpen(false)
          reviewForm.resetFields()
          loadReviews(viewingCycle.id)
        })
        .catch(() => message.error('Failed to record review'))
    })
  }

  const cycleColumns: ColumnsType<TalentReviewCycle> = [
    {
      title: 'Name',
      dataIndex: 'name',
      sorter: (a, b) => a.name.localeCompare(b.name),
    },
    {
      title: 'Year',
      dataIndex: 'year',
      width: 100,
      sorter: (a, b) => a.year - b.year,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      width: 180,
      render: (v) => new Date(v).toLocaleDateString(),
    },
    {
      title: 'Actions',
      width: 280,
      render: (_, cycle) => (
        <Space>
          <Button
            size="small"
            onClick={() => {
              setViewingCycle(cycle)
              loadReviews(cycle.id)
            }}
          >
            Reviews
          </Button>
          {canWrite && cycle.status !== 'COMPLETED' && (
            <>
              {cycle.status === 'DRAFT' && (
                <Button
                  size="small"
                  type="primary"
                  onClick={() => handleChangeStatus(cycle.id, 'ACTIVE')}
                >
                  Activate
                </Button>
              )}
              {cycle.status === 'ACTIVE' && (
                <Button
                  size="small"
                  onClick={() => handleChangeStatus(cycle.id, 'COMPLETED')}
                >
                  Complete
                </Button>
              )}
            </>
          )}
        </Space>
      ),
    },
  ]

  const reviewColumns: ColumnsType<TalentReview> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id) => empName(id),
    },
    {
      title: 'Performance',
      dataIndex: 'performanceBox',
      width: 100,
      render: (v) => (v ? v : '—'),
    },
    {
      title: 'Potential',
      dataIndex: 'potentialBox',
      width: 100,
      render: (v) => (v ? v : '—'),
    },
    {
      title: 'HiPo',
      dataIndex: 'hipoDecision',
      width: 80,
      render: (v) => (v ? <Tag color="purple">Yes</Tag> : <Tag>No</Tag>),
    },
    {
      title: 'Retention Risk',
      dataIndex: 'retentionRisk',
      width: 140,
      render: (v) => (v ? <Tag color={RISK_COLOR[v]}>{v}</Tag> : '—'),
    },
    {
      title: 'Decided By',
      dataIndex: 'decidedBy',
      width: 150,
    },
    {
      title: 'Decided At',
      dataIndex: 'decidedAt',
      width: 180,
      render: (v) => (v ? new Date(v).toLocaleString() : '—'),
    },
    {
      title: 'Actions',
      width: 80,
      render: (_, review) =>
        canWrite && (
          <Button
            size="small"
            onClick={() => {
              reviewForm.setFieldsValue(review)
              setReviewOpen(true)
            }}
          >
            Edit
          </Button>
        ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Talent Reviews</Title>
        {canWrite && (
          <Button
            type="primary"
            onClick={() => {
              cycleForm.resetFields()
              setCycleOpen(true)
            }}
          >
            New Cycle
          </Button>
        )}
      </div>

      <Card>
        <Table
          dataSource={cycles}
          columns={cycleColumns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title="New Talent Review Cycle"
        open={cycleOpen}
        onOk={handleCreateCycle}
        onCancel={() => {
          setCycleOpen(false)
          cycleForm.resetFields()
        }}
      >
        <Form form={cycleForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input placeholder="Talent Review 2026" />
          </Form.Item>
          <Form.Item
            name="year"
            label="Year"
            rules={[{ required: true, message: 'Year is required' }]}
          >
            <InputNumber min={2020} max={2050} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={viewingCycle?.name ?? 'Talent Reviews'}
        open={!!viewingCycle}
        onClose={() => {
          setViewingCycle(null)
          setReviews([])
        }}
        width={1000}
        extra={
          canWrite && viewingCycle && viewingCycle.status !== 'COMPLETED' && (
            <Button
              type="primary"
              onClick={() => {
                reviewForm.resetFields()
                setReviewOpen(true)
              }}
            >
              Record Review
            </Button>
          )
        }
      >
        {viewingCycle && (
          <>
            <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Year">{viewingCycle.year}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={STATUS_COLOR[viewingCycle.status]}>{viewingCycle.status}</Tag>
              </Descriptions.Item>
            </Descriptions>
            <Table
              dataSource={reviews}
              columns={reviewColumns}
              rowKey="id"
              loading={loadingReviews}
              pagination={{ pageSize: 20 }}
            />
          </>
        )}
      </Drawer>

      <Modal
        title="Record Talent Review"
        open={reviewOpen}
        onOk={handleRecordReview}
        onCancel={() => {
          setReviewOpen(false)
          reviewForm.resetFields()
        }}
        width={600}
      >
        <Form form={reviewForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Employee is required' }]}
          >
            <Select
              showSearch
              placeholder="Select employee"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeNo})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="performanceBox" label="Performance Box (1=low, 3=high)">
            <Select
              allowClear
              options={[
                { value: 1, label: '1 - Low' },
                { value: 2, label: '2 - Medium' },
                { value: 3, label: '3 - High' },
              ]}
            />
          </Form.Item>
          <Form.Item name="potentialBox" label="Potential Box (1=low, 3=high)">
            <Select
              allowClear
              options={[
                { value: 1, label: '1 - Low' },
                { value: 2, label: '2 - Medium' },
                { value: 3, label: '3 - High' },
              ]}
            />
          </Form.Item>
          <Form.Item name="hipoDecision" label="HiPo Decision" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="retentionRisk" label="Retention Risk">
            <Select
              allowClear
              options={[
                { value: 'LOW', label: 'Low' },
                { value: 'MEDIUM', label: 'Medium' },
                { value: 'HIGH', label: 'High' },
                { value: 'CRITICAL', label: 'Critical' },
              ]}
            />
          </Form.Item>
          <Form.Item name="retentionAction" label="Retention Action">
            <TextArea rows={2} placeholder="Action plan to retain..." />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <TextArea rows={2} placeholder="Panel notes..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
