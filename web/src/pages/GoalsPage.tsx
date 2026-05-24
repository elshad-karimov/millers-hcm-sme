import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd'
import { BookOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  performanceApi,
  type Goal,
  type GoalCategory,
  type GoalRequest,
  type GoalStatus,
  type ReviewCycle,
} from '../api/performance'
import { learningApi, type Course } from '../api/learning'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'

const CATEGORIES: GoalCategory[] = ['COMPANY', 'DEPARTMENT', 'TEAM', 'INDIVIDUAL', 'DEVELOPMENT']

const CATEGORY_COLOR: Record<GoalCategory, string> = {
  COMPANY: 'magenta',
  DEPARTMENT: 'purple',
  TEAM: 'geekblue',
  INDIVIDUAL: 'cyan',
  DEVELOPMENT: 'green',
}

const STATUS_COLOR: Record<GoalStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'blue',
  ON_TRACK: 'green',
  AT_RISK: 'orange',
  BLOCKED: 'red',
  ACHIEVED: 'cyan',
  MISSED: 'volcano',
  CANCELLED: 'default',
}

interface NewGoalForm {
  employeeId: string
  title: string
  description?: string
  category: GoalCategory
  targetMetric?: string
  weightPercent?: number
  dueDate?: string
  sourceCourseId?: string
}

interface ProgressForm {
  progressPercent: number
  status?: GoalStatus
  note?: string
}

interface RatingForm {
  rating: number
  note?: string
  finalStatus?: GoalStatus
}

