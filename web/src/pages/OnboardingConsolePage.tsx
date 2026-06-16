// M300 — Onboarding journey hub + HR console (Onboarding PRD §1 / §3).
// HR-facing operational view over active onboardings: top-line stats,
// pending-by-type + by-department breakdowns, a filterable table, and a
// per-hire journey drawer (tasks grouped by category) with inline status
// updates. Consumes the rule-matching (A.1) and typed tasks (A.2).

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Card,
  Checkbox,
  Col,
  Empty,
  Drawer,
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
import { onboardingApi, type OnboardingJourney, type OnboardingRow } from '../api/onboarding'

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
  const [journey, setJourney] = useState<OnboardingJourney | null>(null)
  const [loading, setLoading] = useState(false)
  const [groupBy, setGroupBy] = useState<'category' | 'owner'>('category')

  useEffect(() => {
    if (!row) { setJourney(null); return }
    setLoading(true)
    onboardingApi.journey(row.employeeId)
      .then(setJourney)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load journey'))
      .finally(() => setLoading(false))
    /* eslint-disable-next-line */
  }, [row?.employeeId])

  const updateTask = async (task: TaskStatusResponse, status: ChecklistTaskStatusValue) => {
    try {
      await checklistsApi.updateTask(task.id, { status })
      const fresh = await onboardingApi.journey(row!.employeeId)
      setJourney(fresh)
      onChanged()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Update failed')
    }
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
    </Drawer>
  )
}
