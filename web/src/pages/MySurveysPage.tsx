// M116 — Employee survey fill page.
//
// Lists the campaigns open right now; user picks one and answers
// every required question. Anonymous campaigns send a null employee_id
// so HR can't backtrack to the responder.

import { useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  Radio,
  Rate,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import {
  mySurveysApi,
  type AnswerRequest,
  type CampaignResponse,
  type QuestionResponse,
  type TemplateResponse,
} from '../api/surveys'

const { Title, Text, Paragraph } = Typography

export function MySurveysPage() {
  const { message } = AntdApp.useApp()
  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [active, setActive] = useState<CampaignResponse | null>(null)
  const [template, setTemplate] = useState<TemplateResponse | null>(null)
  const [templateLoading, setTemplateLoading] = useState(false)

  // Per-question answer map keyed by questionId.
  const [answers, setAnswers] = useState<Record<string, AnswerRequest>>({})
  const [comment, setComment] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState<Set<string>>(new Set())

  const load = () => {
    setLoading(true)
    mySurveysApi
      .openToday()
      .then(setCampaigns)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])  // eslint-disable-line

  const openCampaign = async (c: CampaignResponse) => {
    setActive(c)
    setAnswers({})
    setComment('')
    setTemplateLoading(true)
    try {
      const t = await mySurveysApi.template(c.templateId)
      setTemplate(t)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to load survey',
      )
      setActive(null)
    } finally {
      setTemplateLoading(false)
    }
  }

  const setAnswer = (q: QuestionResponse, patch: Partial<AnswerRequest>) => {
    setAnswers((prev) => ({
      ...prev,
      [q.id]: {
        ...(prev[q.id] ?? { questionId: q.id }),
        ...patch,
        questionId: q.id,
      },
    }))
  }

  const submit = async () => {
    if (!active || !template) return
    // Client-side check: every required question has an answer.
    const missing = template.questions.filter((q) => {
      if (!q.required) return false
      const a = answers[q.id]
      if (!a) return true
      switch (q.questionType) {
        case 'RATING_1_5':
        case 'RATING_1_10':
        case 'BOOLEAN':
          return a.ratingValue == null
        case 'TEXT':
          return !a.textValue || a.textValue.trim() === ''
        case 'MULTIPLE_CHOICE':
          return !a.choiceValue
      }
      return true
    })
    if (missing.length > 0) {
      message.error(`Please answer ${missing.length} required question${missing.length === 1 ? '' : 's'} first.`)
      return
    }
    setSubmitting(true)
    try {
      await mySurveysApi.submit({
        campaignId: active.id,
        comment: comment.trim() || undefined,
        answers: Object.values(answers),
      })
      message.success('Thanks! Your response was recorded.')
      setSubmitted((prev) => new Set(prev).add(active.id))
      setActive(null)
      setTemplate(null)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Submit failed',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const choicesFromMetadata = (q: QuestionResponse): string[] => {
    if (!q.metadata) return []
    try {
      const parsed = JSON.parse(q.metadata)
      if (Array.isArray(parsed.options)) return parsed.options as string[]
    } catch { /* ignore */ }
    return []
  }

  if (loading) return <Spin />

  // Active form view.
  if (active && template) {
    return (
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Title level={3} style={{ margin: 0 }}>{active.name}</Title>
        {template.anonymous && (
          <Alert
            type="info"
            showIcon
            message="This survey is anonymous"
            description="Your response is recorded without your identity attached. HR sees the aggregate, not who said what."
          />
        )}
        {active.description && (
          <Paragraph type="secondary">{active.description}</Paragraph>
        )}
        {templateLoading ? <Spin /> : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {template.questions.map((q, i) => (
              <Card key={q.id} size="small">
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  <Space>
                    <Tag>Q{i + 1}</Tag>
                    <Text strong>{q.prompt}</Text>
                    {q.required && <Tag color="red">required</Tag>}
                  </Space>
                  {q.questionType === 'RATING_1_5' && (
                    <Rate
                      value={answers[q.id]?.ratingValue ?? 0}
                      onChange={(v) => setAnswer(q, { ratingValue: v })}
                    />
                  )}
                  {q.questionType === 'RATING_1_10' && (
                    <Radio.Group
                      value={answers[q.id]?.ratingValue}
                      onChange={(e) => setAnswer(q, { ratingValue: e.target.value })}
                    >
                      {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => (
                        <Radio.Button key={n} value={n} style={{ width: 44, textAlign: 'center' }}>
                          {n}
                        </Radio.Button>
                      ))}
                    </Radio.Group>
                  )}
                  {q.questionType === 'BOOLEAN' && (
                    <Radio.Group
                      value={answers[q.id]?.ratingValue}
                      onChange={(e) => setAnswer(q, { ratingValue: e.target.value })}
                    >
                      <Radio.Button value={1}>Yes</Radio.Button>
                      <Radio.Button value={0}>No</Radio.Button>
                    </Radio.Group>
                  )}
                  {q.questionType === 'TEXT' && (
                    <Input.TextArea
                      rows={3}
                      value={answers[q.id]?.textValue ?? ''}
                      onChange={(e) => setAnswer(q, { textValue: e.target.value })}
                    />
                  )}
                  {q.questionType === 'MULTIPLE_CHOICE' && (
                    <Radio.Group
                      value={answers[q.id]?.choiceValue}
                      onChange={(e) => setAnswer(q, { choiceValue: e.target.value })}
                    >
                      <Space direction="vertical">
                        {choicesFromMetadata(q).map((c) => (
                          <Radio key={c} value={c}>{c}</Radio>
                        ))}
                      </Space>
                    </Radio.Group>
                  )}
                </Space>
              </Card>
            ))}
            <Card size="small" title="Anything else? (optional)">
              <Input.TextArea
                rows={3}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Free-text feedback for the team."
              />
            </Card>
            <Space>
              <Button onClick={() => { setActive(null); setTemplate(null) }}>Cancel</Button>
              <Button type="primary" loading={submitting} onClick={submit}>Submit response</Button>
            </Space>
          </Space>
        )}
      </Space>
    )
  }

  // List view.
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ margin: 0 }}>Surveys open for you</Title>
      <Paragraph type="secondary">
        Engagement and feedback surveys that are open right now.
        Once submitted, a survey disappears from this list (you can't submit twice).
      </Paragraph>
      {campaigns.length === 0 ? (
        <Empty description="No open surveys right now. Check back later." />
      ) : (
        <Space direction="vertical" style={{ width: '100%' }}>
          {campaigns.map((c) => (
            <Card key={c.id}>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Space direction="vertical" size={0}>
                  <Text strong>{c.name}</Text>
                  {c.description && (
                    <Text type="secondary">{c.description}</Text>
                  )}
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Open through {c.closesOn}
                    {c.anonymous && ' · Anonymous'}
                  </Text>
                </Space>
                <Button
                  type="primary"
                  disabled={submitted.has(c.id)}
                  onClick={() => openCampaign(c)}
                >
                  {submitted.has(c.id) ? 'Submitted' : 'Take survey'}
                </Button>
              </Space>
            </Card>
          ))}
        </Space>
      )}
    </Space>
  )
}