export function GoalsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canManage = hasRole('HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN', 'DEPARTMENT_MANAGER')

  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [cycleId, setCycleId] = useState<string | undefined>()
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [rows, setRows] = useState<Goal[]>([])
  const [loading, setLoading] = useState(false)
  const [createCategory, setCreateCategory] = useState<GoalCategory>('INDIVIDUAL')

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm<NewGoalForm>()
  const [progressOpen, setProgressOpen] = useState<Goal | null>(null)
  const [progressForm] = Form.useForm<ProgressForm>()
  const [rateOpen, setRateOpen] = useState<Goal | null>(null)
  const [rateForm] = Form.useForm<RatingForm>()

  useEffect(() => {
    Promise.all([
      performanceApi.cycles(),
      employeesApi.list({ size: 500 }),
      learningApi.courses({ status: 'PUBLISHED', size: 500 }),
    ]).then(([c, e, cs]) => {
      setCycles(c)
      setEmployees(e.content)
      setCourses(cs.content)
      if (c.length && !cycleId) {
        const open = c.find((x) => x.status === 'OPEN') ?? c[0]
        setCycleId(open.id)
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const load = () => {
    if (!cycleId) return
    setLoading(true)
    performanceApi
      .goals(cycleId, employeeId)
      .then(setRows)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load goals'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cycleId, employeeId])

  const empMap    = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const courseMap = useMemo(() => new Map(courses.map((c) => [c.id, c])), [courses])

  const onCreate = async (v: NewGoalForm) => {
    if (!cycleId) return
    const payload: GoalRequest = {
      cycleId,
      employeeId: v.employeeId,
      title: v.title,
      description: v.description,
      category: v.category,
      targetMetric: v.targetMetric,
      weightPercent: v.weightPercent,
      dueDate: v.dueDate,
      status: 'ACTIVE',
      sourceCourseId: v.category === 'DEVELOPMENT' ? v.sourceCourseId : undefined,
    }
    try {
      await performanceApi.createGoal(payload)
      message.success('Goal created')
      setCreateOpen(false)
      createForm.resetFields()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const onSaveProgress = async (v: ProgressForm) => {
    if (!progressOpen) return
    try {
      await performanceApi.updateGoalProgress(progressOpen.id, v.progressPercent, v.status, v.note)
      message.success('Progress updated')
      setProgressOpen(null)
      progressForm.resetFields()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const onSaveRating = async (v: RatingForm) => {
    if (!rateOpen) return
    try {
      await performanceApi.rateGoal(rateOpen.id, v.rating, v.finalStatus, v.note)
      message.success('Goal rated')
      setRateOpen(null)
      rateForm.resetFields()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const columns: ColumnsType<Goal> = [
    { title: 'Goal #', dataIndex: 'goalNo', width: 110 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = empMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : id
      },
    },
    { title: 'Title', dataIndex: 'title', ellipsis: true },
    {
      title: 'Category',
      dataIndex: 'category',
      width: 130,
      render: (c: GoalCategory) => <Tag color={CATEGORY_COLOR[c]}>{c}</Tag>,
    },
    {
      title: 'Weight',
      dataIndex: 'weightPercent',
      width: 80,
      align: 'right',
      render: (w: number) => `${w}%`,
    },
    {
      title: 'Progress',
      dataIndex: 'progressPercent',
      width: 140,
      render: (p: number) => <Progress percent={Number(p)} size="small" />,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: GoalStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'LMS link',
      dataIndex: 'sourceCourseId',
      width: 90,
      render: (id?: string | null) => {
        if (!id) return null
        const course = courseMap.get(id)
        return (
          <Tooltip title={course ? `${course.code} — ${course.title}` : id}>
            <Tag icon={<BookOutlined />} color="green">Auto-rate</Tag>
          </Tooltip>
        )
      },
    },
    { title: 'Rating', dataIndex: 'rating', width: 80, render: (r: number | null) => r ?? '—' },
    {
      title: '',
      width: 180,
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" onClick={() => {
            progressForm.setFieldsValue({ progressPercent: Number(r.progressPercent), status: r.status })
            setProgressOpen(r)
          }}>
            Progress
          </Button>
          {canManage && (
            <Button size="small" onClick={() => {
              rateForm.setFieldsValue({ rating: r.rating ?? 3 })
              setRateOpen(r)
            }}>
              Rate
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Goals
        </Typography.Title>
      }
      extra={
        canManage && (
          <Button type="primary" disabled={!cycleId} onClick={() => setCreateOpen(true)}>
            New goal
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          placeholder="Cycle"
          style={{ width: 280 }}
          options={cycles.map((c) => ({
            value: c.id,
            label: `${c.code} — ${c.name} (${c.status})`,
          }))}
          value={cycleId}
          onChange={setCycleId}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="All employees"
          style={{ width: 260 }}
          options={employees.map((e) => ({
            value: e.id,
            label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
          }))}
          value={employeeId}
          onChange={setEmployeeId}
        />
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />

      <Modal
        open={createOpen}
        title="New goal"
        onCancel={() => { setCreateOpen(false); setCreateCategory('INDIVIDUAL') }}
        onOk={() => createForm.submit()}
        okText="Create"
        width={640}
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={onCreate}
          initialValues={{ category: 'INDIVIDUAL', weightPercent: 20 }}
          onValuesChange={(changed) => {
            if (changed.category) {
              setCreateCategory(changed.category as GoalCategory)
              if (changed.category !== 'DEVELOPMENT') {
                createForm.setFieldValue('sourceCourseId', undefined)
              }
            }
          }}
        >
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="title" label="Title" rules={[{ required: true, max: 240 }]}>
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="category" label="Category" rules={[{ required: true }]}>
                <Select options={CATEGORIES.map((c) => ({ value: c, label: c }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="weightPercent" label="Weight (%)">
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          {createCategory === 'DEVELOPMENT' && (
            <Form.Item
              name="sourceCourseId"
              label="Auto-rate when course is passed (optional)"
              tooltip="When the employee passes the selected course, this goal will be auto-rated based on their quiz score (score / 20 → 0-5 rating)."
            >
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Link to an LMS course"
                options={courses.map((c) => ({
                  value: c.id,
                  label: `${c.code} — ${c.title}`,
                }))}
              />
            </Form.Item>
          )}
          <Form.Item name="targetMetric" label="Target metric">
            <Input placeholder="e.g. Close 95% of P1 tickets within SLA" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!progressOpen}
        title={`Update progress — ${progressOpen?.goalNo ?? ''}`}
        onCancel={() => setProgressOpen(null)}
        onOk={() => progressForm.submit()}
        okText="Save"
      >
        <Form form={progressForm} layout="vertical" onFinish={onSaveProgress}>
          <Form.Item name="progressPercent" label="Progress (%)" rules={[{ required: true }]}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="Status">
            <Select
              allowClear
              options={(['ACTIVE','ON_TRACK','AT_RISK','BLOCKED'] as GoalStatus[]).map((s) => ({
                value: s, label: s.replace(/_/g, ' '),
              }))}
            />
          </Form.Item>
          <Form.Item name="note" label="Note">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!rateOpen}
        title={`Rate goal — ${rateOpen?.goalNo ?? ''}`}
        onCancel={() => setRateOpen(null)}
        onOk={() => rateForm.submit()}
        okText="Rate"
      >
        <Form form={rateForm} layout="vertical" onFinish={onSaveRating}>
          <Form.Item name="rating" label="Rating (0–5)" rules={[{ required: true }]}>
            <InputNumber min={0} max={5} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="finalStatus" label="Final status (optional override)">
            <Select
              allowClear
              options={(['ACHIEVED','MISSED','CANCELLED'] as GoalStatus[]).map((s) => ({
                value: s, label: s,
              }))}
            />
          </Form.Item>
          <Form.Item name="note" label="Rating note">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
