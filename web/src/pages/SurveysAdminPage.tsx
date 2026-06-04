// M116 — Engagement surveys + eNPS admin page.
//
// Three tabs:
//   1. Templates — define reusable survey blueprints
//   2. Campaigns — launch a template over a date window
//   3. Results — aggregate stats + NPS per question

import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
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
import {
  surveysAdminApi,
  type CampaignResponse,
  type CampaignResults,
  type CampaignStatus,
  type QuestionAggregate,
  type QuestionRequest,
  type QuestionType,
  type TemplateResponse,
} from '../api/surveys'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text, Paragraph } = Typography

const TYPE_OPTIONS: { value: QuestionType; label: string }[] = [
  { value: 'RATING_1_5',     label: 'Rating 1–5 (satisfaction)' },
  { value: 'RATING_1_10',    label: 'Rating 0–10 (eNPS)' },
  { value: 'BOOLEAN',        label: 'Yes / No' },
  { value: 'TEXT',           label: 'Free text' },
  { value: 'MULTIPLE_CHOICE',label: 'Multiple choice' },
]

const STATUS_COLOR: Record<CampaignStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'green',
  CLOSED: 'blue',
  CANCELLED: 'red',
}

// ─── Templates tab ───────────────────────────────────────────────────────────

function TemplatesTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [templates, setTemplates] = useState<TemplateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<TemplateResponse | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [saving, setSaving] = useState(false)

  // Editor state — mirror of the form
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [anonymous, setAnonymous] = useState(false)
  const [active, setActive] = useState(true)
  const [questions, setQuestions] = useState<QuestionRequest[]>([])

  const load = () => {
    setLoading(true)
    surveysAdminApi
      .listTemplates(false)
      .then(setTemplates)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load templates'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])  // eslint-disable-line

  const startCreate = () => {
    setEditing(null)
    setCode('')
    setName('')
    setDescription('')
    setAnonymous(false)
    setActive(true)
    setQuestions([{ orderIndex: 0, prompt: '', questionType: 'RATING_1_5', required: true }])
    setDrawerOpen(true)
  }

  const startEdit = (t: TemplateResponse) => {
    setEditing(t)
    setCode(t.code)
    setName(t.name)
    setDescription(t.description ?? '')
    setAnonymous(t.anonymous)
    setActive(t.active)
    setQuestions(t.questions.map((q) => ({
      orderIndex: q.orderIndex,
      prompt: q.prompt,
      questionType: q.questionType,
      metadata: q.metadata ?? undefined,
      required: q.required,
    })))
    setDrawerOpen(true)
  }

  const updateQ = (idx: number, patch: Partial<QuestionRequest>) => {
    setQuestions((prev) => prev.map((q, i) => i === idx ? { ...q, ...patch } : q))
  }

  const addQ = () => {
    setQuestions((prev) => [...prev, {
      orderIndex: prev.length,
      prompt: '',
      questionType: 'RATING_1_5',
      required: true,
    }])
  }

  const removeQ = (idx: number) => {
    setQuestions((prev) => prev
      .filter((_, i) => i !== idx)
      .map((q, i) => ({ ...q, orderIndex: i })))
  }

  const submit = async () => {
    if (!code || !name) {
      message.error('Code and name are required')
      return
    }
    if (questions.length === 0) {
      message.error('At least one question is required')
      return
    }
    setSaving(true)
    try {
      const req = {
        code, name, description: description || undefined,
        anonymous, active,
        questions: questions.map((q, i) => ({ ...q, orderIndex: i })),
      }
      if (editing) {
        await surveysAdminApi.updateTemplate(editing.id, req)
        message.success('Template updated')
      } else {
        await surveysAdminApi.createTemplate(req)
        message.success('Template created')
      }
      setDrawerOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const cols: ColumnsType<TemplateResponse> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 130,
      render: (v, r) => <a onClick={() => canWrite && startEdit(r)}>{v}</a>,
    },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Anonymous',
      width: 100,
      align: 'center',
      render: (_, r) => r.anonymous ? <Tag color="purple">yes</Tag> : <Tag>no</Tag>,
    },
    {
      title: 'Questions',
      dataIndex: 'questionCount',
      width: 100,
      align: 'right',
    },
    {
      title: 'Status',
      width: 100,
      align: 'center',
      render: (_, r) => r.active
        ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
        {canWrite && <Button type="primary" onClick={startCreate}>New template…</Button>}
      </Space>
      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={templates}
          size="small"
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: <Empty description="No templates yet" /> }}
        />
      </Card>

      <Drawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={780}
        title={editing ? `Edit template — ${editing.code}` : 'New template'}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>Cancel</Button>
            <Button type="primary" loading={saving} onClick={submit}>
              {editing ? 'Save' : 'Create'}
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Row gutter={12}>
            <Col span={8}>
              <Text strong>Code</Text>
              <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="ENPS-Q1-2026" />
            </Col>
            <Col span={16}>
              <Text strong>Name</Text>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Quarterly engagement pulse" />
            </Col>
          </Row>
          <div>
            <Text strong>Description</Text>
            <Input.TextArea rows={2} value={description}
              onChange={(e) => setDescription(e.target.value)} />
          </div>
          <Space>
            <Switch checked={anonymous} onChange={setAnonymous}
              checkedChildren="anonymous" unCheckedChildren="identified" />
            <Switch checked={active} onChange={setActive}
              checkedChildren="active" unCheckedChildren="archived" />
          </Space>

          <Card title="Questions" size="small">
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              {questions.map((q, idx) => (
                <Card key={idx} size="small" style={{ background: '#fafafa' }}>
                  <Row gutter={8}>
                    <Col span={2}>
                      <Tag>Q{idx + 1}</Tag>
                    </Col>
                    <Col span={14}>
                      <Input
                        value={q.prompt}
                        onChange={(e) => updateQ(idx, { prompt: e.target.value })}
                        placeholder="How likely are you to recommend Acme as a place to work?"
                      />
                    </Col>
                    <Col span={6}>
                      <Select
                        value={q.questionType}
                        onChange={(v) => updateQ(idx, { questionType: v })}
                        options={TYPE_OPTIONS}
                        style={{ width: '100%' }}
                      />
                    </Col>
                    <Col span={2}>
                      <Popconfirm
                        title="Remove this question?"
                        onConfirm={() => removeQ(idx)}
                      >
                        <Button size="small" danger>×</Button>
                      </Popconfirm>
                    </Col>
                  </Row>
                  {q.questionType === 'MULTIPLE_CHOICE' && (
                    <Row gutter={8} style={{ marginTop: 8 }}>
                      <Col offset={2} span={20}>
                        <Input
                          placeholder='Choice list as JSON: {"options":["A","B","C"]}'
                          value={q.metadata ?? ''}
                          onChange={(e) => updateQ(idx, { metadata: e.target.value })}
                        />
                      </Col>
                    </Row>
                  )}
                </Card>
              ))}
              <Button onClick={addQ}>+ Add question</Button>
            </Space>
          </Card>
        </Space>
      </Drawer>
    </Space>
  )
}

