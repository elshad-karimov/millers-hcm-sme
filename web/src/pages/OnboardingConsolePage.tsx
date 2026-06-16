// M300 — Onboarding journey hub + HR console (Onboarding PRD §1 / §3).
// HR-facing operational view over active onboardings: top-line stats,
// pending-by-type + by-department breakdowns, a filterable table, and a
// per-hire journey drawer (tasks grouped by category) with inline status
// updates. Consumes the rule-matching (A.1) and typed tasks (A.2).

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Col,
  Empty,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import { DatePicker } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  checklistsApi,
  prettyEnum,
  TASK_STATUS_COLOR,
  TASK_TYPE_COLOR,
  type ChecklistTaskStatusValue,
  type TaskStatusResponse,
} from '../api/checklists'
import {
  onboardingApi,
  type Acknowledgement,
  type Buddy,
  type BuddyRole,
  type Meeting,
  type OnboardingJourney,
  type OnboardingRow,
} from '../api/onboarding'
import { employeesApi } from '../api/employees'
import {
  onboardingRequestsApi,
  REQUEST_CATEGORY_COLOR,
  REQUEST_STATUS_COLOR,
  type ResourceRequest,
} from '../api/onboardingRequests'
import { AttachmentUploader } from '../components/AttachmentUploader'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

// M304 — task types whose evidence is a file upload vs a signed acknowledgement.
const DOC_TYPES = ['DOCUMENT_COLLECTION', 'CONTRACT_SIGNING']
const ACK_TYPES = ['POLICY_ACKNOWLEDGEMENT', 'CONTRACT_SIGNING']
// M305 — buddy/mentor assignment task.
const BUDDY_TYPE = 'BUDDY_TASK'
// M306 — meeting scheduling task.
const MEETING_TYPE = 'MEETING_SCHEDULE'

const { Title, Text } = Typography

const STATUS_OPTIONS: { value: ChecklistTaskStatusValue; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'DONE', label: 'Done' },
  { value: 'SKIPPED', label: 'Skipped' },
]

function joinLabel(daysToJoin?: number | null): { text: string; color: string } {
  if (daysToJoin == null) return { text: '—', color: 'default' }
  if (daysToJoin > 1) return { text: `in ${daysToJoin} days`, color: 'blue' }
  if (daysToJoin === 1) return { text: 'tomorrow', color: 'blue' }
  if (daysToJoin === 0) return { text: 'today', color: 'green' }
  return { text: `${-daysToJoin}d ago`, color: 'default' }
}

