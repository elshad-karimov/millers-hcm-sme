// HCM_14 M404/M405 — classroom training: scheduled sessions of a course with an
// instructor + room, capacity-checked enrolment and per-person attendance
// (PRD 14 §7/§11/§18). Instructors are internal employees or external trainers.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Rate,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { api } from '../../api/client'
import { learningApi, type Course } from '../../api/learning'
import { employeesApi, type Employee } from '../../api/employees'
import { EmployeePicker } from '../../components/EmployeePicker'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

interface Instructor {
  id: string
  employeeId?: string | null
  externalName?: string | null
  email?: string | null
  qualifications?: string | null
  hourlyCost?: number | null
  rating?: number | null
  active: boolean
}

interface TrainingRoom {
  id: string
  code: string
  name: string
  location?: string | null
  capacity?: number | null
  virtual: boolean
  notes?: string | null
  active: boolean
}

type SessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
type AttendanceStatus = 'ENROLLED' | 'ATTENDED' | 'LATE' | 'NO_SHOW' | 'CANCELLED'

interface TrainingSession {
  id: string
  courseId: string
  instructorId?: string | null
  roomId?: string | null
  startAt: string
  endAt: string
  capacity?: number | null
  status: SessionStatus
  notes?: string | null
}

interface Attendance {
  id: string
  sessionId: string
  employeeId: string
  status: AttendanceStatus
  checkedInAt?: string | null
  note?: string | null
}

interface TrainingCost {
  id: string
  courseId?: string | null
  sessionId?: string | null
  costType: string
  description?: string | null
  amount: number
  currency: string
  incurredOn?: string | null
  createdBy?: string | null
  createdAt: string
}

interface TrainingFeedback {
  id: string
  sessionId?: string | null
  courseId?: string | null
  employeeId: string
  overallRating: number
  contentRating?: number | null
  instructorRating?: number | null
  comment?: string | null
  anonymous: boolean
  createdAt: string
}

const SESSION_COLOR: Record<SessionStatus, string> = {
  SCHEDULED: 'blue',
  IN_PROGRESS: 'gold',
  COMPLETED: 'green',
  CANCELLED: 'default',
}

const ATT_COLOR: Record<AttendanceStatus, string> = {
  ENROLLED: 'blue',
  ATTENDED: 'green',
  LATE: 'gold',
  NO_SHOW: 'red',
  CANCELLED: 'default',
}

const COST_TYPES = [
  { value: 'INSTRUCTOR', label: 'Instructor' },
  { value: 'VENUE', label: 'Venue' },
  { value: 'MATERIAL', label: 'Material' },
  { value: 'TRAVEL', label: 'Travel' },
  { value: 'CATERING', label: 'Catering' },
  { value: 'OTHER', label: 'Other' },
]

const COST_TYPE_COLOR: Record<string, string> = {
  INSTRUCTOR: 'blue',
  VENUE: 'purple',
  MATERIAL: 'cyan',
  TRAVEL: 'orange',
  CATERING: 'green',
  OTHER: 'default',
}