// ─── Campaigns tab ───────────────────────────────────────────────────────────

function CampaignsTab() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_WRITE)

  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([])
  const [templates, setTemplates] = useState<TemplateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<{
    templateId: string
    name: string
    description?: string
    window: [ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]
  }>()
  const [saving, setSaving] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([
      surveysAdminApi.listCampaigns(),
      surveysAdminApi.listTemplates(true),
    ])
      .then(([c, t]) => { setCampaigns(c); setTemplates(t) })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])  // eslint-disable-line

  const startCreate = () => {
    form.resetFields()
    form.setFieldsValue({
      window: [dayjs(), dayjs().add(14, 'day')],
    })
    setOpen(true)
  }

  const submit = async () => {
    const v = await form.validateFields()
    setSaving(true)
    try {
      await surveysAdminApi.createCampaign({
        templateId: v.templateId,
        name: v.name,
        description: v.description,
        opensOn: v.window[0].format('YYYY-MM-DD'),
        closesOn: v.window[1].format('YYYY-MM-DD'),
        targetAll: true,
      })
      message.success('Campaign created')
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const change = async (id: string, status: CampaignStatus) => {
    try {
      await surveysAdminApi.changeStatus(id, status)
      message.success(`Status changed to ${status}`)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Status change failed',
      )
    }
  }

  const cols: ColumnsType<CampaignResponse> = [
    {
      title: 'Campaign',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.name}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.templateCode}</Text>
        </Space>
      ),
    },
    {
      title: 'Window',
      width: 200,
      render: (_, r) => (
        <Text style={{ fontSize: 12 }}>
          {dayjs(r.opensOn).format('MMM D')} – {dayjs(r.closesOn).format('MMM D, YYYY')}
        </Text>
      ),
    },
    {
      title: 'Status',
      width: 100,
      render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag>,
    },
    {
      title: 'Responses',
      dataIndex: 'responseCount',
      width: 110,
      align: 'right',
      render: (v: number) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: 'Anonymous',
      width: 100,
      align: 'center',
      render: (_, r) => r.anonymous ? <Tag color="purple">yes</Tag> : <Tag>no</Tag>,
    },
    {
      title: '',
      width: 240,
      render: (_, r) => canWrite ? (
        <Space size={4}>
          {r.status === 'DRAFT' && (
            <Button size="small" type="primary" onClick={() => change(r.id, 'ACTIVE')}>
              Launch
            </Button>
          )}
          {r.status === 'ACTIVE' && (
            <Popconfirm title="Close this campaign? Responses will stop." onConfirm={() => change(r.id, 'CLOSED')}>
              <Button size="small">Close</Button>
            </Popconfirm>
          )}
          {(r.status === 'DRAFT' || r.status === 'ACTIVE') && (
            <Popconfirm title="Cancel this campaign?" onConfirm={() => change(r.id, 'CANCELLED')}>
              <Button size="small" danger>Cancel</Button>
            </Popconfirm>
          )}
        </Space>
      ) : null,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
        {canWrite && (
          <Button type="primary" onClick={startCreate} disabled={templates.length === 0}>
            New campaign…
          </Button>
        )}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={campaigns}
          size="small"
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: <Empty description="No campaigns yet" /> }}
        />
      </Card>

      <Modal
        open={open}
        title="New campaign"
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        okText="Create"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="templateId" label="Template" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={templates.map((t) => ({
                value: t.id,
                label: `${t.code} — ${t.name} (${t.questionCount} questions)`,
              }))}
              placeholder="Pick a template"
            />
          </Form.Item>
          <Form.Item name="name" label="Campaign name" rules={[{ required: true }]}>
            <Input placeholder="Q1 2026 engagement pulse" />
          </Form.Item>
          <Form.Item name="description" label="Description (optional)">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="window" label="Window" rules={[{ required: true }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

// ─── Results tab ─────────────────────────────────────────────────────────────

function ResultsTab() {
  const { message } = AntdApp.useApp()
  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([])
  const [selectedId, setSelectedId] = useState<string | undefined>()
  const [results, setResults] = useState<CampaignResults | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    surveysAdminApi
      .listCampaigns()
      .then((cs) => {
        setCampaigns(cs)
        const firstReportable = cs.find((c) => c.responseCount > 0)
        if (firstReportable) setSelectedId(firstReportable.id)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [message])

  useEffect(() => {
    if (!selectedId) { setResults(null); return }
    surveysAdminApi
      .results(selectedId)
      .then(setResults)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load results'))
  }, [selectedId, message])

  const overallNps = useMemo(() => {
    if (!results) return null
    const npsQuestion = results.questions.find((q) => q.netPromoterScore != null)
    return npsQuestion?.netPromoterScore ?? null
  }, [results])

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space>
        <Text>Campaign:</Text>
        <Select
          style={{ minWidth: 360 }}
          value={selectedId}
          onChange={setSelectedId}
          options={campaigns.map((c) => ({
            value: c.id,
            label: `${c.name} — ${c.responseCount} responses`,
          }))}
          placeholder="Pick a campaign"
        />
      </Space>

      {!results ? (
        <Empty description="No results to show. Pick a campaign with responses." />
      ) : (
        <>
          <Row gutter={16}>
            <Col span={8}>
              <Card><Statistic title="Responses" value={results.responseCount} /></Card>
            </Col>
            <Col span={8}>
              <Card><Statistic title="Questions" value={results.questions.length} /></Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic
                  title="Net Promoter Score"
                  value={overallNps != null ? overallNps : '—'}
                  valueStyle={{
                    color: overallNps == null ? undefined
                      : overallNps >= 30 ? '#52c41a'
                      : overallNps >= 0 ? '#fa8c16'
                      : '#ff4d4f',
                  }}
                  suffix={overallNps != null ? '/100' : ''}
                />
              </Card>
            </Col>
          </Row>

          {results.questions.map((q) => (
            <QuestionResult key={q.questionId} aggregate={q} />
          ))}
        </>
      )}
    </Space>
  )
}

function QuestionResult({ aggregate: q }: { aggregate: QuestionAggregate }) {
  return (
    <Card size="small" title={
      <Space>
        <Tag>{q.questionType}</Tag>
        <Text strong>{q.prompt}</Text>
        <Text type="secondary">{q.answeredCount} answer{q.answeredCount === 1 ? '' : 's'}</Text>
      </Space>
    }>
      {q.answeredCount === 0 ? (
        <Text type="secondary">No responses yet.</Text>
      ) : (
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {q.averageRating != null && (
            <Statistic title="Average" value={q.averageRating} valueStyle={{ fontSize: 18 }} />
          )}
          {q.netPromoterScore != null && (
            <Statistic
              title="NPS"
              value={q.netPromoterScore}
              suffix="/100"
              valueStyle={{
                fontSize: 18,
                color: q.netPromoterScore >= 30 ? '#52c41a'
                  : q.netPromoterScore >= 0 ? '#fa8c16' : '#ff4d4f',
              }}
            />
          )}
          {q.distribution && Object.keys(q.distribution).length > 0 && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>Distribution</Text>
              {Object.entries(q.distribution)
                .sort((a, b) => Number(a[0]) - Number(b[0]))
                .map(([score, count]) => (
                  <div key={score} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Text style={{ minWidth: 30 }}>{score}</Text>
                    <Progress
                      percent={Math.round((count / q.answeredCount) * 100)}
                      size="small"
                      format={() => `${count}`}
                      style={{ flex: 1 }}
                    />
                  </div>
                ))}
            </div>
          )}
          {q.choiceTallies && Object.keys(q.choiceTallies).length > 0 && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>Choices</Text>
              {Object.entries(q.choiceTallies).map(([choice, count]) => (
                <div key={choice} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Text style={{ minWidth: 80 }}>{choice}</Text>
                  <Progress
                    percent={Math.round((count / q.answeredCount) * 100)}
                    size="small"
                    format={() => `${count}`}
                    style={{ flex: 1 }}
                  />
                </div>
              ))}
            </div>
          )}
          {q.textSamples && q.textSamples.length > 0 && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>Sample comments</Text>
              {q.textSamples.map((s, i) => (
                <Paragraph key={i} style={{ marginBottom: 4 }}
                  italic type="secondary">— {s}</Paragraph>
              ))}
            </div>
          )}
        </Space>
      )}
    </Card>
  )
}

// ─── Page shell ──────────────────────────────────────────────────────────────

export function SurveysAdminPage() {
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Engagement surveys</Title>
      <Alert
        type="info"
        showIcon
        message="Phase 1"
        description="Templates + campaigns + anonymous or identified responses, plus per-question aggregates and eNPS. Campaign audience is the full active employee population in Phase 1; per-team segmentation lands in Phase 2."
      />
      <Tabs
        items={[
          { key: 'templates', label: 'Templates', children: <TemplatesTab /> },
          { key: 'campaigns', label: 'Campaigns', children: <CampaignsTab /> },
          { key: 'results',   label: 'Results',   children: <ResultsTab /> },
        ]}
      />
    </Space>
  )
}