export function OnboardingConsolePage() {
  const { message } = AntdApp.useApp()
  const [overview, setOverview] = useState<Awaited<ReturnType<typeof onboardingApi.overview>> | null>(null)
  const [loading, setLoading] = useState(true)
  const [deptFilter, setDeptFilter] = useState<string>()
  const [scope, setScope] = useState<'all' | 'overdue' | 'week'>('all')
  const [journeyFor, setJourneyFor] = useState<OnboardingRow | null>(null)

  const load = () => {
    setLoading(true)
    onboardingApi.overview()
      .then(setOverview)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load() /* eslint-disable-next-line */ }, [])

  const rows = useMemo(() => {
    let r = overview?.rows ?? []
    if (deptFilter) r = r.filter((x) => x.department === deptFilter)
    if (scope === 'overdue') r = r.filter((x) => x.overdueTaskCount > 0)
    if (scope === 'week') r = r.filter((x) => x.daysToJoin != null && x.daysToJoin >= 0 && x.daysToJoin <= 7)
    return r
  }, [overview, deptFilter, scope])

  const cols: ColumnsType<OnboardingRow> = [
    {
      title: 'New hire',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <a onClick={() => setJourneyFor(r)}>{r.employeeName ?? '—'}</a>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {r.employeeNo ?? ''}{r.templateName ? ` · ${r.templateName}` : ''}
          </Text>
        </Space>
      ),
    },
    { title: 'Department', dataIndex: 'department', width: 150 },
    {
      title: 'Joins',
      width: 150,
      render: (_, r) => {
        const j = joinLabel(r.daysToJoin)
        return (
          <Space direction="vertical" size={0}>
            <Text style={{ fontSize: 12 }}>{r.joinDate ? dayjs(r.joinDate).format('YYYY-MM-DD') : '—'}</Text>
            <Tag color={j.color} style={{ marginInlineEnd: 0 }}>{j.text}</Tag>
          </Space>
        )
      },
    },
    {
      title: 'Progress',
      width: 200,
      render: (_, r) => (
        <Tooltip title={`${r.completedTasks} of ${r.totalTasks} tasks · ${r.requiredCompleted}/${r.requiredTotal} required`}>
          <Progress
            percent={r.progressPercent}
            size="small"
            status={r.requiredCompleted >= r.requiredTotal && r.requiredTotal > 0 ? 'success' : 'active'}
          />
        </Tooltip>
      ),
    },
    {
      title: 'Overdue',
      width: 100,
      align: 'center',
      render: (_, r) => r.overdueTaskCount > 0
        ? <Tag color="red">{r.overdueTaskCount}</Tag>
        : <Tag color="green">0</Tag>,
    },
    {
      title: 'Next due',
      width: 120,
      render: (_, r) => r.nextDueDate
        ? <Text type={dayjs(r.nextDueDate).isBefore(dayjs(), 'day') ? 'danger' : undefined}>
            {dayjs(r.nextDueDate).format('YYYY-MM-DD')}
          </Text>
        : '—',
    },
  ]

  if (loading || !overview) return <Spin />

  const deptOptions = overview.byDepartment.map((d) => ({ value: d.department, label: `${d.department} (${d.onboardings})` }))

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={3} style={{ margin: 0 }}>Onboarding console</Title>
        <Text type="secondary">
          Active new-hire onboardings — the best-matching template auto-starts on hire; tasks are
          typed and routed to the responsible team. Click a hire to open their journey.
        </Text>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} md={4}><Card><Statistic title="In onboarding" value={overview.active} /></Card></Col>
        <Col xs={12} sm={8} md={4}><Card><Statistic title="Joining this week" value={overview.joiningThisWeek} /></Card></Col>
        <Col xs={12} sm={8} md={4}><Card><Statistic title="Joining this month" value={overview.joiningThisMonth} /></Card></Col>
        <Col xs={12} sm={8} md={4}><Card><Statistic title="With overdue" value={overview.assignmentsWithOverdue} valueStyle={{ color: overview.assignmentsWithOverdue ? '#cf1322' : undefined }} /></Card></Col>
        <Col xs={12} sm={8} md={4}><Card><Statistic title="Overdue tasks" value={overview.totalOverdueTasks} valueStyle={{ color: overview.totalOverdueTasks ? '#cf1322' : undefined }} /></Card></Col>
      </Row>

      {overview.pendingByType.length > 0 && (
        <Card size="small" title="Pending by task type">
          <Space wrap>
            {overview.pendingByType.map((t) => (
              <Tag key={t.taskType} color={TASK_TYPE_COLOR[t.taskType] ?? 'default'}>
                {prettyEnum(t.taskType)}: <b>{t.pending}</b>
              </Tag>
            ))}
          </Space>
        </Card>
      )}

      <Card>
        <Space style={{ marginBottom: 12 }} wrap>
          <Segmented
            value={scope}
            onChange={(v) => setScope(v as typeof scope)}
            options={[
              { label: 'All', value: 'all' },
              { label: 'Overdue', value: 'overdue' },
              { label: 'Joining this week', value: 'week' },
            ]}
          />
          <Select
            allowClear
            placeholder="All departments"
            style={{ minWidth: 220 }}
            value={deptFilter}
            onChange={setDeptFilter}
            options={deptOptions}
          />
        </Space>
        <Table
          rowKey="assignmentId"
          columns={cols}
          dataSource={rows}
          pagination={{ pageSize: 20 }}
          size="small"
          locale={{ emptyText: <Empty description="No active onboardings" /> }}
        />
      </Card>

      <JourneyDrawer
        row={journeyFor}
        onClose={() => setJourneyFor(null)}
        onChanged={load}
      />
    </Space>
  )
}

// ── Journey drawer — the new-hire's onboarding, tasks grouped by category ───