export function ClassroomTrainingPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [sessions, setSessions] = useState<TrainingSession[]>([])
  const [instructors, setInstructors] = useState<Instructor[]>([])
  const [rooms, setRooms] = useState<TrainingRoom[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)

  const [sessionOpen, setSessionOpen] = useState(false)
  const [editingSession, setEditingSession] = useState<TrainingSession | null>(null)
  const [sessionForm] = Form.useForm()

  const [attFor, setAttFor] = useState<TrainingSession | null>(null)
  const [attRows, setAttRows] = useState<Attendance[]>([])
  const [enrolEmployee, setEnrolEmployee] = useState<string | undefined>()

  const [costs, setCosts] = useState<TrainingCost[]>([])
  const [costOpen, setCostOpen] = useState(false)
  const [costForm] = Form.useForm()

  const [feedback, setFeedback] = useState<TrainingFeedback[]>([])
  const [feedbackOpen, setFeedbackOpen] = useState(false)
  const [feedbackForm] = Form.useForm()

  const [instrOpen, setInstrOpen] = useState(false)
  const [editingInstr, setEditingInstr] = useState<Instructor | null>(null)
  const [instrForm] = Form.useForm()

  const [roomOpen, setRoomOpen] = useState(false)
  const [editingRoom, setEditingRoom] = useState<TrainingRoom | null>(null)
  const [roomForm] = Form.useForm()

  const load = () => {
    setLoading(true)
    Promise.all([
      api.get<TrainingSession[]>('/learning/training-sessions'),
      api.get<Instructor[]>('/learning/instructors'),
      api.get<TrainingRoom[]>('/learning/training-rooms'),
      learningApi.courses({ size: 500 }),
      employeesApi.list({ size: 500 }),
    ])
      .then(([s, i, r, c, e]) => {
        setSessions(s.data)
        setInstructors(i.data)
        setRooms(r.data)
        setCourses(c.content)
        setEmployees(Array.isArray(e) ? e : (e as { content: Employee[] }).content ?? [])
      })
      .catch(() => message.error('Failed to load classroom training'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const courseMap = useMemo(() => new Map(courses.map((c) => [c.id, c])), [courses])
  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const instrName = (i?: Instructor) =>
    !i
      ? '—'
      : i.employeeId
        ? (() => {
            const e = empMap.get(i.employeeId!)
            return e ? `${e.firstName} ${e.lastName}` : i.employeeId
          })()
        : `${i.externalName} (external)`

  const err = (e: unknown, fallback: string) => {
    const x = e as { response?: { data?: { message?: string } } }
    message.error(x.response?.data?.message ?? fallback)
  }

  // ── Sessions ──────────────────────────────────────────────────────────────

  const openSessionEditor = (s?: TrainingSession) => {
    setEditingSession(s ?? null)
    sessionForm.resetFields()
    if (s) {
      sessionForm.setFieldsValue({
        ...s,
        startAt: dayjs(s.startAt),
        endAt: dayjs(s.endAt),
      })
    }
    setSessionOpen(true)
  }

  const saveSession = async () => {
    const v = await sessionForm.validateFields()
    try {
      const req = {
        ...v,
        startAt: v.startAt.toISOString(),
        endAt: v.endAt.toISOString(),
      }
      if (editingSession) await api.put(`/learning/training-sessions/${editingSession.id}`, req)
      else await api.post('/learning/training-sessions', req)
      message.success(editingSession ? 'Session updated' : 'Session scheduled')
      setSessionOpen(false)
      load()
    } catch (e) {
      err(e, 'Save failed')
    }
  }

  const changeStatus = async (s: TrainingSession, status: SessionStatus) => {
    try {
      await api.post(`/learning/training-sessions/${s.id}/status/${status}`)
      message.success(
        status === 'COMPLETED'
          ? 'Session completed — open enrolments finalised as no-show'
          : `Session ${status.toLowerCase().replace(/_/g, ' ')}`,
      )
      load()
      if (attFor?.id === s.id) loadAttendance(s)
    } catch (e) {
      err(e, 'Status change failed')
    }
  }

  const loadAttendance = (s: TrainingSession) => {
    setAttFor(s)
    api
      .get<Attendance[]>(`/learning/training-sessions/${s.id}/attendance`)
      .then((r) => setAttRows(r.data))
      .catch(() => message.error('Failed to load attendance'))
    loadCosts(s.id)
    loadFeedback(s.id)
  }

  const loadCosts = (sessionId: string) => {
    api
      .get<TrainingCost[]>('/learning/training-costs', { params: { sessionId } })
      .then((r) => setCosts(r.data))
      .catch(() => message.error('Failed to load costs'))
  }

  const loadFeedback = (sessionId: string) => {
    api
      .get<TrainingFeedback[]>('/learning/training-feedback', { params: { sessionId } })
      .then((r) => setFeedback(r.data))
      .catch(() => message.error('Failed to load feedback'))
  }

  const enrol = async () => {
    if (!attFor || !enrolEmployee) return
    try {
      await api.post(`/learning/training-sessions/${attFor.id}/attendance`, null, {
        params: { employeeId: enrolEmployee },
      })
      message.success('Enrolled')
      setEnrolEmployee(undefined)
      loadAttendance(attFor)
    } catch (e) {
      err(e, 'Enrolment failed')
    }
  }

  const mark = async (a: Attendance, status: AttendanceStatus) => {
    try {
      await api.post(`/learning/training-attendance/${a.id}/mark/${status}`)
      message.success('Attendance updated')
      if (attFor) loadAttendance(attFor)
    } catch (e) {
      err(e, 'Update failed')
    }
  }

  // ── Costs ─────────────────────────────────────────────────────────────────

  const openCostModal = () => {
    costForm.resetFields()
    costForm.setFieldsValue({ costType: 'OTHER', incurredOn: dayjs() })
    setCostOpen(true)
  }

  const saveCost = async () => {
    if (!attFor) return
    const v = await costForm.validateFields()
    try {
      await api.post('/learning/training-costs', {
        sessionId: attFor.id,
        courseId: attFor.courseId,
        costType: v.costType,
        description: v.description,
        amount: v.amount,
        incurredOn: v.incurredOn?.format('YYYY-MM-DD'),
      })
      message.success('Cost added')
      setCostOpen(false)
      loadCosts(attFor.id)
    } catch (e) {
      err(e, 'Failed to add cost')
    }
  }


  // ── Feedback ──────────────────────────────────────────────────────────────

  const openFeedbackModal = () => {
    feedbackForm.resetFields()
    feedbackForm.setFieldsValue({ anonymous: true })
    setFeedbackOpen(true)
  }

  const submitFeedback = async () => {
    if (!attFor) return
    const v = await feedbackForm.validateFields()
    try {
      await api.post('/learning/training-feedback', {
        sessionId: attFor.id,
        courseId: attFor.courseId,
        employeeId: v.employeeId,
        overallRating: v.overallRating,
        contentRating: v.contentRating,
        instructorRating: v.instructorRating,
        comment: v.comment,
        anonymous: v.anonymous,
      })
      message.success('Feedback submitted')
      setFeedbackOpen(false)
      loadFeedback(attFor.id)
    } catch (e) {
      err(e, 'Failed to submit feedback')
    }
  }

  // ── Instructors / rooms save ──────────────────────────────────────────────

  const saveInstructor = async () => {
    const v = await instrForm.validateFields()
    try {
      if (editingInstr) await api.put(`/learning/instructors/${editingInstr.id}`, v)
      else await api.post('/learning/instructors', v)
      message.success('Instructor saved')
      setInstrOpen(false)
      load()
    } catch (e) {
      err(e, 'Save failed')
    }
  }

  const saveRoom = async () => {
    const v = await roomForm.validateFields()
    try {
      if (editingRoom) await api.put(`/learning/training-rooms/${editingRoom.id}`, v)
      else await api.post('/learning/training-rooms', v)
      message.success('Room saved')
      setRoomOpen(false)
      load()
    } catch (e) {
      err(e, 'Save failed')
    }
  }

  // ── Columns ───────────────────────────────────────────────────────────────

  const sessionColumns: ColumnsType<TrainingSession> = [
    {
      title: 'Course',
      key: 'course',
      render: (_, s) => {
        const c = courseMap.get(s.courseId)
        return c ? `${c.code} — ${c.title}` : s.courseId
      },
    },
    {
      title: 'When',
      key: 'when',
      width: 230,
      render: (_, s) =>
        `${new Date(s.startAt).toLocaleString()} → ${new Date(s.endAt).toLocaleTimeString()}`,
    },
    {
      title: 'Instructor',
      key: 'instructor',
      width: 170,
      render: (_, s) => instrName(instructors.find((i) => i.id === s.instructorId)),
    },
    {
      title: 'Room',
      key: 'room',
      width: 130,
      render: (_, s) => {
        const r = rooms.find((x) => x.id === s.roomId)
        return r ? (
          <Space size={4}>
            {r.code}
            {r.virtual && <Tag>virtual</Tag>}
          </Space>
        ) : (
          '—'
        )
      },
    },
    {
      title: 'Capacity',
      dataIndex: 'capacity',
      width: 90,
      align: 'center',
      render: (v, s) => v ?? rooms.find((x) => x.id === s.roomId)?.capacity ?? '∞',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: SessionStatus) => <Tag color={SESSION_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: '',
      key: 'actions',
      width: 260,
      render: (_, s) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => loadAttendance(s)}>
            Attendance
          </Button>
          {canWrite && s.status === 'SCHEDULED' && (
            <>
              <Button size="small" onClick={() => openSessionEditor(s)}>
                Edit
              </Button>
              <Button size="small" onClick={() => changeStatus(s, 'IN_PROGRESS')}>
                Start
              </Button>
              <Button size="small" danger onClick={() => changeStatus(s, 'CANCELLED')}>
                Cancel
              </Button>
            </>
          )}
          {canWrite && s.status === 'IN_PROGRESS' && (
            <Button size="small" type="primary" onClick={() => changeStatus(s, 'COMPLETED')}>
              Complete
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const instrColumns: ColumnsType<Instructor> = [
    { title: 'Instructor', key: 'name', render: (_, i) => instrName(i) },
    { title: 'Email', dataIndex: 'email', width: 200, render: (v) => v ?? '—' },
    { title: 'Qualifications', dataIndex: 'qualifications', ellipsis: true, render: (v) => v ?? '—' },
    {
      title: 'Cost/h',
      dataIndex: 'hourlyCost',
      width: 90,
      align: 'right',
      render: (v) => (v != null ? `${v} ₼` : '—'),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => (v ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>),
    },
    ...(canWrite
      ? [
          {
            title: '',
            key: 'a',
            width: 70,
            render: (_: unknown, i: Instructor) => (
              <Button
                size="small"
                onClick={() => {
                  setEditingInstr(i)
                  instrForm.setFieldsValue(i)
                  setInstrOpen(true)
                }}
              >
                Edit
              </Button>
            ),
          } as ColumnsType<Instructor>[number],
        ]
      : []),
  ]

  const roomColumns: ColumnsType<TrainingRoom> = [
    { title: 'Code', dataIndex: 'code', width: 110, render: (v) => <Text code>{v}</Text> },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Location', dataIndex: 'location', render: (v) => v ?? '—' },
    { title: 'Capacity', dataIndex: 'capacity', width: 90, align: 'center', render: (v) => v ?? '—' },
    {
      title: 'Virtual',
      dataIndex: 'virtual',
      width: 80,
      render: (v: boolean) => (v ? <Tag color="cyan">Yes</Tag> : 'No'),
    },
    ...(canWrite
      ? [
          {
            title: '',
            key: 'a',
            width: 70,
            render: (_: unknown, r: TrainingRoom) => (
              <Button
                size="small"
                onClick={() => {
                  setEditingRoom(r)
                  roomForm.setFieldsValue(r)
                  setRoomOpen(true)
                }}
              >
                Edit
              </Button>
            ),
          } as ColumnsType<TrainingRoom>[number],
        ]
      : []),
  ]

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            Classroom training
          </Title>
          <Text type="secondary">
            Instructor-led sessions with rooms, capacity and attendance (completing a session
            marks open enrolments as no-show).
          </Text>
        </Col>
      </Row>

      <Tabs
        defaultActiveKey="sessions"
        items={[
          {
            key: 'sessions',
            label: `Sessions (${sessions.length})`,
            children: (
              <Card size="small">
                {canWrite && (
                  <Button type="primary" style={{ marginBottom: 12 }} onClick={() => openSessionEditor()}>
                    Schedule session
                  </Button>
                )}
                <Table
                  rowKey="id"
                  size="small"
                  columns={sessionColumns}
                  dataSource={sessions}
                  pagination={{ pageSize: 15, hideOnSinglePage: true }}
                />
              </Card>
            ),
          },
          {
            key: 'instructors',
            label: `Instructors (${instructors.length})`,
            children: (
              <Card size="small">
                {canWrite && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => {
                      setEditingInstr(null)
                      instrForm.resetFields()
                      instrForm.setFieldsValue({ active: true })
                      setInstrOpen(true)
                    }}
                  >
                    New instructor
                  </Button>
                )}
                <Table rowKey="id" size="small" columns={instrColumns} dataSource={instructors} pagination={false} />
              </Card>
            ),
          },
          {
            key: 'rooms',
            label: `Rooms (${rooms.length})`,
            children: (
              <Card size="small">
                {canWrite && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => {
                      setEditingRoom(null)
                      roomForm.resetFields()
                      roomForm.setFieldsValue({ active: true, virtual: false })
                      setRoomOpen(true)
                    }}
                  >
                    New room
                  </Button>
                )}
                <Table rowKey="id" size="small" columns={roomColumns} dataSource={rooms} pagination={false} />
              </Card>
            ),
          },
        ]}
      />

      {/* Session editor */}
      <Modal
        title={editingSession ? 'Edit session' : 'Schedule session'}
        open={sessionOpen}
        onCancel={() => setSessionOpen(false)}
        onOk={saveSession}
        okText="Save"
        width={640}
        destroyOnClose
      >
        <Form form={sessionForm} layout="vertical">
          <Form.Item name="courseId" label="Course" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={courses.map((c) => ({ value: c.id, label: `${c.code} — ${c.title}` }))}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="startAt" label="Start" rules={[{ required: true }]}>
                <DatePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endAt" label="End" rules={[{ required: true }]}>
                <DatePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item name="instructorId" label="Instructor">
                <Select
                  allowClear
                  options={instructors
                    .filter((i) => i.active)
                    .map((i) => ({ value: i.id, label: instrName(i) }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="roomId" label="Room">
                <Select
                  allowClear
                  options={rooms
                    .filter((r) => r.active)
                    .map((r) => ({ value: r.id, label: `${r.code} — ${r.name}` }))}
                />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="capacity" label="Capacity (blank = room's)">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Instructor editor */}
      <Modal
        title={editingInstr ? 'Edit instructor' : 'New instructor'}
        open={instrOpen}
        onCancel={() => setInstrOpen(false)}
        onOk={saveInstructor}
        okText="Save"
        destroyOnClose
      >
        <Form form={instrForm} layout="vertical">
          <Form.Item name="employeeId" label="Internal employee (leave empty for external)">
            <EmployeePicker allowClear placeholder="Select internal employee" />
          </Form.Item>
          <Form.Item name="externalName" label="External trainer name">
            <Input placeholder="e.g. PwC Academy — Aysel Aliyeva" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="email" label="Email">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="hourlyCost" label="Cost per hour (AZN)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="qualifications" label="Qualifications">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Room editor */}
      <Modal
        title={editingRoom ? 'Edit room' : 'New room'}
        open={roomOpen}
        onCancel={() => setRoomOpen(false)}
        onOk={saveRoom}
        okText="Save"
        destroyOnClose
      >
        <Form form={roomForm} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true }]}>
                <Input placeholder="ROOM-A" disabled={!!editingRoom} />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input placeholder="Main training room" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="location" label="Location / meeting link">
            <Input />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="capacity" label="Capacity">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="virtual" label="Virtual" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Attendance drawer */}
      <Drawer
        title={
          attFor
            ? `Session — ${courseMap.get(attFor.courseId)?.title ?? ''} (${new Date(attFor.startAt).toLocaleDateString()})`
            : ''
        }
        open={!!attFor}
        onClose={() => setAttFor(null)}
        width={720}
      >
        {attFor && (
          <Tabs
            defaultActiveKey="attendance"
            items={[
              {
                key: 'attendance',
                label: `Attendance (${attRows.length})`,
                children: (
                  <>
                    {canWrite &&
                      (attFor.status === 'SCHEDULED' || attFor.status === 'IN_PROGRESS') && (
                        <Space style={{ marginBottom: 12 }}>
                          <Select
                            showSearch
                            optionFilterProp="label"
                            style={{ minWidth: 300 }}
                            placeholder="Enrol employee…"
                            value={enrolEmployee}
                            onChange={setEnrolEmployee}
                            options={employees
                              .filter((e) => !attRows.some((a) => a.employeeId === e.id))
                              .map((e) => ({
                                value: e.id,
                                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                              }))}
                          />
                          <Button type="primary" disabled={!enrolEmployee} onClick={enrol}>
                            Enrol
                          </Button>
                        </Space>
                      )}
                    <Table
                      rowKey="id"
                      size="small"
                      columns={[
                        {
                          title: 'Employee',
                          key: 'emp',
                          render: (_: unknown, a: Attendance) => {
                            const e = empMap.get(a.employeeId)
                            return e ? `${e.firstName} ${e.lastName}` : a.employeeId
                          },
                        },
                        {
                          title: 'Status',
                          dataIndex: 'status',
                          width: 110,
                          render: (v: AttendanceStatus) => (
                            <Tag color={ATT_COLOR[v]}>{v.replace(/_/g, ' ')}</Tag>
                          ),
                        },
                        {
                          title: '',
                          key: 'a',
                          width: 240,
                          render: (_: unknown, a: Attendance) =>
                            canWrite &&
                            a.status !== 'CANCELLED' && (
                              <Space size={4}>
                                <Button
                                  size="small"
                                  type="primary"
                                  onClick={() => mark(a, 'ATTENDED')}
                                >
                                  Attended
                                </Button>
                                <Button size="small" onClick={() => mark(a, 'LATE')}>
                                  Late
                                </Button>
                                <Button size="small" danger onClick={() => mark(a, 'NO_SHOW')}>
                                  No-show
                                </Button>
                              </Space>
                            ),
                        },
                      ]}
                      dataSource={attRows}
                      pagination={false}
                      locale={{ emptyText: 'No one enrolled yet.' }}
                    />
                  </>
                ),
              },
              {
                key: 'costs',
                label: `Costs (${costs.length})`,
                children: (
                  <>
                    <Row gutter={16} style={{ marginBottom: 16 }}>
                      <Col span={12}>
                        <Card size="small">
                          <Statistic
                            title="Total cost"
                            value={costs.reduce((sum, c) => sum + c.amount, 0).toFixed(2)}
                            suffix="₼"
                          />
                        </Card>
                      </Col>
                      <Col span={12}>
                        {canWrite && (
                          <Button
                            type="primary"
                            style={{ marginTop: 8 }}
                            onClick={openCostModal}
                          >
                            Add cost
                          </Button>
                        )}
                      </Col>
                    </Row>
                    <Table
                      rowKey="id"
                      size="small"
                      columns={[
                        {
                          title: 'Type',
                          dataIndex: 'costType',
                          width: 120,
                          render: (v: string) => (
                            <Tag color={COST_TYPE_COLOR[v] ?? 'default'}>
                              {COST_TYPES.find((t) => t.value === v)?.label ?? v}
                            </Tag>
                          ),
                        },
                        {
                          title: 'Description',
                          dataIndex: 'description',
                          ellipsis: true,
                          render: (v) => v ?? '—',
                        },
                        {
                          title: 'Amount',
                          dataIndex: 'amount',
                          width: 110,
                          align: 'right',
                          render: (v: number) => `${v.toFixed(2)} ₼`,
                        },
                        {
                          title: 'Date',
                          dataIndex: 'incurredOn',
                          width: 110,
                          render: (v) => (v ? new Date(v).toLocaleDateString() : '—'),
                        },
                      ]}
                      dataSource={costs}
                      pagination={false}
                      locale={{ emptyText: 'No costs recorded yet.' }}
                    />
                  </>
                ),
              },
              {
                key: 'feedback',
                label: `Feedback (${feedback.length})`,
                children: (
                  <>
                    {canWrite && (
                      <Button
                        type="primary"
                        style={{ marginBottom: 12 }}
                        onClick={openFeedbackModal}
                      >
                        Submit feedback
                      </Button>
                    )}
                    <Table
                      rowKey="id"
                      size="small"
                      columns={[
                        {
                          title: 'Employee',
                          key: 'emp',
                          width: 140,
                          render: (_: unknown, f: TrainingFeedback) => {
                            if (f.anonymous || f.employeeId === '00000000-0000-0000-0000-000000000000')
                              return <Tag color="gold">Anonymous</Tag>
                            const e = empMap.get(f.employeeId)
                            return e ? `${e.firstName} ${e.lastName}` : f.employeeId
                          },
                        },
                        {
                          title: 'Overall',
                          dataIndex: 'overallRating',
                          width: 120,
                          render: (v: number) => <Rate disabled value={v} />,
                        },
                        {
                          title: 'Content',
                          dataIndex: 'contentRating',
                          width: 120,
                          render: (v?: number | null) => (v ? <Rate disabled value={v} /> : '—'),
                        },
                        {
                          title: 'Instructor',
                          dataIndex: 'instructorRating',
                          width: 120,
                          render: (v?: number | null) => (v ? <Rate disabled value={v} /> : '—'),
                        },
                        {
                          title: 'Comment',
                          dataIndex: 'comment',
                          ellipsis: true,
                          render: (v) => v ?? '—',
                        },
                      ]}
                      dataSource={feedback}
                      pagination={false}
                      locale={{ emptyText: 'No feedback submitted yet.' }}
                    />
                  </>
                ),
              },
            ]}
          />
        )}
      </Drawer>

      {/* Cost modal */}
      <Modal
        title="Add training cost"
        open={costOpen}
        onCancel={() => setCostOpen(false)}
        onOk={saveCost}
        okText="Add"
        destroyOnClose
      >
        <Form form={costForm} layout="vertical">
          <Form.Item name="costType" label="Cost type" rules={[{ required: true }]}>
            <Select options={COST_TYPES} />
          </Form.Item>
          <Form.Item name="amount" label="Amount (AZN)" rules={[{ required: true }]}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="incurredOn" label="Date incurred">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Feedback modal */}
      <Modal
        title="Submit training feedback"
        open={feedbackOpen}
        onCancel={() => setFeedbackOpen(false)}
        onOk={submitFeedback}
        okText="Submit"
        width={600}
        destroyOnClose
      >
        <Form form={feedbackForm} layout="vertical">
          <Form.Item name="employeeId" label="Employee" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Select participant…"
              options={attRows
                .filter((a) => a.status === 'ATTENDED' || a.status === 'LATE')
                .map((a) => {
                  const e = empMap.get(a.employeeId)
                  return {
                    value: a.employeeId,
                    label: e ? `${e.firstName} ${e.lastName}` : a.employeeId,
                  }
                })}
            />
          </Form.Item>
          <Divider />
          <Form.Item
            name="overallRating"
            label="Overall rating"
            rules={[{ required: true, message: 'Overall rating is required' }]}
          >
            <Rate />
          </Form.Item>
          <Form.Item name="contentRating" label="Content rating">
            <Rate />
          </Form.Item>
          <Form.Item name="instructorRating" label="Instructor rating">
            <Rate />
          </Form.Item>
          <Form.Item name="comment" label="Comment">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="anonymous" label="Submit anonymously" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
