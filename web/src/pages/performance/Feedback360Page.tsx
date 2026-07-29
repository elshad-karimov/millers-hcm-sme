// HCM_12 M395 — 360° reviewer nominations + questionnaires (PRD §13–§16).
// Nominate reviewers per subject+cycle (anonymous by default), approve/decline,
// complete with a questionnaire-driven response (stored on the existing feedback
// entity), and track §13.4 min/max completeness.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Rate,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  feedback360Api,
  performanceApi,
  type FeedbackQuestion360,
  type FeedbackRelationship,
  type Nomination360,
  type Nomination360Summary,
  type NominationStatus,
  type Questionnaire360,
  type ReviewCycle,
} from '../../api/performance'
import { EmployeePicker } from '../../components/EmployeePicker'
import { useAuth } from '../../auth/AuthContext'
import { RoleSets } from '../../auth/roleSets'

const { Title, Text } = Typography

const STATUS_COLOR: Record<NominationStatus, string> = {
  NOMINATED: 'blue',
  APPROVED: 'cyan',
  COMPLETED: 'green',
  DECLINED: 'red',
  CANCELLED: 'default',
}

const RELATIONSHIPS: FeedbackRelationship[] = [
  'PEER',
  'DIRECT_REPORT',
  'MANAGER',
  'SKIP_LEVEL',
  'CROSS_FUNCTIONAL',
  'EXTERNAL',
  'SELF',
]

const QUESTION_CATEGORIES = [
  'COMPETENCY',
  'BEHAVIORAL',
  'COLLABORATION',
  'LEADERSHIP',
  'STRENGTHS',
  'IMPROVEMENT',
  'OPEN',
] as const

