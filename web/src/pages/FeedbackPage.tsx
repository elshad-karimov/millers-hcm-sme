import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Rate,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  performanceApi,
  type Feedback,
  type FeedbackRelationship,
  type FeedbackRequest,
  type FeedbackVisibility,
  type ReviewCycle,
} from '../api/performance'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'
import { useAuth } from '../auth/AuthContext'

// ── Constants ──────────────────────────────────────────────────────────────────

const RELATIONSHIPS: FeedbackRelationship[] = [
  'PEER',
  'DIRECT_REPORT',
  'MANAGER',
  'SKIP_LEVEL',
  'CROSS_FUNCTIONAL',
  'EXTERNAL',
  'SELF',
]

const REL_COLOR: Record<FeedbackRelationship, string> = {
  PEER: 'blue',
  DIRECT_REPORT: 'cyan',
  MANAGER: 'purple',
  SKIP_LEVEL: 'magenta',
  CROSS_FUNCTIONAL: 'gold',
  EXTERNAL: 'volcano',
  SELF: 'green',
}

const CHART_COLORS = [
  '#5B3FE5',
  '#1677ff',
  '#52c41a',
  '#fa8c16',
  '#ff4d4f',
  '#13c2c2',
  '#faad14',
]

// ── Types ──────────────────────────────────────────────────────────────────────

interface FormValues {
  subjectEmployeeId: string
  authorEmployeeId?: string
  relationship: FeedbackRelationship
  visibility: FeedbackVisibility
  overallRating?: number
  strengths?: string
  improvements?: string
  comments?: string
  submit: boolean
}