function JourneyDrawer({
  row, onClose, onChanged,
}: {
  row: OnboardingRow | null
  onClose: () => void
  onChanged: () => void
}) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)
  const [journey, setJourney] = useState<OnboardingJourney | null>(null)
  const [requests, setRequests] = useState<ResourceRequest[]>([])
  const [acks, setAcks] = useState<Acknowledgement[]>([])
  const [buddies, setBuddies] = useState<Buddy[]>([])
  const [meetings, setMeetings] = useState<Meeting[]>([])
  const [loading, setLoading] = useState(false)
  const [groupBy, setGroupBy] = useState<'category' | 'owner'>('category')
  const [ackFor, setAckFor] = useState<TaskStatusResponse | null>(null)
  const [ackForm] = Form.useForm<{ statement?: string; reference?: string }>()
  const [acking, setAcking] = useState(false)
  const [buddyFor, setBuddyFor] = useState<TaskStatusResponse | null>(null)
  const [buddyForm] = Form.useForm<{ buddyEmployeeId?: string; role: BuddyRole; notes?: string }>()
  const [buddying, setBuddying] = useState(false)
  const [meetingFor, setMeetingFor] = useState<TaskStatusResponse | null>(null)
  const [meetingForm] = Form.useForm<{ title: string; startsAt: ReturnType<typeof dayjs>; durationMinutes: number; location?: string; attendeeEmails?: string; description?: string }>()
  const [scheduling, setScheduling] = useState(false)
  const [empOptions, setEmpOptions] = useState<{ value: string; label: string }[]>([])

  const reload = () => {
    if (!row) return Promise.resolve()
    return Promise.all([
      onboardingApi.journey(row.employeeId),
      onboardingRequestsApi.forEmployee(row.employeeId),
      onboardingApi.acknowledgementsForEmployee(row.employeeId),
      onboardingApi.buddiesForEmployee(row.employeeId),
      onboardingApi.meetingsForEmployee(row.employeeId),
    ]).then(([j, rr, ak, bd, mt]) => {
      setJourney(j); setRequests(rr); setAcks(ak); setBuddies(bd); setMeetings(mt)
    })
  }

  const searchEmployees = (q: string) => {
    employeesApi.list({ search: q || undefined, size: 20, status: 'ACTIVE' })
      .then((p) => setEmpOptions(p.content
        .filter((e) => e.id !== row?.employeeId)
        .map((e) => ({ value: e.id, label: `${e.firstName} ${e.lastName} · ${e.employeeNo}` }))))
      .catch(() => {})
  }

  useEffect(() => {
    if (!row) { setJourney(null); setRequests([]); setAcks([]); setBuddies([]); setMeetings([]); return }
    setLoading(true)
    reload()
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load journey'))
      .finally(() => setLoading(false))
    /* eslint-disable-next-line */
  }, [row?.employeeId])

  const updateTask = async (task: TaskStatusResponse, status: ChecklistTaskStatusValue) => {
    try {
      await checklistsApi.updateTask(task.id, { status })
      await reload()
      onChanged()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Update failed')
    }
  }

  const submitAck = async () => {
    const v = await ackForm.validateFields()
    setAcking(true)
    try {
      await onboardingApi.acknowledge(ackFor!.id, v.statement, v.reference)
      message.success('Acknowledged')
      setAckFor(null)
      await reload()
      onChanged()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed')
    } finally { setAcking(false) }
  }

  const submitBuddy = async () => {
    const v = await buddyForm.validateFields()
    setBuddying(true)
    try {
      await onboardingApi.assignBuddy({
        employeeId: row!.employeeId,
        buddyEmployeeId: v.buddyEmployeeId!,
        role: v.role,
        taskStatusId: buddyFor?.id,
        notes: v.notes,
      })
      message.success(`${prettyEnum(v.role)} assigned`)
      setBuddyFor(null)
      await reload()
      onChanged()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed')
    } finally { setBuddying(false) }
  }

  const ackByTask = useMemo(() => {
    const m = new Map<string, Acknowledgement>()
    for (const a of acks) m.set(a.taskStatusId, a)
    return m
  }, [acks])

  const buddyByTask = useMemo(() => {
    const m = new Map<string, Buddy>()
    for (const b of buddies) {
      if (b.status === 'ACTIVE' && b.taskStatusId) m.set(b.taskStatusId, b)
    }
    return m
  }, [buddies])

  const meetingByTask = useMemo(() => {
    const m = new Map<string, Meeting>()
    for (const mt of meetings) {
      if (mt.taskStatusId) m.set(mt.taskStatusId, mt)
    }
    return m
  }, [meetings])

  const submitMeeting = async () => {
    const v = await meetingForm.validateFields()
    setScheduling(true)
    try {
      const existing = meetingFor ? meetingByTask.get(meetingFor.id) : null
      const req = {
        employeeId: row!.employeeId,
        taskStatusId: meetingFor?.id ?? null,
        title: v.title,
        startsAt: v.startsAt.toISOString(),
        durationMinutes: v.durationMinutes ?? 60,
        location: v.location,
        attendeeEmails: v.attendeeEmails,
        description: v.description,
      }
      if (existing && existing.status === 'SCHEDULED') {
        await onboardingApi.rescheduleMeeting(existing.id, req)
        message.success('Meeting rescheduled — updated .ics sent')
      } else {
        await onboardingApi.scheduleMeeting(req)
        message.success('Meeting scheduled — .ics invite sent')
      }
      setMeetingFor(null)
      await reload()
      onChanged()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed')
    } finally { setScheduling(false) }
  }

  const tasks = journey?.assignment?.tasks ?? []
  const groups = useMemo(() => {
    const m = new Map<string, TaskStatusResponse[]>()
    for (const t of tasks) {
      const key = groupBy === 'category'
        ? (t.category ? prettyEnum(t.category) : 'Uncategorised')
        : (t.ownerRole ?? 'Unassigned')
      if (!m.has(key)) m.set(key, [])
      m.get(key)!.push(t)
    }
    for (const list of m.values()) list.sort((a, b) => a.stepOrder - b.stepOrder)
    return [...m.entries()]
  }, [tasks, groupBy])

  const today = dayjs()

  return (
    <Drawer
      open={!!row}
      width={680}
      onClose={onClose}
      title={journey ? `${journey.employeeName} — onboarding journey` : 'Onboarding journey'}
    >
      {loading && <Spin />}
      {!loading && journey && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap>
            {journey.department && <Tag>{journey.department}</Tag>}
            {journey.joinDate && (
              <Tag color={joinLabel(journey.daysToJoin).color}>
                Joins {dayjs(journey.joinDate).format('YYYY-MM-DD')} ({joinLabel(journey.daysToJoin).text})
              </Tag>
            )}
          </Space>

          {requests.length > 0 && (
            <Card size="small" title="Provisioning requests" styles={{ body: { paddingBlock: 8 } }}>
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                {requests.map((rr) => (
                  <Space key={rr.id} style={{ width: '100%', justifyContent: 'space-between' }} wrap>
                    <Space size={6} wrap>
                      <Text style={{ fontSize: 12 }}>{rr.title}</Text>
                      <Tag color={REQUEST_CATEGORY_COLOR[rr.category]}>{prettyEnum(rr.category)}</Tag>
                      <Text type="secondary" style={{ fontSize: 11 }}>{rr.requestNo}</Text>
                    </Space>
                    <Tag color={REQUEST_STATUS_COLOR[rr.status]}>{prettyEnum(rr.status)}</Tag>
                  </Space>
                ))}
              </Space>
            </Card>
          )}

          {!journey.hasActiveOnboarding || !journey.assignment ? (
            <Empty description="No active onboarding checklist for this employee." />
          ) : (
            <>
              <Progress percent={journey.assignment.progressPercent} />
              <Text>
                {journey.assignment.completedTasks} of {journey.assignment.totalTasks} tasks done ·{' '}
                {journey.assignment.requiredCompleted} of {journey.assignment.requiredTotal} required
              </Text>
              <Segmented
                value={groupBy}
                onChange={(v) => setGroupBy(v as typeof groupBy)}
                options={[{ label: 'By category', value: 'category' }, { label: 'By owner', value: 'owner' }]}
              />

              {groups.map(([groupName, groupTasks]) => (
                <Card key={groupName} size="small" title={groupName}>
                  <Space direction="vertical" size="small" style={{ width: '100%' }}>
                    {groupTasks.map((task) => {
                      const done = task.status === 'DONE' || task.status === 'SKIPPED'
                      const overdue = !done && task.dueDate != null && dayjs(task.dueDate).isBefore(today, 'day')
                      return (
                        <Card
                          key={task.id}
                          size="small"
                          style={{ borderLeft: `3px solid ${done ? '#52c41a' : overdue ? '#cf1322' : '#1677ff'}` }}
                        >
                          <Space direction="vertical" size={4} style={{ width: '100%' }}>
                            <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
                              <Space wrap>
                                <Tag>{task.stepOrder}</Tag>
                                <Text strong>{task.title}</Text>
                                {task.required && <Tag color="red">required</Tag>}
                                {task.taskType !== 'MANUAL_TASK' && (
                                  <Tag color={TASK_TYPE_COLOR[task.taskType] ?? 'default'}>{prettyEnum(task.taskType)}</Tag>
                                )}
                                {task.ownerRole && <Tag>{task.ownerRole}</Tag>}
                              </Space>
                              <Tag color={TASK_STATUS_COLOR[task.status]}>{task.status.replace(/_/g, ' ')}</Tag>
                            </Space>
                            {task.description && (
                              <Text type="secondary" style={{ fontSize: 12 }}>{task.description}</Text>
                            )}
                            <Space wrap>
                              {task.dueDate && (
                                <Text type={overdue ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
                                  Due {task.dueDate}{overdue ? ' · overdue' : ''}
                                </Text>
                              )}
                              {STATUS_OPTIONS.map((opt) => (
                                <Checkbox
                                  key={opt.value}
                                  checked={task.status === opt.value}
                                  onChange={() => updateTask(task, opt.value)}
                                >
                                  {opt.label}
                                </Checkbox>
                              ))}
                            </Space>

                            {/* M304 — document/contract evidence: file upload */}
                            {DOC_TYPES.includes(task.taskType) && (
                              <AttachmentUploader
                                ownerModule="lifecycle"
                                ownerEntity="checklisttaskstatus"
                                ownerId={task.id}
                              />
                            )}

                            {/* M304 — policy/contract acknowledgement */}
                            {ACK_TYPES.includes(task.taskType) && (
                              ackByTask.has(task.id) ? (
                                <Text type="success" style={{ fontSize: 12 }}>
                                  ✓ {prettyEnum(ackByTask.get(task.id)!.kind)} acknowledged by{' '}
                                  {ackByTask.get(task.id)!.acknowledgedBy ?? '—'} on{' '}
                                  {dayjs(ackByTask.get(task.id)!.acknowledgedAt).format('YYYY-MM-DD')}
                                  {ackByTask.get(task.id)!.reference ? ` · ${ackByTask.get(task.id)!.reference}` : ''}
                                </Text>
                              ) : canWrite ? (
                                <Button size="small" type="primary" ghost onClick={() => {
                                  setAckFor(task); ackForm.resetFields()
                                }}>
                                  Acknowledge / sign…
                                </Button>
                              ) : null
                            )}

                            {/* M305 — buddy/mentor assignment */}
                            {task.taskType === BUDDY_TYPE && (
                              buddyByTask.has(task.id) ? (
                                <Text type="success" style={{ fontSize: 12 }}>
                                  ✓ {prettyEnum(buddyByTask.get(task.id)!.role)} assigned:{' '}
                                  <b>{buddyByTask.get(task.id)!.buddyName ?? buddyByTask.get(task.id)!.buddyNo ?? '—'}</b>
                                  {buddyByTask.get(task.id)!.buddyNo ? ` (${buddyByTask.get(task.id)!.buddyNo})` : ''}{' '}
                                  on {dayjs(buddyByTask.get(task.id)!.assignedAt).format('YYYY-MM-DD')}
                                </Text>
                              ) : canWrite ? (
                                <Button size="small" type="primary" ghost onClick={() => {
                                  setBuddyFor(task); buddyForm.resetFields(); searchEmployees('')
                                }}>
                                  Assign buddy / mentor…
                                </Button>
                              ) : null
                            )}

                            {/* M306 — meeting scheduling / calendar invite */}
                            {task.taskType === MEETING_TYPE && (() => {
                              const mt = meetingByTask.get(task.id)
                              if (mt) {
                                return (
                                  <Space direction="vertical" size={2}>
                                    <Text type={mt.status === 'CANCELLED' ? 'secondary' : 'success'} style={{ fontSize: 12 }}>
                                      {mt.status === 'CANCELLED' ? '✗ Cancelled: ' : '✓ '}
                                      <b>{mt.title}</b> — {dayjs(mt.startsAt).format('YYYY-MM-DD HH:mm')}{' '}
                                      ({mt.durationMinutes} min){mt.location ? ` · ${mt.location}` : ''}{' '}
                                      <Text type="secondary" style={{ fontSize: 11 }}>{mt.meetingNo}</Text>
                                      {mt.inviteSentAt && (
                                        <> · invite sent {dayjs(mt.inviteSentAt).format('YYYY-MM-DD')}</>
                                      )}
                                    </Text>
                                    {canWrite && mt.status === 'SCHEDULED' && (
                                      <Button size="small" ghost onClick={() => {
                                        setMeetingFor(task)
                                        meetingForm.setFieldsValue({
                                          title: mt.title,
                                          startsAt: dayjs(mt.startsAt),
                                          durationMinutes: mt.durationMinutes,
                                          location: mt.location ?? undefined,
                                          attendeeEmails: mt.attendeeEmails ?? undefined,
                                          description: mt.description ?? undefined,
                                        })
                                      }}>
                                        Reschedule…
                                      </Button>
                                    )}
                                  </Space>
                                )
                              }
                              return canWrite ? (
                                <Button size="small" type="primary" ghost onClick={() => {
                                  setMeetingFor(task)
                                  meetingForm.resetFields()
                                  meetingForm.setFieldsValue({ title: task.title, durationMinutes: 60 })
                                }}>
                                  Schedule meeting…
                                </Button>
                              ) : null
                            })()}
                          </Space>
                        </Card>
                      )
                    })}
                  </Space>
                </Card>
              ))}
            </>
          )}
        </Space>
      )}

      <Modal
        open={!!ackFor}
        title={ackFor ? `Acknowledge — ${ackFor.title}` : ''}
        onCancel={() => setAckFor(null)}
        onOk={submitAck}
        confirmLoading={acking}
        okText="Acknowledge"
      >
        <Form form={ackForm} layout="vertical">
          <Form.Item name="statement" label="Statement" extra="What the employee agreed to (recorded for compliance).">
            <Input.TextArea rows={2} placeholder="I have read and agree to the Code of Conduct." />
          </Form.Item>
          <Form.Item name="reference" label="Reference (optional)" extra="Policy code, document name, or contract reference.">
            <Input placeholder="e.g. POL-COC-v3 · Employment contract 2026" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!buddyFor}
        title={buddyFor ? `Assign buddy / mentor — ${buddyFor.title}` : ''}
        onCancel={() => setBuddyFor(null)}
        onOk={submitBuddy}
        confirmLoading={buddying}
        okText="Assign"
      >
        <Form form={buddyForm} layout="vertical" initialValues={{ role: 'BUDDY' }}>
          <Form.Item name="buddyEmployeeId" label="Buddy / mentor" rules={[{ required: true, message: 'Select an employee' }]}>
            <Select
              showSearch
              filterOption={false}
              onSearch={searchEmployees}
              placeholder="Search by name or employee number…"
              options={empOptions}
              notFoundContent="Type to search active employees"
            />
          </Form.Item>
          <Form.Item name="role" label="Role" rules={[{ required: true }]}>
            <Select options={[{ value: 'BUDDY', label: 'Buddy (peer support)' }, { value: 'MENTOR', label: 'Mentor (career guidance)' }]} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={2} placeholder="Any context for this pairing…" />
          </Form.Item>
        </Form>
      </Modal>

      {/* M306 — schedule / reschedule meeting modal */}
      <Modal
        open={!!meetingFor}
        title={meetingFor
          ? (meetingByTask.get(meetingFor.id)?.status === 'SCHEDULED'
              ? `Reschedule — ${meetingFor.title}`
              : `Schedule meeting — ${meetingFor.title}`)
          : ''}
        onCancel={() => setMeetingFor(null)}
        onOk={submitMeeting}
        confirmLoading={scheduling}
        okText={meetingFor && meetingByTask.get(meetingFor.id)?.status === 'SCHEDULED' ? 'Reschedule' : 'Schedule'}
        width={520}
      >
        <Form form={meetingForm} layout="vertical">
          <Form.Item name="title" label="Meeting title" rules={[{ required: true, message: 'Title is required' }]}>
            <Input placeholder="e.g. First-day orientation, Meet the team…" />
          </Form.Item>
          <Form.Item name="startsAt" label="Date & time" rules={[{ required: true, message: 'Select a date and time' }]}>
            <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} minuteStep={15} />
          </Form.Item>
          <Form.Item name="durationMinutes" label="Duration (minutes)" rules={[{ required: true }]}>
            <InputNumber min={15} max={480} step={15} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="location" label="Location (optional)">
            <Input placeholder="e.g. Meeting room 3B, Zoom link…" />
          </Form.Item>
          <Form.Item name="attendeeEmails" label="Additional attendees (optional)" extra="Comma-separated e-mails. The new hire is always included.">
            <Input.TextArea rows={2} placeholder="manager@millers.az, buddy@millers.az" />
          </Form.Item>
          <Form.Item name="description" label="Notes (optional)">
            <Input.TextArea rows={2} placeholder="Agenda, instructions…" />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  )
}
