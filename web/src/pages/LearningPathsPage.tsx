// M95 — Learning path templates + per-path assignment surface.
// HR_READ to view, HR_WRITE to assign.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import {
  learningPathsApi,
  pathAssignmentsApi,
  type AssignmentResponse,
  type LearningPath,
  type LearningPathDetail,
  type PathAssignmentStatus,
} from '../api/learningPaths'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text, Paragraph } = Typography

const STATUS_COLOR: Record<PathAssignmentStatus, string> = {
  ASSIGNED: 'blue',
  IN_PROGRESS: 'gold',
  COMPLETED: 'green',
  CANCELLED: 'default',
}

export function LearningPathsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canAssign = hasRole(...RoleSets.HR_WRITE)

  const [paths, setPaths] = useState<LearningPath[]>([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<LearningPathDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([])
  const [assignmentsLoading, setAssignmentsLoading] = useState(false)
  const [assignOpen, setAssignOpen] = useState(false)
  const [assignForm] = Form.useForm<{
    employeeId: string
    targetCompletionDate?: ReturnType<typeof dayjs>
    notes?: string
  }>()
  const [assigning, setAssigning] = useState(false)

  useEffect(() => {
    setLoading(true)
    learningPathsApi
      .list(true)
      .then(setPaths)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load paths'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openPath = (p: LearningPath) => {
    setDetailLoading(true)
    setAssignmentsLoading(true)
    Promise.all([
      learningPathsApi.get(p.id),
      pathAssignmentsApi.forPath(p.id),
    ])
      .then(([detail, asg]) => {
        setSelected(detail)
        setAssignments(asg)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load path detail'))
      .finally(() => {
        setDetailLoading(false)
        setAssignmentsLoading(false)
      })
  }

  const submitAssign = async () => {
    if (!selected) return
    const v = await assignForm.validateFields()
    setAssigning(true)
    try {
      await pathAssignmentsApi.assign(selected.id, {
        employeeId: v.employeeId,
        targetCompletionDate: v.targetCompletionDate?.format('YYYY-MM-DD'),
        notes: v.notes,
      })
      message.success('Assigned')
      setAssignOpen(false)
      assignForm.resetFields()
      const fresh = await pathAssignmentsApi.forPath(selected.id)
      setAssignments(fresh)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Assign failed',
      )
    } finally {
      setAssigning(false)
    }
  }

  const cancelAssignment = async (a: AssignmentResponse) => {
    try {
      await pathAssignmentsApi.cancel(a.id, { reason: 'Cancelled from path admin' })
      message.success('Cancelled')
      if (selected) {
        const fresh = await pathAssignmentsApi.forPath(selected.id)
        setAssignments(fresh)
      }
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Cancel failed',
      )
    }
  }

  const pathCols: ColumnsType<LearningPath> = [
    {
      title: 'Path',
      render: (_, p) => (
        <Space direction="vertical" size={0}>
          <Text strong>{p.name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{p.pathNo}</Text>
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      width: 100,
      render: (v: boolean) => (v ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>),
    },
    {
      title: '',
      width: 100,
      render: (_, p) => <Button size="small" onClick={() => openPath(p)}>Open</Button>,
    },
  ]

  const assignmentCols: ColumnsType<AssignmentResponse> = [
    { title: 'Employee', dataIndex: 'employeeName', render: (v) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 130,
      render: (s: PathAssignmentStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Progress',
      width: 200,
      render: (_, a) => (
        <Tooltip title={`${a.completedSteps} of ${a.totalSteps} courses passed`}>
          <Progress percent={a.progressPercent} size="small" />
        </Tooltip>
      ),
    },
    {
      title: 'Target',
      dataIndex: 'targetCompletionDate',
      width: 130,
      render: (v?: string | null) => (v ? dayjs(v).format('YYYY-MM-DD') : '—'),
    },
    { title: 'Assigned by', dataIndex: 'assignedBy', width: 140, render: (v) => v ?? '—' },
    {
      title: '',
      width: 110,
      render: (_, a) =>
        a.status === 'ASSIGNED' || a.status === 'IN_PROGRESS' ? (
          canAssign ? (
            <Popconfirm
              title="Cancel this assignment?"
              onConfirm={() => cancelAssignment(a)}
              okText="Cancel"
              cancelText="Keep"
            >
              <Button size="small" danger>Cancel</Button>
            </Popconfirm>
          ) : null
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {a.status === 'COMPLETED' && a.completedAt
              ? `Done ${dayjs(a.completedAt).format('YYYY-MM-DD')}`
              : a.status === 'CANCELLED' && a.cancelledAt
              ? `${dayjs(a.cancelledAt).format('YYYY-MM-DD')}`
              : ''}
          </Text>
        ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Learning paths</Title>
      <Paragraph type="secondary" style={{ margin: 0 }}>
        Curricula — ordered sequences of courses that HR can assign to employees
        as Individual Development Plans. Progress is derived from existing course
        enrolments, so what you see here always matches the learner's transcript.
      </Paragraph>

      <Row gutter={16}>
        <Col xs={24} lg={9}>
          <Card title="Templates" size="small">
            <Table
              rowKey="id"
              columns={pathCols}
              dataSource={paths}
              loading={loading}
              pagination={{ pageSize: 20 }}
              size="small"
              locale={{ emptyText: <Empty description="No active paths" /> }}
            />
          </Card>
        </Col>

        <Col xs={24} lg={15}>
          {!selected ? (
            <Card style={{ minHeight: 200, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Text type="secondary">Pick a path to see its steps + assignments.</Text>
            </Card>
          ) : detailLoading ? (
            <Card style={{ minHeight: 200, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Spin />
            </Card>
          ) : (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Card
                title={<Space><span>{selected.name}</span><Text type="secondary">({selected.pathNo})</Text></Space>}
                extra={
                  canAssign ? (
                    <Button type="primary" onClick={() => setAssignOpen(true)}>Assign…</Button>
                  ) : null
                }
              >
                {selected.description ? (
                  <Paragraph style={{ marginBottom: 12 }}>{selected.description}</Paragraph>
                ) : null}
                <List
                  size="small"
                  bordered
                  dataSource={selected.steps ?? []}
                  locale={{ emptyText: 'No steps configured' }}
                  renderItem={(s) => (
                    <List.Item>
                      <Space>
                        <Tag>{s.stepOrder}</Tag>
                        <Link to={`/learning/courses/${s.courseId}`}>
                          {s.courseCode ? `${s.courseCode} — ${s.courseTitle ?? ''}` : s.courseTitle ?? s.courseId}
                        </Link>
                        {s.requiredToAdvance ? (
                          <Tag color="red">required</Tag>
                        ) : (
                          <Tag>optional</Tag>
                        )}
                      </Space>
                    </List.Item>
                  )}
                />
              </Card>

              <Card title={`Assignments (${assignments.length})`} loading={assignmentsLoading}>
                <Table
                  rowKey="id"
                  columns={assignmentCols}
                  dataSource={assignments}
                  pagination={{ pageSize: 25 }}
                  size="small"
                  locale={{ emptyText: <Empty description="No assignments yet" /> }}
                />
              </Card>
            </Space>
          )}
        </Col>
      </Row>

      <Modal
        open={assignOpen}
        title={selected ? `Assign "${selected.name}"` : 'Assign'}
        onCancel={() => setAssignOpen(false)}
        onOk={submitAssign}
        confirmLoading={assigning}
        okText="Assign"
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            name="employeeId"
            label="Employee ID"
            rules={[{ required: true, message: 'Required' }]}
            extra="UUID of the employee (paste from the employees page)."
          >
            <Input placeholder="00000000-0000-0000-0000-000000000000" />
          </Form.Item>
          <Form.Item name="targetCompletionDate" label="Target completion (optional)">
            <DatePicker style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={3} placeholder="Why this path? Manager input? Linked review?" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