interface SubjectSummary {
  id: string          // subject employee id
  count: number
  submitted: number
  avg: number | null
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function empLabel(e: Employee) {
  return `${e.employeeNo} — ${e.firstName} ${e.lastName}`
}

function empLabelMap(empMap: Map<string, Employee>, id: string) {
  const e = empMap.get(id)
  return e ? empLabel(e) : id
}

function avgRatingOf(rows: Feedback[]): number | null {
  const rated = rows.filter(r => r.overallRating != null)
  if (!rated.length) return null
  return rated.reduce((s, r) => s + Number(r.overallRating), 0) / rated.length
}

// ── AggregationPanel ───────────────────────────────────────────────────────────

function AggregationPanel({ rows }: { rows: Feedback[] }) {
  const submitted = rows.filter(r => r.status === 'SUBMITTED')
  const avgRating = avgRatingOf(submitted)

  const relData = RELATIONSHIPS.map(rel => {
    const relRows = submitted.filter(r => r.relationship === rel)
    const avg = avgRatingOf(relRows)
    return {
      name: rel.replace(/_/g, ' '),
      avg: avg != null ? +avg.toFixed(2) : 0,
      count: relRows.length,
    }
  }).filter(x => x.count > 0)

  return (
    <Card
      size="small"
      style={{ marginBottom: 16, background: '#f8f8ff', borderColor: '#d0c8f8' }}
    >
      <Row gutter={32} style={{ marginBottom: relData.length ? 16 : 0 }}>
        <Col>
          <Statistic title="Responses" value={rows.length} />
        </Col>
        <Col>
          <Statistic title="Submitted" value={submitted.length} />
        </Col>
        <Col>
          <Statistic title="Drafts" value={rows.length - submitted.length} />
        </Col>
        {avgRating != null && (
          <Col>
            <Statistic
              title="Avg rating"
              value={avgRating.toFixed(2)}
              suffix="/ 5"
            />
          </Col>
        )}
        {avgRating != null && (
          <Col style={{ display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
            <Rate
              disabled
              allowHalf
              value={avgRating}
              count={5}
              style={{ fontSize: 20 }}
            />
          </Col>
        )}
      </Row>

      {relData.length > 0 && (
        <>
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>
            Average rating by relationship (submitted, rated responses only)
          </Typography.Text>
          <ResponsiveContainer width="100%" height={Math.max(100, relData.length * 38)}>
            <BarChart
              data={relData}
              layout="vertical"
              margin={{ left: 0, right: 48, top: 2, bottom: 2 }}
            >
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis
                type="number"
                domain={[0, 5]}
                tickCount={6}
                tick={{ fontSize: 11 }}
              />
              <YAxis
                type="category"
                dataKey="name"
                width={128}
                tick={{ fontSize: 12 }}
              />
              <Tooltip
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                formatter={(v: any) => (typeof v === 'number' ? `${v.toFixed(2)} / 5` : String(v ?? ''))}
              />
              <Bar dataKey="avg" radius={[0, 4, 4, 0]}>
                <LabelList
                  dataKey="avg"
                  position="right"
                  style={{ fontSize: 11, fill: '#555' }}
                  // eslint-disable-next-line @typescript-eslint/no-explicit-any
                  formatter={(v: any) => (typeof v === 'number' && v > 0 ? v.toFixed(1) : '')}
                />
                {relData.map((_, i) => (
                  <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </>
      )}
    </Card>
  )
}

// ── SubjectOverviewTable ───────────────────────────────────────────────────────

function SubjectOverviewTable({
  rows,
  empMap,
  onSelect,
}: {
  rows: Feedback[]
  empMap: Map<string, Employee>
  onSelect: (subjectId: string) => void
}) {
  const subjects = useMemo<SubjectSummary[]>(() => {
    const map = new Map<string, Feedback[]>()
    rows.forEach(r => {
      const arr = map.get(r.subjectEmployeeId) ?? []
      arr.push(r)
      map.set(r.subjectEmployeeId, arr)
    })
    return Array.from(map.entries()).map(([id, list]) => {
      const submitted = list.filter(r => r.status === 'SUBMITTED')
      return {
        id,
        count: list.length,
        submitted: submitted.length,
        avg: avgRatingOf(submitted),
      }
    })
  }, [rows])

  const columns: ColumnsType<SubjectSummary> = [
    {
      title: 'Employee',
      dataIndex: 'id',
      render: (id: string) => empLabelMap(empMap, id),
    },
    {
      title: 'Total responses',
      dataIndex: 'count',
      width: 130,
      align: 'center',
    },
    {
      title: 'Submitted',
      dataIndex: 'submitted',
      width: 110,
      align: 'center',
    },
    {
      title: 'Avg rating',
      dataIndex: 'avg',
      width: 220,
      render: (v: number | null) =>
        v != null ? (
          <Space size={6}>
            <Rate disabled allowHalf value={v} count={5} style={{ fontSize: 14 }} />
            <Typography.Text type="secondary">{v.toFixed(1)}</Typography.Text>
          </Space>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <Button size="small" onClick={() => onSelect(r.id)}>
          Details
        </Button>
      ),
    },
  ]

  return (
    <Table
      rowKey="id"
      columns={columns}
      dataSource={subjects}
      pagination={false}
      size="small"
      locale={{ emptyText: 'No feedback recorded for this cycle yet.' }}
    />
  )
}

// ── FeedbackTable ──────────────────────────────────────────────────────────────

function FeedbackTable({
  rows,
  empMap,
  loading,
}: {
  rows: Feedback[]
  empMap: Map<string, Employee>
  loading: boolean
}) {
  const columns: ColumnsType<Feedback> = [
    {
      title: 'Subject',
      dataIndex: 'subjectEmployeeId',
      render: (id: string) => empLabelMap(empMap, id),
    },
    {
      title: 'Relationship',
      dataIndex: 'relationship',
      width: 150,
      render: (r: FeedbackRelationship) => (
        <Tag color={REL_COLOR[r]}>{r.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: 'Author',
      dataIndex: 'authorEmployeeId',
      render: (id: string | null, r) => {
        if (r.visibility === 'ANONYMOUS')
          return <Typography.Text type="secondary">anonymous</Typography.Text>
        if (!id) return '—'
        return empLabelMap(empMap, id)
      },
    },
    {
      title: 'Rating',
      dataIndex: 'overallRating',
      width: 175,
      render: (v: number | null) =>
        v != null ? (
          <Space size={6}>
            <Rate disabled allowHalf value={Number(v)} count={5} style={{ fontSize: 13 }} />
            <Typography.Text>{Number(v).toFixed(1)}</Typography.Text>
          </Space>
        ) : (
          '—'
        ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: string) => (
        <Tag color={s === 'SUBMITTED' ? 'green' : 'orange'}>{s}</Tag>
      ),
    },
    {
      title: 'Strengths / Improvements',
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          {r.strengths && <Typography.Text>👍 {r.strengths}</Typography.Text>}
          {r.improvements && <Typography.Text>🛠 {r.improvements}</Typography.Text>}
        </Space>
      ),
    },
  ]

  return (
    <Table
      rowKey="id"
      loading={loading}
      columns={columns}
      dataSource={rows}
      pagination={false}
      size="small"
    />
  )
}

// ── MyFeedbackTab ──────────────────────────────────────────────────────────────

function MyFeedbackTab({
  rows,
  empMap,
  loading,
  onSubmitDraft,
}: {
  rows: Feedback[]
  empMap: Map<string, Employee>
  loading: boolean
  onSubmitDraft: (id: string) => Promise<void>
}) {
  const [submitting, setSubmitting] = useState<string | null>(null)

  const handleSubmit = async (id: string) => {
    setSubmitting(id)
    await onSubmitDraft(id).finally(() => setSubmitting(null))
  }

  if (!loading && !rows.length) {
    return (
      <Typography.Text
        type="secondary"
        style={{ display: 'block', textAlign: 'center', padding: '40px 0' }}
      >
        You haven't given any feedback in this cycle yet.
      </Typography.Text>
    )
  }

  const columns: ColumnsType<Feedback> = [
    {
      title: 'Subject (being reviewed)',
      dataIndex: 'subjectEmployeeId',
      render: (id: string) => empLabelMap(empMap, id),
    },
    {
      title: 'Relationship',
      dataIndex: 'relationship',
      width: 150,
      render: (r: FeedbackRelationship) => (
        <Tag color={REL_COLOR[r]}>{r.replace(/_/g, ' ')}</Tag>
      ),
    },
    {
      title: 'Rating',
      dataIndex: 'overallRating',
      width: 175,
      render: (v: number | null) =>
        v != null ? (
          <Space size={6}>
            <Rate disabled allowHalf value={Number(v)} count={5} style={{ fontSize: 13 }} />
            <Typography.Text>{Number(v).toFixed(1)}</Typography.Text>
          </Space>
        ) : (
          '—'
        ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 160,
      render: (s: string, r) => (
        <Space>
          <Tag color={s === 'SUBMITTED' ? 'green' : 'orange'}>{s}</Tag>
          {s === 'DRAFT' && (
            <Button
              size="small"
              type="primary"
              loading={submitting === r.id}
              onClick={() => handleSubmit(r.id)}
            >
              Submit
            </Button>
          )}
        </Space>
      ),
    },
    {
      title: 'Strengths / Improvements',
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          {r.strengths && <Typography.Text>👍 {r.strengths}</Typography.Text>}
          {r.improvements && <Typography.Text>🛠 {r.improvements}</Typography.Text>}
        </Space>
      ),
    },
  ]

  return (
    <Table
      rowKey="id"
      loading={loading}
      columns={columns}
      dataSource={rows}
      pagination={false}
      size="small"
    />
  )
}

// ── Main Page ──────────────────────────────────────────────────────────────────

export function FeedbackPage() {
  const { message } = AntdApp.useApp()
  const { user } = useAuth()

  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [cycleId, setCycleId] = useState<string | undefined>()
  const [subjectId, setSubjectId] = useState<string | undefined>()
  const [allRows, setAllRows] = useState<Feedback[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<FormValues>()

  // Initial data load
  useEffect(() => {
    Promise.all([
      performanceApi.cycles(),
      employeesApi.list({ size: 500 }),
    ]).then(([c, e]) => {
      setCycles(c)
      setEmployees(e.content)
      if (c.length) {
        const openCycle = c.find(x => x.status === 'OPEN') ?? c[0]
        setCycleId(openCycle.id)
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Load all feedback for the selected cycle (no subject filter — filter client-side)
  const reload = (cid = cycleId) => {
    if (!cid) return
    setLoading(true)
    performanceApi
      .feedback(cid)
      .then(setAllRows)
      .catch(err =>
        message.error(err?.response?.data?.message ?? 'Failed to load feedback'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cycleId])

  const empMap = useMemo(
    () => new Map(employees.map(e => [e.id, e])),
    [employees],
  )

  // Client-side subject filter
  const rows = useMemo(
    () =>
      subjectId
        ? allRows.filter(r => r.subjectEmployeeId === subjectId)
        : allRows,
    [allRows, subjectId],
  )

  // My given feedback — identified by createdBy matching the logged-in username
  const myRows = useMemo(
    () => (user ? allRows.filter(r => r.createdBy === user.username) : []),
    [allRows, user],
  )

  const onSubmit = async (v: FormValues) => {
    if (!cycleId) return
    const payload: FeedbackRequest = {
      cycleId,
      subjectEmployeeId: v.subjectEmployeeId,
      authorEmployeeId:
        v.visibility === 'ANONYMOUS' ? undefined : v.authorEmployeeId,
      relationship: v.relationship,
      visibility: v.visibility,
      overallRating: v.overallRating,
      strengths: v.strengths,
      improvements: v.improvements,
      comments: v.comments,
      submit: v.submit,
    }
    try {
      await performanceApi.submitFeedback(payload)
      message.success(v.submit ? 'Feedback submitted' : 'Feedback draft saved')
      setOpen(false)
      form.resetFields()
      reload()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data
          ?.message ?? 'Save failed',
      )
    }
  }

  const onSubmitDraft = async (id: string) => {
    try {
      await performanceApi.submitDraft(id)
      message.success('Feedback submitted')
      reload()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data
          ?.message ?? 'Failed to submit draft',
      )
    }
  }

  const myBadge =
    myRows.length > 0 ? ` (${myRows.length})` : ''

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          360° Feedback
        </Typography.Title>
      }
      extra={
        <Button type="primary" disabled={!cycleId} onClick={() => setOpen(true)}>
          + Give feedback
        </Button>
      }
    >
      {/* Cycle selector */}
      <Row style={{ marginBottom: 16 }}>
        <Col>
          <Select
            placeholder="Select cycle"
            style={{ width: 320 }}
            options={cycles.map(c => ({
              value: c.id,
              label: `${c.code} — ${c.name} (${c.status})`,
            }))}
            value={cycleId}
            onChange={v => {
              setCycleId(v)
              setSubjectId(undefined)
            }}
          />
        </Col>
      </Row>

      <Tabs
        items={[
          {
            key: 'subject',
            label: 'Subject view',
            children: (
              <>
                <Space style={{ marginBottom: 12 }} wrap>
                  <EmployeePicker
                    allowClear
                    placeholder="All subjects"
                    style={{ width: 300 }}
                    value={subjectId}
                    onChange={setSubjectId}
                  />
                  {subjectId && (
                    <Button size="small" onClick={() => setSubjectId(undefined)}>
                      Show all
                    </Button>
                  )}
                </Space>

                {/* Aggregation panel when a specific subject is selected */}
                {subjectId && rows.length > 0 && (
                  <AggregationPanel rows={rows} />
                )}

                {/* Detail table when subject selected, overview when not */}
                {subjectId ? (
                  <FeedbackTable
                    rows={rows}
                    empMap={empMap}
                    loading={loading}
                  />
                ) : (
                  <SubjectOverviewTable
                    rows={allRows}
                    empMap={empMap}
                    onSelect={setSubjectId}
                  />
                )}
              </>
            ),
          },
          {
            key: 'mine',
            label: `My given feedback${myBadge}`,
            children: (
              <MyFeedbackTab
                rows={myRows}
                empMap={empMap}
                loading={loading}
                onSubmitDraft={onSubmitDraft}
              />
            ),
          },
        ]}
      />

      {/* Give-feedback modal */}
      <Modal
        open={open}
        title="Give feedback"
        onCancel={() => {
          setOpen(false)
          form.resetFields()
        }}
        onOk={() => form.submit()}
        okText="Save"
        width={640}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={onSubmit}
          initialValues={{
            relationship: 'PEER',
            visibility: 'NORMAL',
            submit: true,
          }}
        >
          <Form.Item
            name="subjectEmployeeId"
            label="Subject (person being reviewed)"
            rules={[{ required: true, message: 'Please select a subject' }]}
          >
            <EmployeePicker placeholder="Select subject" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="relationship"
                label="Your relationship to subject"
                rules={[{ required: true }]}
              >
                <Select
                  options={RELATIONSHIPS.map(r => ({
                    value: r,
                    label: r.replace(/_/g, ' '),
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="visibility" label="Visibility">
                <Select
                  options={[
                    { value: 'NORMAL', label: 'Normal (name visible to HR)' },
                    { value: 'ANONYMOUS', label: 'Anonymous' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="authorEmployeeId"
            label="Author employee (leave blank for anonymous submissions)"
          >
            <EmployeePicker allowClear placeholder="Select author" />
          </Form.Item>

          <Form.Item name="overallRating" label="Overall rating (0 – 5)">
            <InputNumber min={0} max={5} step={0.5} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="strengths" label="Strengths">
            <Input.TextArea rows={3} placeholder="What does this person do well?" />
          </Form.Item>

          <Form.Item name="improvements" label="Areas for improvement">
            <Input.TextArea rows={3} placeholder="What could they develop further?" />
          </Form.Item>

          <Form.Item name="comments" label="Additional comments">
            <Input.TextArea rows={2} />
          </Form.Item>

          <Form.Item name="submit" valuePropName="checked">
            <Checkbox>Submit now (uncheck to save as draft)</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
