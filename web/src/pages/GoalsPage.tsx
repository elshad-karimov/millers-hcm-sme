import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
  Tree,
  Typography,
  App as AntdApp,
} from 'antd'
import { BookOutlined, BranchesOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import type { DataNode } from 'antd/es/tree'
import {
  goalTypesApi,
  performanceApi,
  type Goal,
  type GoalApprovalStatus,
  type GoalCategory,
  type GoalProgressUpdate,
  type GoalRequest,
  type GoalStatus,
  type GoalTreeNode,
  type GoalTypeEntry,
  type ReviewCycle,
} from '../api/performance'
import { learningApi, type Course } from '../api/learning'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

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

// M392 — plan-level approval state
const APPROVAL_COLOR: Record<GoalApprovalStatus, string> = {
  NOT_SUBMITTED: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
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
  /** M403 — business goal type (auto-picks the category). */
  goalTypeId?: string
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
  const canManage = hasRole(...RoleSets.HR_PLUS_MANAGERS_WRITE)

  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [goalTypes, setGoalTypes] = useState<GoalTypeEntry[]>([])
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

  // M392 — goal-plan approval + §6.4 progress trail
  const [submitting, setSubmitting] = useState(false)
  const [historyFor, setHistoryFor] = useState<Goal | null>(null)
  const [history, setHistory] = useState<GoalProgressUpdate[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)

  // M130 — OKR tree + cascade
  const [view, setView] = useState<'list' | 'tree'>('list')
  const [tree, setTree] = useState<GoalTreeNode[]>([])
  const [treeLoading, setTreeLoading] = useState(false)
  const [cascadeOpen, setCascadeOpen] = useState<GoalTreeNode | null>(null)
  const [cascadeForm] = Form.useForm<{ employeeId: string; weightPercent?: number }>()

  useEffect(() => {
    Promise.all([
      performanceApi.cycles(),
      employeesApi.list({ size: 500 }),
      learningApi.courses({ status: 'PUBLISHED', size: 500 }),
      goalTypesApi.list(),
    ]).then(([c, e, cs, gt]) => {
      setCycles(c)
      setEmployees(e.content)
      setCourses(cs.content)
      setGoalTypes(gt)
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

  // M130 — load OKR tree whenever cycle changes or the user opens the tab.
  const loadTree = () => {
    if (!cycleId) { setTree([]); return }
    setTreeLoading(true)
    performanceApi.goalTree(cycleId)
      .then(setTree)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load OKR tree'))
      .finally(() => setTreeLoading(false))
  }
  useEffect(() => {
    if (view === 'tree') loadTree()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cycleId, view])

  const empMap    = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])
  const courseMap = useMemo(() => new Map(courses.map((c) => [c.id, c])), [courses])

  // M130 — fold the flat tree into Ant Tree DataNode[]. Roots first.
  const treeData: DataNode[] = useMemo(() => {
    if (!tree.length) return []
    const byId = new Map(tree.map((n) => [n.id, { ...n, children: [] as GoalTreeNode[] }]))
    const roots: typeof tree = []
    for (const n of byId.values()) {
      if (n.parentGoalId && byId.has(n.parentGoalId)) {
        byId.get(n.parentGoalId)!.children.push(n)
      } else {
        roots.push(n)
      }
    }
    const toNode = (n: GoalTreeNode & { children: GoalTreeNode[] }): DataNode => ({
      key: n.id,
      title: (
        <Space size={8}>
          <Tag color="blue">{n.goalNo}</Tag>
          <Typography.Text strong>{n.title}</Typography.Text>
          <Typography.Text type="secondary">{n.employeeName}</Typography.Text>
          <Progress percent={Number(n.progressPercent)} size="small"
            style={{ width: 80 }} format={(p) => `${p}%`} />
          {n.descendantCount > 0 && (
            <Tooltip title="Alignment: weighted average of descendant progress">
              <Tag color="purple">align {n.alignmentPercent.toFixed(1)}%</Tag>
            </Tooltip>
          )}
          {n.descendantCount > 0 && (
            <Tag>{n.descendantCount} below</Tag>
          )}
          {canManage && (
            <Button size="small" type="link" icon={<BranchesOutlined />}
              onClick={(e) => {
                e.stopPropagation()
                setCascadeOpen(n)
                cascadeForm.setFieldsValue({ weightPercent: Number(n.weightPercent) })
              }}>
              Cascade
            </Button>
          )}
        </Space>
      ),
      children: n.children.map((c) =>
        toNode(c as GoalTreeNode & { children: GoalTreeNode[] })),
    })
    return roots.map((r) =>
      toNode(r as GoalTreeNode & { children: GoalTreeNode[] }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tree, canManage])

  const onCascade = async (v: { employeeId: string; weightPercent?: number }) => {
    if (!cascadeOpen) return
    try {
      await performanceApi.cascadeGoal(cascadeOpen.id, {
        employeeId: v.employeeId,
        weightPercent: v.weightPercent ?? null,
      })
      message.success('Goal cascaded')
      setCascadeOpen(null)
      cascadeForm.resetFields()
      loadTree()
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Cascade failed',
      )
    }
  }

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
      goalTypeId: v.goalTypeId,
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

  // M392 — submit the employee's DRAFT plan for manager approval (weights must total 100)
  const weightTotal = useMemo(
    () =>
      rows
        .filter((g) => g.status !== 'CANCELLED')
        .reduce((s, g) => s + Number(g.weightPercent ?? 0), 0),
    [rows],
  )

  const onSubmitPlan = async () => {
    if (!cycleId || !employeeId) return
    setSubmitting(true)
    try {
      const updated = await performanceApi.submitGoalPlan(cycleId, employeeId)
      message.success(`Goal plan submitted — ${updated.length} goals awaiting manager approval`)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Submit failed',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const openHistory = (g: Goal) => {
    setHistoryFor(g)
    setLoadingHistory(true)
    performanceApi
      .goalProgressHistory(g.id)
      .then(setHistory)
      .catch(() => message.error('Failed to load progress history'))
      .finally(() => setLoadingHistory(false))
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
      title: 'Type',
      dataIndex: 'goalTypeId',
      width: 150,
      render: (id?: string | null) => {
        if (!id) return <Typography.Text type="secondary">—</Typography.Text>
        const t = goalTypes.find((x) => x.id === id)
        return t ? <Tag color="purple">{t.name}</Tag> : '—'
      },
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
      title: 'Approval',
      dataIndex: 'approvalStatus',
      width: 120,
      render: (s: GoalApprovalStatus) =>
        s === 'NOT_SUBMITTED' ? (
          <Typography.Text type="secondary">—</Typography.Text>
        ) : (
          <Tag color={APPROVAL_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>
        ),
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
      width: 240,
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" onClick={() => {
            progressForm.setFieldsValue({ progressPercent: Number(r.progressPercent), status: r.status })
            setProgressOpen(r)
          }}>
            Progress
          </Button>
          <Button size="small" onClick={() => openHistory(r)}>
            History
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
        {/* M392 — submit the selected employee's DRAFT plan (weights must total 100) */}
        {employeeId && rows.length > 0 && (
          <Tooltip
            title={
              weightTotal === 100
                ? 'Send this employee’s goal plan to their manager for approval'
                : `Goal weights must total exactly 100 to submit (currently ${weightTotal})`
            }
          >
            <Button
              type="primary"
              ghost
              loading={submitting}
              disabled={
                weightTotal !== 100 ||
                rows.some((g) => g.approvalStatus === 'PENDING_APPROVAL') ||
                !rows.some((g) => g.status === 'DRAFT' && g.approvalStatus !== 'APPROVED')
              }
              onClick={onSubmitPlan}
            >
              Submit plan for approval
            </Button>
          </Tooltip>
        )}
        {employeeId && rows.length > 0 && (
          <Tag color={weightTotal === 100 ? 'green' : 'red'}>weights {weightTotal}%</Tag>
        )}
      </Space>
      <Tabs
        activeKey={view}
        onChange={(k) => setView(k as 'list' | 'tree')}
        items={[
          { key: 'list', label: 'List',
            children: <Table rowKey="id" loading={loading} columns={columns}
                             dataSource={rows} pagination={false} /> },
          { key: 'tree', label: 'OKR tree',
            children: treeData.length === 0
              ? <Typography.Text type="secondary">
                  {treeLoading ? 'Loading…' : 'No goals in this cycle yet.'}
                </Typography.Text>
              : <Tree treeData={treeData} defaultExpandAll selectable={false} showLine /> },
        ]}
      />

      <Modal
        open={!!cascadeOpen}
        title={cascadeOpen ? `Cascade — ${cascadeOpen.goalNo} ${cascadeOpen.title}` : ''}
        onCancel={() => setCascadeOpen(null)}
        onOk={() => cascadeForm.submit()}
        okText="Cascade"
        width={520}
      >
        {cascadeOpen && (
          <Form form={cascadeForm} layout="vertical" onFinish={onCascade}>
            <Form.Item name="employeeId" label="Cascade to employee"
              rules={[{ required: true }]}
              tooltip="Title, description, category, and due date will copy from the parent. Status starts as DRAFT.">
              <Select
                showSearch
                optionFilterProp="label"
                options={employees
                  .filter((e) => e.id !== cascadeOpen.employeeId)
                  .map((e) => ({
                    value: e.id,
                    label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                  }))}
              />
            </Form.Item>
            <Form.Item name="weightPercent" label="Weight (%)"
              tooltip="Defaults to the parent goal's weight. Override for a partial share.">
              <InputNumber min={0} max={100} style={{ width: '100%' }} />
            </Form.Item>
          </Form>
        )}
      </Modal>

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
          {/* M403 — business goal type; picking one pre-selects the category */}
          <Form.Item name="goalTypeId" label="Goal type">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="e.g. Sales goal, Compliance goal…"
              options={goalTypes.map((t) => ({ value: t.id, label: t.name }))}
              onChange={(id) => {
                const t = goalTypes.find((x) => x.id === id)
                if (t) {
                  createForm.setFieldValue('category', t.defaultCategory)
                  setCreateCategory(t.defaultCategory)
                }
              }}
            />
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

      {/* M392 — §6.4 progress-update trail */}
      <Drawer
        title={historyFor ? `Progress history — ${historyFor.goalNo} ${historyFor.title}` : ''}
        open={!!historyFor}
        onClose={() => setHistoryFor(null)}
        width={520}
      >
        {loadingHistory ? (
          <Typography.Text type="secondary">Loading…</Typography.Text>
        ) : history.length === 0 ? (
          <Typography.Text type="secondary">No progress updates recorded yet.</Typography.Text>
        ) : (
          <Timeline
            items={history.map((u) => ({
              children: (
                <Space direction="vertical" size={0}>
                  <Typography.Text>
                    {u.oldProgress ?? 0}% → <strong>{u.newProgress}%</strong>
                    {u.oldStatus !== u.newStatus && u.newStatus && (
                      <Tag style={{ marginLeft: 8 }}>{u.newStatus.replace(/_/g, ' ')}</Tag>
                    )}
                  </Typography.Text>
                  {u.note && <Typography.Text type="secondary">{u.note}</Typography.Text>}
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {u.recordedBy ?? '—'} · {new Date(u.recordedAt).toLocaleString()}
                  </Typography.Text>
                </Space>
              ),
            }))}
          />
        )}
      </Drawer>
    </Card>
  )
}