export function Feedback360Page() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canAdmin = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [questionnaires, setQuestionnaires] = useState<Questionnaire360[]>([])
  const [loading, setLoading] = useState(true)

  const [cycleId, setCycleId] = useState<string | undefined>()
  const [subjectId, setSubjectId] = useState<string | undefined>()
  const [rows, setRows] = useState<Nomination360[]>([])
  const [summary, setSummary] = useState<Nomination360Summary | null>(null)
  const [loadingRows, setLoadingRows] = useState(false)

  // nominate
  const [nomOpen, setNomOpen] = useState(false)
  const [savingNom, setSavingNom] = useState(false)
  const [nomForm] = Form.useForm<{
    subjectEmployeeId: string
    reviewerEmployeeId: string
    relationship: FeedbackRelationship
    questionnaireId?: string
    anonymous: boolean
    dueDate?: dayjs.Dayjs | null
  }>()

  // complete (respond)
  const [responding, setResponding] = useState<Nomination360 | null>(null)
  const [savingResponse, setSavingResponse] = useState(false)
  const [respForm] = Form.useForm<Record<string, unknown>>()

  // questionnaire editor
  const [qOpen, setQOpen] = useState(false)
  const [editingQ, setEditingQ] = useState<Questionnaire360 | null>(null)
  const [savingQ, setSavingQ] = useState(false)
  const [qQuestions, setQQuestions] = useState<FeedbackQuestion360[]>([])
  const [qForm] = Form.useForm<{ code: string; name: string; description?: string; active: boolean }>()

  const load = () => {
    setLoading(true)
    Promise.all([
      performanceApi.cycles(),
      feedback360Api.questionnaires(),
    ])
      .then(([c, q]) => {
        setCycles(c)
        setQuestionnaires(q)
        if (!cycleId && c.length) {
          const open = c.find((x) => x.status === 'OPEN') ?? c[0]
          setCycleId(open.id)
        }
      })
      .catch(() => message.error('Failed to load 360° data'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line react-hooks/exhaustive-deps

  const loadRows = () => {
    if (!cycleId) return
    setLoadingRows(true)
    Promise.all([
      feedback360Api.requests({ cycleId, subjectEmployeeId: subjectId }),
      subjectId
        ? feedback360Api.summary(cycleId, subjectId)
        : Promise.resolve(null),
    ])
      .then(([r, s]) => {
        setRows(r)
        setSummary(s)
      })
      .catch(() => message.error('Failed to load nominations'))
      .finally(() => setLoadingRows(false))
  }
  useEffect(loadRows, [cycleId, subjectId]) // eslint-disable-line react-hooks/exhaustive-deps

  const qMap = useMemo(() => new Map(questionnaires.map((q) => [q.id, q])), [questionnaires])

  const saveNomination = async () => {
    if (!cycleId) return
    const v = await nomForm.validateFields()
    setSavingNom(true)
    try {
      await feedback360Api.nominate({
        cycleId,
        subjectEmployeeId: v.subjectEmployeeId,
        reviewerEmployeeId: v.reviewerEmployeeId,
        relationship: v.relationship,
        questionnaireId: v.questionnaireId,
        anonymous: v.anonymous,
        dueDate: v.dueDate ? v.dueDate.format('YYYY-MM-DD') : null,
      })
      message.success('Reviewer nominated')
      setNomOpen(false)
      nomForm.resetFields()
      loadRows()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Nomination failed')
    } finally {
      setSavingNom(false)
    }
  }

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn()
      message.success(ok)
      loadRows()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Action failed')
    }
  }

  const saveResponse = async () => {
    if (!responding) return
    const v = await respForm.validateFields()
    const questionnaire = responding.questionnaireId
      ? qMap.get(responding.questionnaireId)
      : undefined
    const answers: Record<string, unknown> = {}
    questionnaire?.questions.forEach((q, i) => {
      const val = v[`q_${i}`]
      if (val !== undefined && val !== null && val !== '') {
        answers[q.questionText] = val
      }
    })
    setSavingResponse(true)
    try {
      await feedback360Api.complete(responding.id, {
        overallRating: v.overallRating as number | undefined,
        strengths: v.strengths as string | undefined,
        improvements: v.improvements as string | undefined,
        comments: v.comments as string | undefined,
        competencies: Object.keys(answers).length ? answers : undefined,
      })
      message.success('Feedback submitted — request completed')
      setResponding(null)
      respForm.resetFields()
      loadRows()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Submit failed')
    } finally {
      setSavingResponse(false)
    }
  }

  // ── Questionnaire editor ──────────────────────────────────────────────────

  const openQEditor = (q?: Questionnaire360) => {
    setEditingQ(q ?? null)
    qForm.resetFields()
    if (q) {
      qForm.setFieldsValue({
        code: q.code,
        name: q.name,
        description: q.description ?? undefined,
        active: q.active,
      })
      setQQuestions(q.questions.map((x) => ({ ...x })))
    } else {
      qForm.setFieldsValue({ active: true })
      setQQuestions([{ questionText: '', questionType: 'RATING', category: 'COMPETENCY', required: true }])
    }
    setQOpen(true)
  }

  const saveQuestionnaire = async () => {
    const v = await qForm.validateFields()
    const qs = qQuestions.filter((q) => q.questionText.trim())
    if (!qs.length) {
      message.error('Add at least one question')
      return
    }
    setSavingQ(true)
    try {
      const req = { ...v, questions: qs }
      if (editingQ) await feedback360Api.updateQuestionnaire(editingQ.id, req)
      else await feedback360Api.createQuestionnaire(req)
      message.success(editingQ ? 'Questionnaire updated' : 'Questionnaire created')
      setQOpen(false)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    } finally {
      setSavingQ(false)
    }
  }

  // ── Tables ────────────────────────────────────────────────────────────────

  const columns: ColumnsType<Nomination360> = [
    {
      title: 'Subject',
      key: 'subject',
      render: (_, n) => n.subjectName ?? n.subjectEmployeeId,
    },
    {
      title: 'Reviewer',
      key: 'reviewer',
      render: (_, n) => (
        <Space size={4}>
          {n.reviewerName ?? n.reviewerEmployeeId}
          {n.anonymous && <Tag>anonymous</Tag>}
        </Space>
      ),
    },
    {
      title: 'Relationship',
      dataIndex: 'relationship',
      width: 140,
      render: (v: string) => <Tag color="geekblue">{v.replace(/_/g, ' ')}</Tag>,
    },
    {
      title: 'Questionnaire',
      dataIndex: 'questionnaireName',
      width: 170,
      render: (v) => v ?? <Text type="secondary">free-form</Text>,
    },
    { title: 'Due', dataIndex: 'dueDate', width: 110, render: (v) => v ?? '—' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (v: NominationStatus) => <Tag color={STATUS_COLOR[v]}>{v}</Tag>,
    },
    {
      title: '',
      key: 'actions',
      width: 260,
      render: (_, n) => (
        <Space size={4} wrap>
          {n.status === 'NOMINATED' && (
            <Button size="small" onClick={() => act(() => feedback360Api.approve(n.id), 'Approved')}>
              Approve
            </Button>
          )}
          {(n.status === 'NOMINATED' || n.status === 'APPROVED') && (
            <>
              <Button
                size="small"
                type="primary"
                onClick={() => {
                  respForm.resetFields()
                  setResponding(n)
                }}
              >
                Respond
              </Button>
              <Button
                size="small"
                danger
                onClick={() =>
                  Modal.confirm({
                    title: 'Decline this feedback request?',
                    content: (
                      <Input.TextArea
                        id="decline-reason-input"
                        placeholder="Reason (optional)"
                        rows={2}
                      />
                    ),
                    onOk: () =>
                      act(
                        () =>
                          feedback360Api.decline(
                            n.id,
                            (document.getElementById('decline-reason-input') as HTMLTextAreaElement)
                              ?.value || undefined,
                          ),
                        'Declined',
                      ),
                  })
                }
              >
                Decline
              </Button>
              <Button
                size="small"
                onClick={() => act(() => feedback360Api.cancelRequest(n.id), 'Cancelled')}
              >
                Cancel
              </Button>
            </>
          )}
          {n.status === 'DECLINED' && n.declineReason && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {n.declineReason}
            </Text>
          )}
        </Space>
      ),
    },
  ]

  const qColumns: ColumnsType<Questionnaire360> = [
    { title: 'Code', dataIndex: 'code', width: 140, render: (v) => <Text code>{v}</Text> },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Questions',
      key: 'questions',
      width: 110,
      align: 'center',
      render: (_, q) => q.questions.length,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (v: boolean) => (v ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>),
    },
    ...(canAdmin
      ? [
          {
            title: '',
            key: 'actions',
            width: 80,
            render: (_: unknown, q: Questionnaire360) => (
              <Button size="small" onClick={() => openQEditor(q)}>
                Edit
              </Button>
            ),
          } as ColumnsType<Questionnaire360>[number],
        ]
      : []),
  ]

  const respondingQuestionnaire = responding?.questionnaireId
    ? qMap.get(responding.questionnaireId)
    : undefined

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            360° Feedback
          </Title>
          <Text type="secondary">
            Nominate reviewers (peer / subordinate / manager / cross-functional), track approval
            and completion; responses land on the Feedback page. Anonymous by default.
          </Text>
        </Col>
      </Row>

      <Tabs
        defaultActiveKey="nominations"
        items={[
          {
            key: 'nominations',
            label: 'Nominations',
            children: (
              <Card size="small">
                <Space style={{ marginBottom: 12 }} wrap>
                  <Select
                    style={{ minWidth: 260 }}
                    placeholder="Review cycle"
                    value={cycleId}
                    onChange={setCycleId}
                    options={cycles.map((c) => ({ value: c.id, label: c.name }))}
                  />
                  <EmployeePicker
                    allowClear
                    style={{ minWidth: 260 }}
                    placeholder="All subjects"
                    value={subjectId}
                    onChange={setSubjectId}
                  />
                  <Button
                    type="primary"
                    disabled={!cycleId}
                    onClick={() => {
                      nomForm.resetFields()
                      nomForm.setFieldsValue({
                        anonymous: true,
                        subjectEmployeeId: subjectId,
                        relationship: 'PEER',
                      })
                      setNomOpen(true)
                    }}
                  >
                    Nominate reviewer
                  </Button>
                  {summary && (
                    <Space size={4}>
                      <Tag color={summary.meetsMinimum ? 'green' : 'red'}>
                        {summary.completed} completed
                        {summary.minReviewers != null ? ` / min ${summary.minReviewers}` : ''}
                      </Tag>
                      <Tag>
                        {summary.nominated} nominated · {summary.approved} approved ·{' '}
                        {summary.declined} declined
                        {summary.maxReviewers != null ? ` · max ${summary.maxReviewers}` : ''}
                      </Tag>
                    </Space>
                  )}
                </Space>
                <Table
                  rowKey="id"
                  size="small"
                  loading={loadingRows}
                  columns={columns}
                  dataSource={rows}
                  pagination={{ pageSize: 20, hideOnSinglePage: true }}
                />
              </Card>
            ),
          },
          {
            key: 'questionnaires',
            label: `Questionnaires (${questionnaires.length})`,
            children: (
              <Card size="small">
                {canAdmin && (
                  <Button type="primary" style={{ marginBottom: 12 }} onClick={() => openQEditor()}>
                    New questionnaire
                  </Button>
                )}
                <Table
                  rowKey="id"
                  size="small"
                  columns={qColumns}
                  dataSource={questionnaires}
                  pagination={false}
                  expandable={{
                    expandedRowRender: (q) => (
                      <ul style={{ margin: 0 }}>
                        {q.questions.map((x) => (
                          <li key={x.id ?? x.questionText}>
                            {x.questionText}{' '}
                            <Tag>{x.questionType}</Tag>
                            {x.category && <Tag color="purple">{x.category}</Tag>}
                            {x.required && <Tag color="orange">required</Tag>}
                          </li>
                        ))}
                      </ul>
                    ),
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* Nominate modal */}
      <Modal
        title="Nominate reviewer"
        open={nomOpen}
        onCancel={() => setNomOpen(false)}
        onOk={saveNomination}
        confirmLoading={savingNom}
        okText="Nominate"
        destroyOnClose
      >
        <Form form={nomForm} layout="vertical">
          <Form.Item name="subjectEmployeeId" label="Subject (who is reviewed)" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select subject" />
          </Form.Item>
          <Form.Item name="reviewerEmployeeId" label="Reviewer" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select reviewer" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="relationship" label="Relationship" rules={[{ required: true }]}>
                <Select
                  options={RELATIONSHIPS.map((r) => ({
                    value: r,
                    label: r.replace(/_/g, ' '),
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="questionnaireId" label="Questionnaire (optional)">
                <Select
                  allowClear
                  options={questionnaires
                    .filter((q) => q.active)
                    .map((q) => ({ value: q.id, label: q.name }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="anonymous" label="Anonymous response" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="dueDate" label="Due date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* Respond (complete) modal */}
      <Modal
        title={
          responding
            ? `Feedback on ${responding.subjectName ?? 'employee'} (${responding.relationship.replace(/_/g, ' ')})`
            : ''
        }
        open={!!responding}
        onCancel={() => setResponding(null)}
        onOk={saveResponse}
        confirmLoading={savingResponse}
        okText="Submit feedback"
        width={640}
        destroyOnClose
      >
        <Form form={respForm} layout="vertical">
          {respondingQuestionnaire && (
            <Card size="small" title={respondingQuestionnaire.name} style={{ marginBottom: 12 }}>
              {respondingQuestionnaire.questions.map((q, i) => (
                <Form.Item
                  key={q.id ?? i}
                  name={`q_${i}`}
                  label={q.questionText}
                  rules={q.required ? [{ required: true, message: 'Required' }] : []}
                >
                  {q.questionType === 'RATING' ? <Rate count={5} /> : <Input.TextArea rows={2} />}
                </Form.Item>
              ))}
            </Card>
          )}
          <Form.Item name="overallRating" label="Overall rating (0–5)">
            <InputNumber min={0} max={5} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="strengths" label="Strengths">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="improvements" label="Improvement areas">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="comments" label="Comments">
            <Input.TextArea rows={2} />
          </Form.Item>
          {responding?.anonymous && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              This response is anonymous — the subject will not see your name.
            </Text>
          )}
        </Form>
      </Modal>

      {/* Questionnaire editor */}
      <Modal
        title={editingQ ? `Edit questionnaire — ${editingQ.code}` : 'New questionnaire'}
        open={qOpen}
        onCancel={() => setQOpen(false)}
        onOk={saveQuestionnaire}
        confirmLoading={savingQ}
        okText="Save"
        width={760}
        destroyOnClose
      >
        <Form form={qForm} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 40 }]}>
                <Input placeholder="STD_360" disabled={!!editingQ} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
                <Input placeholder="Standard 360 questionnaire" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={1} />
          </Form.Item>
        </Form>
        <Typography.Text strong>Questions</Typography.Text>
        {qQuestions.map((q, i) => (
          <Row key={i} gutter={8} style={{ marginTop: 8 }} align="middle">
            <Col span={11}>
              <Input
                placeholder="Question text"
                value={q.questionText}
                onChange={(e) => {
                  const next = [...qQuestions]
                  next[i] = { ...q, questionText: e.target.value }
                  setQQuestions(next)
                }}
              />
            </Col>
            <Col span={4}>
              <Select
                style={{ width: '100%' }}
                value={q.questionType ?? 'RATING'}
                onChange={(v) => {
                  const next = [...qQuestions]
                  next[i] = { ...q, questionType: v }
                  setQQuestions(next)
                }}
                options={[
                  { value: 'RATING', label: 'Rating' },
                  { value: 'TEXT', label: 'Text' },
                ]}
              />
            </Col>
            <Col span={5}>
              <Select
                style={{ width: '100%' }}
                allowClear
                placeholder="Category"
                value={q.category ?? undefined}
                onChange={(v) => {
                  const next = [...qQuestions]
                  next[i] = { ...q, category: v ?? null }
                  setQQuestions(next)
                }}
                options={QUESTION_CATEGORIES.map((c) => ({ value: c, label: c }))}
              />
            </Col>
            <Col span={2}>
              <Switch
                checked={q.required ?? true}
                checkedChildren="req"
                unCheckedChildren="opt"
                onChange={(v) => {
                  const next = [...qQuestions]
                  next[i] = { ...q, required: v }
                  setQQuestions(next)
                }}
              />
            </Col>
            <Col span={2}>
              <Button
                size="small"
                danger
                onClick={() => setQQuestions(qQuestions.filter((_, x) => x !== i))}
              >
                ✕
              </Button>
            </Col>
          </Row>
        ))}
        <Button
          style={{ marginTop: 8 }}
          onClick={() =>
            setQQuestions([
              ...qQuestions,
              { questionText: '', questionType: 'RATING', category: null, required: true },
            ])
          }
        >
          Add question
        </Button>
      </Modal>
    </div>
  )
}
